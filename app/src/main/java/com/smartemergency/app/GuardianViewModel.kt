package com.smartemergency.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel

/**
 * ViewModel that bridges the GuardianRepository (Firestore) with the
 * GuardianDashboardActivity UI.
 *
 * All Firestore listeners are started on init and cleaned up when the
 * ViewModel is cleared (Activity destroyed).
 */
class GuardianViewModel : ViewModel() {

    private val repository = GuardianRepository()

    // ── Exposed LiveData ────────────────────────────────────────────

    /** Current alert status (Moving / Idle / Emergency). */
    val alertData: LiveData<GuardianRepository.AlertData> = repository.alertData

    /** Latest GPS coordinates of the tracked user. */
    val trackingData: LiveData<GuardianRepository.TrackingData> = repository.trackingData

    /** Error messages from Firestore operations. */
    val error: LiveData<String> = repository.error

    /** True while we haven't received the first data snapshot yet. */
    private val _isLoading = MediatorLiveData<Boolean>().apply { value = true }
    val isLoading: LiveData<Boolean> get() = _isLoading

    // ── Initialisation ──────────────────────────────────────────────

    init {
        repository.startAlertListener()
        repository.startTrackingListener()

        // Turn off the loading spinner as soon as we get EITHER data source
        _isLoading.addSource(alertData) { _isLoading.value = false }
        _isLoading.addSource(trackingData) { _isLoading.value = false }
    }

    // ── Lifecycle ───────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        repository.removeAllListeners()
    }
}
