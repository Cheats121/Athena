package com.athena.j.athena

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * Displays a credential and protects sensitive actions
 * with biometric or master-password authentication.
 */
class EntryDetailActivity : BaseSecureActivity() {

    companion object {

        private const val TAG =
            "AthenaEntry"
    }

    // =============================================================
    // AUTHENTICATED ACTIONS
    // =============================================================

    private enum class SensitiveAction {
        REVEAL,
        COPY,
        EDIT,
        DELETE
    }

    // =============================================================
    // VAULT
    // =============================================================

    private var vaultUri: Uri? =
        null

    private var entryIndex: Int =
        -1

    // =============================================================
    // ENTRY METADATA
    // =============================================================

    private var hostname: String =
        ""

    private var username: String =
        ""

    private var created: Long =
        0L

    private var updated: Long =
        0L

    // =============================================================
    // UI
    // =============================================================

    private lateinit var toolbar: MaterialToolbar
    private lateinit var entryTitle: TextView
    private lateinit var usernameField: TextView
    private lateinit var passwordField: TextView

    private lateinit var eyeButton: ImageView
    private lateinit var copyButton: ImageView
    private lateinit var editButton: ImageView
    private lateinit var deleteButton: ImageView

    // =============================================================
    // STATE
    // =============================================================

    private var authInProgress =
        false

    private var passwordVisible =
        false

    // =============================================================
    // CREATE
    // =============================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_entry_detail
        )

        // Resolve active vault.
        vaultUri =
            intent
                .getStringExtra(
                    "vaultUri"
                )
                ?.let {
                    Uri.parse(
                        it
                    )
                }
                ?: VaultRuntimeSession
                    .getVaultUri()

        entryIndex =
            intent.getIntExtra(
                "entryIndex",
                -1
            )

        if (
            vaultUri == null ||
            entryIndex < 0
        ) {

            Toast.makeText(
                this,
                "Invalid vault entry",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        // Load non-sensitive metadata passed by VaultActivity.
        hostname =
            intent.getStringExtra(
                "hostname"
            ) ?: "Unknown"

        username =
            intent.getStringExtra(
                "username"
            ) ?: ""

        created =
            intent.getLongExtra(
                "created",
                0L
            )

        updated =
            intent.getLongExtra(
                "updated",
                0L
            )

        setupViews()
        setupToolbar()
        showMaskedLayout()
        setupActions()
    }

    // =============================================================
    // VIEWS
    // =============================================================

    private fun setupViews() {

        toolbar =
            findViewById(
                R.id.toolbar
            )

        entryTitle =
            findViewById(
                R.id.entryTitle
            )

        usernameField =
            findViewById(
                R.id.usernameField
            )

        passwordField =
            findViewById(
                R.id.passwordField
            )

        eyeButton =
            findViewById(
                R.id.eyeButton
            )

        copyButton =
            findViewById(
                R.id.copyButton
            )

        editButton =
            findViewById(
                R.id.editButton
            )

        deleteButton =
            findViewById(
                R.id.deleteButton
            )
    }

    // =============================================================
    // TOOLBAR
    // =============================================================

    private fun setupToolbar() {

        setSupportActionBar(
            toolbar
        )

        toolbar
            .setNavigationOnClickListener {

                finish()
            }
    }

    // =============================================================
    // MASKED UI
    // =============================================================

    private fun showMaskedLayout() {

        passwordVisible =
            false

        entryTitle.text =
            hostname

        usernameField.text =
            username

        passwordField.transformationMethod =
            null

        passwordField.text =
            "••••••••••••"

        findViewById<TextView>(
            R.id.createdText
        ).text =
            "Created: ${
                if (created > 0L) {
                    formatTimestamp(
                        created
                    )
                } else {
                    "N/A"
                }
            }"

        findViewById<TextView>(
            R.id.editedText
        ).text =
            "Last edited: ${
                if (updated > 0L) {
                    formatTimestamp(
                        updated
                    )
                } else {
                    "N/A"
                }
            }"
    }

    // =============================================================
    // ACTION BUTTONS
    // =============================================================

    private fun setupActions() {

        eyeButton
            .setOnClickListener {

                if (
                    passwordVisible
                ) {

                    // Hiding does not require authentication.
                    showMaskedLayout()

                } else {

                    authenticateFor(
                        SensitiveAction.REVEAL
                    )
                }
            }

        copyButton
            .setOnClickListener {

                authenticateFor(
                    SensitiveAction.COPY
                )
            }

        editButton
            .setOnClickListener {

                authenticateFor(
                    SensitiveAction.EDIT
                )
            }

        deleteButton
            .setOnClickListener {

                showDeleteConfirmation()
            }
    }

    // =============================================================
    // DELETE CONFIRMATION
    // =============================================================

    private fun showDeleteConfirmation() {

        val view =
            layoutInflater.inflate(
                R.layout.dialog_delete_confirm,
                null
            )

        val cancelButton =
            view.findViewById<TextView>(
                R.id.cancelButton
            )

        val yesButton =
            view.findViewById<TextView>(
                R.id.yesButton
            )

        val dialog =
            androidx.appcompat.app.AlertDialog
                .Builder(
                    this,
                    R.style.AthenaDialogTheme
                )
                .setView(
                    view
                )
                .setCancelable(
                    true
                )
                .create()

        cancelButton
            .setOnClickListener {

                dialog.dismiss()
            }

        yesButton
            .setOnClickListener {

                dialog.dismiss()

                authenticateFor(
                    SensitiveAction.DELETE
                )
            }

        dialog.show()
    }

    // =============================================================
    // AUTHENTICATION
    // =============================================================

    private fun authenticateFor(
        action: SensitiveAction
    ) {

        if (
            authInProgress
        ) {

            return
        }

        if (
            vaultUri == null ||
            !VaultRuntimeSession.isUnlocked()
        ) {

            handleExpiredSession()
            return
        }

        authInProgress =
            true

        if (
            biometricAuthenticationAvailable()
        ) {

            authenticateWithBiometrics(
                action
            )

        } else {

            authInProgress =
                false

            authenticateWithMasterPassword(
                action
            )
        }
    }

    // =============================================================
    // BIOMETRIC AVAILABILITY
    // =============================================================

    private fun biometricAuthenticationAvailable(): Boolean {

        return (
                BiometricStore
                    .isBiometricAvailable(
                        this
                    ) &&
                        BiometricStore
                            .hasWrappedDek(
                                this
                            ) &&
                        VaultSessionManager
                            .isBiometricEnabled(
                                this
                            )
                )
    }

    // =============================================================
    // BIOMETRIC AUTH
    // =============================================================

    private fun authenticateWithBiometrics(
        action: SensitiveAction
    ) {

        val iv =
            BiometricStore
                .getStoredIv(
                    this
                )

        if (
            iv == null
        ) {

            authInProgress =
                false

            authenticateWithMasterPassword(
                action
            )

            return
        }

        val cipher =
            BiometricStore
                .initDecryptCipher(
                    iv
                )

        iv.fill(
            0
        )

        if (
            cipher == null
        ) {

            authInProgress =
                false

            authenticateWithMasterPassword(
                action
            )

            return
        }

        val prompt =
            BiometricPrompt(
                this,
                mainExecutor,
                object :
                    BiometricPrompt.AuthenticationCallback() {

                    override fun onAuthenticationSucceeded(
                        result:
                        BiometricPrompt.AuthenticationResult
                    ) {

                        authInProgress =
                            false

                        val cryptoCipher =
                            result
                                .cryptoObject
                                ?.cipher

                        if (
                            cryptoCipher == null
                        ) {

                            centeredToast(
                                "Biometric authentication failed"
                            )

                            return
                        }

                        val dek =
                            BiometricStore
                                .unwrapDek(
                                    this@EntryDetailActivity,
                                    cryptoCipher
                                )

                        if (
                            dek == null
                        ) {

                            centeredToast(
                                "Biometric authentication failed"
                            )

                            return
                        }

                        handleAuthenticatedDek(
                            dek,
                            action
                        )
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {

                        authInProgress =
                            false

                        // Negative button falls back to password.
                        if (
                            errorCode ==
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) {

                            authenticateWithMasterPassword(
                                action
                            )
                        }
                    }

                    override fun onAuthenticationFailed() {

                        // Keep the biometric prompt active.
                    }
                }
            )

        val promptInfo =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(
                    actionTitle(
                        action
                    )
                )
                .setSubtitle(
                    "Authenticate to continue"
                )
                .setAllowedAuthenticators(
                    BiometricManager
                        .Authenticators
                        .BIOMETRIC_STRONG
                )
                .setNegativeButtonText(
                    "Use password"
                )
                .build()

        prompt.authenticate(
            promptInfo,
            BiometricPrompt.CryptoObject(
                cipher
            )
        )
    }

    // =============================================================
    // MASTER PASSWORD AUTH
    // =============================================================

    private fun authenticateWithMasterPassword(
        action: SensitiveAction
    ) {

        val uri =
            vaultUri
                ?: return

        showMasterPasswordDialog(
            title =
                actionTitle(
                    action
                ),
            subtitle =
                "Re-enter your master password to continue.",
            positiveLabel =
                actionPositiveLabel(
                    action
                )
        ) { password ->

            // Argon2 work stays off the UI thread.
            lifecycleScope.launch(
                Dispatchers.IO
            ) {

                val dek =
                    try {

                        VaultManager
                            .deriveVaultKey(
                                this@EntryDetailActivity,
                                uri,
                                password
                            )

                    } catch (e: Exception) {

                        Log.w(
                            TAG,
                            "Password authentication failed",
                            e
                        )

                        null
                    }

                withContext(
                    Dispatchers.Main
                ) {

                    if (
                        dek == null
                    ) {

                        centeredToast(
                            "Incorrect master password"
                        )

                    } else {

                        handleAuthenticatedDek(
                            dek,
                            action
                        )
                    }
                }
            }
        }
    }

    // =============================================================
    // AUTHENTICATED ACTION
    // =============================================================

    private fun handleAuthenticatedDek(
        dek: ByteArray,
        action: SensitiveAction
    ) {

        val uri =
            vaultUri

        if (
            uri == null
        ) {

            dek.fill(
                0
            )

            handleExpiredSession()
            return
        }

        lifecycleScope.launch(
            Dispatchers.IO
        ) {

            try {

                // Verify the DEK against the selected vault.
                val vault =
                    VaultManager
                        .loadVaultWithKey(
                            this@EntryDetailActivity,
                            uri,
                            dek
                        )
                        ?: throw SecurityException(
                            "Vault authentication failed"
                        )

                if (
                    entryIndex < 0 ||
                    entryIndex >= vault.length()
                ) {

                    throw IndexOutOfBoundsException(
                        "Credential no longer exists"
                    )
                }

                val entry =
                    vault.optJSONObject(
                        entryIndex
                    )
                        ?: throw IllegalStateException(
                            "Credential is invalid"
                        )

                when (
                    action
                ) {

                    SensitiveAction.REVEAL -> {

                        val loaded =
                            readCredential(
                                entry
                            )

                        withContext(
                            Dispatchers.Main
                        ) {

                            revealCredential(
                                loaded
                            )
                        }
                    }

                    SensitiveAction.COPY -> {

                        val password =
                            entry.optString(
                                "password",
                                ""
                            )

                        if (
                            password.isEmpty()
                        ) {

                            throw IllegalStateException(
                                "Password is missing"
                            )
                        }

                        withContext(
                            Dispatchers.Main
                        ) {

                            ClipboardUtils.copySensitive(
                                context =
                                    this@EntryDetailActivity,
                                label =
                                    "Password",
                                text =
                                    password,
                                clearAfterMs =
                                    30_000L
                            ) {

                                centeredToast(
                                    "Clipboard cleared"
                                )
                            }

                            centeredToast(
                                "Password copied"
                            )
                        }
                    }

                    SensitiveAction.EDIT -> {

                        // Edit screen reloads the entry by vault index.
                        withContext(
                            Dispatchers.Main
                        ) {

                            openEditEntry()
                        }
                    }

                    SensitiveAction.DELETE -> {

                        // Remove the exact vault-array entry.
                        vault.remove(
                            entryIndex
                        )

                        VaultManager
                            .saveVaultWithKey(
                                this@EntryDetailActivity,
                                uri,
                                vault,
                                dek
                            )

                        withContext(
                            Dispatchers.Main
                        ) {

                            centeredToast(
                                "Entry deleted"
                            )

                            val result =
                                Intent().apply {

                                    putExtra(
                                        "entryDeleted",
                                        true
                                    )
                                }

                            setResult(
                                RESULT_OK,
                                result
                            )

                            finish()
                        }
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Sensitive credential operation failed",
                    e
                )

                withContext(
                    Dispatchers.Main
                ) {

                    centeredToast(
                        "Unable to complete operation"
                    )
                }

            } finally {

                // Destroy temporary DEK copy.
                dek.fill(
                    0
                )
            }
        }
    }

    // =============================================================
    // READ ENTRY
    // =============================================================

    private fun readCredential(
        entry: JSONObject
    ): LoadedCredential {

        val loadedHostname =
            entry.optString(
                "hostname",
                hostname
            )

        val loadedUsername =
            entry.optString(
                "username",
                username
            )

        val loadedPassword =
            entry.optString(
                "password",
                ""
            )

        if (
            loadedPassword.isEmpty()
        ) {

            throw IllegalStateException(
                "Credential password is missing"
            )
        }

        return LoadedCredential(
            hostname =
                loadedHostname,
            username =
                loadedUsername,
            password =
                loadedPassword,
            created =
                entry.optLong(
                    "created",
                    created
                ),
            updated =
                entry.optLong(
                    "updated",
                    updated
                )
        )
    }

    // =============================================================
    // REVEAL
    // =============================================================

    private fun revealCredential(
        credential: LoadedCredential
    ) {

        hostname =
            credential.hostname

        username =
            credential.username

        created =
            credential.created

        updated =
            credential.updated

        entryTitle.text =
            credential.hostname

        usernameField.text =
            credential.username

        // Show authenticated plaintext password.
        passwordField.transformationMethod =
            null

        passwordField.text =
            credential.password

        passwordField.invalidate()

        findViewById<TextView>(
            R.id.createdText
        ).text =
            "Created: ${
                if (created > 0L) {
                    formatTimestamp(
                        created
                    )
                } else {
                    "N/A"
                }
            }"

        findViewById<TextView>(
            R.id.editedText
        ).text =
            "Last edited: ${
                if (updated > 0L) {
                    formatTimestamp(
                        updated
                    )
                } else {
                    "N/A"
                }
            }"

        passwordVisible =
            true

        centeredToast(
            "Password revealed"
        )
    }

    // =============================================================
    // OPEN EDIT SCREEN
    // =============================================================

    private fun openEditEntry() {

        val uri =
            vaultUri
                ?: return

        val editIntent =
            Intent(
                this,
                EditEntryActivity::class.java
            ).apply {

                putExtra(
                    "vaultUri",
                    uri.toString()
                )

                putExtra(
                    "entryIndex",
                    entryIndex
                )
            }

        startActivity(
            editIntent
        )

        pushSlideTransition()
    }

    // =============================================================
    // AUTH UI TEXT
    // =============================================================

    private fun actionTitle(
        action: SensitiveAction
    ): String {

        return when (
            action
        ) {

            SensitiveAction.REVEAL ->
                "Reveal password"

            SensitiveAction.COPY ->
                "Copy password"

            SensitiveAction.EDIT ->
                "Edit credential"

            SensitiveAction.DELETE ->
                "Delete credential"
        }
    }

    private fun actionPositiveLabel(
        action: SensitiveAction
    ): String {

        return when (
            action
        ) {

            SensitiveAction.REVEAL ->
                "Reveal"

            SensitiveAction.COPY ->
                "Copy"

            SensitiveAction.EDIT ->
                "Continue"

            SensitiveAction.DELETE ->
                "Delete"
        }
    }

    // =============================================================
    // SESSION EXPIRED
    // =============================================================

    private fun handleExpiredSession() {

        VaultRuntimeSession.clear()

        centeredToast(
            "Vault session expired. Unlock again."
        )

        finish()
    }

    // =============================================================
    // TOAST
    // =============================================================

    private fun centeredToast(
        message: String
    ) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).apply {

            setGravity(
                Gravity.CENTER,
                0,
                0
            )

            show()
        }
    }

    // =============================================================
    // DATE
    // =============================================================

    private fun formatTimestamp(
        timestamp: Long
    ): String {

        return SimpleDateFormat(
            "dd MMM yyyy, HH:mm",
            Locale.getDefault()
        ).format(
            Date(
                timestamp
            )
        )
    }

    // =============================================================
    // SENSITIVE UI CLEANUP
    // =============================================================

    override fun clearSensitiveData() {

        if (
            ::passwordField.isInitialized
        ) {

            passwordField.transformationMethod =
                null

            passwordField.text =
                "••••••••••••"
        }

        passwordVisible =
            false
    }

    // =============================================================
    // INTERNAL MODEL
    // =============================================================

    private data class LoadedCredential(
        val hostname: String,
        val username: String,
        val password: String,
        val created: Long,
        val updated: Long
    )
}