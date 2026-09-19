package com.athena.j.athena

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
class EditEntryActivityInstrumentedTest {

    // =============================================================
    // CONSTANTS
    // =============================================================

    private companion object {

        const val MASTER_PASSWORD =
            "StrongMasterPassword!123"

        const val FIRST_HOSTNAME =
            "amazon.com"

        const val FIRST_USERNAME =
            "amazon@example.com"

        const val FIRST_PASSWORD =
            "AmazonSecret!111"

        const val SECOND_HOSTNAME =
            "github.com"

        const val SECOND_USERNAME =
            "github@example.com"

        const val SECOND_PASSWORD =
            "GithubSecret!222"
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
            ActivityScenario<EditEntryActivity>? =
        null

    // =============================================================
    // SETUP
    // =============================================================

    @Before
    fun setup() {

        VaultRuntimeSession.clear()

        VaultSessionManager.forgetVault(
            context
        )

        vaultFile =
            File(
                context.cacheDir,
                "edit-entry-test-${System.nanoTime()}.json"
            )

        vaultFile.createNewFile()

        vaultUri =
            Uri.fromFile(
                vaultFile
            )

        // ---------------------------------------------------------
        // Create real Athena v3 encrypted vault
        // ---------------------------------------------------------

        VaultManager.createVault(
            context,
            vaultUri,
            MASTER_PASSWORD
        )

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
                            FIRST_HOSTNAME
                        )

                        put(
                            "username",
                            FIRST_USERNAME
                        )

                        put(
                            "password",
                            FIRST_PASSWORD
                        )

                        put(
                            "created",
                            1_700_000_000_000L
                        )

                        put(
                            "updated",
                            1_700_000_100_000L
                        )
                    }
                )

                put(
                    JSONObject().apply {

                        put(
                            "type",
                            "password"
                        )

                        put(
                            "hostname",
                            SECOND_HOSTNAME
                        )

                        put(
                            "username",
                            SECOND_USERNAME
                        )

                        put(
                            "password",
                            SECOND_PASSWORD
                        )

                        put(
                            "created",
                            1_700_000_200_000L
                        )

                        put(
                            "updated",
                            1_700_000_300_000L
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

            /*
             * Runtime session makes its own defensive copy.
             */
            VaultRuntimeSession.setSession(
                vaultUri,
                dek
            )

            VaultSessionManager.saveSession(
                context,
                vaultUri
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
            vaultFile.delete()
        } catch (_: Exception) {
        }
    }

    // =============================================================
    // HELPERS
    // =============================================================

    private fun launchEditor(
        entryIndex: Int = 1
    ) {

        val intent =
            Intent(
                context,
                EditEntryActivity::class.java
            ).apply {

                putExtra(
                    "vaultUri",
                    vaultUri.toString()
                )

                putExtra(
                    "entryIndex",
                    entryIndex
                )
            }

        scenario =
            ActivityScenario.launch(
                intent
            )

        waitForEntryLoaded()
    }

    private fun waitForEntryLoaded(
        timeoutMs: Long = 5_000L
    ) {

        val start =
            System.currentTimeMillis()

        while (
            System.currentTimeMillis() - start <
            timeoutMs
        ) {

            var loaded =
                false

            try {

                scenario?.onActivity { activity ->

                    val saveButton =
                        activity.findViewById<android.widget.TextView>(
                            R.id.saveButton
                        )

                    val hostname =
                        activity.findViewById<android.widget.EditText>(
                            R.id.hostnameInput
                        )

                    loaded =
                        saveButton.isEnabled &&
                                hostname.text.isNotEmpty()
                }

            } catch (_: Exception) {
            }

            if (
                loaded
            ) {

                InstrumentationRegistry
                    .getInstrumentation()
                    .waitForIdleSync()

                return
            }

            Thread.sleep(
                50L
            )
        }

        fail(
            "Timed out waiting for EditEntryActivity to load"
        )
    }

    private fun waitUntil(
        timeoutMs: Long = 5_000L,
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

    private fun loadVault(): JSONArray {

        val dek =
            VaultRuntimeSession.getVaultDek()

        assertNotNull(
            "Runtime DEK should be available",
            dek
        )

        try {

            return requireNotNull(
                VaultManager.loadVaultWithKey(
                    context,
                    vaultUri,
                    dek!!
                )
            )

        } finally {

            dek?.fill(0)
        }
    }

    private fun loadVaultWithMasterPassword(): JSONArray {

        return requireNotNull(
            VaultManager.loadVault(
                context,
                vaultUri,
                MASTER_PASSWORD
            )
        )
    }

    // =============================================================
    // INITIAL LOAD
    // =============================================================

    @Test
    fun initialState_loadsCorrectEntry() {

        launchEditor(
            entryIndex = 1
        )

        onView(
            withId(
                R.id.hostnameInput
            )
        )
            .check(
                matches(
                    withText(
                        SECOND_HOSTNAME
                    )
                )
            )

        onView(
            withId(
                R.id.usernameInput
            )
        )
            .check(
                matches(
                    withText(
                        SECOND_USERNAME
                    )
                )
            )

        onView(
            withId(
                R.id.passwordInput
            )
        )
            .check(
                matches(
                    withText(
                        SECOND_PASSWORD
                    )
                )
            )
    }

    // =============================================================
    // EXACT INDEX
    // =============================================================

    @Test
    fun editingSecondEntry_doesNotModifyFirstEntry() {

        launchEditor(
            entryIndex = 1
        )

        onView(
            withId(
                R.id.hostnameInput
            )
        )
            .perform(
                replaceText(
                    "gitlab.com"
                )
            )

        onView(
            withId(
                R.id.usernameInput
            )
        )
            .perform(
                replaceText(
                    "new@example.com"
                )
            )

        onView(
            withId(
                R.id.passwordInput
            )
        )
            .perform(
                replaceText(
                    "NewPassword!999"
                )
            )

        onView(
            withId(
                R.id.saveButton
            )
        )
            .perform(
                click()
            )

        waitUntil {

            val vault =
                loadVaultWithMasterPassword()

            vault
                .getJSONObject(
                    1
                )
                .optString(
                    "hostname"
                ) == "gitlab.com"
        }

        val vault =
            loadVaultWithMasterPassword()

        assertEquals(
            2,
            vault.length()
        )

        val first =
            vault.getJSONObject(
                0
            )

        val second =
            vault.getJSONObject(
                1
            )

        assertEquals(
            FIRST_HOSTNAME,
            first.getString(
                "hostname"
            )
        )

        assertEquals(
            FIRST_USERNAME,
            first.getString(
                "username"
            )
        )

        assertEquals(
            FIRST_PASSWORD,
            first.getString(
                "password"
            )
        )

        assertEquals(
            "gitlab.com",
            second.getString(
                "hostname"
            )
        )

        assertEquals(
            "new@example.com",
            second.getString(
                "username"
            )
        )

        assertEquals(
            "NewPassword!999",
            second.getString(
                "password"
            )
        )
    }

    // =============================================================
    // HOSTNAME NORMALIZATION
    // =============================================================

    @Test
    fun save_normalizesHttpsWwwAndPath() {

        launchEditor(
            entryIndex = 1
        )

        onView(
            withId(
                R.id.hostnameInput
            )
        )
            .perform(
                replaceText(
                    "https://www.example.com/login/account"
                )
            )

        onView(
            withId(
                R.id.saveButton
            )
        )
            .perform(
                click()
            )

        waitUntil {

            val vault =
                loadVaultWithMasterPassword()

            vault
                .getJSONObject(
                    1
                )
                .optString(
                    "hostname"
                ) == "example.com"
        }

        val entry =
            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )

        assertEquals(
            "example.com",
            entry.getString(
                "hostname"
            )
        )
    }

    @Test
    fun save_normalizesHttpPrefix() {

        launchEditor(
            entryIndex = 1
        )

        onView(
            withId(
                R.id.hostnameInput
            )
        )
            .perform(
                replaceText(
                    "http://example.org/path"
                )
            )

        onView(
            withId(
                R.id.saveButton
            )
        )
            .perform(
                click()
            )

        waitUntil {

            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )
                .optString(
                    "hostname"
                ) == "example.org"
        }

        assertEquals(
            "example.org",
            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )
                .getString(
                    "hostname"
                )
        )
    }

    // =============================================================
    // HOSTNAME VALIDATION
    // =============================================================

    @Test
    fun blankHostname_doesNotSave() {

        launchEditor(
            entryIndex = 1
        )

        val before =
            loadVaultWithMasterPassword()
                .toString()

        onView(
            withId(
                R.id.hostnameInput
            )
        )
            .perform(
                replaceText(
                    ""
                )
            )

        onView(
            withId(
                R.id.saveButton
            )
        )
            .perform(
                click()
            )

        Thread.sleep(
            300L
        )

        val after =
            loadVaultWithMasterPassword()
                .toString()

        assertEquals(
            "Blank hostname must not modify encrypted vault",
            before,
            after
        )
    }

    // =============================================================
    // USERNAME VALIDATION
    // =============================================================

    @Test
    fun blankUsername_doesNotSave() {

        launchEditor(
            entryIndex = 1
        )

        val before =
            loadVaultWithMasterPassword()
                .toString()

        onView(
            withId(
                R.id.usernameInput
            )
        )
            .perform(
                replaceText(
                    ""
                )
            )

        onView(
            withId(
                R.id.saveButton
            )
        )
            .perform(
                click()
            )

        Thread.sleep(
            300L
        )

        val after =
            loadVaultWithMasterPassword()
                .toString()

        assertEquals(
            "Blank username must not modify encrypted vault",
            before,
            after
        )
    }

    // =============================================================
    // PASSWORD VALIDATION
    // =============================================================

    @Test
    fun blankPassword_doesNotSave() {

        launchEditor(
            entryIndex = 1
        )

        val before =
            loadVaultWithMasterPassword()
                .toString()

        onView(
            withId(
                R.id.passwordInput
            )
        )
            .perform(
                replaceText(
                    ""
                )
            )

        onView(
            withId(
                R.id.saveButton
            )
        )
            .perform(
                click()
            )

        Thread.sleep(
            300L
        )

        val after =
            loadVaultWithMasterPassword()
                .toString()

        assertEquals(
            "Blank password must not modify encrypted vault",
            before,
            after
        )
    }

    // =============================================================
    // SUCCESSFUL SAVE
    // =============================================================

    @Test
    fun successfulSave_persistsAllEditedFields() {

        launchEditor(
            entryIndex = 1
        )

        onView(
            withId(
                R.id.hostnameInput
            )
        )
            .perform(
                replaceText(
                    "newsite.com"
                )
            )

        onView(
            withId(
                R.id.usernameInput
            )
        )
            .perform(
                replaceText(
                    "updated@example.com"
                )
            )

        onView(
            withId(
                R.id.passwordInput
            )
        )
            .perform(
                replaceText(
                    "UpdatedPassword!123"
                )
            )

        onView(
            withId(
                R.id.saveButton
            )
        )
            .perform(
                click()
            )

        waitUntil {

            val entry =
                loadVaultWithMasterPassword()
                    .getJSONObject(
                        1
                    )

            entry.optString(
                "password"
            ) == "UpdatedPassword!123"
        }

        val entry =
            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )

        assertEquals(
            "newsite.com",
            entry.getString(
                "hostname"
            )
        )

        assertEquals(
            "updated@example.com",
            entry.getString(
                "username"
            )
        )

        assertEquals(
            "UpdatedPassword!123",
            entry.getString(
                "password"
            )
        )
    }

    // =============================================================
    // UPDATED TIMESTAMP
    // =============================================================

    @Test
    fun successfulSave_updatesTimestamp() {

        val before =
            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )
                .getLong(
                    "updated"
                )

        launchEditor(
            entryIndex = 1
        )

        onView(
            withId(
                R.id.usernameInput
            )
        )
            .perform(
                replaceText(
                    "timestamp-test@example.com"
                )
            )

        onView(
            withId(
                R.id.saveButton
            )
        )
            .perform(
                click()
            )

        waitUntil {

            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )
                .optString(
                    "username"
                ) == "timestamp-test@example.com"
        }

        val after =
            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )
                .getLong(
                    "updated"
                )

        assertTrue(
            "Saving an edited credential should update timestamp",
            after > before
        )
    }

    // =============================================================
    // CREATED TIMESTAMP PRESERVED
    // =============================================================

    @Test
    fun successfulSave_preservesCreatedTimestamp() {

        val before =
            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )
                .getLong(
                    "created"
                )

        launchEditor(
            entryIndex = 1
        )

        onView(
            withId(
                R.id.usernameInput
            )
        )
            .perform(
                replaceText(
                    "created-test@example.com"
                )
            )

        onView(
            withId(
                R.id.saveButton
            )
        )
            .perform(
                click()
            )

        waitUntil {

            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )
                .optString(
                    "username"
                ) == "created-test@example.com"
        }

        val after =
            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )
                .getLong(
                    "created"
                )

        assertEquals(
            "Editing must not change credential creation timestamp",
            before,
            after
        )
    }

    // =============================================================
    // TYPE PRESERVED / NORMALIZED
    // =============================================================

    @Test
    fun successfulSave_setsTypeToPassword() {

        launchEditor(
            entryIndex = 1
        )

        onView(
            withId(
                R.id.usernameInput
            )
        )
            .perform(
                replaceText(
                    "type-test@example.com"
                )
            )

        onView(
            withId(
                R.id.saveButton
            )
        )
            .perform(
                click()
            )

        waitUntil {

            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )
                .optString(
                    "username"
                ) == "type-test@example.com"
        }

        assertEquals(
            "password",
            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )
                .getString(
                    "type"
                )
        )
    }

    // =============================================================
    // OBSOLETE DEVELOPMENT FIELDS REMOVED
    // =============================================================

    @Test
    fun successfulSave_removesObsoleteDevelopmentFields() {

        /*
         * Inject the old development-only fields before opening
         * EditEntryActivity.
         */
        val dek =
            VaultRuntimeSession.getVaultDek()

        assertNotNull(
            dek
        )

        try {

            val vault =
                requireNotNull(
                    VaultManager.loadVaultWithKey(
                        context,
                        vaultUri,
                        dek!!
                    )
                )

            val entry =
                vault.getJSONObject(
                    1
                )

            entry.put(
                "entryType",
                "old"
            )

            entry.put(
                "keyType",
                "old"
            )

            entry.put(
                "primaryProvider",
                "old"
            )

            entry.put(
                "linkedPrimary",
                true
            )

            VaultManager.saveVaultWithKey(
                context,
                vaultUri,
                vault,
                dek
            )

        } finally {

            dek?.fill(0)
        }

        launchEditor(
            entryIndex = 1
        )

        onView(
            withId(
                R.id.usernameInput
            )
        )
            .perform(
                replaceText(
                    "cleanup@example.com"
                )
            )

        onView(
            withId(
                R.id.saveButton
            )
        )
            .perform(
                click()
            )

        waitUntil {

            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )
                .optString(
                    "username"
                ) == "cleanup@example.com"
        }

        val entry =
            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )

        assertFalse(
            entry.has(
                "entryType"
            )
        )

        assertFalse(
            entry.has(
                "keyType"
            )
        )

        assertFalse(
            entry.has(
                "primaryProvider"
            )
        )

        assertFalse(
            entry.has(
                "linkedPrimary"
            )
        )
    }

    // =============================================================
    // UNSAVED CHANGES
    // =============================================================

    @Test
    fun cancelWithUnsavedChanges_showsDiscardDialog() {

        launchEditor(
            entryIndex = 1
        )

        onView(
            withId(
                R.id.usernameInput
            )
        )
            .perform(
                replaceText(
                    "changed@example.com"
                )
            )

        /*
         * This is the EditEntryActivity cancel button.
         */
        onView(
            withId(
                R.id.cancelButton
            )
        )
            .perform(
                click()
            )

        onView(
            withId(
                R.id.discardButton
            )
        )
            .check(
                matches(
                    isDisplayed()
                )
            )
    }

    // =============================================================
    // KEEP EDITING
    // =============================================================

    @Test
    fun keepEditing_doesNotDiscardChanges() {

        launchEditor(
            entryIndex = 1
        )

        onView(
            withId(
                R.id.usernameInput
            )
        )
            .perform(
                replaceText(
                    "keep-editing@example.com"
                )
            )

        onView(
            withId(
                R.id.cancelButton
            )
        )
            .perform(
                click()
            )

        /*
         * The dialog's "keep editing" button also uses cancelButton.
         *
         * At this point the dialog is the active Espresso window,
         * so this resolves to the dialog button.
         */
        onView(
            withId(
                R.id.cancelButton
            )
        )
            .perform(
                click()
            )

        onView(
            withId(
                R.id.usernameInput
            )
        )
            .check(
                matches(
                    withText(
                        "keep-editing@example.com"
                    )
                )
            )

        /*
         * Vault itself should still contain original value because
         * no save occurred.
         */
        assertEquals(
            SECOND_USERNAME,
            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )
                .getString(
                    "username"
                )
        )
    }

    // =============================================================
    // DISCARD CHANGES
    // =============================================================

    @Test
    fun discardChanges_doesNotPersistEdits() {

        launchEditor(
            entryIndex = 1
        )

        onView(
            withId(
                R.id.usernameInput
            )
        )
            .perform(
                replaceText(
                    "discard-me@example.com"
                )
            )

        onView(
            withId(
                R.id.cancelButton
            )
        )
            .perform(
                click()
            )

        onView(
            withId(
                R.id.discardButton
            )
        )
            .perform(
                click()
            )

        Thread.sleep(
            300L
        )

        val entry =
            loadVaultWithMasterPassword()
                .getJSONObject(
                    1
                )

        assertEquals(
            "Discarding edits must not change encrypted vault",
            SECOND_USERNAME,
            entry.getString(
                "username"
            )
        )
    }

    // =============================================================
    // NO UNSAVED CHANGES
    // =============================================================

    @Test
    fun cancelWithoutChanges_doesNotModifyVault() {

        launchEditor(
            entryIndex = 1
        )

        val before =
            loadVaultWithMasterPassword()
                .toString()

        onView(
            withId(
                R.id.cancelButton
            )
        )
            .perform(
                click()
            )

        Thread.sleep(
            300L
        )

        val after =
            loadVaultWithMasterPassword()
                .toString()

        assertEquals(
            before,
            after
        )
    }

    // =============================================================
    // SESSION EXPIRY
    // =============================================================

    @Test
    fun saveAfterRuntimeSessionExpires_doesNotModifyVault() {

        launchEditor(
            entryIndex = 1
        )

        val before =
            loadVaultWithMasterPassword()
                .toString()

        onView(
            withId(
                R.id.usernameInput
            )
        )
            .perform(
                replaceText(
                    "should-not-save@example.com"
                )
            )

        /*
         * Simulate timeout/lock destroying the runtime DEK while
         * EditEntryActivity is still present.
         */
        VaultRuntimeSession.clear()

        onView(
            withId(
                R.id.saveButton
            )
        )
            .perform(
                click()
            )

        Thread.sleep(
            500L
        )

        assertFalse(
            "Expired runtime session must remain locked",
            VaultRuntimeSession.isUnlocked()
        )

        val after =
            loadVaultWithMasterPassword()
                .toString()

        assertEquals(
            "Expired session must not write edited credential data",
            before,
            after
        )
    }

    // =============================================================
    // INVALID INDEX
    // =============================================================

    @Test
    fun invalidEntryIndex_failsClosed() {

        val intent =
            Intent(
                context,
                EditEntryActivity::class.java
            ).apply {

                putExtra(
                    "vaultUri",
                    vaultUri.toString()
                )

                putExtra(
                    "entryIndex",
                    999
                )
            }

        scenario =
            ActivityScenario.launch(
                intent
            )

        Thread.sleep(
            1_000L
        )

        /*
         * Most importantly: malformed index must not mutate the file.
         */
        val vault =
            loadVaultWithMasterPassword()

        assertEquals(
            2,
            vault.length()
        )

        assertEquals(
            FIRST_HOSTNAME,
            vault
                .getJSONObject(
                    0
                )
                .getString(
                    "hostname"
                )
        )

        assertEquals(
            SECOND_HOSTNAME,
            vault
                .getJSONObject(
                    1
                )
                .getString(
                    "hostname"
                )
        )
    }

    // =============================================================
    // PASSWORD GENERATION
    // =============================================================

    @Test
    fun regenerateButton_replacesPasswordWithGeneratedValue() {

        launchEditor(
            entryIndex = 1
        )

        var before =
            ""

        scenario!!.onActivity { activity ->

            before =
                activity
                    .findViewById<android.widget.EditText>(
                        R.id.passwordInput
                    )
                    .text
                    .toString()
        }

        onView(
            withId(
                R.id.regenerateButton
            )
        )
            .perform(
                click()
            )

        var after =
            ""

        scenario!!.onActivity { activity ->

            after =
                activity
                    .findViewById<android.widget.EditText>(
                        R.id.passwordInput
                    )
                    .text
                    .toString()
        }

        assertNotEquals(
            "Regenerate should replace old credential password",
            before,
            after
        )

        assertTrue(
            "Generated password should respect configured minimum length",
            after.length >= 12
        )

        assertTrue(
            after.any {
                it.isLowerCase()
            }
        )

        assertTrue(
            after.any {
                it.isUpperCase()
            }
        )

        assertTrue(
            after.any {
                it.isDigit()
            }
        )

        assertTrue(
            after.any {
                !it.isLetterOrDigit()
            }
        )
    }
}