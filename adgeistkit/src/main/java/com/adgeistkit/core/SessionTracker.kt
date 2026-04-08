package com.adgeistkit.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.adgeistkit.data.network.UTMAnalytics
import com.adgeistkit.logging.SdkShield
import com.adgeistkit.workers.SessionUploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

/**
 * Tracks session duration for UTM-attributed sessions.
 * Uses ProcessLifecycleOwner to detect app foreground/background events.
 * Persists session state to survive process death and uses WorkManager for reliable upload.
 */
class SessionTracker(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val bidRequestBackendDomain: String
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "SessionTracker"
        
        // SharedPreferences keys
        private const val PREF_SESSION_TRACKING = "session_tracking_active"
        private const val KEY_ACCUMULATED_DURATION = "accumulated_duration"
        private const val KEY_LAST_PERSIST_TIME = "last_persist_time"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_UTM_SOURCE = "utm_source"
        private const val KEY_UTM_DATA = "utm_data"
        
        // Timing constants
        private const val PERSIST_INTERVAL_MS = 30_000L // 30 seconds
        private const val IMMEDIATE_SEND_TIMEOUT_MS = 2000L // 2 seconds
        private const val SESSION_STALENESS_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val utmAnalytics = UTMAnalytics(bidRequestBackendDomain)
    private val workManager = WorkManager.getInstance(context)

    // Session tracking state
    private var sessionStartTime: Long = 0L
    private var totalActiveTime: Long = 0L
    private var isPaused: Boolean = false
    private var isTracking: Boolean = false
    private var currentSessionId: String? = null
    
    private var periodicPersistJob: Job? = null

    init {
        // Register lifecycle observer on the main thread to avoid IllegalStateException
        // especially when initialized from background threads (e.g. in React Native)
        Handler(Looper.getMainLooper()).post {
            SdkShield.runSafely("SessionTracker.initLifecycle") {
                ProcessLifecycleOwner.get().lifecycle.addObserver(this)
                Log.d(TAG, "SessionTracker: Lifecycle observer registered on main thread")
            }
        }
        
        // Check for orphaned sessions from previous app run
        checkForOrphanedSession()
    }

    /**
     * Start tracking session duration for a specific session ID
     */
    fun startSessionTracking(sessionId: String) {
        if (isTracking) {
            Log.d(TAG, "Session tracking already active for session: $currentSessionId")
            return
        }

        currentSessionId = sessionId
        sessionStartTime = SystemClock.elapsedRealtime()
        totalActiveTime = 0L
        isPaused = false
        isTracking = true

        // Mark as tracking in prefs
        prefs.edit().putBoolean(PREF_SESSION_TRACKING, true).apply()
        
        Log.d(TAG, "Session tracking started: $sessionId")
        
        // Start periodic persistence
        startPeriodicPersist()
    }

    /**
     * Pause session tracking and accumulate elapsed time
     */
    private fun pauseSessionTracking() {
        if (!isPaused && isTracking && sessionStartTime > 0) {
            val elapsed = SystemClock.elapsedRealtime() - sessionStartTime
            totalActiveTime += elapsed
            isPaused = true
            Log.d(TAG, "Session tracking paused. Accumulated: ${totalActiveTime}ms")
        }
    }

    /**
     * Resume session tracking
     */
    private fun resumeSessionTracking() {
        if (isPaused && isTracking) {
            sessionStartTime = SystemClock.elapsedRealtime()
            isPaused = false
            Log.d(TAG, "Session tracking resumed")
        }
    }

    /**
     * Get total session duration in milliseconds
     */
    private fun getTotalSessionDuration(): Long {
        var currentSegmentTime = 0L
        if (!isPaused && isTracking && sessionStartTime > 0) {
            currentSegmentTime = SystemClock.elapsedRealtime() - sessionStartTime
        }
        return totalActiveTime + currentSegmentTime
    }

    /**
     * Persist session state to SharedPreferences
     */
    private fun persistSessionState() {
        if (!isTracking) return

        val duration = getTotalSessionDuration()

        prefs.edit().apply {
            putBoolean(PREF_SESSION_TRACKING, true)
            putLong(KEY_ACCUMULATED_DURATION, duration)
            putString(KEY_SESSION_ID, currentSessionId)
            putLong(KEY_LAST_PERSIST_TIME, System.currentTimeMillis())
            commit() // Use commit for reliability on app termination
        }
        
        Log.d(TAG, "Session state persisted: duration=${duration}ms")
    }

    /**
     * Start periodic persistence to prevent data loss
     */
    private fun startPeriodicPersist() {
        periodicPersistJob?.cancel()
        periodicPersistJob = scope.launch {
            while (isActive && isTracking) {
                delay(PERSIST_INTERVAL_MS)
                if (isTracking && !isPaused) {
                    persistSessionState()
                }
            }
        }
    }

    /**
     * Stop periodic persistence
     */
    private fun stopPeriodicPersist() {
        periodicPersistJob?.cancel()
        periodicPersistJob = null
    }

    /**
     * Schedule WorkManager upload job
     */
    private fun scheduleSessionEventUpload() {
        val sessionId = currentSessionId ?: prefs.getString(KEY_SESSION_ID, null)
        val duration = getTotalSessionDuration()
        val utmSource = prefs.getString(KEY_UTM_SOURCE, "") ?: ""
        val utmData = prefs.getString(KEY_UTM_DATA, "") ?: ""

        if (sessionId == null || duration <= 0) {
            Log.w(TAG, "Cannot schedule upload: invalid session data")
            return
        }

        val inputData = Data.Builder()
            .putString(SessionUploadWorker.KEY_SESSION_ID, sessionId)
            .putLong(SessionUploadWorker.KEY_DURATION_MS, duration)
            .putString(SessionUploadWorker.KEY_UTM_SOURCE, utmSource)
            .putString(SessionUploadWorker.KEY_UTM_DATA, utmData)
            .putString(SessionUploadWorker.KEY_BACKEND_DOMAIN, bidRequestBackendDomain)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<SessionUploadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        val workName = "session_upload_$sessionId"
        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            uploadRequest
        )

        Log.i(TAG, "WorkManager job scheduled for session: $sessionId, duration: ${duration}ms")
    }

    /**
     * Attempt immediate send with timeout, fallback to WorkManager
     */
    private fun sendSessionDurationEvent() {
        val sessionId = currentSessionId ?: return
        val duration = getTotalSessionDuration()
        val utmSource = prefs.getString(KEY_UTM_SOURCE, "") ?: ""
        val utmData = prefs.getString(KEY_UTM_DATA, "") ?: ""

        if (duration <= 0) {
            Log.w(TAG, "Session duration is 0, skipping event")
            return
        }

        scope.launch {
            try {
                withTimeout(IMMEDIATE_SEND_TIMEOUT_MS) {
                    utmAnalytics.sendSessionDurationEvent(
                        sessionId = sessionId,
                        durationMs = duration,
                        utmSource = utmSource,
                        utmData = utmData
                    ) { success, error ->
                        if (success) {
                            Log.i(TAG, "Session duration sent immediately: $duration ms")
                            clearSessionState()
                            
                            // Cancel WorkManager job since we succeeded
                            val workName = "session_upload_$sessionId"
                            workManager.cancelUniqueWork(workName)
                        } else {
                            Log.w(TAG, "Immediate send failed: $error. WorkManager will retry.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Immediate send timed out or failed. WorkManager will handle retry.")
            }
        }
    }

    /**
     * Clear session tracking state
     */
    private fun clearSessionState() {
        isTracking = false
        currentSessionId = null
        sessionStartTime = 0L
        totalActiveTime = 0L
        isPaused = false
        stopPeriodicPersist()

        prefs.edit().apply {
            remove(PREF_SESSION_TRACKING)
            remove(KEY_ACCUMULATED_DURATION)
            remove(KEY_LAST_PERSIST_TIME)
            apply()
        }
        
        Log.d(TAG, "Session state cleared")
    }

    /**
     * Check for orphaned sessions from previous app run
     */
    private fun checkForOrphanedSession() {
        val wasTracking = prefs.getBoolean(PREF_SESSION_TRACKING, false)
        
        if (wasTracking) {
            val lastPersistTime = prefs.getLong(KEY_LAST_PERSIST_TIME, 0)
            val currentTime = System.currentTimeMillis()
            val timeSinceLastPersist = currentTime - lastPersistTime

            if (timeSinceLastPersist > SESSION_STALENESS_MS) {
                // Session was killed - schedule upload with saved data
                Log.i(TAG, "Detected orphaned session, scheduling upload")
                scheduleSessionEventUpload()
            } else {
                // Recent session - might be a quick restart, clear stale state
                Log.d(TAG, "Recent session state found, clearing")
                clearSessionState()
            }
        }
    }

    // Lifecycle callbacks

    override fun onStart(owner: LifecycleOwner) {
        SdkShield.runSafely("SessionTracker.onStart") {
            Log.d(TAG, "App foregrounded")
            resumeSessionTracking()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        SdkShield.runSafely("SessionTracker.onStop") {
            Log.d(TAG, "App backgrounded")

            if (isTracking) {
                pauseSessionTracking()
                persistSessionState()

                // Schedule WorkManager upload
                scheduleSessionEventUpload()

                // Also attempt immediate send
                sendSessionDurationEvent()
            }
        }
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        stopPeriodicPersist()
        scope.cancel()
        Handler(Looper.getMainLooper()).post {
            SdkShield.runSafely("SessionTracker.destroy") {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
            }
        }
        Log.d(TAG, "SessionTracker destroyed")
    }
}
