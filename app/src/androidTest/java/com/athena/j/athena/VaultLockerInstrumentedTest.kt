package com.athena.j.athena

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultLockerInstrumentedTest {

    // =============================================================
    // CONTEXT
    // =============================================================

    private val context: Context
        get() =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

    // =============================================================
    // ACTIVITY
    // =============================================================

    private var scenario:
            ActivityScenario<MainActivity>? =
        null

    // =============================================================
    // SETUP
    // =============================================================

    @Before
    fun setup() {

        /*
         * Start from a completely clean state.
         */
        VaultRuntimeSession.clear()

        VaultSessionManager.forgetVault(
            context
        )

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

    // =============================================================
    // CLEANUP
    // =============================================================

    @After
    fun cleanup() {

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
            scenario?.close()
        } catch (_: Exception) {
        }

        scenario =
            null
    }

    // =============================================================
    // HELPERS
    // =============================================================

    private fun runOnActivity(
        action: (
            MainActivity
        ) -> Unit
    ) {

        val activeScenario =
            scenario
                ?: throw IllegalStateException(
                    "ActivityScenario is not initialized"
                )

        activeScenario.onActivity { activity ->

            action(
                activity
            )
        }
    }

    private fun createTestUri(): Uri {

        return Uri.parse(
            "content://athena/test-vault"
        )
    }

    private fun createTestDek(
        value: Byte
    ): ByteArray {

        return ByteArray(
            32
        ) {
            value
        }
    }

    // =============================================================
    // LOCK CLEARS RUNTIME SESSION
    // =============================================================

    @Test
    fun lockNow_clearsRuntimeSession() {

        val uri =
            createTestUri()

        val dek =
            createTestDek(
                0x11
            )

        VaultRuntimeSession.setSession(
            uri,
            dek
        )

        dek.fill(0)

        assertTrue(
            "Vault should initially be unlocked",
            VaultRuntimeSession.isUnlocked()
        )

        runOnActivity { activity ->

            VaultLocker.lockNow(
                activity
            )
        }

        assertFalse(
            "Lock must destroy unlocked runtime state",
            VaultRuntimeSession.isUnlocked()
        )

        assertNull(
            "Vault DEK must not remain accessible after lock",
            VaultRuntimeSession.getVaultDek()
        )

        assertNull(
            "Runtime vault URI must be cleared after lock",
            VaultRuntimeSession.getVaultUri()
        )
    }

    // =============================================================
    // LOCK CLEARS DEK EVEN WHEN SESSION EXISTS
    // =============================================================

    @Test
    fun lockNow_destroysActiveDek() {

        val uri =
            createTestUri()

        val dek =
            createTestDek(
                0x22
            )

        VaultRuntimeSession.setSession(
            uri,
            dek
        )

        dek.fill(0)

        val before =
            VaultRuntimeSession.getVaultDek()

        assertNotNull(
            before
        )

        before!!.fill(0)

        runOnActivity { activity ->

            VaultLocker.lockNow(
                activity
            )
        }

        val after =
            VaultRuntimeSession.getVaultDek()

        assertNull(
            "There must be no retrievable DEK after lock",
            after
        )
    }

    // =============================================================
    // LOCK PRESERVES REMEMBERED VAULT URI
    // =============================================================

    @Test
    fun lockNow_preservesRememberedVaultUri() {

        val uri =
            createTestUri()

        val dek =
            createTestDek(
                0x33
            )

        VaultSessionManager.saveSession(
            context,
            uri
        )

        VaultRuntimeSession.setSession(
            uri,
            dek
        )

        dek.fill(0)

        assertEquals(
            uri,
            VaultSessionManager.getSessionUri(
                context
            )
        )

        runOnActivity { activity ->

            VaultLocker.lockNow(
                activity
            )
        }

        assertFalse(
            VaultRuntimeSession.isUnlocked()
        )

        assertEquals(
            "Normal lock must preserve remembered vault URI",
            uri,
            VaultSessionManager.getSessionUri(
                context
            )
        )
    }

    // =============================================================
    // LOCK PRESERVES BIOMETRIC PREFERENCE
    // =============================================================

    @Test
    fun lockNow_preservesBiometricPreference() {

        val uri =
            createTestUri()

        val dek =
            createTestDek(
                0x44
            )

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

        assertTrue(
            VaultSessionManager.isBiometricEnabled(
                context
            )
        )

        runOnActivity { activity ->

            VaultLocker.lockNow(
                activity
            )
        }

        assertFalse(
            VaultRuntimeSession.isUnlocked()
        )

        assertTrue(
            "Normal lock must not disable biometric quick unlock",
            VaultSessionManager.isBiometricEnabled(
                context
            )
        )
    }

    // =============================================================
    // LOCK PRESERVES BOTH PERSISTENT VALUES
    // =============================================================

    @Test
    fun lockNow_preservesPersistentUnlockMetadata() {

        val uri =
            createTestUri()

        val dek =
            createTestDek(
                0x55
            )

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

        runOnActivity { activity ->

            VaultLocker.lockNow(
                activity
            )
        }

        assertFalse(
            "Runtime session must be destroyed",
            VaultRuntimeSession.isUnlocked()
        )

        assertEquals(
            "Remembered URI should remain available",
            uri,
            VaultSessionManager.getSessionUri(
                context
            )
        )

        assertTrue(
            "Biometric preference should remain enabled",
            VaultSessionManager.isBiometricEnabled(
                context
            )
        )
    }

    // =============================================================
    // LOCK CLEARS CLIPBOARD
    // =============================================================

    @Test
    fun lockNow_clearsClipboard() {

        val uri =
            createTestUri()

        val dek =
            createTestDek(
                0x66
            )

        VaultRuntimeSession.setSession(
            uri,
            dek
        )

        dek.fill(0)

        var clipboardCleared =
            false

        runOnActivity { activity ->

            val clipboard =
                activity.getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

            clipboard.setPrimaryClip(
                ClipData.newPlainText(
                    "Password",
                    "SuperSecretPassword"
                )
            )

            /*
             * Confirm test setup succeeded before invoking lock.
             */
            assertTrue(
                clipboard.hasPrimaryClip()
            )

            VaultLocker.lockNow(
                activity
            )

            clipboardCleared =
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.P
                ) {

                    !clipboard.hasPrimaryClip()

                } else {

                    val clip =
                        clipboard.primaryClip

                    val text =
                        if (
                            clip != null &&
                            clip.itemCount > 0
                        ) {

                            clip
                                .getItemAt(
                                    0
                                )
                                .coerceToText(
                                    activity
                                )
                                ?.toString()

                        } else {

                            null
                        }

                    text.isNullOrEmpty()
                }
        }

        assertTrue(
            "Lock must remove password data from clipboard",
            clipboardCleared
        )
    }

    // =============================================================
    // LOCK FINISHES CURRENT ACTIVITY
    // =============================================================

    @Test
    fun lockNow_finishesCurrentActivity() {

        val uri =
            createTestUri()

        val dek =
            createTestDek(
                0x77
            )

        VaultRuntimeSession.setSession(
            uri,
            dek
        )

        dek.fill(0)

        var activityWasFinishing =
            false

        runOnActivity { activity ->

            assertFalse(
                activity.isFinishing
            )

            VaultLocker.lockNow(
                activity
            )

            activityWasFinishing =
                activity.isFinishing
        }

        assertTrue(
            "The Activity that initiated lock should be finishing",
            activityWasFinishing
        )
    }

    // =============================================================
    // LOCK WORKS WHEN NO VAULT IS UNLOCKED
    // =============================================================

    @Test
    fun lockNow_whenAlreadyLocked_doesNotRestoreSession() {

        VaultRuntimeSession.clear()

        assertFalse(
            VaultRuntimeSession.isUnlocked()
        )

        runOnActivity { activity ->

            VaultLocker.lockNow(
                activity
            )
        }

        assertFalse(
            "Calling lock while already locked must remain locked",
            VaultRuntimeSession.isUnlocked()
        )

        assertNull(
            VaultRuntimeSession.getVaultDek()
        )

        assertNull(
            VaultRuntimeSession.getVaultUri()
        )
    }
}