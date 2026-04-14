package com.smartemergency.app

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.smartemergency.app.databinding.ActivityMainBinding
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var countDownTimer: CountDownTimer? = null
    private var isMonitoring = false
    private var isCountdownActive = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    /** Handles idle-location detection and automatic SMS alerting. */
    private lateinit var idleLocationMonitor: IdleLocationMonitor

    // ── Firebase ────────────────────────────────────────────────────
    private val firestore = FirebaseFirestore.getInstance()
    private var locationCallback: LocationCallback? = null
    private var locationRequest: LocationRequest? = null

    // Status enum
    enum class SafetyStatus { SAFE, MONITORING, HIGH_RISK }
    private var currentStatus = SafetyStatus.SAFE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        idleLocationMonitor = IdleLocationMonitor(this)


        setupSosButton()
        setupMonitoringToggle()
        setupBottomNavigation()
        startSosPulseAnimation()
        updateStatusUI(SafetyStatus.SAFE)
    }

    // ────────────────────────────────────────────
    // SOS Button
    // ────────────────────────────────────────────

    private fun setupSosButton() {
        binding.btnSos.setOnClickListener {
            if (!isCountdownActive) {
                startCountdown()
            }
        }

        binding.btnCancelCountdown.setOnClickListener {
            cancelCountdown()
        }
    }

    private fun startCountdown() {
        isCountdownActive = true
        binding.cardCountdown.visibility = View.VISIBLE

        countDownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt() + 1
                binding.tvCountdownNumber.text = secondsLeft.toString()
            }

            override fun onFinish() {
                isCountdownActive = false
                binding.cardCountdown.visibility = View.GONE
                triggerEmergency()
            }
        }.start()
    }

    private fun cancelCountdown() {
        countDownTimer?.cancel()
        isCountdownActive = false
        binding.cardCountdown.visibility = View.GONE
        Toast.makeText(this, "Emergency cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun startSosPulseAnimation() {
        val pulseAnim = ScaleAnimation(
            1.0f, 1.08f, 1.0f, 1.08f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 1000
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        binding.viewSosOuterRing.startAnimation(pulseAnim)
    }

    // ────────────────────────────────────────────
    // Status Management
    // ────────────────────────────────────────────

    private fun updateStatusUI(status: SafetyStatus) {
        currentStatus = status
        when (status) {
            SafetyStatus.SAFE -> {
                binding.tvStatusBadge.text = getString(R.string.status_safe)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.safe_green))
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_safe)
                binding.tvStatusText.text = getString(R.string.status_safe)
                binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.safe_green))
                binding.viewStatusDot.setBackgroundResource(R.drawable.bg_dot_active)
            }
            SafetyStatus.MONITORING -> {
                binding.tvStatusBadge.text = getString(R.string.status_monitoring)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.monitoring_amber))
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_monitoring)
                binding.tvStatusText.text = getString(R.string.status_monitoring)
                binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.monitoring_amber))
                binding.viewStatusDot.setBackgroundResource(R.drawable.bg_dot_active)
            }
            SafetyStatus.HIGH_RISK -> {
                binding.tvStatusBadge.text = getString(R.string.status_high_risk)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.danger_red))
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_danger)
                binding.tvStatusText.text = getString(R.string.status_high_risk)
                binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.danger_red))
                binding.viewStatusDot.setBackgroundResource(R.drawable.bg_dot_active)
            }
        }
    }

    // ────────────────────────────────────────────
    // Background Monitoring Toggle
    // ────────────────────────────────────────────

    private fun setupMonitoringToggle() {
        binding.switchMonitoring.setOnCheckedChangeListener { _, isChecked ->
            isMonitoring = isChecked
            if (isChecked) {
                binding.tvMonitoringStatus.text = getString(R.string.monitoring_enabled)
                binding.tvMonitoringStatus.setTextColor(
                    ContextCompat.getColor(this, R.color.safe_green)
                )
                updateStatusUI(SafetyStatus.MONITORING)
                startMonitoring()
            } else {
                binding.tvMonitoringStatus.text = getString(R.string.monitoring_disabled)
                binding.tvMonitoringStatus.setTextColor(
                    ContextCompat.getColor(this, R.color.text_hint)
                )
                updateStatusUI(SafetyStatus.SAFE)
                stopMonitoring()
            }
        }
    }

    // ────────────────────────────────────────────
    // Bottom Navigation
    // ────────────────────────────────────────────

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_contacts -> {
                    startActivity(Intent(this, EmergencyContactsActivity::class.java))
                    true
                }
                R.id.nav_tracking -> {
                    startActivity(Intent(this, LiveTrackingActivity::class.java))
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }


    private fun sendSMS(message: String) {
        android.util.Log.d("SMS_DEBUG","sendSMS called")

        val contacts = ContactManager.getContacts(this)
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No emergency contacts configured!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val smsManager = SmsManager.getDefault()
            for (contact in contacts) {
                smsManager.sendTextMessage(contact.phone, null, message, null, null)
            }
            Toast.makeText(this, "SOS Sent to ${contacts.size} contacts!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "SMS failed", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            // SOS emergency grant
            1 -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    sendSOS()
                } else {
                    Toast.makeText(this, "Permissions required!", Toast.LENGTH_SHORT).show()
                }
            }
            // Location permission grant for idle monitoring
            IdleLocationMonitor.LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted – retry starting the monitor
                    idleLocationMonitor.startLocationMonitoring()
                    Toast.makeText(this, "Location granted – idle monitoring started", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Location permission denied – idle monitoring disabled", Toast.LENGTH_SHORT).show()
                    binding.switchMonitoring.isChecked = false
                }
            }
        }
    }



    override fun onResume() {
        super.onResume()
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        updateLastLocationUI()
    }

    private fun updateLastLocationUI() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    binding.tvLastLocation.text = "Lat: ${"%.4f".format(location.latitude)}, Lon: ${"%.4f".format(location.longitude)}"
                } else {
                    binding.tvLastLocation.text = "Location unavailable"
                }
            }.addOnFailureListener {
                binding.tvLastLocation.text = "Failed to get location"
            }
        } else {
            binding.tvLastLocation.text = "Location permission required"
        }
    }

    // ────────────────────────────────────────────
    // Placeholder Functions (No backend logic)
    // ────────────────────────────────────────────

    /**
     * Placeholder: Triggers the emergency alert sequence.
     * In a real app, this would send SMS, share location, etc.
     */
    private fun triggerEmergency() {
        updateStatusUI(SafetyStatus.HIGH_RISK)

        sendSOS()

        // ── Write Emergency status to Firestore so Parent sees it ──
        pushAlertToFirestore("Emergency", "SOS triggered by user!")

        Toast.makeText(this, "Emergency Triggered!", Toast.LENGTH_LONG).show()

        val intent = Intent(this, AlertStatusActivity::class.java)
        intent.putExtra("ALERT_ACTIVE", true)
        startActivity(intent)
    }

    private fun sendSOS() {
        android.util.Log.d("SMS_DEBUG","sendSOS called")

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                1
            )
            return
        }

        val baseMessage = "I am in danger! Please help!"

        // Try getting location, push to Firestore, and send SMS
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude
                    // Push location to Firestore for Guardian Dashboard
                    pushLocationToFirestore(lat, lon)
                    val locationMsg = "$baseMessage My location: https://maps.google.com/?q=$lat,$lon"
                    sendSMS(locationMsg)
                } else {
                    sendSMS(baseMessage)
                }
            }.addOnFailureListener {
                sendSMS(baseMessage)
            }
        } else {
            sendSMS(baseMessage)
        }
    }

    // ── Firestore Write Helpers ──────────────────────────────────────

    /**
     * Pushes a status + message to Firestore alerts/latest.
     * The Guardian Dashboard reads this in real-time.
     */
    private fun pushAlertToFirestore(status: String, message: String = "") {
        val prefs = getSharedPreferences("smart_emergency_prefs", MODE_PRIVATE)
        val phoneNumber = prefs.getString("my_phone_number", "") ?: ""

        val data = hashMapOf(
            "status" to status,
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "phoneNumber" to phoneNumber
        )
        firestore.collection("alerts")
            .document("latest")
            .set(data, SetOptions.merge())
            .addOnFailureListener { e ->
                android.util.Log.e("Firestore", "Failed to push alert", e)
            }
    }

    /**
     * Pushes current GPS coordinates to Firestore trackingUpdates/latest.
     * The Guardian Dashboard map updates from this.
     */
    private fun pushLocationToFirestore(lat: Double, lon: Double) {
        val data = hashMapOf(
            "latitude" to lat,
            "longitude" to lon,
            "timestamp" to System.currentTimeMillis()
        )
        firestore.collection("trackingUpdates")
            .document("latest")
            .set(data, SetOptions.merge())
            .addOnFailureListener { e ->
                android.util.Log.e("Firestore", "Failed to push location", e)
            }
    }



    /**
     * Starts idle-location monitoring.
     * If ACCESS_FINE_LOCATION has not been granted yet, the system permission
     * dialog is shown first. Once the user grants it, [onRequestPermissionsResult]
     * automatically retries [IdleLocationMonitor.startLocationMonitoring].
     */
    private fun startMonitoring() {
        if (idleLocationMonitor.needsLocationPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                IdleLocationMonitor.LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        idleLocationMonitor.startLocationMonitoring()

        // Start Emergency Detection Service
        val serviceIntent = Intent(this, EmergencyDetectionService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // ── Start periodic location updates → push to Firestore ──
        startLiveLocationUpdates()

        // Mark status as Moving in Firestore
        pushAlertToFirestore("Moving")

        Toast.makeText(this, "Idle & Emergency monitoring started", Toast.LENGTH_SHORT).show()
    }

    @Suppress("MissingPermission")
    private fun startLiveLocationUpdates() {
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 15_000L // every 15 seconds
        ).setMinUpdateIntervalMillis(10_000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                pushLocationToFirestore(loc.latitude, loc.longitude)
                // Update status to Moving while location is changing
                pushAlertToFirestore("Moving")
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest!!,
                locationCallback!!,
                mainLooper
            )
        }
    }

    /**
     * Stops idle-location monitoring and cancels any pending idle alert.
     */
    private fun stopMonitoring() {
        idleLocationMonitor.stopLocationMonitoring()

        // Stop periodic location updates
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null

        // Mark status as Idle in Firestore
        pushAlertToFirestore("Idle")

        // Stop Emergency Detection Service
        val serviceIntent = Intent(this, EmergencyDetectionService::class.java)
        stopService(serviceIntent)

        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        idleLocationMonitor.stopLocationMonitoring()
    }
}
