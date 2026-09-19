package com.athena.j.athena

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle


/**
 * Handles copying sensitive data to the clipboard
 * and clearing it after a short delay.
 */
object ClipboardUtils {

    // =============================================================
    // HANDLER
    // =============================================================

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    // =============================================================
    // PENDING CLEAR
    // =============================================================

    private var pendingClearRunnable: Runnable? =
        null

    // =============================================================
    // COPY SENSITIVE
    // =============================================================

    /**
     * Copies sensitive text and schedules clipboard clearing.
     *
     * Any previous clear timer is cancelled so the newest copied
     * value receives the full timeout.
     */
    @Synchronized
    fun copySensitive(
        context: Context,
        label: String = "Password",
        text: String,
        clearAfterMs: Long = 30_000L,
        onCleared: (() -> Unit)? = null
    ) {

        val clipboard =
            context.getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager

        // Cancel any previous clear timer.
        pendingClearRunnable
            ?.let {

                handler.removeCallbacks(
                    it
                )
            }

        pendingClearRunnable =
            null

        // Create clipboard content.
        val clip =
            ClipData.newPlainText(
                label,
                text
            )

        // Mark clipboard content as sensitive on Android 13+.
        if (
            Build.VERSION.SDK_INT >= 33
        ) {

            val extras =
                PersistableBundle().apply {

                    putBoolean(
                        ClipDescription.EXTRA_IS_SENSITIVE,
                        true
                    )
                }

            clip.description.extras =
                extras
        }

        // Copy to clipboard.
        clipboard.setPrimaryClip(
            clip
        )

        // Schedule clipboard clearing.
        val clearRunnable =
            Runnable {

                try {

                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.P
                    ) {

                        clipboard.clearPrimaryClip()

                    } else {

                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "",
                                ""
                            )
                        )
                    }

                } catch (_: Exception) {
                    // Clipboard clearing is best-effort.
                }

                synchronized(this) {

                    pendingClearRunnable =
                        null
                }

                onCleared?.invoke()
            }

        pendingClearRunnable =
            clearRunnable

        handler.postDelayed(
            clearRunnable,
            clearAfterMs
        )
    }

    // =============================================================
    // EXPLICIT CLEAR
    // =============================================================

    /**
     * Cancels any pending timer and clears the clipboard immediately.
     */
    @Synchronized
    fun clearPendingSensitiveClipboard(
        context: Context
    ) {

        // Cancel pending clear task.
        pendingClearRunnable
            ?.let {

                handler.removeCallbacks(
                    it
                )
            }

        pendingClearRunnable =
            null

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

        } catch (_: Exception) {
            // Best-effort cleanup.
        }
    }
}