package com.smartemergency.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.smartemergency.app.databinding.ActivityLiveTrackingBinding

class LiveTrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLiveTrackingBinding
    private var isSharing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupSharingToggle()
    }

    private fun setupToolbar() {
        binding.toolbarTracking.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupSharingToggle() {
        binding.btnToggleSharing.setOnClickListener {
            isSharing = !isSharing
            updateSharingUI()
        }
    }

    private fun updateSharingUI() {
        if (isSharing) {
            // Sharing active state
            binding.tvSharingStatus.text = getString(R.string.sharing_location)
            binding.tvSharingStatus.setTextColor(
                ContextCompat.getColor(this, R.color.safe_green)
            )
            binding.viewSharingDot.setBackgroundResource(R.drawable.bg_dot_active)
            binding.btnToggleSharing.text = getString(R.string.btn_stop_sharing)
            binding.btnToggleSharing.setBackgroundColor(
                ContextCompat.getColor(this, R.color.sos_red)
            )

            // Show placeholder coordinates
            binding.tvLatitude.text = "28.6139"
            binding.tvLongitude.text = "77.2090"
            binding.tvAccuracy.text = "± 8 m"
            binding.tvLastUpdated.text = "10:33:52"

            Toast.makeText(this, "Location sharing started", Toast.LENGTH_SHORT).show()
            // TODO: Start actual location updates
        } else {
            // Sharing inactive state
            binding.tvSharingStatus.text = getString(R.string.not_sharing)
            binding.tvSharingStatus.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
            binding.viewSharingDot.setBackgroundResource(R.drawable.bg_dot_inactive)
            binding.btnToggleSharing.text = getString(R.string.btn_start_sharing)
            binding.btnToggleSharing.setBackgroundColor(
                ContextCompat.getColor(this, R.color.accent)
            )

            // Reset to placeholders
            binding.tvLatitude.text = getString(R.string.placeholder_coord)
            binding.tvLongitude.text = getString(R.string.placeholder_coord)
            binding.tvAccuracy.text = getString(R.string.placeholder_accuracy)
            binding.tvLastUpdated.text = getString(R.string.placeholder_time)

            Toast.makeText(this, "Location sharing stopped", Toast.LENGTH_SHORT).show()
            // TODO: Stop location updates
        }
    }
}
