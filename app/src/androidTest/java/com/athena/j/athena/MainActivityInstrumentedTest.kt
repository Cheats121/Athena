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
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
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

    // =============================================================
    // CONSTANTS
    // =============================================================

    private companion object {

        const val MASTER_PASSWORD =
            "StrongMasterPassword!123"

        const val WRONG_PASSWORD =
            "DefinitelyWrongPassword!999"
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
    // TEST STATE
    // =============================================================

    private lateinit var vaultFile: File

    private lateinit var vaultUri: Uri

    private var scenario:
            ActivityScenario<MainActivity>? =
        null

    // =============================================================
    // SETUP
    // =============================================================

    @Before
    fun setup() {

        // ---------------------------------------------------------
        // Clear runtime state
        // ---------------------------------------------------------

        VaultRuntimeSession.clear()

        TimeoutManager.clear()

        // ---------------------------------------------------------
        // Clear remembered vault / biometric state
        // ---------------------------------------------------------

        VaultSessionManager.forgetVault(
            context
        )

        try {

            BiometricStore.clear(
                context
            )

        } catch (_: Exception) {
        }

        VaultSessionManager.setBiometricEnabled(
            context,
            false
        )

        // ---------------------------------------------------------
        // Create temporary real Athena vault
        // ---------------------------------------------------------

        vaultFile =
            File(
                context.cacheDir,
                "main-activity-test-${System.nanoTime()}.json"
            )

        vaultFile.createNewFile()

        vaultUri =
            Uri.fromFile(
                vaultFile
            )

        VaultManager.createVault(
            context,
            vaultUri,
            MASTER_PASSWORD
        )

        // ---------------------------------------------------------
        // Put one credential inside it
        // ---------------------------------------------------------

        val dek =
            VaultManager.deriveVaultKey(
                context,
                vaultUri,
                MASTER_PASSWORD
            )

        assertNotNull(
            dek
        )

        val vault =
            JSONArray().apply {

                put(
                    JSONObject().apply {

                        put(
                            "type",
                            "password"
                        )

                        put(
                            "hostname",
                            "github.com"
                        )

                        put(
                            "username",
                            "test@example.com"
                        )

                        put(
                            "password",
                            "CredentialPassword!456"
                        )

                        put(
                            "created",
                            1_700_000_000_000L
                        )

                        put(
                            "updated",
                            1_700_000_000_000L
                        )
                    }
                )
            }

        try {

            VaultManager.saveVaultWithKey(
                context,
                vaultUri,
                vault,
                dek!!
            )

        } finally {

            dek?.fill(0)
        }
    }

    // =============================================================
    // CLEANUP
    // =============================================================

    @After
    fun cleanup() {

        try {

            scenario?.close()

        } catch (_: Exception) {
        }

        scenario =
            null

        try {

            TimeoutManager.clear()

        } catch (_: Exception) {
        }

        try {

            VaultRuntimeSession.clear()

        } catch (_: Exception) {
        }

        try {

            VaultSessionManager.forgetVault(
                context
            )

        } catch (_: Exception) {
        }

        try {

            BiometricStore.clear(
                context
            )

        } catch (_: Exception) {
        }

        try {

            vaultFile.delete()

        } catch (_: Exception) {
        }
    }

    // =============================================================
    // HELPERS
    // =============================================================

    private fun launchMainActivity() {

        scenario =
            ActivityScenario.launch(
                MainActivity::class.java
            )

        scenario!!
            .moveToState(
                Lifecycle.State.RESUMED
            )

        InstrumentationRegistry
            .getInstrumentation()
            .waitForIdleSync()
    }

    private fun launchMainActivityWithRememberedVault() {

        VaultSessionManager.saveSession(
            context,
            vaultUri
        )

        launchMainActivity()
    }

    private fun waitUntil(
        timeoutMs: Long = 8_000L,
        condition: () -> Boolean
    ) {

        val start =
            System.currentTimeMillis()

        while (
            System.currentTimeMillis() - start <
            timeoutMs
        ) {

            if (
                condition()
            ) {

                return
            }

            Thread.sleep(
                50L
            )
        }

        fail(
            "Timed out waiting for condition"
        )
    }

    private fun readPasswordField(): String {

        var result =
            ""

        scenario!!.onActivity { activity ->

            result =
                activity
                    .findViewById<EditText>(
                        R.id.masterPassword
                    )
                    .text
                    .toString()
        }

        return result
    }

    private fun isUnlockButtonEnabled(): Boolean {

        var result =
            false

        scenario!!.onActivity { activity ->

            result =
                activity
                    .findViewById<Button>(
                        R.id.loginButton
                    )
                    .isEnabled
        }

        return result
    }

    private fun isPasswordInputEnabled(): Boolean {

        var result =
            false

        scenario!!.onActivity { activity ->

            result =
                activity
                    .findViewById<EditText>(
                        R.id.masterPassword
                    )
                    .isEnabled
        }

        return result
    }

    // =============================================================
    // INITIAL STATE
    // =============================================================

    @Test
    fun initialState_withoutRememberedVault_disablesPasswordInput() {

        launchMainActivity()

        onView(
            withId(
                R.id.masterPassword
            )
        )
            .check(
                matches(
                    not(
                        isEnabled()
                    )
                )
            )
    }

    @Test
    fun initialState_withoutRememberedVault_disablesUnlockButton() {

        launchMainActivity()

        onView(
            withId(
                R.id.loginButton
            )
        )
            .check(
                matches(
                    not(
                        isEnabled()
                    )
                )
            )
    }

    // =============================================================
    // REMEMBERED VAULT
    // =============================================================

    @Test
    fun rememberedVault_enablesPasswordInput() {

        launchMainActivityWithRememberedVault()

        onView(
            withId(
                R.id.masterPassword
            )
        )
            .check(
                matches(
                    isEnabled()
                )
            )
    }

    @Test
    fun rememberedVault_enablesUnlockButton() {

        launchMainActivityWithRememberedVault()

        onView(
            withId(
                R.id.loginButton
            )
        )
            .check(
                matches(
                    isEnabled()
                )
            )
    }

    @Test
    fun rememberedVault_doesNotAutomaticallyCreateRuntimeSession() {

        launchMainActivityWithRememberedVault()

        /*
         * A remembered URI is metadata only.
         *
         * It must never equal an unlocked vault.
         */
        assertFalse(
            "Remembered vault URI must not automatically unlock vault",
            VaultRuntimeSession.isUnlocked()
        )

        assertNull(
            VaultRuntimeSession.getVaultDek()
        )
    }

    // =============================================================
    // WRONG PASSWORD
    // =============================================================

    @Test
    fun wrongPassword_doesNotUnlockVault() {

        launchMainActivityWithRememberedVault()

        onView(
            withId(
                R.id.masterPassword
            )
        )
            .perform(
                replaceText(
                    WRONG_PASSWORD
                )
            )

        onView(
            withId(
                R.id.loginButton
            )
        )
            .perform(
                click()
            )

        /*
         * Argon2 is intentionally expensive.
         */
        Thread.sleep(
            3_000L
        )

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

        onView(
            withId(
                R.id.masterPassword
            )
        )
            .perform(
                replaceText(
                    WRONG_PASSWORD
                )
            )

        onView(
            withId(
                R.id.loginButton
            )
        )
            .perform(
                click()
            )

        Thread.sleep(
            3_000L
        )

        assertEquals(
            "Wrong password must not corrupt remembered vault metadata",
            vaultUri,
            VaultSessionManager.getSessionUri(
                context
            )
        )
    }

    @Test
    fun wrongPassword_reenablesUnlockButton() {

        launchMainActivityWithRememberedVault()

        onView(
            withId(
                R.id.masterPassword
            )
        )
            .perform(
                replaceText(
                    WRONG_PASSWORD
                )
            )

        onView(
            withId(
                R.id.loginButton
            )
        )
            .perform(
                click()
            )

        waitUntil {

            isUnlockButtonEnabled()
        }

        assertTrue(
            "Unlock button should become available again after failed authentication",
            isUnlockButtonEnabled()
        )
    }

    // =============================================================
    // CORRECT PASSWORD
    // =============================================================

    @Test
    fun correctPassword_createsUnlockedRuntimeSession() {

        launchMainActivityWithRememberedVault()

        onView(
            withId(
                R.id.masterPassword
            )
        )
            .perform(
                replaceText(
                    MASTER_PASSWORD
                )
            )

        onView(
            withId(
                R.id.loginButton
            )
        )
            .perform(
                click()
            )

        waitUntil(
            timeoutMs = 10_000L
        ) {

            VaultRuntimeSession.isUnlocked()
        }

        assertTrue(
            "Correct master password should unlock runtime session",
            VaultRuntimeSession.isUnlocked()
        )

        val dek =
            VaultRuntimeSession.getVaultDek()

        assertNotNull(
            "Successful password authentication must create runtime DEK",
            dek
        )

        assertEquals(
            32,
            dek!!.size
        )

        dek.fill(0)
    }

    @Test
    fun correctPassword_runtimeSessionUsesCorrectVaultUri() {

        launchMainActivityWithRememberedVault()

        onView(
            withId(
                R.id.masterPassword
            )
        )
            .perform(
                replaceText(
                    MASTER_PASSWORD
                )
            )

        onView(
            withId(
                R.id.loginButton
            )
        )
            .perform(
                click()
            )

        waitUntil(
            timeoutMs = 10_000L
        ) {

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

        onView(
            withId(
                R.id.masterPassword
            )
        )
            .perform(
                replaceText(
                    MASTER_PASSWORD
                )
            )

        onView(
            withId(
                R.id.loginButton
            )
        )
            .perform(
                click()
            )

        waitUntil(
            timeoutMs = 10_000L
        ) {

            VaultRuntimeSession.isUnlocked()
        }

        val dek =
            VaultRuntimeSession.getVaultDek()

        assertNotNull(
            dek
        )

        try {

            val vault =
                VaultManager.loadVaultWithKey(
                    context,
                    vaultUri,
                    dek!!
                )

            assertNotNull(
                "Runtime DEK must authenticate selected encrypted vault",
                vault
            )

            assertEquals(
                1,
                vault!!.length()
            )

            assertEquals(
                "github.com",
                vault
                    .getJSONObject(
                        0
                    )
                    .getString(
                        "hostname"
                    )
            )

        } finally {

            dek?.fill(0)
        }
    }

    // =============================================================
    // PASSWORD FIELD CLEANUP
    // =============================================================

    @Test
    fun correctPassword_clearsMasterPasswordField() {

        launchMainActivityWithRememberedVault()

        onView(
            withId(
                R.id.masterPassword
            )
        )
            .perform(
                replaceText(
                    MASTER_PASSWORD
                )
            )

        assertEquals(
            MASTER_PASSWORD,
            readPasswordField()
        )

        onView(
            withId(
                R.id.loginButton
            )
        )
            .perform(
                click()
            )

        waitUntil(
            timeoutMs = 10_000L
        ) {

            VaultRuntimeSession.isUnlocked()
        }

        waitUntil(
            timeoutMs = 3_000L
        ) {

            readPasswordField().isEmpty()
        }

        assertEquals(
            "Successful unlock should clear master password from UI",
            "",
            readPasswordField()
        )
    }

    // =============================================================
    // PERSISTED URI
    // =============================================================

    @Test
    fun correctPassword_preservesRememberedVaultUri() {

        launchMainActivityWithRememberedVault()

        onView(
            withId(
                R.id.masterPassword
            )
        )
            .perform(
                replaceText(
                    MASTER_PASSWORD
                )
            )

        onView(
            withId(
                R.id.loginButton
            )
        )
            .perform(
                click()
            )

        waitUntil(
            timeoutMs = 10_000L
        ) {

            VaultRuntimeSession.isUnlocked()
        }

        assertEquals(
            vaultUri,
            VaultSessionManager.getSessionUri(
                context
            )
        )
    }

    // =============================================================
    // SESSION PERSISTENCE DOES NOT CONTAIN PASSWORD
    // =============================================================

    @Test
    fun successfulUnlock_doesNotPersistMasterPassword() {

        launchMainActivityWithRememberedVault()

        onView(
            withId(
                R.id.masterPassword
            )
        )
            .perform(
                replaceText(
                    MASTER_PASSWORD
                )
            )

        onView(
            withId(
                R.id.loginButton
            )
        )
            .perform(
                click()
            )

        waitUntil(
            timeoutMs = 10_000L
        ) {

            VaultRuntimeSession.isUnlocked()
        }

        val sessionPrefs =
            context.getSharedPreferences(
                "athena_session_v3",
                Context.MODE_PRIVATE
            )

        sessionPrefs
            .all
            .values
            .forEach { value ->

                assertNotEquals(
                    "Master password must never appear in persisted session metadata",
                    MASTER_PASSWORD,
                    value?.toString()
                )
            }
    }

    // =============================================================
    // PASSWORD MASKING
    // =============================================================

    @Test
    fun masterPasswordField_usesInstantPasswordMasking() {

        launchMainActivityWithRememberedVault()

        var usesCorrectTransformation =
            false

        scenario!!.onActivity { activity ->

            val input =
                activity.findViewById<EditText>(
                    R.id.masterPassword
                )

            usesCorrectTransformation =
                input.transformationMethod is
                        InstantPasswordTransformationMethod
        }

        assertTrue(
            "MainActivity master password input must use instant masking",
            usesCorrectTransformation
        )
    }
}