package com.athena.j.athena

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log

/**
 * Persists only NON-SECRET vault session metadata.
 *
 * This class NEVER stores:
 *
 * - master password
 * - password-derived KEK
 * - Vault DEK
 * - credential contents
 * - biometric Keystore key
 *
 *
 * It stores only:
 *
 * - the last selected vault URI
 * - whether biometric quick unlock is enabled
 *
 *
 * Security-sensitive key material belongs in:
 *
 * VaultRuntimeSession
 *      -> unlocked DEK in RAM only
 *
 * BiometricStore
 *      -> biometric-wrapped DEK ciphertext
 *      -> wrapping key stays inside Android Keystore
 */
object VaultSessionManager {

    private const val TAG =
        "AthenaSession"

    // =============================================================
    // STORAGE
    // =============================================================

    private const val PREF_NAME =
        "athena_session_v3"

    private const val KEY_VAULT_URI =
        "vault_uri"

    private const val KEY_BIOMETRIC_ENABLED =
        "biometric_enabled"

    // =============================================================
    // PREFERENCES
    // =============================================================

    private fun prefs(
        context: Context
    ): SharedPreferences {

        return context
            .applicationContext
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
    }

    // =============================================================
    // SAVE VAULT URI
    // =============================================================

    /**
     * Remembers which vault the user last selected.
     *
     * The URI is not secret cryptographic material.
     */
    fun saveSession(
        context: Context,
        uri: Uri?
    ) {

        val editor =
            prefs(context)
                .edit()

        if (
            uri == null
        ) {

            editor.remove(
                KEY_VAULT_URI
            )

        } else {

            editor.putString(
                KEY_VAULT_URI,
                uri.toString()
            )
        }

        /*
         * commit() is intentional here.
         *
         * Session state changes are infrequent and we prefer
         * knowing whether persistence actually succeeded.
         */
        val success =
            editor.commit()

        if (
            !success
        ) {

            Log.w(
                TAG,
                "Failed to persist vault URI"
            )
        }
    }

    // =============================================================
    // GET VAULT URI
    // =============================================================

    fun getSessionUri(
        context: Context
    ): Uri? {

        val stored =
            prefs(context)
                .getString(
                    KEY_VAULT_URI,
                    null
                )
                ?: return null

        return try {

            val uri =
                Uri.parse(
                    stored
                )

            /*
             * Uri.parse() is permissive.
             *
             * Require a useful scheme before trusting the stored
             * value as a Storage Access Framework vault location.
             */
            if (
                uri.scheme.isNullOrBlank()
            ) {

                null

            } else {

                uri
            }

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Stored vault URI is invalid",
                e
            )

            null
        }
    }

    // =============================================================
    // SESSION PRESENT
    // =============================================================

    fun hasSession(
        context: Context
    ): Boolean {

        return getSessionUri(
            context
        ) != null
    }

    // =============================================================
    // BIOMETRIC FLAG
    // =============================================================

    fun setBiometricEnabled(
        context: Context,
        enabled: Boolean
    ) {

        val success =
            prefs(context)
                .edit()
                .putBoolean(
                    KEY_BIOMETRIC_ENABLED,
                    enabled
                )
                .commit()

        if (
            !success
        ) {

            Log.w(
                TAG,
                "Failed to persist biometric preference"
            )
        }
    }

    fun isBiometricEnabled(
        context: Context
    ): Boolean {

        return prefs(context)
            .getBoolean(
                KEY_BIOMETRIC_ENABLED,
                false
            )
    }

    // =============================================================
    // NORMAL LOCK
    // =============================================================

    /**
     * Normal locking does NOT call this method.
     *
     * Locking only wipes the in-memory DEK.
     *
     * The remembered vault URI and biometric configuration remain
     * so Athena can unlock the same vault again.
     */
    fun clearRuntimeOnly() {

        VaultRuntimeSession.clear()
    }

    // =============================================================
    // FORGET VAULT
    // =============================================================

    /**
     * Completely forgets the selected vault from Athena.
     *
     * This is different from simply locking it.
     *
     * Call this for:
     *
     * - "Forget vault"
     * - security reset
     * - switching permanently to another vault
     */
    fun forgetVault(
        context: Context
    ) {

        /*
         * Destroy active DEK first.
         */
        VaultRuntimeSession.clear()

        /*
         * Remove biometric-wrapped DEK and Keystore wrapping key.
         */
        try {

            BiometricStore.clear(
                context
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Failed to clear biometric store while forgetting vault",
                e
            )
        }

        /*
         * Remove remembered vault metadata.
         */
        val success =
            prefs(context)
                .edit()
                .clear()
                .commit()

        if (
            !success
        ) {

            Log.w(
                TAG,
                "Failed to clear persistent session metadata"
            )
        }
    }

    // =============================================================
    // DISABLE BIOMETRICS ONLY
    // =============================================================

    /**
     * Disables biometric quick unlock without forgetting the vault.
     */
    fun disableBiometrics(
        context: Context
    ) {

        try {

            BiometricStore.clear(
                context
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Failed to clear biometric store",
                e
            )
        }

        setBiometricEnabled(
            context,
            false
        )
    }

    // =============================================================
    // LEGACY COMPATIBILITY
    // =============================================================

    /**
     * Kept temporarily because older code may still call this.
     *
     * Semantically, clearSession() now means "forget vault",
     * not merely "lock".
     *
     * Once all old call sites are removed, delete this method.
     */
    fun clearSession(
        context: Context
    ) {

        forgetVault(
            context
        )
    }
}