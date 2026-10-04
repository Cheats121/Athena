package com.athena.j.athena

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log

object VaultSessionManager {

    private const val TAG = "AthenaSession"
    private const val PREF_NAME = "athena_session_v4"
    private const val KEY_VAULT_URI = "vault_uri"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
    }
    fun saveSession(context: Context, uri: Uri?) {
        val editor = prefs(context).edit()
        if (uri == null) {
            editor.remove(KEY_VAULT_URI)
        } else {
            editor.putString(KEY_VAULT_URI, uri.toString())
        }
        val success = editor.commit()
        if (!success) {
            Log.w(TAG, "Failed to persist vault URI")
        }
    }

    fun getSessionUri(context: Context): Uri? {
        val stored = prefs(context).getString(KEY_VAULT_URI, null) ?: return null
        return try {
            val uri = Uri.parse(stored)
            if (uri.scheme.isNullOrBlank()) {
                null
            } else {
                uri
            }

        } catch (e: Exception) {
            Log.w(TAG, "Stored vault URI is invalid", e)
            null
        }
    }

    fun hasSession(context: Context): Boolean {
        return getSessionUri(context) != null
    }
    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        val success = prefs(context)
            .edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .commit()

        if (!success) {
            Log.w(TAG, "Failed to persist biometric preference")
        }
    }

    fun isBiometricEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun clearRuntimeOnly() {
        VaultRuntimeSession.clear()
    }

    fun forgetVault(context: Context) {
        val uri = getSessionUri(context)
        var vaultId: ByteArray? = null
        try {
            if (uri != null) {
                vaultId = VaultManager.getVaultId(
                    context = context,
                    uri = uri
                )
            }
            VaultRuntimeSession.clear()
            if (vaultId != null) {
                try {
                    RecoveryKeyStore.clear(
                        context = context,
                        vaultId = vaultId
                    )
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "Failed to clear recovery-key cache while forgetting vault",
                        e
                    )
                }
            }
            try {
                BiometricStore.clear(context)
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Failed to clear biometric store while forgetting vault",
                    e
                )
            }
            val success = prefs(context).edit().clear().commit()
            if (!success) {
                Log.w(TAG, "Failed to clear persistent session metadata")
            }

        } finally {
            vaultId?.fill(0)
        }
    }

    fun disableBiometrics(context: Context) {
        try {
            BiometricStore.clear(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear biometric store", e)
        }
        setBiometricEnabled(context, false)
    }

    fun clearSession(context: Context) {
        forgetVault(context)
    }
}