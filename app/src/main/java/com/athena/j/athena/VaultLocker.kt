package com.athena.j.athena

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object VaultLocker {
    private const val TAG = "AthenaLock"
    fun lockNow(activity: Activity) {
        VaultRuntimeSession.clear()
        if (activity is BaseSecureActivity) {
            try {
                activity.clearSensitiveData()
            } catch (e: Exception) {
                Log.w(TAG, "Activity sensitive-data cleanup failed", e)
            }
        }
        TimeoutManager.stopTimer()
        clearClipboard(activity.applicationContext)
        returnToLockedScreen(activity)
    }

    private fun clearClipboard(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (clipboard.hasPrimaryClip()) {
                    clipboard.clearPrimaryClip()
                }
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }

        } catch (e: Exception) {
            Log.w(TAG, "Unable to clear clipboard during lock", e)
        }
    }

    private fun returnToLockedScreen(activity: Activity) {
        val intent = Intent(activity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finish()
    }
}