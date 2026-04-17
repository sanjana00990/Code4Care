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
        binding.etMyName.setText(prefs.getString("my_name", ""))
        binding.etMyPhoneNumber.setText(prefs.getString("my_phone_number", ""))
        binding.etIdleThreshold.setText(prefs.getString("idle_threshold", "15"))
    }

    override fun onPause() {
        super.onPause()
        val prefs = getSharedPreferences("smart_emergency_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString("my_name", binding.etMyName.text.toString().trim())
            .putString("my_phone_number", binding.etMyPhoneNumber.text.toString().trim())
            .putString("idle_threshold", binding.etIdleThreshold.text.toString().trim())
            .apply()
    }

    private fun setupToolbar() {
        binding.toolbarSettings.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupSwitches() {
        val prefs = getSharedPreferences("smart_emergency_prefs", MODE_PRIVATE)

        // Restore states
        binding.switchIdleDetection.isChecked = prefs.getBoolean("idle_detection", false)
        binding.switchAutoDanger.isChecked = prefs.getBoolean("auto_danger", false)
        binding.switchShakeTrigger.isChecked = prefs.getBoolean("shake_trigger", true)
        binding.switchPowerTrigger.isChecked = prefs.getBoolean("power_trigger", true)

        // Idle Detection Toggle
        binding.switchIdleDetection.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("idle_detection", isChecked).apply()
            val status = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "Idle detection $status", Toast.LENGTH_SHORT).show()
        }

        // Automatic Danger Detection Toggle
        binding.switchAutoDanger.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_danger", isChecked).apply()
            val status = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "Auto danger detection $status", Toast.LENGTH_SHORT).show()
        }

        // Shake Trigger Toggle
        binding.switchShakeTrigger.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("shake_trigger", isChecked).apply()
            val status = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "Shake trigger $status", Toast.LENGTH_SHORT).show()
        }

        // Power Button Trigger Toggle
        binding.switchPowerTrigger.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("power_trigger", isChecked).apply()
            val status = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "Power button trigger $status", Toast.LENGTH_SHORT).show()
        }
    }
}
