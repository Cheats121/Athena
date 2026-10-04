package com.athena.j.athena

import android.content.Context
import android.net.Uri
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.not
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    private companion object {
        const val MASTER_PASSWORD = "StrongMasterPassword!123"
        const val WRONG_PASSWORD = "DefinitelyWrongPassword!999"
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var vaultFile: File
    private lateinit var vaultUri: Uri
    private lateinit var recoveryKey: ByteArray
    private lateinit var vaultId: ByteArray

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setup() {
        VaultRuntimeSession.clear()
        TimeoutManager.clear()
        VaultSessionManager.forgetVault(context)

        try {
            BiometricStore.clear(context)
        } catch (_: Exception) {}

        try {
            RecoveryKeyStore.clearAll(context)
        } catch (_: Exception) {}

        VaultSessionManager.setBiometricEnabled(context, false)

        vaultFile = File(
            context.cacheDir,
            "main-activity-test-${System.nanoTime()}.json"
        )

        vaultFile.createNewFile()
        vaultUri = Uri.fromFile(vaultFile)
        recoveryKey = VaultManager.generateRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = vaultUri,
            password = MASTER_PASSWORD,
            recoveryKey = recoveryKey
        )

        vaultId = requireNotNull(
            VaultManager.getVaultId(
                context = context,
                uri = vaultUri
            )
        )

        val recoveryStored = RecoveryKeyStore.save(
            context = context,
            vaultId = vaultId,
            recoveryKey = recoveryKey
        )

        assertTrue(
            "Recovery key should be cached for MainActivity unlock tests",
            recoveryStored
        )

        val dek = VaultManager.deriveVaultKey(
            context = context,
            uri = vaultUri,
            password = MASTER_PASSWORD,
            recoveryKey = recoveryKey
        )

        assertNotNull(dek)

        val vault = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("type", "password")
                    put("hostname", "github.com")
                    put("username", "test@example.com")
                    put("password", "CredentialPassword!456")
                    put("created", 1_700_000_000_000L)
                    put("updated", 1_700_000_000_000L)
                }
            )
        }

        try {
            VaultManager.saveVaultWithKey(
                context = context,
                uri = vaultUri,
                entries = vault,
                vaultKey = dek!!
            )
        } finally {
            dek?.fill(0)
        }
    }

    @After
    fun cleanup() {
        try {
            scenario?.close()
        } catch (_: Exception) {}

        scenario = null

        try {
            TimeoutManager.clear()
        } catch (_: Exception) {}

        try {
            VaultRuntimeSession.clear()
        } catch (_: Exception) {}

        try {
            VaultSessionManager.forgetVault(context)
        } catch (_: Exception) {}

        try {
            BiometricStore.clear(context)
        } catch (_: Exception) {}

        try {
            RecoveryKeyStore.clearAll(context)
        } catch (_: Exception) {}

        try {
            if (::recoveryKey.isInitialized) {
                recoveryKey.fill(0)
            }
        } catch (_: Exception) {}

        try {
            if (::vaultId.isInitialized) {
                vaultId.fill(0)
            }
        } catch (_: Exception) {}

        try {
            vaultFile.delete()
        } catch (_: Exception) {}
    }

    private fun launchMainActivity() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.moveToState(Lifecycle.State.RESUMED)

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun launchMainActivityWithRememberedVault() {
        VaultSessionManager.saveSession(context, vaultUri)
        launchMainActivity()
    }

    private fun waitUntil(
        timeoutMs: Long = 8_000L,
        condition: () -> Boolean
    ) {
        val start = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < timeoutMs) {
            if (condition()) return
            Thread.sleep(50L)
        }

        fail("Timed out waiting for condition")
    }

    private fun readPasswordField(): String {
        var result = ""

        scenario!!.onActivity { activity ->
            result = activity
                .findViewById<EditText>(R.id.masterPassword)
                .text
                .toString()
        }

        return result
    }

    private fun isUnlockButtonEnabled(): Boolean {
        var result = false

        scenario!!.onActivity { activity ->
            result = activity.findViewById<Button>(R.id.loginButton).isEnabled
        }

        return result
    }

    private fun removeCachedRecoveryKey() {
        RecoveryKeyStore.clear(
            context = context,
            vaultId = vaultId
        )

        assertFalse(
            "Recovery-key cache must be empty for this test",
            RecoveryKeyStore.contains(
                context = context,
                vaultId = vaultId
            )
        )
    }

    private fun enterMasterPasswordAndUnlock() {
        onView(withId(R.id.masterPassword))
            .perform(replaceText(MASTER_PASSWORD))

        onView(withId(R.id.loginButton))
            .perform(click())
    }

    @Test
    fun initialState_withoutRememberedVault_disablesPasswordInput() {
        launchMainActivity()

        onView(withId(R.id.masterPassword))
            .check(matches(not(isEnabled())))
    }

    @Test
    fun initialState_withoutRememberedVault_disablesUnlockButton() {
        launchMainActivity()

        onView(withId(R.id.loginButton))
            .check(matches(not(isEnabled())))
    }

    @Test
    fun rememberedVault_enablesPasswordInput() {
        launchMainActivityWithRememberedVault()

        onView(withId(R.id.masterPassword))
            .check(matches(isEnabled()))
    }

    @Test
    fun rememberedVault_enablesUnlockButton() {
        launchMainActivityWithRememberedVault()

        onView(withId(R.id.loginButton))
            .check(matches(isEnabled()))
    }

    @Test
    fun rememberedVault_doesNotAutomaticallyCreateRuntimeSession() {
        launchMainActivityWithRememberedVault()

        assertFalse(
            "Remembered vault must not automatically unlock runtime session",
            VaultRuntimeSession.isUnlocked()
        )

        assertNull(VaultRuntimeSession.getVaultDek())
    }

    @Test
    fun wrongPassword_doesNotUnlockVault() {
        launchMainActivityWithRememberedVault()

        onView(withId(R.id.masterPassword))
            .perform(replaceText(WRONG_PASSWORD))

        onView(withId(R.id.loginButton))
            .perform(click())

        Thread.sleep(3_000L)

        assertFalse(
            "Wrong master password must not unlock runtime session",
            VaultRuntimeSession.isUnlocked()
        )

        assertNull(
            "Wrong password must never produce persistent runtime DEK",
            VaultRuntimeSession.getVaultDek()
        )
    }

    @Test
    fun wrongPassword_doesNotReplaceRememberedVaultUri() {
        launchMainActivityWithRememberedVault()

        onView(withId(R.id.masterPassword))
            .perform(replaceText(WRONG_PASSWORD))

        onView(withId(R.id.loginButton))
            .perform(click())

        Thread.sleep(3_000L)

        assertEquals(
            "Wrong password must not corrupt remembered vault metadata",
            vaultUri,
            VaultSessionManager.getSessionUri(context)
        )
    }

    @Test
    fun wrongPassword_reenablesUnlockButton() {
        launchMainActivityWithRememberedVault()

        onView(withId(R.id.masterPassword))
            .perform(replaceText(WRONG_PASSWORD))

        onView(withId(R.id.loginButton))
            .perform(click())

        waitUntil {
            isUnlockButtonEnabled()
        }

        assertTrue(
            "Unlock button should become available again after failed authentication",
            isUnlockButtonEnabled()
        )
    }

    @Test
    fun correctPassword_createsUnlockedRuntimeSession() {
        launchMainActivityWithRememberedVault()
        enterMasterPasswordAndUnlock()

        waitUntil(timeoutMs = 10_000L) {
            VaultRuntimeSession.isUnlocked()
        }

        assertTrue(
            "Correct master password with cached recovery key should unlock runtime session",
            VaultRuntimeSession.isUnlocked()
        )

        val dek = VaultRuntimeSession.getVaultDek()

        assertNotNull(
            "Successful v4 authentication must create runtime DEK",
            dek
        )

        assertEquals(32, dek!!.size)

        dek.fill(0)
    }

    @Test
    fun correctPassword_runtimeSessionUsesCorrectVaultUri() {
        launchMainActivityWithRememberedVault()
        enterMasterPasswordAndUnlock()

        waitUntil(timeoutMs = 10_000L) {
            VaultRuntimeSession.isUnlocked()
        }

        assertEquals(
            "Runtime session should be bound to selected vault",
            vaultUri,
            VaultRuntimeSession.getVaultUri()
        )
    }

    @Test
    fun correctPassword_runtimeDekDecryptsSelectedVault() {
        launchMainActivityWithRememberedVault()
        enterMasterPasswordAndUnlock()

        waitUntil(timeoutMs = 10_000L) {
            VaultRuntimeSession.isUnlocked()
        }

        val dek = VaultRuntimeSession.getVaultDek()

        assertNotNull(dek)

        try {
            val vault = VaultManager.loadVaultWithKey(
                context = context,
                uri = vaultUri,
                vaultKey = dek!!
            )

            assertNotNull(
                "Runtime DEK must authenticate selected encrypted vault",
                vault
            )

            assertEquals(1, vault!!.length())

            assertEquals(
                "github.com",
                vault.getJSONObject(0).getString("hostname")
            )

        } finally {
            dek?.fill(0)
        }
    }

    @Test
    fun missingCachedRecoveryKey_showsRecoveryDialog() {
        removeCachedRecoveryKey()

        launchMainActivityWithRememberedVault()
        enterMasterPasswordAndUnlock()

        onView(withId(R.id.recoveryKeyInput))
            .check(matches(isDisplayed()))

        onView(withId(R.id.dialogTitleText))
            .check(matches(withText("Recovery key required")))

        assertFalse(
            "Vault must remain locked until recovery key is supplied",
            VaultRuntimeSession.isUnlocked()
        )
    }

    @Test
    fun invalidRecoveryKeyFormat_doesNotUnlockVault() {
        removeCachedRecoveryKey()

        launchMainActivityWithRememberedVault()
        enterMasterPasswordAndUnlock()

        onView(withId(R.id.recoveryKeyInput))
            .perform(replaceText("NOT-A-VALID-RECOVERY-KEY"))

        onView(withId(R.id.unlockButton))
            .perform(click())

        onView(withId(R.id.recoveryErrorText))
            .check(matches(withText("Invalid recovery key format")))

        assertFalse(
            "Malformed recovery key must not unlock vault",
            VaultRuntimeSession.isUnlocked()
        )
    }

    @Test
    fun wrongRecoveryKey_doesNotUnlockVault() {
        removeCachedRecoveryKey()

        val wrongRecoveryKey = VaultManager.generateRecoveryKey()

        try {
            val wrongRecoveryCode = RecoveryKeyCodec.encode(wrongRecoveryKey)

            launchMainActivityWithRememberedVault()
            enterMasterPasswordAndUnlock()

            onView(withId(R.id.recoveryKeyInput))
                .perform(replaceText(wrongRecoveryCode))

            onView(withId(R.id.unlockButton))
                .perform(click())

            onView(withId(R.id.recoveryErrorText))
                .check(matches(withText("Incorrect password or recovery key")))

            assertFalse(
                "Wrong recovery key must not unlock runtime session",
                VaultRuntimeSession.isUnlocked()
            )

            assertFalse(
                "Wrong recovery key must not become trusted",
                RecoveryKeyStore.contains(
                    context = context,
                    vaultId = vaultId
                )
            )

        } finally {
            wrongRecoveryKey.fill(0)
        }
    }

    @Test
    fun correctRecoveryKey_unlocksVault() {
        removeCachedRecoveryKey()

        val recoveryCode = RecoveryKeyCodec.encode(recoveryKey)

        launchMainActivityWithRememberedVault()
        enterMasterPasswordAndUnlock()

        onView(withId(R.id.recoveryKeyInput))
            .perform(replaceText(recoveryCode))

        onView(withId(R.id.unlockButton))
            .perform(click())

        waitUntil(timeoutMs = 10_000L) {
            VaultRuntimeSession.isUnlocked()
        }

        assertTrue(
            "Correct password and recovery key should unlock vault",
            VaultRuntimeSession.isUnlocked()
        )

        assertEquals(
            vaultUri,
            VaultRuntimeSession.getVaultUri()
        )
    }

    @Test
    fun successfulManualRecoveryKey_isCachedForFutureUnlocks() {
        removeCachedRecoveryKey()

        val recoveryCode = RecoveryKeyCodec.encode(recoveryKey)

        launchMainActivityWithRememberedVault()
        enterMasterPasswordAndUnlock()

        onView(withId(R.id.recoveryKeyInput))
            .perform(replaceText(recoveryCode))

        onView(withId(R.id.unlockButton))
            .perform(click())

        waitUntil(timeoutMs = 10_000L) {
            VaultRuntimeSession.isUnlocked()
        }

        assertTrue(
            "Successful manual recovery should cache recovery key",
            RecoveryKeyStore.contains(
                context = context,
                vaultId = vaultId
            )
        )

        val cached = RecoveryKeyStore.load(
            context = context,
            vaultId = vaultId
        )

        assertNotNull(cached)

        try {
            assertArrayEquals(
                "Cached key must match the vault recovery key",
                recoveryKey,
                cached
            )
        } finally {
            cached?.fill(0)
        }
    }

    @Test
    fun afterManualRecovery_nextUnlockNeedsOnlyMasterPassword() {
        removeCachedRecoveryKey()

        val recoveryCode = RecoveryKeyCodec.encode(recoveryKey)

        launchMainActivityWithRememberedVault()
        enterMasterPasswordAndUnlock()

        onView(withId(R.id.recoveryKeyInput))
            .perform(replaceText(recoveryCode))

        onView(withId(R.id.unlockButton))
            .perform(click())

        waitUntil(timeoutMs = 10_000L) {
            VaultRuntimeSession.isUnlocked()
        }

        assertTrue(
            RecoveryKeyStore.contains(
                context = context,
                vaultId = vaultId
            )
        )

        VaultRuntimeSession.clear()

        try {
            scenario?.close()
        } catch (_: Exception) {}

        scenario = null

        launchMainActivityWithRememberedVault()
        enterMasterPasswordAndUnlock()

        waitUntil(timeoutMs = 10_000L) {
            VaultRuntimeSession.isUnlocked()
        }

        assertTrue(
            "Trusted device should unlock with master password after recovery key was cached",
            VaultRuntimeSession.isUnlocked()
        )
    }

    @Test
    fun correctPassword_clearsMasterPasswordField() {
        launchMainActivityWithRememberedVault()

        onView(withId(R.id.masterPassword))
            .perform(replaceText(MASTER_PASSWORD))

        assertEquals(
            MASTER_PASSWORD,
            readPasswordField()
        )

        onView(withId(R.id.loginButton))
            .perform(click())

        waitUntil(timeoutMs = 10_000L) {
            VaultRuntimeSession.isUnlocked()
        }

        waitUntil(timeoutMs = 3_000L) {
            readPasswordField().isEmpty()
        }

        assertEquals(
            "Successful unlock should clear master password from UI",
            "",
            readPasswordField()
        )
    }

    @Test
    fun correctPassword_preservesRememberedVaultUri() {
        launchMainActivityWithRememberedVault()
        enterMasterPasswordAndUnlock()

        waitUntil(timeoutMs = 10_000L) {
            VaultRuntimeSession.isUnlocked()
        }

        assertEquals(
            vaultUri,
            VaultSessionManager.getSessionUri(context)
        )
    }

    @Test
    fun successfulUnlock_doesNotPersistMasterPassword() {
        launchMainActivityWithRememberedVault()
        enterMasterPasswordAndUnlock()

        waitUntil(timeoutMs = 10_000L) {
            VaultRuntimeSession.isUnlocked()
        }

        val sessionPrefs = context.getSharedPreferences(
            "athena_session_v4",
            Context.MODE_PRIVATE
        )

        sessionPrefs.all.values.forEach { value ->
            assertNotEquals(
                "Master password must never appear in persisted session metadata",
                MASTER_PASSWORD,
                value?.toString()
            )
        }
    }

    @Test
    fun masterPasswordField_usesInstantPasswordMasking() {
        launchMainActivityWithRememberedVault()

        var usesCorrectTransformation = false

        scenario!!.onActivity { activity ->
            val input = activity.findViewById<EditText>(R.id.masterPassword)

            usesCorrectTransformation =
                input.transformationMethod is InstantPasswordTransformationMethod
        }

        assertTrue(
            "MainActivity master password input must use instant masking",
            usesCorrectTransformation
        )
    }
}