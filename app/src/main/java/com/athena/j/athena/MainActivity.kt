package com.athena.j.athena

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
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

    // =============================================================
    // UI
    // =============================================================

    private lateinit var selectVaultButton: Button
    private lateinit var masterPasswordInput: EditText
    private lateinit var unlockButton: Button
    private lateinit var createVaultText: TextView

    // =============================================================
    // STATE
    // =============================================================

    private var vaultUri: Uri? = null

    /**
     * Temporary password used only between:
     *
     * password creation dialog
     *          ↓
     * Android CreateDocument picker
     *          ↓
     * VaultManager.createVault()
     *
     * It is cleared immediately afterward.
     *
     * NOTE:
     * Kotlin String cannot be reliably zeroed in memory.
     * This is why we keep its lifetime as short as practical.
     */
    private var pendingVaultPassword: String? = null

    private var biometricPromptActive = false

    // =============================================================
    // PICK EXISTING VAULT
    // =============================================================

    private val pickVaultFile =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri == null) {

                centeredToast(
                    "No vault selected"
                )

                return@registerForActivityResult
            }

            tryPersistUriPermission(
                uri
            )

            vaultUri =
                uri

            masterPasswordInput.isEnabled =
                true

            unlockButton.isEnabled =
                true

            centeredToast(
                "Vault selected ✅"
            )

            /*
             * If biometric quick unlock is already configured,
             * attempt it for the remembered vault.
             *
             * We only do this if the selected URI matches the
             * persisted vault URI.
             */
            val savedUri =
                VaultSessionManager
                    .getSessionUri(this)

            if (
                savedUri != null &&
                savedUri == uri &&
                biometricQuickUnlockAvailable()
            ) {

                maybeOfferBiometricQuickUnlock(
                    uri
                )
            }
        }

    // =============================================================
    // CREATE NEW VAULT LOCATION
    // =============================================================

    private val chooseVaultLocation =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument(
                "application/json"
            )
        ) { uri ->

            val password =
                pendingVaultPassword

            /*
             * Clear our field immediately.
             *
             * The local variable remains only for the duration
             * of this callback.
             */
            pendingVaultPassword =
                null

            if (
                uri == null ||
                password.isNullOrEmpty()
            ) {

                centeredToast(
                    "Vault creation cancelled"
                )

                return@registerForActivityResult
            }

            try {

                tryPersistUriPermission(
                    uri
                )

                // -------------------------------------------------
                // Create Athena v3 vault
                // -------------------------------------------------

                VaultManager.createVault(
                    this,
                    uri,
                    password
                )

                /*
                 * Authenticate the new file and retrieve its
                 * random 256-bit DEK.
                 */
                val dek =
                    VaultManager.deriveVaultKey(
                        this,
                        uri,
                        password
                    )
                        ?: throw SecurityException(
                            "New vault verification failed"
                        )

                try {

                    /*
                     * Store only the DEK in RAM.
                     *
                     * The master password is NOT retained.
                     */
                    VaultRuntimeSession.setSession(
                        uri,
                        dek
                    )

                    VaultSessionManager.saveSession(
                        this,
                        uri
                    )

                    vaultUri =
                        uri

                    masterPasswordInput.text.clear()

                    centeredToast(
                        "Vault created successfully ✅"
                    )

                    /*
                     * A newly created vault can also enable
                     * biometric quick unlock.
                     */
                    maybeEnrollBiometric(
                        uri = uri,
                        dek = dek,
                        onFinished = {

                            goToVault(
                                uri
                            )
                        }
                    )

                } finally {

                    /*
                     * setSession() made its own defensive copy.
                     */
                    dek.fill(0)
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Vault creation failed",
                    e
                )

                VaultRuntimeSession.clear()

                centeredToast(
                    "Error creating vault ❌"
                )
            }
        }

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
            R.layout.activity_main
        )

        setupViews()
        setupListeners()

        /*
         * A master password should never be briefly displayed
         * as normal text while typing.
         */
        masterPasswordInput.transformationMethod =
            InstantPasswordTransformationMethod()

        masterPasswordInput.isEnabled =
            false

        unlockButton.isEnabled =
            false

        /*
         * If Athena remembers a vault URI and biometric quick
         * unlock is configured, offer quick unlock on launch.
         */
        val savedUri =
            VaultSessionManager
                .getSessionUri(this)

        if (
            savedUri != null
        ) {

            vaultUri =
                savedUri

            masterPasswordInput.isEnabled =
                true

            unlockButton.isEnabled =
                true

            if (
                biometricQuickUnlockAvailable()
            ) {

                maybeOfferBiometricQuickUnlock(
                    savedUri
                )
            }
        }
    }

    // =============================================================
    // VIEW SETUP
    // =============================================================

    private fun setupViews() {

        selectVaultButton =
            findViewById(
                R.id.selectVaultButton
            )

        masterPasswordInput =
            findViewById(
                R.id.masterPassword
            )

        unlockButton =
            findViewById(
                R.id.loginButton
            )

        createVaultText =
            findViewById(
                R.id.createVault
            )
    }

    private fun setupListeners() {

        selectVaultButton
            .setOnClickListener {

                openVaultPicker()
            }

        unlockButton
            .setOnClickListener {

                unlockVaultWithPassword()
            }

        createVaultText
            .setOnClickListener {

                beginVaultCreation()
            }
    }

    // =============================================================
    // CREATE VAULT
    // =============================================================

    private fun beginVaultCreation() {

        showNewVaultPasswordDialog(
            onPasswordReady = { password ->

                /*
                 * Keep this only as long as required while the
                 * system file picker is open.
                 */
                pendingVaultPassword =
                    password

                chooseVaultLocation.launch(
                    "athena-vault.json"
                )
            }
        )
    }

    // =============================================================
    // PASSWORD UNLOCK
    // =============================================================

    private fun unlockVaultWithPassword() {

        val uri =
            vaultUri

        if (
            uri == null
        ) {

            centeredToast(
                "Please select a vault first."
            )

            return
        }

        val password =
            masterPasswordInput
                .text
                .toString()

        if (
            password.isEmpty()
        ) {

            centeredToast(
                "Enter your master password."
            )

            return
        }

        unlockButton.isEnabled =
            false

        try {

            /*
             * v3 deriveVaultKey():
             *
             * password
             *    ↓
             * Argon2id KEK
             *    ↓
             * unwrap DEK
             *    ↓
             * verify vault AES-GCM
             *    ↓
             * return random DEK
             *
             * So a non-null result already means the master
             * password and encrypted vault authenticated.
             */
            val dek =
                VaultManager.deriveVaultKey(
                    this,
                    uri,
                    password
                )

            if (
                dek == null
            ) {

                centeredToast(
                    "Invalid vault or password ❌"
                )

                return
            }

            try {

                /*
                 * RuntimeSession makes its own DEK copy.
                 */
                VaultRuntimeSession.setSession(
                    uri,
                    dek
                )

                VaultSessionManager.saveSession(
                    this,
                    uri
                )

                tryPersistUriPermission(
                    uri
                )

                /*
                 * Clear password from the UI as soon as
                 * authentication succeeds.
                 */
                masterPasswordInput.text.clear()

                centeredToast(
                    "Vault unlocked ✅"
                )

                maybeEnrollBiometric(
                    uri = uri,
                    dek = dek,
                    onFinished = {

                        goToVault(
                            uri
                        )
                    }
                )

            } finally {

                /*
                 * Destroy our temporary copy.
                 *
                 * RuntimeSession has its own copy.
                 */
                dek.fill(0)
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Password unlock failed",
                e
            )

            centeredToast(
                "Unable to unlock vault ❌"
            )

        } finally {

            unlockButton.isEnabled =
                true
        }
    }

    // =============================================================
    // BIOMETRIC AVAILABILITY
    // =============================================================

    private fun biometricQuickUnlockAvailable(): Boolean {

        return (
                BiometricStore
                    .isBiometricAvailable(this) &&

                        BiometricStore
                            .hasWrappedDek(this) &&

                        VaultSessionManager
                            .isBiometricEnabled(this)
                )
    }

    // =============================================================
    // BIOMETRIC QUICK UNLOCK
    // =============================================================

    private fun maybeOfferBiometricQuickUnlock(
        uri: Uri
    ) {

        if (
            biometricPromptActive
        ) {
            return
        }

        if (
            !biometricQuickUnlockAvailable()
        ) {
            return
        }

        val storedIv =
            BiometricStore
                .getStoredIv(this)
                ?: return

        val decryptCipher =
            BiometricStore
                .initDecryptCipher(
                    storedIv
                )
                ?: return

        biometricPromptActive =
            true

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

                        biometricPromptActive =
                            false

                        val cipher =
                            result
                                .cryptoObject
                                ?.cipher

                        if (
                            cipher == null
                        ) {

                            centeredToast(
                                "Biometric crypto error ❌"
                            )

                            return
                        }

                        val dek =
                            BiometricStore
                                .unwrapDek(
                                    this@MainActivity,
                                    cipher
                                )

                        if (
                            dek == null
                        ) {

                            centeredToast(
                                "Biometric unlock failed ❌"
                            )

                            return
                        }

                        try {

                            /*
                             * Never trust a stored wrapped DEK
                             * blindly.
                             *
                             * Verify that this DEK actually
                             * authenticates the selected vault.
                             */
                            val valid =
                                VaultManager
                                    .verifyIntegrityWithKey(
                                        this@MainActivity,
                                        uri,
                                        dek
                                    )

                            if (
                                !valid
                            ) {

                                centeredToast(
                                    "Biometric vault verification failed ❌"
                                )

                                /*
                                 * Stored DEK no longer matches
                                 * this vault.
                                 */
                                BiometricStore.clear(
                                    this@MainActivity
                                )

                                VaultSessionManager
                                    .setBiometricEnabled(
                                        this@MainActivity,
                                        false
                                    )

                                return
                            }

                            VaultRuntimeSession
                                .setSession(
                                    uri,
                                    dek
                                )

                            VaultSessionManager
                                .saveSession(
                                    this@MainActivity,
                                    uri
                                )

                            masterPasswordInput
                                .text
                                .clear()

                            centeredToast(
                                "Vault unlocked with biometrics ✅"
                            )

                            goToVault(
                                uri
                            )

                        } finally {

                            dek.fill(0)
                        }
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {

                        biometricPromptActive =
                            false

                        /*
                         * Negative button is "Use password".
                         * The password input remains available.
                         */
                    }

                    override fun onAuthenticationFailed() {

                        /*
                         * Non-fatal.
                         *
                         * BiometricPrompt remains open so the
                         * user can try again.
                         */
                    }
                }
            )

        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(
                    "Unlock Athena"
                )
                .setSubtitle(
                    "Authenticate to unlock your vault"
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
            info,
            BiometricPrompt.CryptoObject(
                decryptCipher
            )
        )
    }

    // =============================================================
    // BIOMETRIC ENROLLMENT
    // =============================================================

    /**
     * Wraps the random v3 vault DEK using the biometric-bound
     * Android Keystore key.
     *
     * If biometrics are unavailable, already configured, or the
     * user declines enrollment, Athena simply proceeds normally.
     */
    private fun maybeEnrollBiometric(
        uri: Uri,
        dek: ByteArray,
        onFinished: () -> Unit
    ) {

        if (
            !BiometricStore
                .isBiometricAvailable(this)
        ) {

            onFinished()
            return
        }

        if (
            VaultSessionManager
                .isBiometricEnabled(this) &&
            BiometricStore
                .hasWrappedDek(this)
        ) {

            onFinished()
            return
        }

        val encryptCipher =
            BiometricStore
                .initEncryptCipher()

        if (
            encryptCipher == null
        ) {

            onFinished()
            return
        }

        if (
            biometricPromptActive
        ) {

            onFinished()
            return
        }

        /*
         * Biometric callback may occur after the method returns.
         *
         * We therefore need a temporary owned DEK copy.
         */
        val biometricDek =
            dek.copyOf()

        biometricPromptActive =
            true

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

                        biometricPromptActive =
                            false

                        try {

                            val cipher =
                                result
                                    .cryptoObject
                                    ?.cipher

                            if (
                                cipher == null
                            ) {

                                centeredToast(
                                    "Unable to enable biometric unlock"
                                )

                                return
                            }

                            BiometricStore.storeDek(
                                this@MainActivity,
                                cipher,
                                biometricDek
                            )

                            VaultSessionManager
                                .setBiometricEnabled(
                                    this@MainActivity,
                                    true
                                )

                            VaultSessionManager
                                .saveSession(
                                    this@MainActivity,
                                    uri
                                )

                            centeredToast(
                                "Biometric quick unlock enabled ✅"
                            )

                        } catch (e: Exception) {

                            Log.e(
                                TAG,
                                "Biometric enrollment failed",
                                e
                            )

                            BiometricStore.clear(
                                this@MainActivity
                            )

                            VaultSessionManager
                                .setBiometricEnabled(
                                    this@MainActivity,
                                    false
                                )

                            centeredToast(
                                "Biometric setup failed"
                            )

                        } finally {

                            biometricDek.fill(0)

                            onFinished()
                        }
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {

                        biometricPromptActive =
                            false

                        biometricDek.fill(0)

                        /*
                         * User selected "Not now" or biometric
                         * authentication could not continue.
                         */
                        onFinished()
                    }

                    override fun onAuthenticationFailed() {

                        /*
                         * Do not call onFinished here.
                         *
                         * AuthenticationFailed means the sensor
                         * did not recognize the attempt, but the
                         * prompt remains active for retry.
                         */
                    }
                }
            )

        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(
                    "Enable biometric unlock?"
                )
                .setSubtitle(
                    "Use strong biometrics to unlock this vault"
                )
                .setAllowedAuthenticators(
                    BiometricManager
                        .Authenticators
                        .BIOMETRIC_STRONG
                )
                .setNegativeButtonText(
                    "Not now"
                )
                .build()

        prompt.authenticate(
            info,
            BiometricPrompt.CryptoObject(
                encryptCipher
            )
        )
    }

    // =============================================================
    // FILE PICKER
    // =============================================================

    private fun openVaultPicker() {

        pickVaultFile.launch(
            arrayOf(
                "application/json",
                "text/plain",
                "application/octet-stream"
            )
        )
    }

    // =============================================================
    // URI PERMISSION
    // =============================================================

    private fun tryPersistUriPermission(
        uri: Uri
    ) {

        try {

            contentResolver
                .takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

        } catch (e: SecurityException) {

            /*
             * Some document providers do not support persisted
             * write permission. The current grant may still work.
             */
            Log.w(
                TAG,
                "Persistable URI permission unavailable",
                e
            )
        }
    }

    // =============================================================
    // OPEN VAULT ACTIVITY
    // =============================================================

    private fun goToVault(
        uri: Uri
    ) {

        val intent =
            Intent(
                this,
                VaultActivity::class.java
            ).apply {

                putExtra(
                    "vaultUri",
                    uri.toString()
                )
            }

        startActivity(
            intent
        )

        pushSlideTransition()
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
    // SECURITY CLEANUP
    // =============================================================

    override fun clearSensitiveData() {

        if (
            ::masterPasswordInput.isInitialized
        ) {

            masterPasswordInput
                .text
                ?.clear()
        }
    }
}