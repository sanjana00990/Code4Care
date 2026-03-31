package com.smartemergency.app

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.smartemergency.app.databinding.ActivityAlertStatusBinding

class AlertStatusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertStatusBinding
    private var isAlertActive = false
    private var progressTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupActionButtons()

        // Check if opened from emergency trigger
        isAlertActive = intent.getBooleanExtra("ALERT_ACTIVE", false)
        if (isAlertActive) {
            showAlertActive()
        } else {
            showNoAlert()
        }
    }

    private fun setupToolbar() {
        binding.toolbarAlert.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupActionButtons() {
        binding.btnCancelAlert.setOnClickListener {
            cancelAlert()
        }

        binding.btnImSafe.setOnClickListener {
            markAsSafe()
        }
    }

    // ────────────────────────────────────────────
    // Alert State Management
    // ────────────────────────────────────────────

    private fun showAlertActive() {
        binding.cardAlertActive.visibility = View.VISIBLE
        binding.cardProgressSteps.visibility = View.VISIBLE
        binding.layoutAlertActions.visibility = View.VISIBLE
        binding.layoutNoAlert.visibility = View.GONE

        // Simulate progress through steps
        simulateAlertProgress()
    }

    private fun showNoAlert() {
        binding.cardAlertActive.visibility = View.GONE
        binding.cardProgressSteps.visibility = View.GONE
        binding.layoutAlertActions.visibility = View.GONE
        binding.layoutNoAlert.visibility = View.VISIBLE
    }

    private fun simulateAlertProgress() {
        // Step 1: Already complete (Emergency Detected)
        updateStepUI(
            binding.iconStep1, binding.tvStep1Status,
            StepState.COMPLETE
        )

        // Step 2: SMS Sent (simulate after 2s)
        updateStepUI(
            binding.iconStep2, binding.tvStep2Status,
            StepState.IN_PROGRESS
        )

        // Step 3 & 4: Pending
        updateStepUI(
            binding.iconStep3, binding.tvStep3Status,
            StepState.PENDING
        )
        updateStepUI(
            binding.iconStep4, binding.tvStep4Status,
            StepState.PENDING
        )

        // Simulate step progression
        progressTimer = object : CountDownTimer(6000, 2000) {
            var currentStep = 2

            override fun onTick(millisUntilFinished: Long) {
                when (currentStep) {
                    2 -> {
                        updateStepUI(
                            binding.iconStep2, binding.tvStep2Status,
                            StepState.COMPLETE
                        )
                        updateStepUI(
                            binding.iconStep3, binding.tvStep3Status,
                            StepState.IN_PROGRESS
                        )
                        currentStep++
                    }
                    3 -> {
                        updateStepUI(
                            binding.iconStep3, binding.tvStep3Status,
                            StepState.COMPLETE
                        )
                        updateStepUI(
                            binding.iconStep4, binding.tvStep4Status,
                            StepState.IN_PROGRESS
                        )
                        currentStep++
                    }
                }
            }

            override fun onFinish() {
                updateStepUI(
                    binding.iconStep4, binding.tvStep4Status,
                    StepState.COMPLETE
                )
            }
        }.start()
    }

    // ────────────────────────────────────────────
    // Step UI Updates
    // ────────────────────────────────────────────

    enum class StepState { COMPLETE, IN_PROGRESS, PENDING }

    private fun updateStepUI(icon: ImageView, statusText: TextView, state: StepState) {
        when (state) {
            StepState.COMPLETE -> {
                icon.alpha = 1.0f
                statusText.text = getString(R.string.status_complete)
                statusText.setTextColor(ContextCompat.getColor(this, R.color.safe_green))
            }
            StepState.IN_PROGRESS -> {
                icon.alpha = 0.6f
                statusText.text = getString(R.string.status_in_progress)
                statusText.setTextColor(ContextCompat.getColor(this, R.color.monitoring_amber))
            }
            StepState.PENDING -> {
                icon.alpha = 0.3f
                statusText.text = getString(R.string.status_pending)
                statusText.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
            }
        }
    }

    // ────────────────────────────────────────────
    // Actions
    // ────────────────────────────────────────────

    private fun cancelAlert() {
        progressTimer?.cancel()
        isAlertActive = false
        showNoAlert()
        Toast.makeText(this, "Alert cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun markAsSafe() {
        progressTimer?.cancel()
        isAlertActive = false
        showNoAlert()
        Toast.makeText(this, "Marked as safe. Stay safe! 💚", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressTimer?.cancel()
    }
}
