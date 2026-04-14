package com.smartemergency.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * EmergencyDetectionService
 *
 * A foreground service that runs in the background and monitors two SOS triggers:
 *   1. Shake detection  – shake the phone hard 3× within 2 seconds.
 *   2. Power button     – press the power button 3× rapidly (screen-off events).
 *
 * When either trigger fires, [triggerSOS] is called which sends an SMS to all
 * saved emergency contacts and launches AlertStatusActivity.
 *
 * The service is started from MainActivity when monitoring is enabled and is
 * declared in AndroidManifest.xml with foregroundServiceType="specialUse".
 */
class EmergencyDetectionService : Service(), SensorEventListener {

    // ── Notification ─────────────────────────────────────────────────────
    companion object {
        private const val TAG = "EmergencyDetectionSvc"
        private const val CHANNEL_ID = "emergency_detection_channel"
        private const val NOTIFICATION_ID = 42

        // Shake thresholds
        private const val SHAKE_THRESHOLD_G = 2.7f        // force (in G) to count as a shake
        private const val SHAKE_COUNT_REQUIRED = 3         // shakes needed to trigger
        private const val SHAKE_RESET_TIME_MS = 2000L      // window to accumulate shakes

        // Power button thresholds
        private const val POWER_PRESS_COUNT_REQUIRED = 3
        private const val POWER_PRESS_RESET_TIME_MS = 2000L

        // SOS burst settings
        /** Total number of SOS messages sent per trigger event. */
        private const val SOS_MESSAGE_COUNT = 3
        /** Delay between consecutive SOS messages (30 seconds). */
        private const val SOS_MESSAGE_INTERVAL_MS = 30_000L
    }

    // ── Sensor ────────────────────────────────────────────────────────────
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var shakeCount = 0
    private var lastShakeTime = 0L

    // ── Power button receiver ─────────────────────────────────────────────
    private var powerButtonReceiver: BroadcastReceiver? = null
    private var powerPressCount = 0
    private var lastPowerPressTime = 0L

    // ── SOS burst state ───────────────────────────────────────────────────
    /**
     * True while a SOS burst is in progress (messages still being scheduled).
     * Any new trigger is silently ignored until the burst completes.
     */
    private var isSosBurstActive = false
    private var sosBurstMessagesSent = 0
    private val sosHandler = Handler(Looper.getMainLooper())

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerShakeDetector()
        registerPowerButtonReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        // START_STICKY – system restarts the service if killed
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        powerButtonReceiver?.let { unregisterReceiver(it) }
        sosHandler.removeCallbacksAndMessages(null)   // cancel any pending SOS messages
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Shake Detection ───────────────────────────────────────────────────

    private fun registerShakeDetector() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.d(TAG, "Accelerometer registered")
        } ?: Log.w(TAG, "No accelerometer found on this device")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate total force in G (subtract gravity constant 9.8 to get net force)
        val gForce = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat() / SensorManager.GRAVITY_EARTH

        if (gForce > SHAKE_THRESHOLD_G) {
            val now = SystemClock.elapsedRealtime()

            // Reset count if outside the time window
            if (now - lastShakeTime > SHAKE_RESET_TIME_MS) {
                shakeCount = 0
            }

            shakeCount++
            lastShakeTime = now
            Log.d(TAG, "Shake detected! Count: $shakeCount / $SHAKE_COUNT_REQUIRED")

            if (shakeCount >= SHAKE_COUNT_REQUIRED) {
                shakeCount = 0
                Log.d(TAG, "Shake SOS triggered!")
                triggerSOS(source = "Shake")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    // ── Power Button Detection ─────────────────────────────────────────────

    private fun registerPowerButtonReceiver() {
        powerButtonReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    val now = SystemClock.elapsedRealtime()

                    if (now - lastPowerPressTime > POWER_PRESS_RESET_TIME_MS) {
                        powerPressCount = 0
                    }

                    powerPressCount++
                    lastPowerPressTime = now
                    Log.d(TAG, "Power button press detected! Count: $powerPressCount / $POWER_PRESS_COUNT_REQUIRED")

                    if (powerPressCount >= POWER_PRESS_COUNT_REQUIRED) {
                        powerPressCount = 0
                        Log.d(TAG, "Power button SOS triggered!")
                        triggerSOS(source = "Power Button")
                    }
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(powerButtonReceiver, filter)
        Log.d(TAG, "Power button receiver registered")
    }

    // ── SOS Trigger ───────────────────────────────────────────────────────

    /**
     * Central SOS trigger. Called by either the shake or power-button detector.
     *
     * Behaviour:
     *   • If a burst is already running, this call is ignored (no duplicate bursts).
     *   • Otherwise, starts a burst: sends [SOS_MESSAGE_COUNT] messages to every
     *     emergency contact, with [SOS_MESSAGE_INTERVAL_MS] (30 s) between sends.
     *   • AlertStatusActivity is launched immediately on the first trigger.
     *
     * @param source Human-readable label for logging ("Shake" or "Power Button").
     */
    private fun triggerSOS(source: String) {
        if (isSosBurstActive) {
            Log.d(TAG, "SOS burst already in progress – ignoring $source trigger")
            return
        }

        Log.i(TAG, "SOS triggered via $source – starting $SOS_MESSAGE_COUNT-message burst")
        isSosBurstActive = true
        sosBurstMessagesSent = 0

        // Launch AlertStatusActivity immediately (needs FLAG_ACTIVITY_NEW_TASK from a Service)
        val alertIntent = Intent(this, AlertStatusActivity::class.java).apply {
            putExtra("ALERT_ACTIVE", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(alertIntent)

        // ── Push to Guardian Dashboard instantly ──
        pushAlertToFirestore("Emergency", "SOS automatically triggered by $source!")

        // Schedule the SMS burst
        scheduleSosBurst()
    }

    /**
     * Publishes the background hardware-triggered SOS to Firestore 
     * so it immediately pops up on the Parent's Dashboard.
     */
    private fun pushAlertToFirestore(status: String, msg: String) {
        val prefs = getSharedPreferences("smart_emergency_prefs", Context.MODE_PRIVATE)
        val phoneNumber = prefs.getString("my_phone_number", "") ?: ""
        
        val data = hashMapOf(
            "status" to status,
            "message" to msg,
            "timestamp" to System.currentTimeMillis(),
            "phoneNumber" to phoneNumber
        )
        FirebaseFirestore.getInstance().collection("alerts")
            .document("latest")
            .set(data, SetOptions.merge())
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to push background alert to Guardian", e)
            }
    }

    /**
     * Schedules [SOS_MESSAGE_COUNT] SMS sends, each separated by [SOS_MESSAGE_INTERVAL_MS].
     *
     *  Message 1 → sent immediately (delay = 0)
     *  Message 2 → sent after 30 s
     *  Message 3 → sent after 60 s
     */
    private fun scheduleSosBurst() {
        for (i in 0 until SOS_MESSAGE_COUNT) {
            val delayMs = i * SOS_MESSAGE_INTERVAL_MS
            sosHandler.postDelayed({
                sosBurstMessagesSent++
                Log.i(TAG, "Sending SOS message $sosBurstMessagesSent / $SOS_MESSAGE_COUNT")
                sendEmergencySMS(messageNumber = sosBurstMessagesSent)

                // Reset burst state after the last message
                if (sosBurstMessagesSent >= SOS_MESSAGE_COUNT) {
                    resetSosBurst()
                }
            }, delayMs)
        }
    }

    /** Clears the active-burst flag so the next shake/press can trigger again. */
    private fun resetSosBurst() {
        isSosBurstActive = false
        sosBurstMessagesSent = 0
        Log.d(TAG, "SOS burst complete – detector re-armed")
    }

    /**
     * Sends one round of SOS SMS to every saved emergency contact.
     *
     * @param messageNumber Which send in the burst this is (1, 2, or 3), included
     *                      in the message so the recipient knows it's a repeated alert.
     */
    private fun sendEmergencySMS(messageNumber: Int = 1) {
        val contacts = ContactManager.getContacts(this)
        if (contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts – SMS not sent")
            return
        }

        val message = "[SOS $messageNumber/$SOS_MESSAGE_COUNT] EMERGENCY! I need help! " +
                "This alert was triggered automatically on my phone. " +
                "Please call me or contact the authorities immediately."

        try {
            @Suppress("DEPRECATION")
            val smsManager: SmsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)

            for (contact in contacts) {
                smsManager.sendMultipartTextMessage(contact.phone, null, parts, null, null)
                Log.d(TAG, "SOS SMS $messageNumber sent to ${contact.name} (${contact.phone})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SOS SMS $messageNumber", e)
        }
    }

    // ── Foreground Notification ────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Emergency Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors shake and power button for SOS trigger"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmartEmergency Active")
            .setContentText("Shake or press power 3× to send SOS")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
