package com.athena.j.athena

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Handles editing an existing vault credential.
 *
 * Loads the selected entry, validates changes,
 * saves back to the encrypted vault, and clears
 * sensitive UI data when leaving the screen.
 */
class EditEntryActivity : BaseSecureActivity() {

    companion object {

        private const val TAG =
            "AthenaVault"
    }

    // =============================================================
    // VAULT
    // =============================================================

    private var vaultUri: Uri? =
        null

    private var entryIndex: Int =
        -1

    // =============================================================
    // ORIGINAL VALUES
    // =============================================================

    private var originalHostname: String =
        ""

    private var originalUsername: String =
        ""

    private var originalPassword: String =
        ""

    // =============================================================
    // UI
    // =============================================================

    private lateinit var toolbar: MaterialToolbar

    private lateinit var hostnameInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText

    private lateinit var regenerateButton: ImageView
    private lateinit var saveButton: TextView
    private lateinit var cancelButton: TextView

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
            R.layout.activity_edit_entry
        )

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

        // Require a valid unlocked vault session.
        if (
            vaultUri == null ||
            entryIndex < 0 ||
            !VaultRuntimeSession.isUnlocked()
        ) {

            handleExpiredSession()
            return
        }

        setupViews()
        setupListeners()

        loadEntry()
    }

    // =============================================================
    // VIEWS
    // =============================================================

    private fun setupViews() {

        toolbar =
            findViewById(
                R.id.editEntryToolbar
            )

        hostnameInput =
            findViewById(
                R.id.hostnameInput
            )

        usernameInput =
            findViewById(
                R.id.usernameInput
            )

        passwordInput =
            findViewById(
                R.id.passwordInput
            )

        regenerateButton =
            findViewById(
                R.id.regenerateButton
            )

        cancelButton =
            findViewById(
                R.id.cancelButton
            )

        saveButton =
            findViewById(
                R.id.saveButton
            )

        setSupportActionBar(
            toolbar
        )

        toolbar
            .setNavigationOnClickListener {

                maybeDiscardChanges()
            }
    }

    // =============================================================
    // LISTENERS
    // =============================================================

    private fun setupListeners() {

        regenerateButton
            .setOnClickListener {

                val generated =
                    PasswordGenerator.generate()

                passwordInput.setText(
                    generated
                )

                passwordInput.setSelection(
                    passwordInput.text.length
                )
            }

        cancelButton
            .setOnClickListener {

                maybeDiscardChanges()
            }

        saveButton
            .setOnClickListener {

                saveChanges()
            }
    }

    // =============================================================
    // LOAD ENTRY
    // =============================================================

    private fun loadEntry() {

        val uri =
            vaultUri

        if (
            uri == null
        ) {

            handleExpiredSession()
            return
        }

        val dek =
            VaultRuntimeSession
                .getVaultDek()

        if (
            dek == null
        ) {

            handleExpiredSession()
            return
        }

        saveButton.isEnabled =
            false

        lifecycleScope.launch(
            Dispatchers.IO
        ) {

            try {

                // Load and authenticate the vault.
                val vault =
                    VaultManager
                        .loadVaultWithKey(
                            this@EditEntryActivity,
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

                val loadedHostname =
                    entry.optString(
                        "hostname",
                        ""
                    )

                val loadedUsername =
                    entry.optString(
                        "username",
                        ""
                    )

                val loadedPassword =
                    entry.optString(
                        "password",
                        ""
                    )

                if (
                    loadedHostname.isBlank() ||
                    loadedPassword.isEmpty()
                ) {

                    throw IllegalStateException(
                        "Credential data is incomplete"
                    )
                }

                withContext(
                    Dispatchers.Main
                ) {

                    originalHostname =
                        loadedHostname

                    originalUsername =
                        loadedUsername

                    originalPassword =
                        loadedPassword

                    hostnameInput.setText(
                        loadedHostname
                    )

                    usernameInput.setText(
                        loadedUsername
                    )

                    passwordInput.setText(
                        loadedPassword
                    )

                    saveButton.isEnabled =
                        true
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to load credential for editing",
                    e
                )

                withContext(
                    Dispatchers.Main
                ) {

                    saveButton.isEnabled =
                        false

                    Toast.makeText(
                        this@EditEntryActivity,
                        "Unable to load entry ❌",
                        Toast.LENGTH_LONG
                    ).show()

                    finish()
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
    // NORMALIZE WEBSITE / APP
    // =============================================================

    private fun normalizeHostname(
        raw: String
    ): String {

        var value =
            raw.trim()

        if (
            value.isBlank()
        ) {

            return ""
        }

        value =
            value
                .removePrefix(
                    "https://"
                )
                .removePrefix(
                    "http://"
                )
                .removePrefix(
                    "www."
                )

        if (
            value.contains(
                "/"
            )
        ) {

            value =
                value.substringBefore(
                    "/"
                )
        }

        return value.trim()
    }

    // =============================================================
    // UNSAVED CHANGES
    // =============================================================

    private fun hasUnsavedChanges(): Boolean {

        if (
            !::hostnameInput.isInitialized ||
            !::usernameInput.isInitialized ||
            !::passwordInput.isInitialized
        ) {

            return false
        }

        val currentHostname =
            normalizeHostname(
                hostnameInput
                    .text
                    .toString()
            )

        val currentUsername =
            usernameInput
                .text
                .toString()
                .trim()

        val currentPassword =
            passwordInput
                .text
                .toString()

        return (
                currentHostname != originalHostname ||
                        currentUsername != originalUsername ||
                        currentPassword != originalPassword
                )
    }

    private fun maybeDiscardChanges() {

        // Exit immediately when nothing changed.
        if (
            !hasUnsavedChanges()
        ) {

            clearSensitiveUi()
            finish()
            return
        }

        val view =
            layoutInflater.inflate(
                R.layout.dialog_discard_changes,
                null
            )

        val keepEditingButton =
            view.findViewById<TextView>(
                R.id.cancelButton
            )

        val discardButton =
            view.findViewById<TextView>(
                R.id.discardButton
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

        keepEditingButton
            .setOnClickListener {

                dialog.dismiss()
            }

        discardButton
            .setOnClickListener {

                dialog.dismiss()

                clearSensitiveUi()

                Toast.makeText(
                    this,
                    "Changes discarded",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }

        dialog.show()
    }

    // =============================================================
    // SAVE
    // =============================================================

    private fun saveChanges() {

        val uri =
            vaultUri

        if (
            uri == null
        ) {

            handleExpiredSession()
            return
        }

        val hostname =
            normalizeHostname(
                hostnameInput
                    .text
                    .toString()
            )

        val username =
            usernameInput
                .text
                .toString()
                .trim()

        val password =
            passwordInput
                .text
                .toString()

        // Validate required fields.
        if (
            hostname.isBlank()
        ) {

            Toast.makeText(
                this,
                "Website or app name is required.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (
            username.isBlank()
        ) {

            Toast.makeText(
                this,
                "Username or email is required.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (
            password.isBlank()
        ) {

            Toast.makeText(
                this,
                "Password cannot be empty.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val dek =
            VaultRuntimeSession
                .getVaultDek()

        if (
            dek == null
        ) {

            handleExpiredSession()
            return
        }

        saveButton.isEnabled =
            false

        lifecycleScope.launch(
            Dispatchers.IO
        ) {

            try {

                // Load current authenticated vault.
                val vault =
                    VaultManager
                        .loadVaultWithKey(
                            this@EditEntryActivity,
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

                // Update the exact vault entry.
                entry.put(
                    "type",
                    "password"
                )

                entry.put(
                    "hostname",
                    hostname
                )

                entry.put(
                    "username",
                    username
                )

                entry.put(
                    "password",
                    password
                )

                entry.put(
                    "updated",
                    System.currentTimeMillis()
                )

                // Remove obsolete development fields.
                entry.remove(
                    "entryType"
                )

                entry.remove(
                    "keyType"
                )

                entry.remove(
                    "primaryProvider"
                )

                entry.remove(
                    "linkedPrimary"
                )

                // Save encrypted vault.
                VaultManager
                    .saveVaultWithKey(
                        this@EditEntryActivity,
                        uri,
                        vault,
                        dek
                    )

                withContext(
                    Dispatchers.Main
                ) {

                    originalHostname =
                        hostname

                    originalUsername =
                        username

                    originalPassword =
                        password

                    Toast.makeText(
                        this@EditEntryActivity,
                        "Entry updated ✅",
                        Toast.LENGTH_SHORT
                    ).show()

                    setResult(
                        RESULT_OK
                    )

                    clearSensitiveUi()

                    finish()
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to save credential",
                    e
                )

                withContext(
                    Dispatchers.Main
                ) {

                    saveButton.isEnabled =
                        true

                    Toast.makeText(
                        this@EditEntryActivity,
                        "Failed to save entry ❌",
                        Toast.LENGTH_LONG
                    ).show()
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
    // SESSION EXPIRED
    // =============================================================

    private fun handleExpiredSession() {

        VaultRuntimeSession.clear()

        clearSensitiveUi()

        Toast.makeText(
            this,
            "Vault session expired. Please unlock again.",
            Toast.LENGTH_LONG
        ).show()

        finish()
    }

    // =============================================================
    // SENSITIVE CLEANUP
    // =============================================================

    private fun clearSensitiveUi() {

        if (
            ::passwordInput.isInitialized
        ) {

            passwordInput
                .text
                ?.clear()
        }

        // Reduce lifetime of sensitive String references.
        originalPassword =
            ""
    }

    override fun clearSensitiveData() {

        clearSensitiveUi()
    }

    // =============================================================
    // BACK
    // =============================================================

    @Deprecated(
        "Deprecated in Java"
    )
    override fun onBackPressed() {

        maybeDiscardChanges()
    }
}