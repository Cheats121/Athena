package com.athena.j.athena

import android.content.Context
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class BiometricStoreInstrumentedTest {

    // =============================================================
    // CONSTANTS MIRRORING BIOMETRIC STORE FORMAT
    // =============================================================

    /*
     * These values mirror the current BiometricStore persistence
     * format so we can deliberately inject malformed state.
     */

    private companion object {

        const val PREFS_NAME =
            "athena_biometric_store_v3"

        const val PREF_VERSION =
            "version"

        const val PREF_CIPHERTEXT =
            "wrapped_dek_ciphertext"

        const val PREF_NONCE =
            "wrapped_dek_nonce"

        const val STORAGE_VERSION =
            1

        const val KEY_ALIAS =
            "athena_v3_biometric_dek_key"

        const val DEK_BYTES =
            32

        const val GCM_TAG_BYTES =
            16

        const val WRAPPED_DEK_BYTES =
            DEK_BYTES + GCM_TAG_BYTES

        const val GCM_NONCE_BYTES =
            12
    }

    // =============================================================
    // CONTEXT
    // =============================================================

    private val context: Context
        get() =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

    // =============================================================
    // PREFERENCES
    // =============================================================

    private val prefs
        get() =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

    // =============================================================
    // SETUP / CLEANUP
    // =============================================================

    @Before
    fun setup() {

        try {
            BiometricStore.clear(
                context
            )
        } catch (_: Exception) {
        }

        prefs
            .edit()
            .clear()
            .commit()
    }

    @After
    fun cleanup() {

        try {
            BiometricStore.clear(
                context
            )
        } catch (_: Exception) {
        }

        prefs
            .edit()
            .clear()
            .commit()
    }

    // =============================================================
    // HELPERS
    // =============================================================

    private fun encode(
        bytes: ByteArray
    ): String {

        return Base64.encodeToString(
            bytes,
            Base64.NO_WRAP
        )
    }

    private fun validCiphertext(): ByteArray {

        return ByteArray(
            WRAPPED_DEK_BYTES
        ) { index ->

            (
                    index and 0x7F
                    ).toByte()
        }
    }

    private fun validNonce(): ByteArray {

        return ByteArray(
            GCM_NONCE_BYTES
        ) { index ->

            (
                    index + 1
                    ).toByte()
        }
    }

    private fun storeRawWrappedDek(
        version: Int = STORAGE_VERSION,
        ciphertext: ByteArray = validCiphertext(),
        nonce: ByteArray = validNonce()
    ) {

        prefs
            .edit()
            .clear()
            .putInt(
                PREF_VERSION,
                version
            )
            .putString(
                PREF_CIPHERTEXT,
                encode(
                    ciphertext
                )
            )
            .putString(
                PREF_NONCE,
                encode(
                    nonce
                )
            )
            .commit()
    }

    private fun keystoreContainsBiometricKey(): Boolean {

        return try {

            val keyStore =
                KeyStore.getInstance(
                    "AndroidKeyStore"
                )

            keyStore.load(
                null
            )

            keyStore.containsAlias(
                KEY_ALIAS
            )

        } catch (_: Exception) {

            false
        }
    }

    // =============================================================
    // EMPTY STORE
    // =============================================================

    @Test
    fun hasWrappedDek_falseWhenStoreEmpty() {

        assertFalse(
            "Empty biometric store must not report a wrapped DEK",
            BiometricStore.hasWrappedDek(
                context
            )
        )
    }

    // =============================================================
    // VALID STRUCTURAL DATA
    // =============================================================

    @Test
    fun hasWrappedDek_trueForStructurallyValidStoredData() {

        storeRawWrappedDek()

        assertTrue(
            "Correct version, ciphertext length and nonce length should be structurally valid",
            BiometricStore.hasWrappedDek(
                context
            )
        )
    }

    // =============================================================
    // STORAGE VERSION
    // =============================================================

    @Test
    fun hasWrappedDek_falseWhenVersionMissing() {

        prefs
            .edit()
            .clear()
            .putString(
                PREF_CIPHERTEXT,
                encode(
                    validCiphertext()
                )
            )
            .putString(
                PREF_NONCE,
                encode(
                    validNonce()
                )
            )
            .commit()

        assertFalse(
            "Missing storage version must invalidate biometric state",
            BiometricStore.hasWrappedDek(
                context
            )
        )
    }

    @Test
    fun hasWrappedDek_falseWhenVersionWrong() {

        storeRawWrappedDek(
            version = 999
        )

        assertFalse(
            "Unknown biometric storage version must be rejected",
            BiometricStore.hasWrappedDek(
                context
            )
        )
    }

    // =============================================================
    // MISSING VALUES
    // =============================================================

    @Test
    fun hasWrappedDek_falseWhenCiphertextMissing() {

        prefs
            .edit()
            .clear()
            .putInt(
                PREF_VERSION,
                STORAGE_VERSION
            )
            .putString(
                PREF_NONCE,
                encode(
                    validNonce()
                )
            )
            .commit()

        assertFalse(
            "Missing wrapped ciphertext must invalidate biometric state",
            BiometricStore.hasWrappedDek(
                context
            )
        )
    }

    @Test
    fun hasWrappedDek_falseWhenNonceMissing() {

        prefs
            .edit()
            .clear()
            .putInt(
                PREF_VERSION,
                STORAGE_VERSION
            )
            .putString(
                PREF_CIPHERTEXT,
                encode(
                    validCiphertext()
                )
            )
            .commit()

        assertFalse(
            "Missing nonce must invalidate biometric state",
            BiometricStore.hasWrappedDek(
                context
            )
        )
    }

    // =============================================================
    // EMPTY VALUES
    // =============================================================

    @Test
    fun hasWrappedDek_falseWhenCiphertextBlank() {

        prefs
            .edit()
            .clear()
            .putInt(
                PREF_VERSION,
                STORAGE_VERSION
            )
            .putString(
                PREF_CIPHERTEXT,
                ""
            )
            .putString(
                PREF_NONCE,
                encode(
                    validNonce()
                )
            )
            .commit()

        assertFalse(
            BiometricStore.hasWrappedDek(
                context
            )
        )
    }

    @Test
    fun hasWrappedDek_falseWhenNonceBlank() {

        prefs
            .edit()
            .clear()
            .putInt(
                PREF_VERSION,
                STORAGE_VERSION
            )
            .putString(
                PREF_CIPHERTEXT,
                encode(
                    validCiphertext()
                )
            )
            .putString(
                PREF_NONCE,
                ""
            )
            .commit()

        assertFalse(
            BiometricStore.hasWrappedDek(
                context
            )
        )
    }

    // =============================================================
    // INVALID BASE64
    // =============================================================

    @Test
    fun hasWrappedDek_falseForInvalidCiphertextBase64() {

        prefs
            .edit()
            .clear()
            .putInt(
                PREF_VERSION,
                STORAGE_VERSION
            )
            .putString(
                PREF_CIPHERTEXT,
                "%%%NOT_BASE64%%%"
            )
            .putString(
                PREF_NONCE,
                encode(
                    validNonce()
                )
            )
            .commit()

        assertFalse(
            "Malformed wrapped-DEK Base64 must be rejected",
            BiometricStore.hasWrappedDek(
                context
            )
        )
    }

    @Test
    fun hasWrappedDek_falseForInvalidNonceBase64() {

        prefs
            .edit()
            .clear()
            .putInt(
                PREF_VERSION,
                STORAGE_VERSION
            )
            .putString(
                PREF_CIPHERTEXT,
                encode(
                    validCiphertext()
                )
            )
            .putString(
                PREF_NONCE,
                "%%%NOT_BASE64%%%"
            )
            .commit()

        assertFalse(
            "Malformed nonce Base64 must be rejected",
            BiometricStore.hasWrappedDek(
                context
            )
        )
    }

    // =============================================================
    // CIPHERTEXT LENGTH
    // =============================================================

    @Test
    fun hasWrappedDek_falseWhenCiphertextTooShort() {

        storeRawWrappedDek(
            ciphertext =
                ByteArray(
                    WRAPPED_DEK_BYTES - 1
                )
        )

        assertFalse(
            "Wrapped DEK ciphertext with incorrect length must be rejected",
            BiometricStore.hasWrappedDek(
                context
            )
        )
    }

    @Test
    fun hasWrappedDek_falseWhenCiphertextTooLong() {

        storeRawWrappedDek(
            ciphertext =
                ByteArray(
                    WRAPPED_DEK_BYTES + 1
                )
        )

        assertFalse(
            "Oversized wrapped DEK ciphertext must be rejected",
            BiometricStore.hasWrappedDek(
                context
            )
        )
    }

    // =============================================================
    // NONCE LENGTH
    // =============================================================

    @Test
    fun hasWrappedDek_falseWhenNonceTooShort() {

        storeRawWrappedDek(
            nonce =
                ByteArray(
                    GCM_NONCE_BYTES - 1
                )
        )

        assertFalse(
            "Short AES-GCM nonce must be rejected",
            BiometricStore.hasWrappedDek(
                context
            )
        )
    }

    @Test
    fun hasWrappedDek_falseWhenNonceTooLong() {

        storeRawWrappedDek(
            nonce =
                ByteArray(
                    GCM_NONCE_BYTES + 1
                )
        )

        assertFalse(
            "Oversized AES-GCM nonce must be rejected",
            BiometricStore.hasWrappedDek(
                context
            )
        )
    }

    // =============================================================
    // GET STORED IV
    // =============================================================

    @Test
    fun getStoredIv_returnsNullWhenNoWrappedDek() {

        assertNull(
            "No wrapped biometric DEK means no stored nonce",
            BiometricStore.getStoredIv(
                context
            )
        )
    }

    @Test
    fun getStoredIv_returnsExpectedNonce() {

        val nonce =
            validNonce()

        storeRawWrappedDek(
            nonce = nonce
        )

        val result =
            BiometricStore.getStoredIv(
                context
            )

        assertNotNull(
            result
        )

        assertArrayEquals(
            "Stored nonce should be returned unchanged",
            nonce,
            result
        )

        result?.fill(0)
        nonce.fill(0)
    }

    @Test
    fun getStoredIv_returnsTwelveByteNonce() {

        storeRawWrappedDek()

        val result =
            BiometricStore.getStoredIv(
                context
            )

        assertNotNull(
            result
        )

        assertEquals(
            "AES-GCM biometric nonce must be 12 bytes",
            GCM_NONCE_BYTES,
            result!!.size
        )

        result.fill(0)
    }

    // =============================================================
    // STORED IV DEFENSIVE BEHAVIOR
    // =============================================================

    @Test
    fun modifyingReturnedIv_doesNotModifyStoredNonce() {

        val original =
            validNonce()

        storeRawWrappedDek(
            nonce = original
        )

        val first =
            BiometricStore.getStoredIv(
                context
            )

        assertNotNull(
            first
        )

        first!!.fill(0)

        val second =
            BiometricStore.getStoredIv(
                context
            )

        assertNotNull(
            second
        )

        assertArrayEquals(
            "Returned nonce must not expose mutable persistent state",
            original,
            second
        )

        second!!.fill(0)
        original.fill(0)
    }

    // =============================================================
    // CLEAR STORED DATA
    // =============================================================

    @Test
    fun clear_removesWrappedDekPreferences() {

        storeRawWrappedDek()

        assertTrue(
            BiometricStore.hasWrappedDek(
                context
            )
        )

        BiometricStore.clear(
            context
        )

        assertFalse(
            "clear() must remove wrapped biometric DEK state",
            BiometricStore.hasWrappedDek(
                context
            )
        )

        assertTrue(
            "Biometric preferences should be empty after clear()",
            prefs.all.isEmpty()
        )
    }

    // =============================================================
    // CLEAR IS IDEMPOTENT
    // =============================================================

    @Test
    fun clear_whenAlreadyEmpty_doesNotFail() {

        BiometricStore.clear(
            context
        )

        BiometricStore.clear(
            context
        )

        assertFalse(
            BiometricStore.hasWrappedDek(
                context
            )
        )

        assertTrue(
            prefs.all.isEmpty()
        )
    }

    // =============================================================
    // BIOMETRIC AVAILABILITY
    // =============================================================

    @Test
    fun isBiometricAvailable_matchesBiometricManager() {

        val manager =
            BiometricManager.from(
                context
            )

        val expected =
            manager.canAuthenticate(
                BiometricManager
                    .Authenticators
                    .BIOMETRIC_STRONG
            ) ==
                    BiometricManager.BIOMETRIC_SUCCESS

        val actual =
            BiometricStore.isBiometricAvailable(
                context
            )

        assertEquals(
            "BiometricStore availability should match Android BiometricManager",
            expected,
            actual
        )
    }

    @Test
    fun canUseBiometrics_matchesAvailability() {

        assertEquals(
            BiometricStore.isBiometricAvailable(
                context
            ),
            BiometricStore.canUseBiometrics(
                context
            )
        )
    }

    // =============================================================
    // ENCRYPT CIPHER / KEYSTORE
    // =============================================================

    @Test
    fun initEncryptCipher_createsKeystoreKeyWhenSupported() {

        /*
         * Some devices/emulators may not permit creation of an
         * authentication-bound key if their secure lock / biometric
         * configuration does not support it.
         *
         * In that case BiometricStore correctly returns null.
         */
        val cipher =
            BiometricStore.initEncryptCipher()

        if (
            cipher == null
        ) {

            /*
             * Environment does not support this path.
             *
             * The important invariant is that it failed closed.
             */
            assertFalse(
                keystoreContainsBiometricKey()
            )

            return
        }

        assertEquals(
            "AES/GCM/NoPadding",
            cipher.algorithm
        )

        assertTrue(
            "Initializing biometric encryption should create the Android Keystore key",
            keystoreContainsBiometricKey()
        )
    }

    // =============================================================
    // CLEAR KEYSTORE KEY
    // =============================================================

    @Test
    fun clear_deletesBiometricKeystoreKeyWhenCreated() {

        val cipher =
            BiometricStore.initEncryptCipher()

        if (
            cipher == null
        ) {

            /*
             * Keystore environment does not support creation.
             * There is therefore no key to verify deletion for.
             */
            return
        }

        assertTrue(
            "Biometric key should exist before clear()",
            keystoreContainsBiometricKey()
        )

        BiometricStore.clear(
            context
        )

        assertFalse(
            "clear() must delete the biometric Android Keystore key",
            keystoreContainsBiometricKey()
        )
    }

    // =============================================================
    // INIT DECRYPT WITHOUT KEY
    // =============================================================

    @Test
    fun initDecryptCipher_withoutKeystoreKey_returnsNull() {

        BiometricStore.clear(
            context
        )

        val nonce =
            validNonce()

        try {

            val cipher =
                BiometricStore.initDecryptCipher(
                    nonce
                )

            assertNull(
                "Decrypt cipher must not be created without an existing biometric key",
                cipher
            )

        } finally {

            nonce.fill(0)
        }
    }

    // =============================================================
    // INVALID DECRYPT NONCE
    // =============================================================

    @Test
    fun initDecryptCipher_rejectsShortNonce() {

        val result =
            BiometricStore.initDecryptCipher(
                ByteArray(
                    GCM_NONCE_BYTES - 1
                )
            )

        assertNull(
            "Biometric decrypt cipher must reject a nonce shorter than 12 bytes",
            result
        )
    }

    @Test
    fun initDecryptCipher_rejectsLongNonce() {

        val result =
            BiometricStore.initDecryptCipher(
                ByteArray(
                    GCM_NONCE_BYTES + 1
                )
            )

        assertNull(
            "Biometric decrypt cipher must reject a nonce longer than 12 bytes",
            result
        )
    }
}