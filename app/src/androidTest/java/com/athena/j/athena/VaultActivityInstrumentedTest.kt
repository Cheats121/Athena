package com.athena.j.athena

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.navigation.NavigationView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VaultActivityInstrumentedTest {

    // =============================================================
    // CONSTANTS
    // =============================================================

    private companion object {

        const val MASTER_PASSWORD =
            "StrongMasterPassword!123"

        const val AMAZON_HOSTNAME =
            "amazon.com"

        const val AMAZON_USERNAME =
            "amazon@example.com"

        const val AMAZON_PASSWORD =
            "AmazonSecret!111"

        const val GITHUB_HOSTNAME =
            "github.com"

        const val GITHUB_USERNAME =
            "developer@example.com"

        const val GITHUB_PASSWORD =
            "GithubSecret!222"

        const val LEGACY_HOSTNAME =
            "legacy.example"

        const val LEGACY_USERNAME =
            "legacy@example.com"

        const val LEGACY_PASSWORD =
            "LegacySecret!333"
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
            ActivityScenario<VaultActivity>? =
        null

    // =============================================================
    // SETUP
    // =============================================================

    @Before
    fun setup() {

        TimeoutManager.clear()

        VaultRuntimeSession.clear()

        VaultSessionManager.forgetVault(
            context
        )

        vaultFile =
            File(
                context.cacheDir,
                "athena-vault-test-${System.nanoTime()}.json"
            )

        vaultFile.createNewFile()

        vaultUri =
            Uri.fromFile(
                vaultFile
            )

        // ---------------------------------------------------------
        // Create genuine encrypted Athena vault
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

        /*
         * Vault contents:
         *
         * index 0 -> password
         * index 1 -> non-password note
         * index 2 -> password
         * index 3 -> blank type (legacy password entry)
         *
         * VaultActivity should display 0, 2 and 3 only.
         */
        val vault =
            JSONArray().apply {

                // -------------------------------------------------
                // Index 0 - password
                // -------------------------------------------------

                put(
                    JSONObject().apply {

                        put(
                            "type",
                            "password"
                        )

                        put(
                            "hostname",
                            AMAZON_HOSTNAME
                        )

                        put(
                            "username",
                            AMAZON_USERNAME
                        )

                        put(
                            "password",
                            AMAZON_PASSWORD
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

                // -------------------------------------------------
                // Index 1 - non-password entry
                // -------------------------------------------------

                put(
                    JSONObject().apply {

                        put(
                            "type",
                            "note"
                        )

                        put(
                            "hostname",
                            "private-note"
                        )

                        put(
                            "username",
                            "should-not-display"
                        )

                        put(
                            "password",
                            "should-not-display"
                        )
                    }
                )

                // -------------------------------------------------
                // Index 2 - password
                // -------------------------------------------------

                put(
                    JSONObject().apply {

                        put(
                            "type",
                            "password"
                        )

                        put(
                            "hostname",
                            GITHUB_HOSTNAME
                        )

                        put(
                            "username",
                            GITHUB_USERNAME
                        )

                        put(
                            "password",
                            GITHUB_PASSWORD
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

                // -------------------------------------------------
                // Index 3 - legacy blank-type password entry
                // -------------------------------------------------

                put(
                    JSONObject().apply {

                        put(
                            "hostname",
                            LEGACY_HOSTNAME
                        )

                        put(
                            "username",
                            LEGACY_USERNAME
                        )

                        put(
                            "password",
                            LEGACY_PASSWORD
                        )

                        put(
                            "created",
                            1_700_000_400_000L
                        )

                        put(
                            "updated",
                            1_700_000_500_000L
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

            vaultFile.delete()

        } catch (_: Exception) {
        }
    }

    // =============================================================
    // HELPERS
    // =============================================================

    private fun launchVaultActivity() {

        val intent =
            Intent(
                context,
                VaultActivity::class.java
            ).apply {

                putExtra(
                    "vaultUri",
                    vaultUri.toString()
                )
            }

        scenario =
            ActivityScenario.launch(
                intent
            )

        waitForVaultLoaded()
    }

    private fun launchVaultActivityWithoutWaiting() {

        val intent =
            Intent(
                context,
                VaultActivity::class.java
            ).apply {

                putExtra(
                    "vaultUri",
                    vaultUri.toString()
                )
            }

        scenario =
            ActivityScenario.launch(
                intent
            )
    }

    private fun waitUntil(
        timeoutMs: Long = 8_000L,
        condition: () -> Boolean
    ) {

        val started =
            System.currentTimeMillis()

        while (
            System.currentTimeMillis() -
            started <
            timeoutMs
        ) {

            if (
                condition()
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
            "Timed out waiting for condition"
        )
    }

    private fun waitForVaultLoaded(
        expectedItems: Int = 3
    ) {

        waitUntil {

            try {

                recyclerItemCount() ==
                        expectedItems &&
                        !isLoadingVisible()

            } catch (_: Exception) {

                false
            }
        }
    }

    private fun recyclerItemCount(): Int {

        var count =
            -1

        scenario!!.onActivity { activity ->

            val recycler =
                activity.findViewById<RecyclerView>(
                    R.id.vaultRecyclerView
                )

            count =
                recycler
                    .adapter
                    ?.itemCount
                    ?: -1
        }

        return count
    }

    private fun isLoadingVisible(): Boolean {

        var visible =
            true

        scenario!!.onActivity { activity ->

            visible =
                activity
                    .findViewById<View>(
                        R.id.loadingOverlay
                    )
                    .visibility ==
                        View.VISIBLE
        }

        return visible
    }

    private fun headerEntryCountText(): String {

        var result =
            ""

        scenario!!.onActivity { activity ->

            val navigationView =
                activity.findViewById<NavigationView>(
                    R.id.navigationView
                )

            val header =
                navigationView.getHeaderView(
                    0
                )

            result =
                header
                    .findViewById<TextView>(
                        R.id.vaultEntryCountText
                    )
                    .text
                    .toString()
        }

        return result
    }

    private fun headerVaultName(): String {

        var result =
            ""

        scenario!!.onActivity { activity ->

            val navigationView =
                activity.findViewById<NavigationView>(
                    R.id.navigationView
                )

            val header =
                navigationView.getHeaderView(
                    0
                )

            result =
                header
                    .findViewById<TextView>(
                        R.id.vaultNameText
                    )
                    .text
                    .toString()
        }

        return result
    }

    private fun enterSearch(
        value: String
    ) {

        onView(
            withId(
                R.id.searchBar
            )
        )
            .perform(
                replaceText(
                    value
                )
            )

        InstrumentationRegistry
            .getInstrumentation()
            .waitForIdleSync()
    }

    private fun clearSearch() {

        onView(
            withId(
                R.id.searchBar
            )
        )
            .perform(
                clearText()
            )

        InstrumentationRegistry
            .getInstrumentation()
            .waitForIdleSync()
    }

    private fun loadEncryptedVault(): JSONArray {

        val dek =
            VaultRuntimeSession.getVaultDek()

        assertNotNull(
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

    // =============================================================
    // BASIC LOAD
    // =============================================================

    @Test
    fun validSession_loadsPasswordEntries() {

        launchVaultActivity()

        assertEquals(
            "Vault screen should display three password-compatible entries",
            3,
            recyclerItemCount()
        )
    }

    // =============================================================
    // NON-PASSWORD FILTERING
    // =============================================================

    @Test
    fun nonPasswordEntries_areNotDisplayed() {

        launchVaultActivity()

        assertEquals(
            3,
            recyclerItemCount()
        )

        /*
         * The encrypted vault itself still contains four objects.
         */
        assertEquals(
            4,
            loadEncryptedVault().length()
        )
    }

    // =============================================================
    // LEGACY BLANK TYPE
    // =============================================================

    @Test
    fun blankTypeEntry_isTreatedAsPasswordEntry() {

        launchVaultActivity()

        onView(
            withText(
                LEGACY_HOSTNAME
            )
        )
            .check(
                matches(
                    withText(
                        LEGACY_HOSTNAME
                    )
                )
            )
    }

    // =============================================================
    // DISPLAYED DATA
    // =============================================================

    @Test
    fun loadedVault_displaysExpectedCredentials() {

        launchVaultActivity()

        onView(
            withText(
                AMAZON_HOSTNAME
            )
        )
            .check(
                matches(
                    withText(
                        AMAZON_HOSTNAME
                    )
                )
            )

        onView(
            withText(
                GITHUB_HOSTNAME
            )
        )
            .check(
                matches(
                    withText(
                        GITHUB_HOSTNAME
                    )
                )
            )

        onView(
            withText(
                LEGACY_HOSTNAME
            )
        )
            .check(
                matches(
                    withText(
                        LEGACY_HOSTNAME
                    )
                )
            )
    }

    // =============================================================
    // HEADER ENTRY COUNT
    // =============================================================

    @Test
    fun header_showsPasswordEntryCountOnly() {

        launchVaultActivity()

        assertEquals(
            "3 entries",
            headerEntryCountText()
        )
    }

    // =============================================================
    // HEADER VAULT NAME
    // =============================================================

    @Test
    fun header_showsVaultNameWithoutJsonExtension() {

        launchVaultActivity()

        assertEquals(
            vaultFile
                .name
                .substringBeforeLast(
                    "."
                ),
            headerVaultName()
        )
    }

    // =============================================================
    // SEARCH BY HOSTNAME
    // =============================================================

    @Test
    fun searchByHostname_filtersEntries() {

        launchVaultActivity()

        enterSearch(
            "github"
        )

        waitUntil {

            recyclerItemCount() ==
                    1
        }

        assertEquals(
            1,
            recyclerItemCount()
        )

        onView(
            withText(
                GITHUB_HOSTNAME
            )
        )
            .check(
                matches(
                    withText(
                        GITHUB_HOSTNAME
                    )
                )
            )
    }

    // =============================================================
    // SEARCH BY USERNAME
    // =============================================================

    @Test
    fun searchByUsername_filtersEntries() {

        launchVaultActivity()

        enterSearch(
            "developer@example.com"
        )

        waitUntil {

            recyclerItemCount() ==
                    1
        }

        assertEquals(
            1,
            recyclerItemCount()
        )

        onView(
            withText(
                GITHUB_HOSTNAME
            )
        )
            .check(
                matches(
                    withText(
                        GITHUB_HOSTNAME
                    )
                )
            )
    }

    // =============================================================
    // CASE INSENSITIVE SEARCH
    // =============================================================

    @Test
    fun search_isCaseInsensitive() {

        launchVaultActivity()

        enterSearch(
            "GITHUB"
        )

        waitUntil {

            recyclerItemCount() ==
                    1
        }

        assertEquals(
            1,
            recyclerItemCount()
        )
    }

    // =============================================================
    // NO MATCH
    // =============================================================

    @Test
    fun searchWithNoMatches_displaysZeroEntries() {

        launchVaultActivity()

        enterSearch(
            "this-will-never-match-any-entry"
        )

        waitUntil {

            recyclerItemCount() ==
                    0
        }

        assertEquals(
            0,
            recyclerItemCount()
        )
    }

    // =============================================================
    // CLEAR SEARCH
    // =============================================================

    @Test
    fun clearingSearch_restoresAllPasswordEntries() {

        launchVaultActivity()

        enterSearch(
            "github"
        )

        waitUntil {

            recyclerItemCount() ==
                    1
        }

        clearSearch()

        waitUntil {

            recyclerItemCount() ==
                    3
        }

        assertEquals(
            3,
            recyclerItemCount()
        )
    }

    // =============================================================
    // SEARCH DOES NOT ALTER HEADER COUNT
    // =============================================================

    @Test
    fun search_doesNotChangeVaultEntryCountHeader() {

        launchVaultActivity()

        assertEquals(
            "3 entries",
            headerEntryCountText()
        )

        enterSearch(
            "github"
        )

        waitUntil {

            recyclerItemCount() ==
                    1
        }

        /*
         * Search result count is not the vault entry count.
         */
        assertEquals(
            "3 entries",
            headerEntryCountText()
        )
    }

    // =============================================================
    // MISSING RUNTIME SESSION
    // =============================================================

    @Test
    fun missingRuntimeSession_failsClosed() {

        /*
         * Persistent URI exists, but runtime DEK is removed.
         */
        VaultRuntimeSession.clear()

        assertFalse(
            VaultRuntimeSession.isUnlocked()
        )

        launchVaultActivityWithoutWaiting()

        Thread.sleep(
            500L
        )

        assertFalse(
            "VaultActivity must not recreate an unlocked session",
            VaultRuntimeSession.isUnlocked()
        )

        assertNull(
            VaultRuntimeSession.getVaultDek()
        )
    }

    // =============================================================
    // WRONG RUNTIME DEK
    // =============================================================

    @Test
    fun wrongRuntimeDek_failsClosedAndClearsSession() {

        /*
         * Replace the valid runtime DEK with a random 256-bit value.
         */
        val wrongDek =
            ByteArray(
                32
            ) {
                0x42.toByte()
            }

        VaultRuntimeSession.setSession(
            vaultUri,
            wrongDek
        )

        wrongDek.fill(0)

        assertTrue(
            VaultRuntimeSession.isUnlocked()
        )

        launchVaultActivityWithoutWaiting()

        waitUntil(
            timeoutMs = 5_000L
        ) {

            !VaultRuntimeSession.isUnlocked()
        }

        assertFalse(
            "Vault authentication failure must clear runtime session",
            VaultRuntimeSession.isUnlocked()
        )

        assertNull(
            "Wrong DEK must not remain available",
            VaultRuntimeSession.getVaultDek()
        )
    }

    // =============================================================
    // CORRUPTED VAULT
    // =============================================================

    @Test
    fun corruptedVault_failsClosedAndClearsSession() {

        /*
         * Corrupt authenticated ciphertext while retaining a valid
         * runtime session.
         */

        val root =
            JSONObject(
                vaultFile.readText()
            )

        val vaultObject =
            root.getJSONObject(
                "vault"
            )

        val ciphertext =
            vaultObject.getString(
                "ciphertext"
            )

        val replacement =
            if (
                ciphertext.first() == 'A'
            ) {
                'B'
            } else {
                'A'
            }

        vaultObject.put(
            "ciphertext",
            replacement +
                    ciphertext.substring(
                        1
                    )
        )

        vaultFile.writeText(
            root.toString()
        )

        assertTrue(
            VaultRuntimeSession.isUnlocked()
        )

        launchVaultActivityWithoutWaiting()

        waitUntil(
            timeoutMs = 5_000L
        ) {

            !VaultRuntimeSession.isUnlocked()
        }

        assertFalse(
            "Corrupt authenticated vault must cause session failure",
            VaultRuntimeSession.isUnlocked()
        )

        assertNull(
            VaultRuntimeSession.getVaultDek()
        )
    }

    // =============================================================
    // RELOAD ON RESUME
    // =============================================================

    @Test
    fun resume_reloadsVaultAfterExternalChange() {

        launchVaultActivity()

        assertEquals(
            3,
            recyclerItemCount()
        )

        // ---------------------------------------------------------
        // Background the Activity
        // ---------------------------------------------------------

        scenario!!
            .moveToState(
                Lifecycle.State.STARTED
            )

        // ---------------------------------------------------------
        // Modify encrypted vault while Activity is paused
        // ---------------------------------------------------------

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

            vault.put(
                JSONObject().apply {

                    put(
                        "type",
                        "password"
                    )

                    put(
                        "hostname",
                        "new-entry.example"
                    )

                    put(
                        "username",
                        "new@example.com"
                    )

                    put(
                        "password",
                        "NewSecret!444"
                    )

                    put(
                        "created",
                        System.currentTimeMillis()
                    )

                    put(
                        "updated",
                        System.currentTimeMillis()
                    )
                }
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

        // ---------------------------------------------------------
        // Resume VaultActivity
        //
        // onSecureResume() should reload from encrypted storage.
        // ---------------------------------------------------------

        scenario!!
            .moveToState(
                Lifecycle.State.RESUMED
            )

        waitUntil(
            timeoutMs = 8_000L
        ) {

            recyclerItemCount() ==
                    4
        }

        assertEquals(
            4,
            recyclerItemCount()
        )

        assertEquals(
            "4 entries",
            headerEntryCountText()
        )

        onView(
            withText(
                "new-entry.example"
            )
        )
            .check(
                matches(
                    withText(
                        "new-entry.example"
                    )
                )
            )
    }

    // =============================================================
    // SEARCH DOES NOT MUTATE ENCRYPTED VAULT
    // =============================================================

    @Test
    fun searching_doesNotModifyEncryptedVault() {

        launchVaultActivity()

        val before =
            vaultFile.readText()

        enterSearch(
            "github"
        )

        waitUntil {

            recyclerItemCount() ==
                    1
        }

        clearSearch()

        waitUntil {

            recyclerItemCount() ==
                    3
        }

        val after =
            vaultFile.readText()

        assertEquals(
            "Searching must never rewrite encrypted vault contents",
            before,
            after
        )
    }
}