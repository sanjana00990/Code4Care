package com.smartemergency.app

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Repository that encapsulates all Firestore operations for the Guardian Dashboard.
 *
 * Listens to two collections in real-time:
 *  • "alerts"           → user status  (Moving / Idle / Emergency)
 *  • "trackingUpdates"  → live GPS coordinates + timestamp
 */
class GuardianRepository {

    companion object {
        private const val TAG = "GuardianRepository"
        private const val COLLECTION_ALERTS = "alerts"
        private const val COLLECTION_TRACKING = "trackingUpdates"
        private const val DOC_LATEST = "latest"
    }

    private val firestore = FirebaseFirestore.getInstance()

    // ── Alert / Status ──────────────────────────────────────────────

    data class AlertData(
        val status: String = "Idle",
        val message: String = "",
        val timestamp: Long = 0L,
        val phoneNumber: String = ""
    )

    private val _alertData = MutableLiveData<AlertData>()
    val alertData: LiveData<AlertData> get() = _alertData

    private var alertListener: ListenerRegistration? = null

    // ── Location Tracking ───────────────────────────────────────────

    data class TrackingData(
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val timestamp: Long = 0L
    )

    private val _trackingData = MutableLiveData<TrackingData>()
    val trackingData: LiveData<TrackingData> get() = _trackingData

    private var trackingListener: ListenerRegistration? = null

    // ── Error channel ───────────────────────────────────────────────

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    // ── Public API ──────────────────────────────────────────────────

    /** Start listening for real-time alert status changes. */
    fun startAlertListener() {
        alertListener?.remove()

        alertListener = firestore.collection(COLLECTION_ALERTS)
            .document(DOC_LATEST)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Log.e(TAG, "Alert listener error", exception)
                    _error.postValue("Failed to fetch alert status")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val status = snapshot.getString("status") ?: "Idle"
                    val message = snapshot.getString("message") ?: ""
                    val timestamp = snapshot.getLong("timestamp") ?: 0L
                    val phoneNumber = snapshot.getString("phoneNumber") ?: ""

                    _alertData.postValue(AlertData(status, message, timestamp, phoneNumber))
                    Log.d(TAG, "Alert update → status=$status")
                } else {
                    // Document doesn't exist yet – default to Idle
                    _alertData.postValue(AlertData("Idle", "", 0L, ""))
                    Log.d(TAG, "No alert document found, defaulting to Idle")
                }
            }
    }

    /** Start listening for real-time location updates. */
    fun startTrackingListener() {
        trackingListener?.remove()

        trackingListener = firestore.collection(COLLECTION_TRACKING)
            .document(DOC_LATEST)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Log.e(TAG, "Tracking listener error", exception)
                    _error.postValue("Failed to fetch location data")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val lat = snapshot.getDouble("latitude") ?: 0.0
                    val lon = snapshot.getDouble("longitude") ?: 0.0
                    val ts  = snapshot.getLong("timestamp") ?: 0L

                    _trackingData.postValue(TrackingData(lat, lon, ts))
                    Log.d(TAG, "Tracking update → lat=$lat, lon=$lon")
                } else {
                    Log.d(TAG, "No tracking document found")
                }
            }
    }

    /** Remove all Firestore listeners to prevent leaks. */
    fun removeAllListeners() {
        alertListener?.remove()
        trackingListener?.remove()
        alertListener = null
        trackingListener = null
        Log.d(TAG, "All listeners removed")
    }
}
