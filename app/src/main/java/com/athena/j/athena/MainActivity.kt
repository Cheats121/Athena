package com.athena.j.athena

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

class MainActivity : BaseSecureActivity() {

    companion object {
        private const val TAG = "AthenaAuth"
    }
    private lateinit var selectVaultButton: Button
    private lateinit var masterPasswordInput: EditText
    private lateinit var unlockButton: Button
    private lateinit var createVaultText: TextView
    private var vaultUri: Uri? = null
    private var pendingVaultPassword: String? = null
    private var biometricPromptActive = false

    private val pickVaultFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                centeredToast("No vault selected")
                return@registerForActivityResult
            }
            tryPersistUriPermission(uri)
            vaultUri = uri
            masterPasswordInput.isEnabled = true
            unlockButton.isEnabled = true
            centeredToast("Vault selected ✅")
            val savedUri = VaultSessionManager.getSessionUri(this)
            if (
                savedUri != null &&
                savedUri == uri &&
                biometricQuickUnlockAvailable()
            ) {
                maybeOfferBiometricQuickUnlock(uri)
            }
        }

    private val chooseVaultLocation =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            val password = pendingVaultPassword
            pendingVaultPassword = null
            if (uri == null || password.isNullOrEmpty()) {
                centeredToast("Vault creation cancelled")
                return@registerForActivityResult
            }
            val recoveryKey = VaultManager.generateRecoveryKey()
            var recoveryKeyTransferred = false
            try {
                tryPersistUriPermission(uri)
                VaultManager.createVault(
                    context = this,
                    uri = uri,
                    password = password,
                    recoveryKey = recoveryKey
                )
                vaultUri = uri
                recoveryKeyTransferred = true

                showNewVaultRecoveryKey(
                    uri = uri,
                    password = password,
                    recoveryKey = recoveryKey
                )
            } catch (e: Exception) {
                Log.e(TAG, "Vault creation failed", e)
                VaultRuntimeSession.clear()
                centeredToast("Error creating vault ❌")
            } finally {
                if (!recoveryKeyTransferred) {
                    recoveryKey.fill(0)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupViews()
        setupListeners()
        masterPasswordInput.transformationMethod =
            InstantPasswordTransformationMethod()
        masterPasswordInput.isEnabled = false
        unlockButton.isEnabled = false
        val savedUri = VaultSessionManager.getSessionUri(this)
        if (savedUri != null) {
            vaultUri = savedUri
            masterPasswordInput.isEnabled = true
            unlockButton.isEnabled = true
            if (biometricQuickUnlockAvailable()) {
                maybeOfferBiometricQuickUnlock(savedUri)
            }
        }
    }

    private fun setupViews() {
        selectVaultButton = findViewById(R.id.selectVaultButton)
        masterPasswordInput = findViewById(R.id.masterPassword)
        unlockButton = findViewById(R.id.loginButton)
        createVaultText = findViewById(R.id.createVault)
    }

    private fun setupListeners() {
        selectVaultButton.setOnClickListener {
            openVaultPicker()
        }

        unlockButton.setOnClickListener {
            unlockVaultWithPassword()
        }

        createVaultText.setOnClickListener {
            beginVaultCreation()
        }
    }

    private fun beginVaultCreation() {
        showNewVaultPasswordDialog(
            onPasswordReady = { password ->
                pendingVaultPassword = password
                chooseVaultLocation.launch("athena-vault.json")
            }
        )
    }

    private fun showNewVaultRecoveryKey(
        uri: Uri,
        password: String,
        recoveryKey: ByteArray
    ) {
        val dialogView = layoutInflater.inflate(
            R.layout.dialog_recovery_key,
            null
        )

        val recoveryKeyText =
            dialogView.findViewById<TextView>(R.id.recoveryKeyText)

        val savedButton =
            dialogView.findViewById<TextView>(R.id.savedButton)
        val recoveryCode = RecoveryKeyCodec.encode(recoveryKey)
        recoveryKeyText.text = recoveryCode
        val dialog = android.app.Dialog(this)
        dialog.setContentView(dialogView)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setBackgroundDrawableResource(
            android.R.color.transparent
        )
        savedButton.setOnClickListener {
            recoveryKeyText.text = ""
            dialog.dismiss()

            finishNewVaultCreation(
                uri = uri,
                password = password,
                recoveryKey = recoveryKey
            )
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun finishNewVaultCreation(
        uri: Uri,
        password: String,
        recoveryKey: ByteArray
    ) {
        var dek: ByteArray? = null
        var vaultId: ByteArray? = null
        try {
            dek = VaultManager.deriveVaultKey(
                context = this,
                uri = uri,
                password = password,
                recoveryKey = recoveryKey
            ) ?: throw SecurityException("New vault verification failed")

            vaultId = VaultManager.getVaultId(
                context = this,
                uri = uri
            ) ?: throw SecurityException("Unable to read vault identifier")

            val cached = RecoveryKeyStore.save(
                context = this,
                vaultId = vaultId,
                recoveryKey = recoveryKey
            )

            if (!cached) {
                centeredToast("Vault created, but recovery key could not be cached")
            }

            VaultRuntimeSession.setSession(uri, dek)
            VaultSessionManager.saveSession(this, uri)
            vaultUri = uri
            masterPasswordInput.text.clear()
            centeredToast("Vault created successfully ✅")
            maybeEnrollBiometric(
                uri = uri,
                dek = dek,
                onFinished = {
                    goToVault(uri)
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "New vault verification failed", e)
            VaultRuntimeSession.clear()
            centeredToast("Vault verification failed ❌")

        } finally {
            recoveryKey.fill(0)
            vaultId?.fill(0)
            dek?.fill(0)
        }
    }

    private fun unlockVaultWithPassword() {
        val uri = vaultUri
        if (uri == null) {
            centeredToast("Please select a vault first.")
            return
        }

        val password = masterPasswordInput.text.toString()
        if (password.isEmpty()) {
            centeredToast("Enter your master password.")
            return
        }

        unlockButton.isEnabled = false

        var vaultId: ByteArray? = null
        var cachedRecoveryKey: ByteArray? = null
        try {
            vaultId = VaultManager.getVaultId(
                context = this,
                uri = uri
            )

            if (vaultId == null) {
                centeredToast("Invalid Athena v4 vault ❌")
                unlockButton.isEnabled = true
                return
            }

            cachedRecoveryKey = RecoveryKeyStore.load(
                context = this,
                vaultId = vaultId
            )

            if (cachedRecoveryKey != null) {
                unlockUsingFactors(
                    uri = uri,
                    password = password,
                    recoveryKey = cachedRecoveryKey,
                    cacheRecoveryKey = false
                )
                unlockButton.isEnabled = true
                return
            }

            showRecoveryKeyEntryDialog(
                uri = uri,
                password = password
            )

        } catch (e: Exception) {
            Log.e(TAG, "Password unlock failed", e)
            centeredToast("Unable to unlock vault ❌")
            unlockButton.isEnabled = true

        } finally {
            vaultId?.fill(0)
            cachedRecoveryKey?.fill(0)
        }
    }

    private fun showRecoveryKeyEntryDialog(
        uri: Uri,
        password: String
    ) {
        val dialogView = layoutInflater.inflate(
            R.layout.dialog_enter_recovery_key,
            null
        )
        val recoveryKeyInput =
            dialogView.findViewById<EditText>(R.id.recoveryKeyInput)

        recoveryKeyInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        recoveryKeyInput.transformationMethod = null

        val recoveryErrorText =
            dialogView.findViewById<TextView>(R.id.recoveryErrorText)

        val cancelButton =
            dialogView.findViewById<TextView>(R.id.cancelButton)

        val recoveryUnlockButton =
            dialogView.findViewById<TextView>(R.id.unlockButton)

        recoveryKeyInput.inputType =
            InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

        recoveryKeyInput.importantForAutofill =
            View.IMPORTANT_FOR_AUTOFILL_NO

        val dialog = android.app.Dialog(this)
        dialog.setContentView(dialogView)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setBackgroundDrawableResource(
            android.R.color.transparent
        )

        cancelButton.setOnClickListener {
            recoveryKeyInput.text.clear()
            recoveryErrorText.text = ""
            dialog.dismiss()
            unlockButton.isEnabled = true
        }

        recoveryUnlockButton.setOnClickListener {
            recoveryErrorText.visibility = View.GONE
            recoveryErrorText.text = ""
            val recoveryKey = RecoveryKeyCodec.decode(
                recoveryKeyInput.text.toString()
            )

            if (recoveryKey == null) {
                recoveryErrorText.text = "Invalid recovery key format"
                recoveryErrorText.visibility = View.VISIBLE
                return@setOnClickListener
            }

            recoveryUnlockButton.isEnabled = false
            cancelButton.isEnabled = false

            val success =
                try {
                    unlockUsingFactors(
                        uri = uri,
                        password = password,
                        recoveryKey = recoveryKey,
                        cacheRecoveryKey = true
                    )
                } finally {
                    recoveryKey.fill(0)
                }

            if (success) {
                recoveryKeyInput.text.clear()
                recoveryErrorText.text = ""
                dialog.dismiss()
            } else {
                recoveryErrorText.text = "Incorrect password or recovery key"
                recoveryErrorText.visibility = View.VISIBLE
                recoveryUnlockButton.isEnabled = true
                cancelButton.isEnabled = true
            }
        }

        dialog.setOnCancelListener {
            recoveryKeyInput.text.clear()
            recoveryErrorText.text = ""
            unlockButton.isEnabled = true
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun unlockUsingFactors(
        uri: Uri,
        password: String,
        recoveryKey: ByteArray,
        cacheRecoveryKey: Boolean
    ): Boolean {
        var dek: ByteArray? = null
        var vaultId: ByteArray? = null
        return try {
            dek = VaultManager.deriveVaultKey(
                context = this,
                uri = uri,
                password = password,
                recoveryKey = recoveryKey
            )

            if (dek == null) {
                centeredToast("Invalid password or recovery key ❌")
                return false
            }

            if (cacheRecoveryKey) {
                vaultId = VaultManager.getVaultId(
                    context = this,
                    uri = uri
                )

                if (vaultId != null) {
                    val stored = RecoveryKeyStore.save(
                        context = this,
                        vaultId = vaultId,
                        recoveryKey = recoveryKey
                    )

                    if (!stored) {
                        centeredToast("Vault unlocked, but recovery key was not cached")
                    }
                }
            }

            VaultRuntimeSession.setSession(uri, dek)
            VaultSessionManager.saveSession(this, uri)
            tryPersistUriPermission(uri)
            masterPasswordInput.text.clear()
            centeredToast("Vault unlocked ✅")
            maybeEnrollBiometric(
                uri = uri,
                dek = dek,
                onFinished = {
                    goToVault(uri)
                }
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "v4 vault unlock failed", e)
            centeredToast("Unable to unlock vault ❌")
            false
        } finally {
            vaultId?.fill(0)
            dek?.fill(0)
        }
    }

    private fun biometricQuickUnlockAvailable(): Boolean {
        return BiometricStore.isBiometricAvailable(this) &&
                BiometricStore.hasWrappedDek(this) &&
                VaultSessionManager.isBiometricEnabled(this)
    }

    private fun maybeOfferBiometricQuickUnlock(uri: Uri) {
        if (biometricPromptActive) return
        if (!biometricQuickUnlockAvailable()) return
        val storedIv = BiometricStore.getStoredIv(this) ?: return
        val decryptCipher = BiometricStore.initDecryptCipher(storedIv) ?: return
        biometricPromptActive = true
        val prompt = BiometricPrompt(
            this,
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    biometricPromptActive = false
                    val cipher = result.cryptoObject?.cipher
                    if (cipher == null) {
                        centeredToast("Biometric crypto error ❌")
                        return
                    }
                    val dek = BiometricStore.unwrapDek(
                        this@MainActivity,
                        cipher
                    )
                    if (dek == null) {
                        centeredToast("Biometric unlock failed ❌")
                        return
                    }
                    try {
                        val valid = VaultManager.verifyIntegrityWithKey(
                            this@MainActivity,
                            uri,
                            dek
                        )
                        if (!valid) {
                            centeredToast("Biometric vault verification failed ❌")

                            BiometricStore.clear(this@MainActivity)

                            VaultSessionManager.setBiometricEnabled(
                                this@MainActivity,
                                false
                            )

                            return
                        }

                        VaultRuntimeSession.setSession(uri, dek)
                        VaultSessionManager.saveSession(
                            this@MainActivity,
                            uri
                        )
                        masterPasswordInput.text.clear()
                        centeredToast("Vault unlocked with biometrics ✅")
                        goToVault(uri)

                    } finally {
                        dek.fill(0)
                    }
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    biometricPromptActive = false
                }

                override fun onAuthenticationFailed() {}
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Athena")
            .setSubtitle("Authenticate to unlock your vault")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .setNegativeButtonText("Use password")
            .build()

        prompt.authenticate(
            info,
            BiometricPrompt.CryptoObject(decryptCipher)
        )
    }

    private fun maybeEnrollBiometric(
        uri: Uri,
        dek: ByteArray,
        onFinished: () -> Unit
    ) {
        if (!BiometricStore.isBiometricAvailable(this)) {
            onFinished()
            return
        }

        if (
            VaultSessionManager.isBiometricEnabled(this) &&
            BiometricStore.hasWrappedDek(this)
        ) {
            onFinished()
            return
        }

        val encryptCipher = BiometricStore.initEncryptCipher()
        if (encryptCipher == null) {
            onFinished()
            return
        }

        if (biometricPromptActive) {
            onFinished()
            return
        }

        val biometricDek = dek.copyOf()
        biometricPromptActive = true
        val prompt = BiometricPrompt(
            this,
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    biometricPromptActive = false

                    try {
                        val cipher = result.cryptoObject?.cipher

                        if (cipher == null) {
                            centeredToast("Unable to enable biometric unlock")
                            return
                        }

                        BiometricStore.storeDek(
                            this@MainActivity,
                            cipher,
                            biometricDek
                        )

                        VaultSessionManager.setBiometricEnabled(
                            this@MainActivity,
                            true
                        )

                        VaultSessionManager.saveSession(
                            this@MainActivity,
                            uri
                        )

                        centeredToast("Biometric quick unlock enabled ✅")

                    } catch (e: Exception) {
                        Log.e(TAG, "Biometric enrollment failed", e)
                        BiometricStore.clear(this@MainActivity)
                        VaultSessionManager.setBiometricEnabled(
                            this@MainActivity,
                            false
                        )

                        centeredToast("Biometric setup failed")

                    } finally {
                        biometricDek.fill(0)
                        onFinished()
                    }
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    biometricPromptActive = false
                    biometricDek.fill(0)
                    onFinished()
                }

                override fun onAuthenticationFailed() {}
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Enable biometric unlock?")
            .setSubtitle("Use strong biometrics to unlock this vault")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .setNegativeButtonText("Not now")
            .build()

        prompt.authenticate(
            info,
            BiometricPrompt.CryptoObject(encryptCipher)
        )
    }

    private fun openVaultPicker() {
        pickVaultFile.launch(
            arrayOf(
                "application/json",
                "text/plain",
                "application/octet-stream"
            )
        )
    }

    private fun tryPersistUriPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

        } catch (e: SecurityException) {
            Log.w(TAG, "Persistable URI permission unavailable", e)
        }
    }

    private fun goToVault(uri: Uri) {
        val intent = Intent(this, VaultActivity::class.java).apply {
            putExtra("vaultUri", uri.toString())
        }

        startActivity(intent)
        pushSlideTransition()
    }

    private fun centeredToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.CENTER, 0, 0)
            show()
        }
    }

    override fun clearSensitiveData() {
        if (::masterPasswordInput.isInitialized) {
            masterPasswordInput.text?.clear()
        }
    }
}