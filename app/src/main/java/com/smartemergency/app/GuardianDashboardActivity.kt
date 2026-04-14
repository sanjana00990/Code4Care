package com.smartemergency.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.smartemergency.app.databinding.ActivityGuardianDashboardBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guardian Dashboard – shows the tracked user's live status, location on
 * OpenStreetMap (no API key required), and provides one-tap action buttons.
 *
 * Architecture: observes [GuardianViewModel] LiveData which is backed by
 * [GuardianRepository] snapshot listeners on Firestore.
 */
class GuardianDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuardianDashboardBinding
    private val viewModel: GuardianViewModel by viewModels()

    private lateinit var mapView: MapView
    private var userMarker: Marker? = null
    private var isFirstLocation = true

    // Last known coordinates for action buttons
    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0

    // Keep an in-memory timeline of emergencies so they stay on screen
    private val alertHistory = mutableListOf<GuardianRepository.AlertData>()

    // ── Lifecycle ───────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OSMDroid configuration (must be done before inflating the layout)
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityGuardianDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupMap()
        setupActionButtons()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    // ── Toolbar ─────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.toolbarGuardian.setNavigationOnClickListener { finish() }
    }

    // ── OpenStreetMap ───────────────────────────────────────────────

    private fun setupMap() {
        mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        val mapController = mapView.controller
        mapController.setZoom(16.0)

        // Default centre (India)
        val defaultPoint = GeoPoint(20.5937, 78.9629)
        mapController.setCenter(defaultPoint)
    }

    private fun updateMapMarker(lat: Double, lon: Double) {
        if (lat == 0.0 && lon == 0.0) return

        val position = GeoPoint(lat, lon)

        if (userMarker == null) {
            userMarker = Marker(mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "User Location"
            }
            mapView.overlays.add(userMarker)
        }

        userMarker?.position = position

        if (isFirstLocation) {
            mapView.controller.animateTo(position)
            mapView.controller.setZoom(17.0)
            isFirstLocation = false
        } else {
            mapView.controller.animateTo(position)
        }

        mapView.invalidate()
    }

    // ── Action Buttons ──────────────────────────────────────────────

    private fun setupActionButtons() {
        binding.btnCallPolice.setOnClickListener {
            // "112" is the standard National Emergency Number in India (and many other places)
            val policeNumber = "112"
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$policeNumber"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot open dialer", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenMaps.setOnClickListener {
            if (lastLat != 0.0 || lastLon != 0.0) {
                val uri = Uri.parse("geo:$lastLat,$lastLon?q=$lastLat,$lastLon(User+Location)")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback: open in browser
                    val webUri = Uri.parse("https://maps.google.com/?q=$lastLat,$lastLon")
                    startActivity(Intent(Intent.ACTION_VIEW, webUri))
                }
            } else {
                Toast.makeText(this, "Location not available yet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Observe ViewModel ───────────────────────────────────────────

    private fun observeViewModel() {
        // Loading state
        viewModel.isLoading.observe(this) { loading ->
            binding.tvGuardianStatus.text = if (loading) "Connecting…" else ""
        }

        // Alert / Status updates
        viewModel.alertData.observe(this) { alert ->
            updateStatusUI(alert)
        }

        // Location updates
        viewModel.trackingData.observe(this) { tracking ->
            updateLocationUI(tracking)
            updateMapMarker(tracking.latitude, tracking.longitude)
        }

        // Errors
        viewModel.error.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ── UI Helpers ──────────────────────────────────────────────────

    private fun updateStatusUI(alert: GuardianRepository.AlertData) {
        val status = alert.status

        // Status text
        binding.tvGuardianStatus.text = status

        // Status dot & badge colour
        when (status.lowercase()) {
            "emergency" -> {
                binding.viewStatusDotGuardian.setBackgroundResource(R.drawable.bg_status_danger)
                binding.tvStatusBadgeGuardian.text = "EMERGENCY"
                binding.tvStatusBadgeGuardian.setTextColor(
                    ContextCompat.getColor(this, R.color.danger_red)
                )
                binding.tvStatusBadgeGuardian.setBackgroundResource(R.drawable.bg_status_danger)
            }
            "moving" -> {
                binding.viewStatusDotGuardian.setBackgroundResource(R.drawable.bg_status_safe)
                binding.tvStatusBadgeGuardian.text = "LIVE"
                binding.tvStatusBadgeGuardian.setTextColor(
                    ContextCompat.getColor(this, R.color.safe_green)
                )
                binding.tvStatusBadgeGuardian.setBackgroundResource(R.drawable.bg_status_safe)
            }
            else -> { // Idle or unknown
                binding.viewStatusDotGuardian.setBackgroundResource(R.drawable.bg_status_monitoring)
                binding.tvStatusBadgeGuardian.text = "IDLE"
                binding.tvStatusBadgeGuardian.setTextColor(
                    ContextCompat.getColor(this, R.color.monitoring_amber)
                )
                binding.tvStatusBadgeGuardian.setBackgroundResource(R.drawable.bg_status_monitoring)
            }
        }

        // Last updated time
        if (alert.timestamp > 0) {
            val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            binding.tvLastUpdated.text = sdf.format(Date(alert.timestamp))
        }

        // Alert message (shown only during emergencies)
        if (alert.message.isNotBlank()) {
            binding.tvAlertMessage.visibility = View.VISIBLE
            binding.tvAlertMessage.text = "⚠ ${alert.message}"
        } else {
            binding.tvAlertMessage.visibility = View.GONE
        }

        // Recent alerts section
        updateRecentAlerts(alert)
    }

    private fun updateLocationUI(tracking: GuardianRepository.TrackingData) {
        lastLat = tracking.latitude
        lastLon = tracking.longitude

        binding.tvGuardianLat.text = "%.6f".format(tracking.latitude)
        binding.tvGuardianLon.text = "%.6f".format(tracking.longitude)
    }

    private fun updateRecentAlerts(alert: GuardianRepository.AlertData) {
        // If it's an emergency, add it to our history list (avoiding duplicates by timestamp)
        if (alert.status.lowercase() == "emergency") {
            if (alertHistory.none { it.timestamp == alert.timestamp }) {
                alertHistory.add(0, alert) // Insert at top
            }
        }

        // Render the history list
        if (alertHistory.isNotEmpty()) {
            binding.tvNoAlerts.visibility = View.GONE
            binding.layoutAlertsList.visibility = View.VISIBLE
            binding.layoutAlertsList.removeAllViews()

            val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())

            for (historyAlert in alertHistory) {
                val timeStr = if (historyAlert.timestamp > 0) sdf.format(Date(historyAlert.timestamp)) else "Just now"

                val alertView = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 12, 0, 12)
                }

                val dot = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(8.dpToPx(), 8.dpToPx()).apply {
                        topMargin = 6.dpToPx()
                        marginEnd = 12.dpToPx()
                    }
                    setBackgroundResource(R.drawable.bg_status_danger)
                }

                val textLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                }

                val titleTv = TextView(this).apply {
                    text = "🚨 Emergency Alert"
                    setTextColor(ContextCompat.getColor(context, R.color.danger_red))
                    textSize = 14f
                }

                val timeTv = TextView(this).apply {
                    text = timeStr
                    setTextColor(ContextCompat.getColor(context, R.color.text_hint))
                    textSize = 12f
                }

                val msgTv = TextView(this).apply {
                    text = historyAlert.message.ifBlank { "SOS triggered by user!" }
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    textSize = 13f
                    setPadding(0, 4, 0, 0)
                }

                textLayout.addView(titleTv)
                textLayout.addView(timeTv)
                textLayout.addView(msgTv)
                
                alertView.addView(dot)
                alertView.addView(textLayout)

                binding.layoutAlertsList.addView(alertView)
            }
        } else {
            binding.tvNoAlerts.visibility = View.VISIBLE
            binding.layoutAlertsList.visibility = View.GONE
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
