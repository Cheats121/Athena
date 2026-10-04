package com.athena.j.athena

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.lang.ref.WeakReference

object TimeoutManager {

    private const val TAG = "AthenaTimeout"
    private const val DEFAULT_TIMEOUT_DURATION_MS = 2L * 60L * 1000L
    @Volatile
    private var timeoutDurationMs = DEFAULT_TIMEOUT_DURATION_MS
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private var currentActivity = WeakReference<Activity>(null)
    private var applicationContext: Context? = null
    @Volatile
    private var appInForeground = false
    @Volatile
    private var lastInteractionElapsedMs = 0L
    @Volatile
    private var timeoutTriggered = false
    @Synchronized
    fun resetTimeout(activity: Activity) {
        applicationContext = activity.applicationContext
        currentActivity = WeakReference(activity)
        appInForeground = true

        if (!VaultRuntimeSession.isUnlocked()) {
            cancelScheduledTimeout()
            lastInteractionElapsedMs = SystemClock.elapsedRealtime()
            timeoutTriggered = false
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (lastInteractionElapsedMs > 0L &&
            now - lastInteractionElapsedMs >= timeoutDurationMs
        ) {
            triggerTimeout()
            return
        }

        lastInteractionElapsedMs = now
        timeoutTriggered = false
        scheduleTimeout(timeoutDurationMs)
    }
    @Synchronized
    fun stopTimer() {
        appInForeground = false
        if (!VaultRuntimeSession.isUnlocked()) {
            cancelScheduledTimeout()
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (lastInteractionElapsedMs <= 0L) {
            lastInteractionElapsedMs = now
        }

        val elapsed = now - lastInteractionElapsedMs
        val remaining = timeoutDurationMs - elapsed
        if (remaining <= 0L) {
            triggerTimeout()
        } else {
            scheduleTimeout(remaining)
        }
    }

    @Synchronized
    private fun scheduleTimeout(delayMs: Long) {
        cancelScheduledTimeout()
        val runnable = Runnable {
            triggerTimeout()
        }

        timeoutRunnable = runnable
        handler.postDelayed(
            runnable,
            delayMs.coerceAtLeast(0L)
        )
    }

    @Synchronized
    private fun cancelScheduledTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }
    @Synchronized
    private fun triggerTimeout() {
        if (timeoutTriggered) return
        timeoutTriggered = true
        cancelScheduledTimeout()
        VaultRuntimeSession.clear()
        applicationContext?.let {
            clearClipboard(it)
        }

        val activity = currentActivity.get()
        if (
            appInForeground &&
            activity != null &&
            !activity.isFinishing &&
            !activity.isDestroyed
        ) {
            VaultLocker.lockNow(activity)
        } else {
            Log.i(TAG, "Vault locked while Athena was in background")
        }

        currentActivity = WeakReference(null)
        lastInteractionElapsedMs = 0L
    }

    private fun clearClipboard(context: Context) {
        try {
            val clipboard = context.getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (clipboard.hasPrimaryClip()) {
                    clipboard.clearPrimaryClip()
                }
            } else {
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("", "")
                )
            }

        } catch (e: Exception) {
            Log.w(TAG, "Unable to clear clipboard during timeout", e)
        }
    }

    @Synchronized
    fun clear() {
        cancelScheduledTimeout()
        currentActivity = WeakReference(null)
        applicationContext = null
        appInForeground = false
        lastInteractionElapsedMs = 0L
        timeoutTriggered = false
    }

    fun getTimeoutDurationMs(): Long {
        return timeoutDurationMs
    }

    @Synchronized
    fun setTimeoutDurationForTesting(durationMs: Long) {
        require(durationMs > 0L)
        timeoutDurationMs = durationMs
    }

    @Synchronized
    fun resetTimeoutDurationForTesting() {
        timeoutDurationMs = DEFAULT_TIMEOUT_DURATION_MS
    }
}