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


/**
 * Athena biometric vault-key storage.
 *
 * Protects Athena's random v3 vault Data Encryption Key (DEK)
 * using an authentication-protected AES key stored in the
 * Android Keystore.
 *
 * ARCHITECTURE
 *
 *      Random 256-bit Vault DEK
 *                │
 *                ▼
 *      Android Keystore AES key
 *                │
 *                ▼
 *          AES-256-GCM wrap
 *                │
 *                ▼
 *      ciphertext + nonce stored locally
 *
 * KEYSTORE PROTECTION
 * - key material never leaves Android Keystore
 * - requires BIOMETRIC_STRONG authentication
 * - requires authentication for each cryptographic operation
 * - invalidates the key when biometric enrollment changes
 * - requests StrongBox when supported
 *
 * STORAGE
 * - SharedPreferences stores only encrypted DEK material
 * - plaintext DEK is never persisted
 * - Android Keystore key material is never stored in preferences
 *
 * SECURITY
 * - AES-GCM provides authenticated encryption
 * - a fixed AAD value provides domain separation for DEK wrapping
 * - stored ciphertext and nonce lengths are strictly validated
 */
object BiometricStore {

    // =============================================================
    // LOGGING
    // =============================================================

    private const val TAG =
        "AthenaBiometric"

    // =============================================================
    // PERSISTED STORAGE
    // =============================================================

    private const val PREFS_NAME =
        "athena_biometric_store_v3"

    private const val PREF_VERSION =
        "version"

    private const val PREF_CIPHERTEXT =
        "wrapped_dek_ciphertext"

    private const val PREF_NONCE =
        "wrapped_dek_nonce"

    private const val STORAGE_VERSION =
        1

    // =============================================================
    // ANDROID KEYSTORE
    // =============================================================

    private const val ANDROID_KEYSTORE =
        "AndroidKeyStore"

    private const val KEY_ALIAS =
        "athena_v3_biometric_dek_key"

    private const val KEY_BITS =
        256

    // =============================================================
    // AES-GCM PARAMETERS
    // =============================================================

    private const val AES_MODE =
        "AES/GCM/NoPadding"

    private const val GCM_TAG_BITS =
        128

    private const val GCM_NONCE_BYTES =
        12

    private const val DEK_BYTES =
        32

    /*
     * Wrapped output consists of:
     *
     * 32-byte DEK
     * +
     * 16-byte AES-GCM authentication tag
     */
    private const val WRAPPED_DEK_BYTES =
        DEK_BYTES + 16

    // =============================================================
    // AUTHENTICATED ASSOCIATED DATA
    // =============================================================

    /**
     * Domain-separation AAD for biometric DEK wrapping.
     *
     * This binds the authenticated encryption operation to Athena's
     * biometric vault-DEK use case.
     *
     * IMPORTANT:
     *
     * updateAAD() must only be called after BiometricPrompt has
     * successfully authenticated the operation.
     *
     * Authentication-per-operation Android Keystore keys may reject
     * cryptographic use before authentication with a
     * KEY_USER_NOT_AUTHENTICATED error.
     */
    private val WRAP_AAD =
        "ATHENA|V3|BIOMETRIC|VAULT-DEK"
            .toByteArray(
                StandardCharsets.UTF_8
            )

    // =============================================================
    // BIOMETRIC AVAILABILITY
    // =============================================================

    /**
     * Returns whether the device can currently authenticate using
     * BIOMETRIC_STRONG.
     *
     * Examples may include supported fingerprint or face
     * authentication implementations.
     */
    fun isBiometricAvailable(
        context: Context
    ): Boolean {

        val biometricManager =
            BiometricManager.from(
                context
            )

        return biometricManager
            .canAuthenticate(
                BiometricManager
                    .Authenticators
                    .BIOMETRIC_STRONG
            ) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Compatibility helper for callers that only need to know
     * whether biometric authentication is currently usable.
     */
    fun canUseBiometrics(
        context: Context
    ): Boolean {

        return isBiometricAvailable(
            context
        )
    }

    // =============================================================
    // STORED WRAPPED DEK STATE
    // =============================================================

    /**
     * Returns whether a structurally valid wrapped DEK is present.
     *
     * Validation includes:
     *
     * - expected storage version
     * - ciphertext presence
     * - nonce presence
     * - valid Base64 encoding
     * - expected ciphertext length
     * - expected AES-GCM nonce length
     *
     * This does not prove that the wrapped DEK is decryptable.
     * Cryptographic authentication occurs during unwrapDek().
     */
    fun hasWrappedDek(
        context: Context
    ): Boolean {

        val preferences =
            prefs(
                context
            )

        // ---------------------------------------------------------
        // Validate storage format version
        // ---------------------------------------------------------

        if (
            preferences.getInt(
                PREF_VERSION,
                -1
            ) != STORAGE_VERSION
        ) {

            return false
        }

        // ---------------------------------------------------------
        // Load encoded ciphertext and nonce
        // ---------------------------------------------------------

        val encodedCiphertext =
            preferences.getString(
                PREF_CIPHERTEXT,
                null
            )

        val encodedNonce =
            preferences.getString(
                PREF_NONCE,
                null
            )

        if (
            encodedCiphertext.isNullOrBlank() ||
            encodedNonce.isNullOrBlank()
        ) {

            return false
        }

        // ---------------------------------------------------------
        // Decode and validate lengths
        // ---------------------------------------------------------

        return try {

            val ciphertext =
                Base64.decode(
                    encodedCiphertext,
                    Base64.NO_WRAP
                )

            val nonce =
                Base64.decode(
                    encodedNonce,
                    Base64.NO_WRAP
                )

            try {

                ciphertext.size ==
                        WRAPPED_DEK_BYTES &&
                        nonce.size ==
                        GCM_NONCE_BYTES

            } finally {

                ciphertext.fill(
                    0
                )

                nonce.fill(
                    0
                )
            }

        } catch (_: Exception) {

            false
        }
    }

    // =============================================================
    // STORED NONCE
    // =============================================================

    /**
     * Returns a decoded copy of the stored AES-GCM nonce.
     *
     * Returns null when:
     *
     * - no valid wrapped DEK exists
     * - the stored nonce is malformed
     * - the nonce has an unexpected length
     *
     * The caller owns the returned ByteArray.
     */
    fun getStoredIv(
        context: Context
    ): ByteArray? {

        if (
            !hasWrappedDek(
                context
            )
        ) {

            return null
        }

        val encodedNonce =
            prefs(
                context
            )
                .getString(
                    PREF_NONCE,
                    null
                )
                ?: return null

        return try {

            val nonce =
                Base64.decode(
                    encodedNonce,
                    Base64.NO_WRAP
                )

            if (
                nonce.size !=
                GCM_NONCE_BYTES
            ) {

                nonce.fill(
                    0
                )

                null

            } else {

                nonce
            }

        } catch (_: Exception) {

            null
        }
    }

    // =============================================================
    // ENCRYPTION CIPHER
    // =============================================================

    /**
     * Initializes the biometric-protected AES-GCM Cipher used to
     * wrap the vault DEK.
     *
     * IMPORTANT:
     *
     * Do not call updateAAD() or doFinal() here.
     *
     * The returned Cipher must first be authenticated through
     * BiometricPrompt.
     */
    fun initEncryptCipher(): Cipher? {

        return try {

            // -----------------------------------------------------
            // Obtain or create Keystore key
            // -----------------------------------------------------

            val secretKey =
                getOrCreateKey()

            // -----------------------------------------------------
            // Initialize AES-GCM encryption operation
            // -----------------------------------------------------

            val cipher =
                Cipher.getInstance(
                    AES_MODE
                )

            cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey
            )

            /*
             * No AAD is supplied yet.
             *
             * This key requires authentication for each operation,
             * so cryptographic use occurs only after the Cipher has
             * passed through BiometricPrompt.
             */
            cipher

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Unable to initialize biometric encryption cipher",
                e
            )

            null
        }
    }

    // =============================================================
    // DECRYPTION CIPHER
    // =============================================================

    /**
     * Initializes the biometric-protected AES-GCM Cipher used to
     * unwrap the vault DEK.
     *
     * The nonce must be the same value generated during wrapping.
     *
     * The returned Cipher must be passed through BiometricPrompt
     * before authenticated decryption occurs.
     */
    fun initDecryptCipher(
        nonce: ByteArray
    ): Cipher? {

        // ---------------------------------------------------------
        // Reject malformed GCM nonce
        // ---------------------------------------------------------

        if (
            nonce.size !=
            GCM_NONCE_BYTES
        ) {

            return null
        }

        return try {

            // -----------------------------------------------------
            // Existing key is required for decryption
            // -----------------------------------------------------

            val secretKey =
                getExistingKey()
                    ?: return null

            // -----------------------------------------------------
            // Initialize AES-GCM decryption operation
            // -----------------------------------------------------

            val cipher =
                Cipher.getInstance(
                    AES_MODE
                )

            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(
                    GCM_TAG_BITS,
                    nonce
                )
            )

            /*
             * Do not call updateAAD() here.
             *
             * The Keystore operation has not yet received a valid
             * biometric authentication token.
             */
            cipher

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Unable to initialize biometric decryption cipher",
                e
            )

            null
        }
    }

    // =============================================================
    // WRAP / STORE DEK
    // =============================================================

    /**
     * Wraps Athena's random 256-bit vault DEK using an authenticated
     * AES-GCM Cipher.
     *
     * [cipher] must be the Cipher returned by:
     *
     * BiometricPrompt.AuthenticationResult.CryptoObject
     *
     * after successful biometric authentication.
     *
     * Stored data contains only:
     *
     * - wrapped DEK ciphertext
     * - AES-GCM nonce
     * - storage format version
     */
    fun storeDek(
        context: Context,
        cipher: Cipher,
        dek: ByteArray
    ) {

        // ---------------------------------------------------------
        // Validate DEK size
        // ---------------------------------------------------------

        require(
            dek.size ==
                    DEK_BYTES
        ) {

            "Vault DEK must be 256 bits"
        }

        var ciphertext: ByteArray? =
            null

        var nonce: ByteArray? =
            null

        try {

            // -----------------------------------------------------
            // Bind operation to Athena biometric DEK domain
            // -----------------------------------------------------

            cipher.updateAAD(
                WRAP_AAD
            )

            // -----------------------------------------------------
            // Encrypt DEK
            // -----------------------------------------------------

            ciphertext =
                cipher.doFinal(
                    dek
                )

            // -----------------------------------------------------
            // Copy generated AES-GCM nonce
            // -----------------------------------------------------

            nonce =
                cipher.iv
                    ?.copyOf()
                    ?: throw SecurityException(
                        "Missing AES-GCM nonce"
                    )

            // -----------------------------------------------------
            // Validate nonce length
            // -----------------------------------------------------

            if (
                nonce.size !=
                GCM_NONCE_BYTES
            ) {

                throw SecurityException(
                    "Invalid biometric wrap nonce"
                )
            }

            // -----------------------------------------------------
            // Validate ciphertext length
            // -----------------------------------------------------

            if (
                ciphertext.size !=
                WRAPPED_DEK_BYTES
            ) {

                throw SecurityException(
                    "Invalid wrapped DEK length"
                )
            }

            // -----------------------------------------------------
            // Encode encrypted storage values
            // -----------------------------------------------------

            val encodedCiphertext =
                Base64.encodeToString(
                    ciphertext,
                    Base64.NO_WRAP
                )

            val encodedNonce =
                Base64.encodeToString(
                    nonce,
                    Base64.NO_WRAP
                )

            // -----------------------------------------------------
            // Persist wrapped DEK atomically
            // -----------------------------------------------------

            val success =
                prefs(
                    context
                )
                    .edit()
                    .clear()
                    .putInt(
                        PREF_VERSION,
                        STORAGE_VERSION
                    )
                    .putString(
                        PREF_CIPHERTEXT,
                        encodedCiphertext
                    )
                    .putString(
                        PREF_NONCE,
                        encodedNonce
                    )
                    .commit()

            if (
                !success
            ) {

                throw IllegalStateException(
                    "Failed to persist wrapped DEK"
                )
            }

        } finally {

            // -----------------------------------------------------
            // Destroy temporary encrypted buffers
            // -----------------------------------------------------

            ciphertext?.fill(
                0
            )

            nonce?.fill(
                0
            )
        }
    }

    // =============================================================
    // UNWRAP DEK
    // =============================================================

    /**
     * Unwraps Athena's random 256-bit vault DEK.
     *
     * [decryptCipher] must be the authenticated Cipher returned by
     * BiometricPrompt after successful authentication.
     *
     * Returns null when:
     *
     * - no valid wrapped DEK exists
     * - stored ciphertext is malformed
     * - AES-GCM authentication fails
     * - decrypted DEK length is invalid
     *
     * The caller owns the returned ByteArray and must wipe it when
     * no longer needed.
     */
    fun unwrapDek(
        context: Context,
        decryptCipher: Cipher
    ): ByteArray? {

        // ---------------------------------------------------------
        // Validate persisted wrapped state
        // ---------------------------------------------------------

        if (
            !hasWrappedDek(
                context
            )
        ) {

            return null
        }

        // ---------------------------------------------------------
        // Load encoded ciphertext
        // ---------------------------------------------------------

        val encodedCiphertext =
            prefs(
                context
            )
                .getString(
                    PREF_CIPHERTEXT,
                    null
                )
                ?: return null

        // ---------------------------------------------------------
        // Decode ciphertext
        // ---------------------------------------------------------

        val ciphertext =
            try {

                Base64.decode(
                    encodedCiphertext,
                    Base64.NO_WRAP
                )

            } catch (_: Exception) {

                return null
            }

        try {

            // -----------------------------------------------------
            // Validate wrapped ciphertext length
            // -----------------------------------------------------

            if (
                ciphertext.size !=
                WRAPPED_DEK_BYTES
            ) {

                return null
            }

            // -----------------------------------------------------
            // Authenticate and decrypt wrapped DEK
            // -----------------------------------------------------

            val dek =
                try {

                    decryptCipher.updateAAD(
                        WRAP_AAD
                    )

                    decryptCipher.doFinal(
                        ciphertext
                    )

                } catch (e: Exception) {

                    Log.w(
                        TAG,
                        "Biometric DEK authentication failed",
                        e
                    )

                    return null
                }

            // -----------------------------------------------------
            // Validate recovered DEK length
            // -----------------------------------------------------

            if (
                dek.size !=
                DEK_BYTES
            ) {

                dek.fill(
                    0
                )

                return null
            }

            return dek

        } finally {

            // -----------------------------------------------------
            // Destroy temporary ciphertext buffer
            // -----------------------------------------------------

            ciphertext.fill(
                0
            )
        }
    }

    // =============================================================
    // CLEAR BIOMETRIC STATE
    // =============================================================

    /**
     * Clears all biometric quick-unlock state.
     *
     * This removes:
     *
     * - persisted wrapped DEK ciphertext
     * - persisted AES-GCM nonce
     * - storage format version
     * - Android Keystore wrapping key
     *
     * After this operation, biometric quick unlock must be enrolled
     * again before it can be used.
     */
    fun clear(
        context: Context
    ) {

        // ---------------------------------------------------------
        // Clear persisted encrypted state
        // ---------------------------------------------------------

        val cleared =
            prefs(
                context
            )
                .edit()
                .clear()
                .commit()

        if (
            !cleared
        ) {

            Log.w(
                TAG,
                "Unable to clear biometric preferences"
            )
        }

        // ---------------------------------------------------------
        // Delete Keystore key
        // ---------------------------------------------------------

        deleteKeystoreKey()
    }

    // =============================================================
    // KEYSTORE KEY RETRIEVAL / CREATION
    // =============================================================

    /**
     * Returns the existing biometric wrapping key or creates a new
     * one when necessary.
     *
     * StrongBox is preferred when available.
     *
     * If StrongBox cannot be used, Athena gracefully falls back to
     * the standard Android Keystore implementation.
     */
    private fun getOrCreateKey(): SecretKey {

        // ---------------------------------------------------------
        // Reuse existing key
        // ---------------------------------------------------------

        getExistingKey()
            ?.let {

                return it
            }

        // ---------------------------------------------------------
        // Prefer StrongBox on supported Android versions
        // ---------------------------------------------------------

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.P
        ) {

            try {

                return generateKey(
                    requestStrongBox = true
                )

            } catch (
                _: StrongBoxUnavailableException
            ) {

                Log.i(
                    TAG,
                    "StrongBox unavailable; falling back to Android Keystore"
                )

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "StrongBox key creation failed; falling back",
                    e
                )
            }
        }

        // ---------------------------------------------------------
        // Standard Android Keystore fallback
        // ---------------------------------------------------------

        return generateKey(
            requestStrongBox = false
        )
    }

    /**
     * Returns the existing biometric wrapping key.
     *
     * Returns null if:
     *
     * - the key does not exist
     * - the Keystore cannot be read
     * - the stored entry is not a SecretKey
     */
    private fun getExistingKey(): SecretKey? {

        return try {

            val keyStore =
                KeyStore.getInstance(
                    ANDROID_KEYSTORE
                )

            keyStore.load(
                null
            )

            keyStore.getKey(
                KEY_ALIAS,
                null
            ) as? SecretKey

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Unable to read biometric Keystore key",
                e
            )

            null
        }
    }

    // =============================================================
    // KEY GENERATION
    // =============================================================

    /**
     * Generates Athena's biometric AES wrapping key.
     *
     * The generated key:
     *
     * - uses AES-256
     * - supports encryption and decryption
     * - uses GCM with no padding
     * - requires randomized encryption
     * - requires user authentication
     * - requires BIOMETRIC_STRONG where supported
     * - requires authentication for every use
     * - invalidates when biometric enrollment changes
     * - optionally requests StrongBox
     */
    private fun generateKey(
        requestStrongBox: Boolean
    ): SecretKey {

        // ---------------------------------------------------------
        // Create Android Keystore key generator
        // ---------------------------------------------------------

        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

        // ---------------------------------------------------------
        // Base AES-GCM key configuration
        // ---------------------------------------------------------

        val builder =
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or
                            KeyProperties.PURPOSE_DECRYPT
                )
                .setKeySize(
                    KEY_BITS
                )
                .setBlockModes(
                    KeyProperties.BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(
                    true
                )
                .setUserAuthenticationRequired(
                    true
                )

        // ---------------------------------------------------------
        // Require authentication for every operation
        // ---------------------------------------------------------

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.R
        ) {

            builder
                .setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG
                )

        } else {

            /*
             * Android 6-10:
             *
             * -1 requires authentication for each individual
             * cryptographic operation.
             */
            @Suppress("DEPRECATION")
            builder
                .setUserAuthenticationValidityDurationSeconds(
                    -1
                )
        }

        // ---------------------------------------------------------
        // Invalidate key when biometric enrollment changes
        // ---------------------------------------------------------

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.N
        ) {

            builder
                .setInvalidatedByBiometricEnrollment(
                    true
                )
        }

        // ---------------------------------------------------------
        // Request StrongBox where supported
        // ---------------------------------------------------------

        if (
            requestStrongBox &&
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.P
        ) {

            builder
                .setIsStrongBoxBacked(
                    true
                )
        }

        // ---------------------------------------------------------
        // Generate key inside Android Keystore
        // ---------------------------------------------------------

        keyGenerator.init(
            builder.build()
        )

        return keyGenerator
            .generateKey()
    }

    // =============================================================
    // KEYSTORE KEY DELETION
    // =============================================================

    /**
     * Deletes Athena's biometric wrapping key from Android Keystore.
     *
     * Failure is logged but does not crash the application.
     */
    private fun deleteKeystoreKey() {

        try {

            val keyStore =
                KeyStore.getInstance(
                    ANDROID_KEYSTORE
                )

            keyStore.load(
                null
            )

            if (
                keyStore.containsAlias(
                    KEY_ALIAS
                )
            ) {

                keyStore.deleteEntry(
                    KEY_ALIAS
                )
            }

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Failed to delete biometric Keystore key",
                e
            )
        }
    }

    // =============================================================
    // HARDWARE SECURITY INFORMATION
    // =============================================================

    /**
     * Diagnostic-only hardware security check.
     *
     * Returns true when the device advertises support for a
     * hardware-backed Android Keystore implementation.
     *
     * IMPORTANT:
     *
     * This does not prove that Athena's specific biometric key is
     * StrongBox-backed.
     */
    fun isHardwareBacked(
        context: Context
    ): Boolean {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            context.packageManager
                .hasSystemFeature(
                    android.content.pm.PackageManager
                        .FEATURE_HARDWARE_KEYSTORE
                )

        } else {

            false
        }
    }

    // =============================================================
    // SHARED PREFERENCES
    // =============================================================

    /**
     * Returns Athena's private biometric storage preferences.
     *
     * Only encrypted wrapped-DEK material is stored here.
     */
    private fun prefs(
        context: Context
    ) =
        context
            .applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
}