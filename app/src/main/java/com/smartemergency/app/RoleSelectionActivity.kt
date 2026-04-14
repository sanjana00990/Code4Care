package com.smartemergency.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.smartemergency.app.databinding.ActivityRoleSelectionBinding

/**
 * Launch screen that lets the user pick their role:
 *  • Parent / Guardian  → opens [GuardianDashboardActivity]
 *  • Child / User       → opens the existing [MainActivity]
 */
class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardParent.setOnClickListener {
            startActivity(Intent(this, GuardianDashboardActivity::class.java))
        }

        binding.cardChild.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }
}
