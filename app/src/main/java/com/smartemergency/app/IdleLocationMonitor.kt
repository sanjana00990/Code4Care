package com.smartemergency.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*

/**
 * IdleLocationMonitor
 *
 * Monitors the user's location continuously using FusedLocationProviderClient.
 * If the user does NOT move more than [IDLE_DISTANCE_METERS] (≈20 m) for
 * [IDLE_TIMEOUT_MS] (5 minutes), an SMS is automatically sent to the
 * hardcoded emergency contact.
 *
 * Usage:
 *   val monitor = IdleLocationMonitor(context)
 *   monitor.startLocationMonitoring()   // start
 *   monitor.stopLocationMonitoring()    // stop (call from onDestroy / toggle-off)
 *
 * Required permissions (already declared in AndroidManifest.xml):
 *   ACCESS_FINE_LOCATION, SEND_SMS
 */
class IdleLocationMonitor(private val context: Context) {

    companion object {
        private const val TAG = "IdleLocationMonitor"

        /** Radius within which the user is considered "not moved" (metres). */
        private const val IDLE_DISTANCE_METERS = 20f

        /** How often FusedLocationProvider delivers a new fix (ms). */
        private const val LOCATION_INTERVAL_MS = 30_000L      // every 30 s

        /** Fastest rate at which location updates will arrive (ms). */
        private const val LOCATION_FASTEST_INTERVAL_MS = 15_000L

        /** Request code used when MainActivity asks for location permission on our behalf. */
        const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    /**
     * Returns true if ACCESS_FINE_LOCATION has NOT yet been granted.
     * Call this from MainActivity before calling [startLocationMonitoring].
     */
    fun needsLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED

    // ── FusedLocationProviderClient ──────────────────────────────────────
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // ── Location request: balanced accuracy / battery trade-off ─────────
    private val locationRequest: LocationRequest = LocationRequest.Builder(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        LOCATION_INTERVAL_MS
    )
        .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL_MS)
        // Only deliver an update if the user moved at least 5 m — cuts updates
        // when truly stationary, saving battery.
        .setMinUpdateDistanceMeters(5f)
        .build()

    // ── State ────────────────────────────────────────────────────────────

    /** The reference location captured when tracking began or when the user last moved. */
    private var lastKnownLocation: Location? = null

    /** True while location updates are being received. */
    private var isMonitoring = false

    /** True once an SMS has been sent for the current idle period (prevents repeat sends). */
    private var smsSentForCurrentIdle = false

    // ── Handler / Runnable for the 5-minute countdown ───────────────────
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Runnable executed when IDLE_TIMEOUT_MS elapses without movement.
     * Sends the emergency SMS.
     */
    private val idleTimeoutRunnable = Runnable {
        Log.d(TAG, "Idle timeout reached – sending SMS")
        sendIdleAlert()
    }

    // ── Location callback ────────────────────────────────────────────────

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val newLocation = result.lastLocation ?: return
            handleNewLocation(newLocation)
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────

    /**
     * Starts continuous location monitoring and the idle-detection logic.
     * Checks for ACCESS_FINE_LOCATION permission before registering updates.
     */
    fun startLocationMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring – ignoring duplicate start call")
            return
        }

        // Guard: location permission must be granted.
        // If not granted, the caller (MainActivity) is responsible for requesting it
        // and calling startLocationMonitoring() again after the grant.
        if (needsLocationPermission()) {
            Log.w(TAG, "ACCESS_FINE_LOCATION not granted – cannot start monitoring")
            Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
            return
        }

        isMonitoring = true
        smsSentForCurrentIdle = false

        // Register location updates on the main looper
        fusedClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        // Kick off the idle timer immediately so that if no fix arrives within
        // 5 minutes we still alert.
        resetIdleTimer()

        Log.d(TAG, "Location monitoring started")
    }

    /**
     * Stops location updates and cancels any pending idle-alert runnable.
     * Call this when monitoring is toggled off or in onDestroy().
     */
    fun stopLocationMonitoring() {
        if (!isMonitoring) return

        isMonitoring = false
        fusedClient.removeLocationUpdates(locationCallback)
        handler.removeCallbacks(idleTimeoutRunnable)
        lastKnownLocation = null
        smsSentForCurrentIdle = false

        Log.d(TAG, "Location monitoring stopped")
    }

    // ────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ────────────────────────────────────────────────────────────────────

    /**
     * Called each time FusedLocationProvider delivers a fresh fix.
     *
     * Logic:
     *   • First fix → store it as the reference and start the idle timer.
     *   • Subsequent fixes → compare distance to reference.
     *       – Moved ≥ IDLE_DISTANCE_METERS → update reference & reset timer.
     *       – Stayed within IDLE_DISTANCE_METERS → do nothing (timer keeps running).
     */
    private fun handleNewLocation(newLocation: Location) {
        val ref = lastKnownLocation

        if (ref == null) {
            // Very first fix: save it and start counting idle time
            lastKnownLocation = newLocation
            resetIdleTimer()
            Log.d(TAG, "First location fix: ${newLocation.latitude}, ${newLocation.longitude}")
            return
        }

        val distanceMoved = ref.distanceTo(newLocation)
        Log.d(TAG, "Distance moved: ${"%.1f".format(distanceMoved)} m")

        if (distanceMoved >= IDLE_DISTANCE_METERS) {
            // User moved significantly → reset everything
            lastKnownLocation = newLocation
            smsSentForCurrentIdle = false   // allow a future alert if idle again
            resetIdleTimer()
            Log.d(TAG, "User moved – idle timer reset")
        }
        // else: user is still near the reference; let the timer run
    }

    private fun getIdleTimeoutMs(): Long {
        val prefs = context.getSharedPreferences("smart_emergency_prefs", Context.MODE_PRIVATE)
        val minutesStr = prefs.getString("idle_threshold", "15") ?: "15"
        val minutes = minutesStr.toLongOrNull() ?: 15L
        return minutes * 60 * 1000L
    }

    /**
     * Cancels any pending idle runnable and schedules a fresh one
     * based on the user's saved threshold.
     */
    private fun resetIdleTimer() {
        handler.removeCallbacks(idleTimeoutRunnable)
        val timeoutMs = getIdleTimeoutMs()
        handler.postDelayed(idleTimeoutRunnable, timeoutMs)
        Log.d(TAG, "Idle timer reset – alert in ${timeoutMs / 60_000} min")
    }

    /**
     * Builds the alert message using the stored reference location and sends it
     * via SmsManager. Duplicate sends within the same idle period are suppressed.
     */
    private fun sendIdleAlert() {
        // Prevent duplicate SMS for the same idle period
        if (smsSentForCurrentIdle) {
            Log.d(TAG, "SMS already sent for this idle period – skipping")
            return
        }

        // Guard: SEND_SMS permission must be granted
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "SEND_SMS not granted – cannot send idle alert")
            Toast.makeText(context, "SMS permission required", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = context.getSharedPreferences("smart_emergency_prefs", Context.MODE_PRIVATE)
        val savedName = prefs.getString("my_name", "")?.trim() ?: ""
        val alertUserName = if (savedName.isNotEmpty()) savedName else "The user"

        val location = lastKnownLocation
        val message = if (location != null) {
            val lat = location.latitude
            val lon = location.longitude
            "$alertUserName is idle at this location: https://maps.google.com/?q=$lat,$lon"
        } else {
            // Rare edge-case: timer fired before any fix arrived
            "$alertUserName appears idle but location is unavailable."
        }

        val contacts = ContactManager.getContacts(context)
        if (contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts saved – cannot send idle alert")
            Toast.makeText(context, "No emergency contacts configured!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            @Suppress("DEPRECATION")   // getDefault() is fine for API < 31; use
            // context-based SmsManager on API 31+ if needed
            val smsManager: SmsManager = SmsManager.getDefault()

            // Split the message in case it exceeds 160 characters
            val parts = smsManager.divideMessage(message)

            for (contact in contacts) {
                smsManager.sendMultipartTextMessage(contact.phone, null, parts, null, null)
                Log.d(TAG, "Idle alert SMS sent to ${contact.name} (${contact.phone}): $message")
            }

            smsSentForCurrentIdle = true
            Toast.makeText(context, "Idle alert sent to ${contacts.size} contacts!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send idle alert SMS", e)
            Toast.makeText(context, "Failed to send idle alert", Toast.LENGTH_SHORT).show()
        }
    }
}
