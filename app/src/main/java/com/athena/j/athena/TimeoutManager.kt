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

/**
 * Athena inactivity / background timeout controller.
 *
 * Security goals:
 *
 * - Auto-lock after inactivity.
 * - Backgrounding Athena must NOT disable the timer.
 * - The Vault DEK is wiped even if Athena is still backgrounded.
 * - Never hold a strong Activity reference.
 * - Never unexpectedly bring Athena to the foreground when a
 *   background timeout occurs.
 *
 *
 * Current policy:
 *
 *      2 minutes without interaction
 *              ↓
 *           LOCK
 *
 *
 * Foreground timeout:
 *
 *      wipe DEK
 *      clear sensitive UI
 *      clear clipboard
 *      return to MainActivity
 *
 *
 * Background timeout:
 *
 *      wipe DEK
 *      clear clipboard
 *      DO NOT launch UI
 *
 * When the user later returns, secure Activities observe that
 * VaultRuntimeSession is locked and fail closed.
 */
object TimeoutManager {

    private const val TAG =
        "AthenaTimeout"

    // =============================================================
    // POLICY
    // =============================================================

    private const val DEFAULT_TIMEOUT_DURATION_MS =
        2L * 60L * 1000L

    @Volatile
    private var timeoutDurationMs =
        DEFAULT_TIMEOUT_DURATION_MS

    // =============================================================
    // TIMER
    // =============================================================

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private var timeoutRunnable: Runnable? =
        null

    // =============================================================
    // STATE
    // =============================================================

    /**
     * WeakReference prevents this singleton from leaking an Activity.
     */
    private var currentActivity =
        WeakReference<Activity>(
            null
        )

    /**
     * Application context is safe to retain.
     */
    private var applicationContext: Context? =
        null

    /**
     * True while one of Athena's Activities is considered active.
     *
     * BaseSecureActivity currently drives this through:
     *
     * resetTimeout() -> foreground
     * stopTimer()    -> background/transition
     */
    @Volatile
    private var appInForeground =
        false

    /**
     * Monotonic time source.
     *
     * SystemClock.elapsedRealtime() is preferable to
     * System.currentTimeMillis() for timeout calculations because
     * changing the wall clock cannot bypass inactivity protection.
     */
    @Volatile
    private var lastInteractionElapsedMs =
        0L

    /**
     * Prevent duplicate timeout execution.
     */
    @Volatile
    private var timeoutTriggered =
        false

    // =============================================================
    // RESET / USER ACTIVITY
    // =============================================================

    /**
     * Called from BaseSecureActivity.onResume() and
     * onUserInteraction().
     */
    @Synchronized
    fun resetTimeout(
        activity: Activity
    ) {

        applicationContext =
            activity.applicationContext

        currentActivity =
            WeakReference(
                activity
            )

        appInForeground =
            true

        /*
         * If there is no unlocked vault, there is no DEK that
         * needs inactivity protection.
         */
        if (
            !VaultRuntimeSession.isUnlocked()
        ) {

            cancelScheduledTimeout()

            lastInteractionElapsedMs =
                SystemClock.elapsedRealtime()

            timeoutTriggered =
                false

            return
        }

        val now =
            SystemClock.elapsedRealtime()

        /*
         * If Athena was backgrounded and the handler was delayed
         * by the OS, enforce the timeout immediately on return.
         */
        if (
            lastInteractionElapsedMs > 0L &&
            now - lastInteractionElapsedMs >=
            timeoutDurationMs
        ) {

            triggerTimeout()
            return
        }

        lastInteractionElapsedMs =
            now

        timeoutTriggered =
            false

        scheduleTimeout(
            timeoutDurationMs
        )
    }

    // =============================================================
    // PAUSE / BACKGROUND
    // =============================================================

    /**
     * Historically this method completely stopped Athena's timer.
     *
     * It intentionally DOES NOT do that anymore.
     *
     * BaseSecureActivity currently calls stopTimer() during
     * onPause(). We use that signal to mark Athena as backgrounded
     * while continuing the security countdown.
     */
    @Synchronized
    fun stopTimer() {

        appInForeground =
            false

        if (
            !VaultRuntimeSession.isUnlocked()
        ) {

            cancelScheduledTimeout()
            return
        }

        val now =
            SystemClock.elapsedRealtime()

        if (
            lastInteractionElapsedMs <= 0L
        ) {

            lastInteractionElapsedMs =
                now
        }

        val elapsed =
            now -
                    lastInteractionElapsedMs

        val remaining =
            timeoutDurationMs -
                    elapsed

        if (
            remaining <= 0L
        ) {

            triggerTimeout()

        } else {

            /*
             * Keep the countdown alive while Athena is backgrounded.
             */
            scheduleTimeout(
                remaining
            )
        }
    }

    // =============================================================
    // SCHEDULE
    // =============================================================

    @Synchronized
    private fun scheduleTimeout(
        delayMs: Long
    ) {

        cancelScheduledTimeout()

        val runnable =
            Runnable {

                triggerTimeout()
            }

        timeoutRunnable =
            runnable

        handler.postDelayed(
            runnable,
            delayMs.coerceAtLeast(
                0L
            )
        )
    }

    // =============================================================
    // CANCEL
    // =============================================================

    @Synchronized
    private fun cancelScheduledTimeout() {

        timeoutRunnable
            ?.let {

                handler.removeCallbacks(
                    it
                )
            }

        timeoutRunnable =
            null
    }

    // =============================================================
    // TRIGGER
    // =============================================================

    @Synchronized
    private fun triggerTimeout() {

        if (
            timeoutTriggered
        ) {
            return
        }

        timeoutTriggered =
            true

        cancelScheduledTimeout()

        /*
         * Cryptographic secret destruction happens first.
         */
        VaultRuntimeSession.clear()

        /*
         * Clipboard may contain a password copied moments earlier.
         */
        applicationContext
            ?.let {

                clearClipboard(
                    it
                )
            }

        val activity =
            currentActivity.get()

        if (
            appInForeground &&
            activity != null &&
            !activity.isFinishing &&
            !activity.isDestroyed
        ) {

            /*
             * Foreground timeout:
             *
             * VaultLocker handles Activity cleanup, clipboard
             * defense-in-depth and clearing the task.
             *
             * VaultRuntimeSession.clear() is safe to call again.
             */
            VaultLocker.lockNow(
                activity
            )

        } else {

            /*
             * Background timeout:
             *
             * DO NOT launch MainActivity here.
             *
             * Starting an Activity from a background timeout would
             * unexpectedly pull Athena over whatever app the user
             * is currently using.
             *
             * The DEK has already been destroyed. When Athena is
             * resumed, secure Activities will see the expired
             * runtime session and return to the locked state.
             */
            Log.i(
                TAG,
                "Vault locked while Athena was in background"
            )
        }

        currentActivity =
            WeakReference(
                null
            )

        lastInteractionElapsedMs =
            0L
    }

    // =============================================================
    // CLIPBOARD
    // =============================================================

    private fun clearClipboard(
        context: Context
    ) {

        try {

            val clipboard =
                context.getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.P
            ) {

                if (
                    clipboard.hasPrimaryClip()
                ) {

                    clipboard.clearPrimaryClip()
                }

            } else {

                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        "",
                        ""
                    )
                )
            }

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Unable to clear clipboard during timeout",
                e
            )
        }
    }

    // =============================================================
    // EXPLICIT RESET
    // =============================================================

    /**
     * Completely clears TimeoutManager's internal state.
     *
     * Useful for explicit logout / app reset operations.
     */
    @Synchronized
    fun clear() {

        cancelScheduledTimeout()

        currentActivity =
            WeakReference(
                null
            )

        applicationContext =
            null

        appInForeground =
            false

        lastInteractionElapsedMs =
            0L

        timeoutTriggered =
            false
    }

    // =============================================================
    // POLICY INFORMATION
    // =============================================================

    fun getTimeoutDurationMs(): Long {

        return timeoutDurationMs
    }

    @Synchronized
    fun setTimeoutDurationForTesting(
        durationMs: Long
    ) {

        require(
            durationMs > 0L
        )

        timeoutDurationMs =
            durationMs
    }

    @Synchronized
    fun resetTimeoutDurationForTesting() {

        timeoutDurationMs =
            DEFAULT_TIMEOUT_DURATION_MS
    }
}