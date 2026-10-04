package com.athena.j.athena

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricManager
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object BiometricStore {

    private const val TAG = "AthenaBiometric"
    private const val PREFS_NAME = "athena_biometric_store_v4"
    private const val PREF_VERSION = "version"
    private const val PREF_CIPHERTEXT = "wrapped_dek_ciphertext"
    private const val PREF_NONCE = "wrapped_dek_nonce"
    private const val STORAGE_VERSION = 1
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "athena_v4_biometric_dek_key"
    private const val KEY_BITS = 256
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_NONCE_BYTES = 12
    private const val DEK_BYTES = 32
    private const val WRAPPED_DEK_BYTES = DEK_BYTES + 16

    private val WRAP_AAD =
        "ATHENA|V4|BIOMETRIC|VAULT-DEK".toByteArray(StandardCharsets.UTF_8)

    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun canUseBiometrics(context: Context): Boolean {
        return isBiometricAvailable(context)
    }

    fun hasWrappedDek(context: Context): Boolean {
        val preferences = prefs(context)
        if (preferences.getInt(PREF_VERSION, -1) != STORAGE_VERSION) return false
        val encodedCiphertext = preferences.getString(PREF_CIPHERTEXT, null)
        val encodedNonce = preferences.getString(PREF_NONCE, null)
        if (encodedCiphertext.isNullOrBlank() || encodedNonce.isNullOrBlank()) return false
        return try {
            val ciphertext = Base64.decode(encodedCiphertext, Base64.NO_WRAP)
            val nonce = Base64.decode(encodedNonce, Base64.NO_WRAP)
            try {
                ciphertext.size == WRAPPED_DEK_BYTES && nonce.size == GCM_NONCE_BYTES
            } finally {
                ciphertext.fill(0)
                nonce.fill(0)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getStoredIv(context: Context): ByteArray? {
        if (!hasWrappedDek(context)) return null
        val encodedNonce = prefs(context).getString(PREF_NONCE, null) ?: return null
        return try {
            val nonce = Base64.decode(encodedNonce, Base64.NO_WRAP)
            if (nonce.size != GCM_NONCE_BYTES) {
                nonce.fill(0)
                null
            } else {
                nonce
            }
        } catch (_: Exception) {
            null
        }
    }

    fun initEncryptCipher(): Cipher? {
        return try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher

        } catch (e: Exception) {
            Log.e(TAG, "Unable to initialize biometric encryption cipher", e)
            null
        }
    }

    fun initDecryptCipher(nonce: ByteArray): Cipher? {
        if (nonce.size != GCM_NONCE_BYTES) return null
        return try {
            val secretKey = getExistingKey() ?: return null
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(GCM_TAG_BITS, nonce)
            )
            cipher

        } catch (e: Exception) {
            Log.w(TAG, "Unable to initialize biometric decryption cipher", e)
            null
        }
    }

    fun storeDek(context: Context, cipher: Cipher, dek: ByteArray) {
        require(dek.size == DEK_BYTES) { "Vault DEK must be 256 bits" }
        var ciphertext: ByteArray? = null
        var nonce: ByteArray? = null
        try {
            cipher.updateAAD(WRAP_AAD)
            ciphertext = cipher.doFinal(dek)
            nonce = cipher.iv?.copyOf()
                ?: throw SecurityException("Missing AES-GCM nonce")

            if (nonce.size != GCM_NONCE_BYTES) {
                throw SecurityException("Invalid biometric wrap nonce")
            }

            if (ciphertext.size != WRAPPED_DEK_BYTES) {
                throw SecurityException("Invalid wrapped DEK length")
            }

            val encodedCiphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            val encodedNonce = Base64.encodeToString(nonce, Base64.NO_WRAP)
            val success = prefs(context)
                .edit()
                .clear()
                .putInt(PREF_VERSION, STORAGE_VERSION)
                .putString(PREF_CIPHERTEXT, encodedCiphertext)
                .putString(PREF_NONCE, encodedNonce)
                .commit()

            if (!success) throw IllegalStateException("Failed to persist wrapped DEK")

        } finally {
            ciphertext?.fill(0)
            nonce?.fill(0)
        }
    }

    fun unwrapDek(context: Context, decryptCipher: Cipher): ByteArray? {
        if (!hasWrappedDek(context)) return null
        val encodedCiphertext = prefs(context).getString(PREF_CIPHERTEXT, null) ?: return null
        val ciphertext = try {
            Base64.decode(encodedCiphertext, Base64.NO_WRAP)
        } catch (_: Exception) {
            return null
        }
        try {
            if (ciphertext.size != WRAPPED_DEK_BYTES) return null
            val dek = try {
                decryptCipher.updateAAD(WRAP_AAD)
                decryptCipher.doFinal(ciphertext)

            } catch (e: Exception) {
                Log.w(TAG, "Biometric DEK authentication failed", e)
                return null
            }

            if (dek.size != DEK_BYTES) {
                dek.fill(0)
                return null
            }

            return dek
        } finally {
            ciphertext.fill(0)
        }
    }

    fun clear(context: Context) {
        val cleared = prefs(context).edit().clear().commit()
        if (!cleared) {
            Log.w(TAG, "Unable to clear biometric preferences")
        }

        deleteKeystoreKey()
    }

    private fun getOrCreateKey(): SecretKey {
        getExistingKey()?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generateKey(requestStrongBox = true)

            } catch (_: StrongBoxUnavailableException) {
                Log.i(TAG, "StrongBox unavailable; falling back to Android Keystore")

            } catch (e: Exception) {
                Log.w(TAG, "StrongBox key creation failed; falling back", e)
            }
        }

        return generateKey(requestStrongBox = false)
    }

    private fun getExistingKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read biometric Keystore key", e)
            null
        }
    }

    private fun generateKey(requestStrongBox: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setInvalidatedByBiometricEnrollment(true)
        }

        if (requestStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun deleteKeystoreKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }

        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete biometric Keystore key", e)
        }
    }

    fun isHardwareBacked(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_HARDWARE_KEYSTORE
            )
        } else {
            false
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}