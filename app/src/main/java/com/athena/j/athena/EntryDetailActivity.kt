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

class EntryDetailActivity : BaseSecureActivity() {

    companion object {
        private const val TAG = "AthenaEntry"
    }
    private enum class SensitiveAction {
        REVEAL,
        COPY,
        EDIT,
        DELETE
    }
    private var vaultUri: Uri? = null
    private var entryIndex: Int = -1
    private var hostname: String = ""
    private var username: String = ""
    private var created: Long = 0L
    private var updated: Long = 0L
    private lateinit var toolbar: MaterialToolbar
    private lateinit var entryTitle: TextView
    private lateinit var usernameField: TextView
    private lateinit var passwordField: TextView
    private lateinit var eyeButton: ImageView
    private lateinit var copyButton: ImageView
    private lateinit var editButton: ImageView
    private lateinit var deleteButton: ImageView

    private var authInProgress = false
    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entry_detail)
        vaultUri = intent.getStringExtra("vaultUri")?.let { Uri.parse(it) }
            ?: VaultRuntimeSession.getVaultUri()
        entryIndex = intent.getIntExtra("entryIndex", -1)
        if (vaultUri == null || entryIndex < 0) {
            Toast.makeText(this, "Invalid vault entry", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        hostname = intent.getStringExtra("hostname") ?: "Unknown"
        username = intent.getStringExtra("username") ?: ""
        created = intent.getLongExtra("created", 0L)
        updated = intent.getLongExtra("updated", 0L)

        setupViews()
        setupToolbar()
        showMaskedLayout()
        setupActions()
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        entryTitle = findViewById(R.id.entryTitle)
        usernameField = findViewById(R.id.usernameField)
        passwordField = findViewById(R.id.passwordField)
        eyeButton = findViewById(R.id.eyeButton)
        copyButton = findViewById(R.id.copyButton)
        editButton = findViewById(R.id.editButton)
        deleteButton = findViewById(R.id.deleteButton)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun showMaskedLayout() {
        passwordVisible = false
        entryTitle.text = hostname
        usernameField.text = username
        passwordField.transformationMethod = null
        passwordField.text = "••••••••••••"
        eyeButton.setImageResource(R.drawable.ic_eye_closed)
        eyeButton.contentDescription = "Show password"

        findViewById<TextView>(R.id.createdText).text =
            "Created: ${if (created > 0L) formatTimestamp(created) else "N/A"}"

        findViewById<TextView>(R.id.editedText).text =
            "Last edited: ${if (updated > 0L) formatTimestamp(updated) else "N/A"}"
    }

    private fun setupActions() {
        eyeButton.setOnClickListener {
            if (passwordVisible) {
                showMaskedLayout()
            } else {
                authenticateFor(SensitiveAction.REVEAL)
            }
        }

        copyButton.setOnClickListener { authenticateFor(SensitiveAction.COPY) }
        editButton.setOnClickListener { authenticateFor(SensitiveAction.EDIT) }
        deleteButton.setOnClickListener { showDeleteConfirmation() }
    }

    private fun showDeleteConfirmation() {
        val view = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        val cancelButton = view.findViewById<TextView>(R.id.cancelButton)
        val yesButton = view.findViewById<TextView>(R.id.yesButton)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.AthenaDialogTheme)
            .setView(view)
            .setCancelable(true)
            .create()

        cancelButton.setOnClickListener { dialog.dismiss() }
        yesButton.setOnClickListener {
            dialog.dismiss()
            authenticateFor(SensitiveAction.DELETE)
        }

        dialog.show()
    }

    private fun authenticateFor(action: SensitiveAction) {
        if (authInProgress) return
        if (vaultUri == null || !VaultRuntimeSession.isUnlocked()) {
            handleExpiredSession()
            return
        }

        authInProgress = true
        if (biometricAuthenticationAvailable()) {
            authenticateWithBiometrics(action)
        } else {
            authInProgress = false
            authenticateWithMasterPassword(action)
        }
    }

    private fun biometricAuthenticationAvailable(): Boolean {
        return BiometricStore.isBiometricAvailable(this) &&
                BiometricStore.hasWrappedDek(this) &&
                VaultSessionManager.isBiometricEnabled(this)
    }

    private fun authenticateWithBiometrics(action: SensitiveAction) {
        val iv = BiometricStore.getStoredIv(this)
        if (iv == null) {
            authInProgress = false
            authenticateWithMasterPassword(action)
            return
        }

        val cipher = BiometricStore.initDecryptCipher(iv)
        iv.fill(0)
        if (cipher == null) {
            authInProgress = false
            authenticateWithMasterPassword(action)
            return
        }

        val prompt = BiometricPrompt(
            this,
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    authInProgress = false
                    val cryptoCipher = result.cryptoObject?.cipher
                    if (cryptoCipher == null) {
                        centeredToast("Biometric authentication failed")
                        return
                    }
                    val dek = BiometricStore.unwrapDek(
                        this@EntryDetailActivity,
                        cryptoCipher
                    )
                    if (dek == null) {
                        centeredToast("Biometric authentication failed")
                        return
                    }
                    handleAuthenticatedDek(dek, action)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    authInProgress = false

                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        authenticateWithMasterPassword(action)
                    }
                }
                override fun onAuthenticationFailed() {}
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(actionTitle(action))
            .setSubtitle("Authenticate to continue")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .setNegativeButtonText("Use password")
            .build()

        prompt.authenticate(
            promptInfo,
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    private fun authenticateWithMasterPassword(action: SensitiveAction) {
        val uri = vaultUri ?: return
        showMasterPasswordDialog(
            title = actionTitle(action),
            subtitle = "Re-enter your master password to continue.",
            positiveLabel = actionPositiveLabel(action)
        ) { password ->

            lifecycleScope.launch(Dispatchers.IO) {
                var vaultId: ByteArray? = null
                var recoveryKey: ByteArray? = null
                var recoveryKeyUnavailable = false

                val dek = try {
                    vaultId = VaultManager.getVaultId(
                        context = this@EntryDetailActivity,
                        uri = uri
                    )
                    if (vaultId == null) {
                        null
                    } else {
                        recoveryKey = RecoveryKeyStore.load(
                            context = this@EntryDetailActivity,
                            vaultId = vaultId
                        )
                        if (recoveryKey == null) {
                            recoveryKeyUnavailable = true
                            null
                        } else {
                            VaultManager.deriveVaultKey(
                                context = this@EntryDetailActivity,
                                uri = uri,
                                password = password,
                                recoveryKey = recoveryKey
                            )
                        }
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "Password authentication failed", e)
                    null

                } finally {
                    recoveryKey?.fill(0)
                    vaultId?.fill(0)
                }
                withContext(Dispatchers.Main) {
                    if (dek == null) {
                        if (recoveryKeyUnavailable) {
                            centeredToast(
                                "Recovery key unavailable. Lock and unlock the vault again."
                            )
                        } else {
                            centeredToast("Incorrect master password")
                        }
                    } else {
                        handleAuthenticatedDek(dek, action)
                    }
                }
            }
        }
    }

    private fun handleAuthenticatedDek(
        dek: ByteArray,
        action: SensitiveAction
    ) {
        val uri = vaultUri
        if (uri == null) {
            dek.fill(0)
            handleExpiredSession()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val vault = VaultManager.loadVaultWithKey(
                    this@EntryDetailActivity,
                    uri,
                    dek
                ) ?: throw SecurityException(
                    "Vault authentication failed"
                )
                if (entryIndex < 0 || entryIndex >= vault.length()) {
                    throw IndexOutOfBoundsException(
                        "Credential no longer exists"
                    )
                }
                val entry = vault.optJSONObject(entryIndex)
                    ?: throw IllegalStateException(
                        "Credential is invalid"
                    )

                when (action) {
                    SensitiveAction.REVEAL -> {
                        val loaded = readCredential(entry)

                        withContext(Dispatchers.Main) {
                            revealCredential(loaded)
                        }
                    }

                    SensitiveAction.COPY -> {
                        val password = entry.optString(
                            "password",
                            ""
                        )

                        if (password.isEmpty()) {
                            throw IllegalStateException(
                                "Password is missing"
                            )
                        }
                        withContext(Dispatchers.Main) {
                            ClipboardUtils.copySensitive(
                                context = this@EntryDetailActivity,
                                label = "Password",
                                text = password,
                                clearAfterMs = 30_000L
                            ) {
                                centeredToast("Clipboard cleared")
                            }

                            centeredToast("Password copied")
                        }
                    }

                    SensitiveAction.EDIT -> {
                        withContext(Dispatchers.Main) {
                            openEditEntry()
                        }
                    }

                    SensitiveAction.DELETE -> {
                        vault.remove(entryIndex)

                        VaultManager.saveVaultWithKey(
                            this@EntryDetailActivity,
                            uri,
                            vault,
                            dek
                        )
                        withContext(Dispatchers.Main) {
                            centeredToast("Entry deleted")

                            val result = Intent().apply {
                                putExtra("entryDeleted", true)
                            }

                            setResult(RESULT_OK, result)
                            finish()
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Sensitive credential operation failed", e)
                withContext(Dispatchers.Main) {
                    centeredToast("Unable to complete operation")
                }

            } finally {
                dek.fill(0)
            }
        }
    }

    private fun readCredential(entry: JSONObject): LoadedCredential {
        val loadedHostname = entry.optString("hostname", hostname)
        val loadedUsername = entry.optString("username", username)
        val loadedPassword = entry.optString("password", "")
        if (loadedPassword.isEmpty()) {
            throw IllegalStateException(
                "Credential password is missing"
            )
        }

        return LoadedCredential(
            hostname = loadedHostname,
            username = loadedUsername,
            password = loadedPassword,
            created = entry.optLong("created", created),
            updated = entry.optLong("updated", updated)
        )
    }

    private fun revealCredential(credential: LoadedCredential) {
        hostname = credential.hostname
        username = credential.username
        created = credential.created
        updated = credential.updated
        entryTitle.text = credential.hostname
        usernameField.text = credential.username
        passwordField.transformationMethod = null
        passwordField.text = credential.password
        passwordField.invalidate()
        findViewById<TextView>(R.id.createdText).text =
            "Created: ${if (created > 0L) formatTimestamp(created) else "N/A"}"
        findViewById<TextView>(R.id.editedText).text =
            "Last edited: ${if (updated > 0L) formatTimestamp(updated) else "N/A"}"
        passwordVisible = true
        eyeButton.setImageResource(R.drawable.ic_eye_open)
        eyeButton.contentDescription = "Hide password"
        centeredToast("Password revealed")
    }

    private fun openEditEntry() {
        val uri = vaultUri ?: return
        val editIntent = Intent(
            this,
            EditEntryActivity::class.java
        ).apply {
            putExtra("vaultUri", uri.toString())
            putExtra("entryIndex", entryIndex)
        }

        startActivity(editIntent)
        pushSlideTransition()
    }

    private fun actionTitle(action: SensitiveAction): String {
        return when (action) {
            SensitiveAction.REVEAL -> "Reveal password"
            SensitiveAction.COPY -> "Copy password"
            SensitiveAction.EDIT -> "Edit credential"
            SensitiveAction.DELETE -> "Delete credential"
        }
    }

    private fun actionPositiveLabel(action: SensitiveAction): String {
        return when (action) {
            SensitiveAction.REVEAL -> "Reveal"
            SensitiveAction.COPY -> "Copy"
            SensitiveAction.EDIT -> "Continue"
            SensitiveAction.DELETE -> "Delete"
        }
    }

    private fun handleExpiredSession() {
        VaultRuntimeSession.clear()
        centeredToast("Vault session expired. Unlock again.")
        finish()
    }

    private fun centeredToast(message: String) {
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

    private fun formatTimestamp(timestamp: Long): String {
        return SimpleDateFormat(
            "dd MMM yyyy, HH:mm",
            Locale.getDefault()
        ).format(
            Date(timestamp)
        )
    }

    override fun clearSensitiveData() {
        if (::passwordField.isInitialized) {
            passwordField.transformationMethod = null
            passwordField.text = "••••••••••••"
        }

        if (::eyeButton.isInitialized) {
            eyeButton.setImageResource(R.drawable.ic_eye_closed)
            eyeButton.contentDescription = "Show password"
        }

        passwordVisible = false
    }

    private data class LoadedCredential(
        val hostname: String,
        val username: String,
        val password: String,
        val created: Long,
        val updated: Long
    )
}