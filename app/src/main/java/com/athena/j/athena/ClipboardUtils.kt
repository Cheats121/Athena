package com.athena.j.athena

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

object ClipboardUtils {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingClearRunnable: Runnable? = null

    @Synchronized
    fun copySensitive(
        context: Context,
        label: String = "Password",
        text: String,
        clearAfterMs: Long = 30_000L,
        onCleared: (() -> Unit)? = null
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        pendingClearRunnable?.let { handler.removeCallbacks(it) }
        pendingClearRunnable = null
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= 33) {
            val extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }

            clip.description.extras = extras
        }

        clipboard.setPrimaryClip(clip)
        val clearRunnable = Runnable {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            } catch (_: Exception) {}

            synchronized(this) {
                pendingClearRunnable = null
            }

            onCleared?.invoke()
        }

        pendingClearRunnable = clearRunnable
        handler.postDelayed(clearRunnable, clearAfterMs)
    }

    @Synchronized
    fun clearPendingSensitiveClipboard(context: Context) {
        pendingClearRunnable?.let { handler.removeCallbacks(it) }
        pendingClearRunnable = null

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (clipboard.hasPrimaryClip()) {
                    clipboard.clearPrimaryClip()
                }
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }

        } catch (_: Exception) {}
    }
}