package com.athena.j.athena

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EntryDetailActivityInstrumentedTest {

    private companion object {
        const val MASTER_PASSWORD = "StrongMasterPassword!123"
        const val HOSTNAME = "github.com"
        const val USERNAME = "test@example.com"
        const val ENTRY_PASSWORD = "VerySecretCredential!456"
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var vaultFile: File
    private lateinit var vaultUri: Uri
    private lateinit var recoveryKey: ByteArray
    private lateinit var vaultId: ByteArray

    private var scenario: ActivityScenario<EntryDetailActivity>? = null

    @Before
    fun setup() {
        VaultRuntimeSession.clear()
        VaultSessionManager.forgetVault(context)
        VaultSessionManager.setBiometricEnabled(context, false)

        try {
            BiometricStore.clear(context)
        } catch (_: Exception) {}

        try {
            RecoveryKeyStore.clearAll(context)
        } catch (_: Exception) {}

        vaultFile = File(
            context.cacheDir,
            "entry-detail-test-${System.nanoTime()}.json"
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
            "Test recovery key should be cached successfully",
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
                    put("hostname", HOSTNAME)
                    put("username", USERNAME)
                    put("password", ENTRY_PASSWORD)
                    put("created", 1_700_000_000_000L)
                    put("updated", 1_700_000_100_000L)
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

            VaultRuntimeSession.setSession(vaultUri, dek)
            VaultSessionManager.saveSession(context, vaultUri)

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
            ClipboardUtils.clearPendingSensitiveClipboard(context)
        } catch (_: Exception) {}

        try {
            VaultRuntimeSession.clear()
        } catch (_: Exception) {}

        try {
            VaultSessionManager.forgetVault(context)
        } catch (_: Exception) {}

        try {
            if (::vaultId.isInitialized) {
                RecoveryKeyStore.clear(
                    context = context,
                    vaultId = vaultId
                )
            }
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

    private fun launchEntryDetail() {
        val intent = Intent(
            context,
            EntryDetailActivity::class.java
        ).apply {
            putExtra("vaultUri", vaultUri.toString())
            putExtra("entryIndex", 0)
            putExtra("hostname", HOSTNAME)
            putExtra("username", USERNAME)
            putExtra("created", 1_700_000_000_000L)
            putExtra("updated", 1_700_000_100_000L)
        }

        scenario = ActivityScenario.launch(intent)

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun loadVault(): JSONArray {
        val dek = VaultRuntimeSession.getVaultDek()

        assertNotNull(dek)

        try {
            return requireNotNull(
                VaultManager.loadVaultWithKey(
                    context = context,
                    uri = vaultUri,
                    vaultKey = dek!!
                )
            )

        } finally {
            dek?.fill(0)
        }
    }

    private fun clipboardText(): String? {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        if (!clipboard.hasPrimaryClip()) return null

        val clip = clipboard.primaryClip ?: return null

        if (clip.itemCount <= 0) return null

        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    private fun waitForAuthenticationWork() {
        Thread.sleep(2_000L)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    fun initialState_passwordIsMasked() {
        launchEntryDetail()

        onView(withId(R.id.passwordField))
            .check(matches(withText("••••••••••••")))
    }

    @Test
    fun initialState_displaysHostnameAndUsername() {
        launchEntryDetail()

        onView(withId(R.id.entryTitle))
            .check(matches(withText(HOSTNAME)))

        onView(withId(R.id.usernameField))
            .check(matches(withText(USERNAME)))
    }

    @Test
    fun reveal_requiresAuthentication() {
        launchEntryDetail()

        var beforeReveal = ""

        scenario!!.onActivity { activity ->
            beforeReveal = activity
                .findViewById<android.widget.TextView>(R.id.passwordField)
                .text
                .toString()
        }

        assertEquals(
            "Password should initially be masked",
            "••••••••••••",
            beforeReveal
        )

        onView(withId(R.id.eyeButton))
            .perform(click())

        onView(withId(R.id.masterPasswordInput))
            .check(matches(isDisplayed()))

        onView(withId(R.id.dialogTitleText))
            .check(matches(withText("Reveal password")))

        var passwordWhileDialogOpen = ""

        scenario!!.onActivity { activity ->
            passwordWhileDialogOpen = activity
                .findViewById<android.widget.TextView>(R.id.passwordField)
                .text
                .toString()
        }

        assertEquals(
            "Credential must remain masked until authentication succeeds",
            "••••••••••••",
            passwordWhileDialogOpen
        )
    }

    @Test
    fun reveal_cancelAuthentication_keepsPasswordMasked() {
        launchEntryDetail()

        onView(withId(R.id.eyeButton))
            .perform(click())

        onView(withId(R.id.cancelButton))
            .perform(click())

        onView(withId(R.id.passwordField))
            .check(matches(withText("••••••••••••")))
    }

    @Test
    fun reveal_wrongMasterPassword_doesNotRevealCredential() {
        launchEntryDetail()

        onView(withId(R.id.eyeButton))
            .perform(click())

        onView(withId(R.id.masterPasswordInput))
            .perform(replaceText("WrongMasterPassword!999"))

        onView(withId(R.id.confirmButton))
            .perform(click())

        waitForAuthenticationWork()

        onView(withId(R.id.passwordField))
            .check(matches(withText("••••••••••••")))
    }

    @Test
    fun reveal_correctMasterPassword_revealsCredential() {
        launchEntryDetail()

        onView(withId(R.id.eyeButton))
            .perform(click())

        onView(withId(R.id.masterPasswordInput))
            .perform(replaceText(MASTER_PASSWORD))

        onView(withId(R.id.confirmButton))
            .perform(click())

        waitForAuthenticationWork()

        onView(withId(R.id.passwordField))
            .check(matches(withText(ENTRY_PASSWORD)))
    }

    @Test
    fun copy_requiresAuthentication_beforeClipboardChanges() {
        launchEntryDetail()

        val existingClipboardValue = "PRE_EXISTING_CLIPBOARD_VALUE"

        scenario!!.onActivity { activity ->
            val clipboard =
                activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText(
                    "Test",
                    existingClipboardValue
                )
            )
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(
            "Test setup must establish known clipboard state",
            existingClipboardValue,
            clipboardText()
        )

        onView(withId(R.id.copyButton))
            .perform(click())

        onView(withId(R.id.masterPasswordInput))
            .check(matches(isDisplayed()))

        onView(withId(R.id.dialogTitleText))
            .check(matches(withText("Copy password")))

        assertEquals(
            "Clipboard must remain unchanged before authentication",
            existingClipboardValue,
            clipboardText()
        )
    }

    @Test
    fun copy_correctMasterPassword_copiesCredential() {
        launchEntryDetail()

        onView(withId(R.id.copyButton))
            .perform(click())

        onView(withId(R.id.masterPasswordInput))
            .perform(replaceText(MASTER_PASSWORD))

        onView(withId(R.id.confirmButton))
            .perform(click())

        waitForAuthenticationWork()

        assertEquals(
            "Authenticated copy should place credential password on clipboard",
            ENTRY_PASSWORD,
            clipboardText()
        )
    }

    @Test
    fun edit_requiresAuthentication() {
        launchEntryDetail()

        onView(withId(R.id.editButton))
            .perform(click())

        onView(withId(R.id.masterPasswordInput))
            .check(matches(isDisplayed()))
    }

    @Test
    fun delete_firstRequiresConfirmation() {
        launchEntryDetail()

        onView(withId(R.id.deleteButton))
            .perform(click())

        onView(withId(R.id.yesButton))
            .check(matches(isDisplayed()))
    }

    @Test
    fun delete_confirmation_thenRequiresAuthentication() {
        launchEntryDetail()

        onView(withId(R.id.deleteButton))
            .perform(click())

        onView(withId(R.id.yesButton))
            .perform(click())

        onView(withId(R.id.masterPasswordInput))
            .check(matches(isDisplayed()))

        val vault = loadVault()

        assertEquals(1, vault.length())
    }

    @Test
    fun delete_cancelAuthentication_doesNotDeleteEntry() {
        launchEntryDetail()

        onView(withId(R.id.deleteButton))
            .perform(click())

        onView(withId(R.id.yesButton))
            .perform(click())

        onView(withId(R.id.cancelButton))
            .perform(click())

        val vault = loadVault()

        assertEquals(
            "Cancelling authentication must leave credential intact",
            1,
            vault.length()
        )

        assertEquals(
            HOSTNAME,
            vault.getJSONObject(0).getString("hostname")
        )
    }

    @Test
    fun delete_correctMasterPassword_removesExactEntry() {
        launchEntryDetail()

        onView(withId(R.id.deleteButton))
            .perform(click())

        onView(withId(R.id.yesButton))
            .perform(click())

        onView(withId(R.id.masterPasswordInput))
            .perform(replaceText(MASTER_PASSWORD))

        onView(withId(R.id.confirmButton))
            .perform(click())

        waitForAuthenticationWork()

        assertTrue(VaultRuntimeSession.isUnlocked())

        val vault = loadVault()

        assertEquals(
            "Authenticated deletion should remove the credential",
            0,
            vault.length()
        )
    }

    @Test
    fun revealedPassword_canBeHiddenWithoutSecondAuthentication() {
        launchEntryDetail()

        onView(withId(R.id.eyeButton))
            .perform(click())

        onView(withId(R.id.masterPasswordInput))
            .perform(replaceText(MASTER_PASSWORD))

        onView(withId(R.id.confirmButton))
            .perform(click())

        waitForAuthenticationWork()

        onView(withId(R.id.passwordField))
            .check(matches(withText(ENTRY_PASSWORD)))

        onView(withId(R.id.eyeButton))
            .perform(click())

        onView(withId(R.id.passwordField))
            .check(matches(withText("••••••••••••")))
    }
}