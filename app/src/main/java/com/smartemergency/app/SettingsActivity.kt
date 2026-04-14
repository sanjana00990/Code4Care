package com.smartemergency.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.smartemergency.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupSwitches()
        setupProfile()
    }

    private fun setupProfile() {
        val prefs = getSharedPreferences("smart_emergency_prefs", MODE_PRIVATE)
        binding.etMyPhoneNumber.setText(prefs.getString("my_phone_number", ""))
    }

    override fun onPause() {
        super.onPause()
        val prefs = getSharedPreferences("smart_emergency_prefs", MODE_PRIVATE)
        prefs.edit().putString("my_phone_number", binding.etMyPhoneNumber.text.toString().trim()).apply()
    }

    private fun setupToolbar() {
        binding.toolbarSettings.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupSwitches() {
        // Idle Detection Toggle
        binding.switchIdleDetection.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "Idle detection $status", Toast.LENGTH_SHORT).show()
            // TODO: Enable/disable idle detection service
        }

        // Automatic Danger Detection Toggle
        binding.switchAutoDanger.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "Auto danger detection $status", Toast.LENGTH_SHORT).show()
            // TODO: Enable/disable sensor-based danger detection
        }

        // Shake Trigger Toggle
        binding.switchShakeTrigger.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "Shake trigger $status", Toast.LENGTH_SHORT).show()
            // TODO: Register/unregister accelerometer listener
        }

        // Power Button Trigger Toggle (UI only)
        binding.switchPowerTrigger.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "Power button trigger $status", Toast.LENGTH_SHORT).show()
            // TODO: Register/unregister screen off broadcast receiver
        }
    }
}
