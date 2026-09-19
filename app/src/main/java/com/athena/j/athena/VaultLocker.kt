package com.athena.j.athena

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Central vault lock operation.
 *
 * Security model:
 *
 * LOCK:
 *  - destroys the in-memory Vault DEK
 *  - clears sensitive Activity UI
 *  - clears clipboard contents
 *  - stops inactivity timers
 *  - destroys the current Activity task
 *  - returns to MainActivity
 *
 * DOES NOT:
 *  - delete the remembered vault URI
 *  - delete biometric configuration
 *  - delete the biometric-wrapped DEK
 *
 * Those persistent values are required so the user can unlock
 * the same vault again with biometrics.
 *
 * "Forget vault" / "Disable biometrics" are separate operations.
 */
object VaultLocker {

    private const val TAG =
        "AthenaLock"

    // =============================================================
    // LOCK NOW
    // =============================================================

    fun lockNow(
        activity: Activity
    ) {

        /*
         * Destroy cryptographic runtime state FIRST.
         *
         * VaultRuntimeSession.clear() wipes the authoritative
         * in-memory 256-bit Vault DEK before removing its reference.
         */
        VaultRuntimeSession.clear()

        /*
         * Give the currently visible secure Activity a chance
         * to immediately remove plaintext passwords / decrypted
         * vault references from its UI.
         */
        if (
            activity is BaseSecureActivity
        ) {

            try {

                activity.clearSensitiveData()

            } catch (e: Exception) {

                /*
                 * Locking must continue even if one Activity's
                 * cleanup routine unexpectedly fails.
                 */
                Log.w(
                    TAG,
                    "Activity sensitive-data cleanup failed",
                    e
                )
            }
        }

        /*
         * Cancel any outstanding inactivity callback.
         *
         * We do this before launching MainActivity so an old
         * Runnable cannot unexpectedly fire in the new task.
         */
        TimeoutManager.stopTimer()

        /*
         * Best-effort clipboard destruction.
         */
        clearClipboard(
            activity.applicationContext
        )

        /*
         * IMPORTANT:
         *
         * Do NOT call:
         *
         * VaultSessionManager.clearSession(...)
         *
         * during a normal lock.
         *
         * That store contains the remembered URI and biometric
         * setting. Neither is the active vault encryption key.
         *
         * The wrapped DEK remains protected by Android Keystore
         * and requires BIOMETRIC_STRONG before it can be unwrapped.
         */

        returnToLockedScreen(
            activity
        )
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

                /*
                 * Older Android fallback.
                 *
                 * There is no clean clearPrimaryClip() API before
                 * API 28, so overwrite the clipboard with an
                 * empty value.
                 */
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        "",
                        ""
                    )
                )
            }

        } catch (e: Exception) {

            /*
             * Clipboard clearing is defense-in-depth.
             *
             * A clipboard service/OEM failure must never prevent
             * destruction of the vault DEK or completion of lock.
             */
            Log.w(
                TAG,
                "Unable to clear clipboard during lock",
                e
            )
        }
    }

    // =============================================================
    // RETURN TO LOGIN
    // =============================================================

    private fun returnToLockedScreen(
        activity: Activity
    ) {

        val intent =
            Intent(
                activity,
                MainActivity::class.java
            ).apply {

                /*
                 * CLEAR_TASK is important:
                 *
                 * It removes EntryDetailActivity,
                 * EditEntryActivity, VaultActivity, etc.
                 *
                 * Those Activities may contain decrypted Strings
                 * or JSON objects that cannot be reliably zeroized
                 * on the JVM.
                 */
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        activity.startActivity(
            intent
        )

        /*
         * The previous task has already been cleared.
         * Finish the originating Activity defensively as well.
         */
        activity.finish()
    }
}