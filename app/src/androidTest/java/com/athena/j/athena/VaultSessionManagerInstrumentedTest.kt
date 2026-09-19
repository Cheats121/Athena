package com.athena.j.athena

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultSessionManagerInstrumentedTest {

    // =============================================================
    // CONTEXT
    // =============================================================

    private val context: Context
        get() =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

    // =============================================================
    // SETUP / CLEANUP
    // =============================================================

    @Before
    fun setup() {

        /*
         * Start each test with no runtime session.
         */
        VaultRuntimeSession.clear()

        /*
         * Clear persistent session state.
         *
         * This also clears biometric state, which is desirable
         * for test isolation.
         */
        VaultSessionManager.forgetVault(
            context
        )
    }

    @After
    fun cleanup() {

        VaultRuntimeSession.clear()

        VaultSessionManager.forgetVault(
            context
        )
    }

    // =============================================================
    // SAVE / LOAD URI
    // =============================================================

    @Test
    fun saveSession_storesVaultUri() {

        val uri =
            Uri.parse(
                "content://athena/test-vault"
            )

        VaultSessionManager.saveSession(
            context,
            uri
        )

        val loaded =
            VaultSessionManager.getSessionUri(
                context
            )

        assertEquals(
            "Stored URI should match the saved vault URI",
            uri,
            loaded
        )
    }

    // =============================================================
    // HAS SESSION
    // =============================================================

    @Test
    fun hasSession_returnsTrueWhenUriExists() {

        val uri =
            Uri.parse(
                "content://athena/test-vault"
            )

        assertFalse(
            VaultSessionManager.hasSession(
                context
            )
        )

        VaultSessionManager.saveSession(
            context,
            uri
        )

        assertTrue(
            "hasSession() should return true after saving a valid URI",
            VaultSessionManager.hasSession(
                context
            )
        )
    }

    // =============================================================
    // NULL URI
    // =============================================================

    @Test
    fun saveSession_nullUri_removesStoredUri() {

        val uri =
            Uri.parse(
                "content://athena/test-vault"
            )

        VaultSessionManager.saveSession(
            context,
            uri
        )

        assertNotNull(
            VaultSessionManager.getSessionUri(
                context
            )
        )

        VaultSessionManager.saveSession(
            context,
            null
        )

        assertNull(
            "Saving null should remove the remembered vault URI",
            VaultSessionManager.getSessionUri(
                context
            )
        )

        assertFalse(
            VaultSessionManager.hasSession(
                context
            )
        )
    }

    // =============================================================
    // REPLACE URI
    // =============================================================

    @Test
    fun saveSession_replacesPreviousVaultUri() {

        val firstUri =
            Uri.parse(
                "content://athena/first-vault"
            )

        val secondUri =
            Uri.parse(
                "content://athena/second-vault"
            )

        VaultSessionManager.saveSession(
            context,
            firstUri
        )

        VaultSessionManager.saveSession(
            context,
            secondUri
        )

        val result =
            VaultSessionManager.getSessionUri(
                context
            )

        assertEquals(
            "Most recently saved URI should replace the old URI",
            secondUri,
            result
        )
    }

    // =============================================================
    // INVALID URI
    // =============================================================

    @Test
    fun invalidStoredUri_withoutScheme_isRejected() {

        /*
         * VaultSessionManager uses:
         *
         * athena_session_v3
         * vault_uri
         *
         * We intentionally inject malformed persisted state here.
         */
        val prefs =
            context.getSharedPreferences(
                "athena_session_v3",
                Context.MODE_PRIVATE
            )

        prefs.edit()
            .putString(
                "vault_uri",
                "this-is-not-a-valid-content-uri"
            )
            .commit()

        val result =
            VaultSessionManager.getSessionUri(
                context
            )

        assertNull(
            "Stored URI without a scheme should be rejected",
            result
        )
    }

    // =============================================================
    // BIOMETRIC DEFAULT
    // =============================================================

    @Test
    fun biometricEnabled_defaultsToFalse() {

        assertFalse(
            "Biometric quick unlock should default to disabled",
            VaultSessionManager.isBiometricEnabled(
                context
            )
        )
    }

    // =============================================================
    // ENABLE BIOMETRICS
    // =============================================================

    @Test
    fun setBiometricEnabled_true_isPersisted() {

        VaultSessionManager.setBiometricEnabled(
            context,
            true
        )

        assertTrue(
            "Biometric enabled flag should persist",
            VaultSessionManager.isBiometricEnabled(
                context
            )
        )
    }

    // =============================================================
    // DISABLE BIOMETRICS FLAG
    // =============================================================

    @Test
    fun setBiometricEnabled_false_isPersisted() {

        VaultSessionManager.setBiometricEnabled(
            context,
            true
        )

        assertTrue(
            VaultSessionManager.isBiometricEnabled(
                context
            )
        )

        VaultSessionManager.setBiometricEnabled(
            context,
            false
        )

        assertFalse(
            "Biometric enabled flag should become false",
            VaultSessionManager.isBiometricEnabled(
                context
            )
        )
    }

    // =============================================================
    // RUNTIME-ONLY CLEAR
    // =============================================================

    @Test
    fun clearRuntimeOnly_destroysRuntimeDek_butKeepsVaultUri() {

        val uri =
            Uri.parse(
                "content://athena/runtime-test"
            )

        val dek =
            ByteArray(
                32
            ) {
                0x11
            }

        VaultSessionManager.saveSession(
            context,
            uri
        )

        VaultRuntimeSession.setSession(
            uri,
            dek
        )

        dek.fill(0)

        assertTrue(
            VaultRuntimeSession.isUnlocked()
        )

        assertNotNull(
            VaultSessionManager.getSessionUri(
                context
            )
        )

        VaultSessionManager.clearRuntimeOnly()

        assertFalse(
            "Runtime-only clear must lock the active session",
            VaultRuntimeSession.isUnlocked()
        )

        assertNull(
            "Runtime DEK must be unavailable after runtime clear",
            VaultRuntimeSession.getVaultDek()
        )

        assertEquals(
            "Remembered vault URI must survive normal runtime lock",
            uri,
            VaultSessionManager.getSessionUri(
                context
            )
        )
    }

    // =============================================================
    // NORMAL LOCK PRESERVES BIOMETRIC FLAG
    // =============================================================

    @Test
    fun clearRuntimeOnly_preservesBiometricPreference() {

        val uri =
            Uri.parse(
                "content://athena/runtime-test"
            )

        val dek =
            ByteArray(
                32
            ) {
                0x22
            }

        VaultSessionManager.saveSession(
            context,
            uri
        )

        VaultSessionManager.setBiometricEnabled(
            context,
            true
        )

        VaultRuntimeSession.setSession(
            uri,
            dek
        )

        dek.fill(0)

        VaultSessionManager.clearRuntimeOnly()

        assertFalse(
            VaultRuntimeSession.isUnlocked()
        )

        assertEquals(
            uri,
            VaultSessionManager.getSessionUri(
                context
            )
        )

        assertTrue(
            "Normal runtime lock should not disable biometric preference",
            VaultSessionManager.isBiometricEnabled(
                context
            )
        )
    }

    // =============================================================
    // FORGET VAULT - URI
    // =============================================================

    @Test
    fun forgetVault_removesStoredUri() {

        val uri =
            Uri.parse(
                "content://athena/test-vault"
            )

        VaultSessionManager.saveSession(
            context,
            uri
        )

        assertNotNull(
            VaultSessionManager.getSessionUri(
                context
            )
        )

        VaultSessionManager.forgetVault(
            context
        )

        assertNull(
            "Forget vault must remove remembered URI",
            VaultSessionManager.getSessionUri(
                context
            )
        )

        assertFalse(
            VaultSessionManager.hasSession(
                context
            )
        )
    }

    // =============================================================
    // FORGET VAULT - BIOMETRIC FLAG
    // =============================================================

    @Test
    fun forgetVault_removesBiometricPreference() {

        VaultSessionManager.setBiometricEnabled(
            context,
            true
        )

        assertTrue(
            VaultSessionManager.isBiometricEnabled(
                context
            )
        )

        VaultSessionManager.forgetVault(
            context
        )

        assertFalse(
            "Forget vault must clear biometric-enabled preference",
            VaultSessionManager.isBiometricEnabled(
                context
            )
        )
    }

    // =============================================================
    // FORGET VAULT - RUNTIME SESSION
    // =============================================================

    @Test
    fun forgetVault_destroysRuntimeSession() {

        val uri =
            Uri.parse(
                "content://athena/test-vault"
            )

        val dek =
            ByteArray(
                32
            ) {
                0x33
            }

        VaultSessionManager.saveSession(
            context,
            uri
        )

        VaultRuntimeSession.setSession(
            uri,
            dek
        )

        dek.fill(0)

        assertTrue(
            VaultRuntimeSession.isUnlocked()
        )

        VaultSessionManager.forgetVault(
            context
        )

        assertFalse(
            "Forget vault must destroy active runtime session",
            VaultRuntimeSession.isUnlocked()
        )

        assertNull(
            VaultRuntimeSession.getVaultDek()
        )

        assertNull(
            VaultRuntimeSession.getVaultUri()
        )
    }

    // =============================================================
    // FORGET EVERYTHING
    // =============================================================

    @Test
    fun forgetVault_clearsAllSessionMetadata() {

        val uri =
            Uri.parse(
                "content://athena/test-vault"
            )

        val dek =
            ByteArray(
                32
            ) {
                0x44
            }

        VaultSessionManager.saveSession(
            context,
            uri
        )

        VaultSessionManager.setBiometricEnabled(
            context,
            true
        )

        VaultRuntimeSession.setSession(
            uri,
            dek
        )

        dek.fill(0)

        VaultSessionManager.forgetVault(
            context
        )

        assertFalse(
            VaultRuntimeSession.isUnlocked()
        )

        assertNull(
            VaultSessionManager.getSessionUri(
                context
            )
        )

        assertFalse(
            VaultSessionManager.hasSession(
                context
            )
        )

        assertFalse(
            VaultSessionManager.isBiometricEnabled(
                context
            )
        )
    }

    // =============================================================
    // LEGACY CLEAR SESSION
    // =============================================================

    @Test
    fun clearSession_behavesLikeForgetVault() {

        val uri =
            Uri.parse(
                "content://athena/legacy-test"
            )

        val dek =
            ByteArray(
                32
            ) {
                0x55
            }

        VaultSessionManager.saveSession(
            context,
            uri
        )

        VaultSessionManager.setBiometricEnabled(
            context,
            true
        )

        VaultRuntimeSession.setSession(
            uri,
            dek
        )

        dek.fill(0)

        VaultSessionManager.clearSession(
            context
        )

        assertFalse(
            "Legacy clearSession() currently means forget vault",
            VaultRuntimeSession.isUnlocked()
        )

        assertNull(
            VaultSessionManager.getSessionUri(
                context
            )
        )

        assertFalse(
            VaultSessionManager.isBiometricEnabled(
                context
            )
        )
    }

    // =============================================================
    // PERSISTENT STORAGE DOES NOT CONTAIN DEK
    // =============================================================

    @Test
    fun sessionPreferences_doNotContainVaultDek() {

        val uri =
            Uri.parse(
                "content://athena/test-vault"
            )

        val dek =
            ByteArray(
                32
            ) {
                0x66
            }

        VaultSessionManager.saveSession(
            context,
            uri
        )

        VaultRuntimeSession.setSession(
            uri,
            dek
        )

        dek.fill(0)

        val prefs =
            context.getSharedPreferences(
                "athena_session_v3",
                Context.MODE_PRIVATE
            )

        val all =
            prefs.all

        assertFalse(
            "Session preferences must never contain a DEK field",
            all.containsKey(
                "vault_dek"
            )
        )

        assertFalse(
            "Session preferences must never contain a generic key field",
            all.containsKey(
                "dek"
            )
        )

        assertFalse(
            "Session preferences must never contain a KEK field",
            all.containsKey(
                "kek"
            )
        )

        assertFalse(
            "Session preferences must never contain a master-password field",
            all.containsKey(
                "master_password"
            )
        )

        assertEquals(
            "Session preference storage should only contain expected metadata",
            setOf(
                "vault_uri"
            ),
            all.keys
        )
    }

    // =============================================================
    // EXPECTED SESSION STORAGE KEYS
    // =============================================================

    @Test
    fun sessionPreferences_onlyContainExpectedMetadata() {

        val uri =
            Uri.parse(
                "content://athena/test-vault"
            )

        VaultSessionManager.saveSession(
            context,
            uri
        )

        VaultSessionManager.setBiometricEnabled(
            context,
            true
        )

        val prefs =
            context.getSharedPreferences(
                "athena_session_v3",
                Context.MODE_PRIVATE
            )

        val keys =
            prefs.all.keys

        assertEquals(
            "Only vault URI and biometric preference should be persisted",
            setOf(
                "vault_uri",
                "biometric_enabled"
            ),
            keys
        )
    }
}