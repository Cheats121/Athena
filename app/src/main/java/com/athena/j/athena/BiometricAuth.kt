package com.athena.j.athena

import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import javax.crypto.Cipher


/**
 * Athena biometric authentication helper.
 *
 * Provides two authentication flows:
 *
 * STANDARD AUTHENTICATION
 * - biometric strong authentication
 * - device credential fallback
 * - no cryptographic object required
 *
 * CRYPTOGRAPHIC AUTHENTICATION
 * - biometric/device authentication
 * - authentication-bound Cipher
 * - returns the authenticated Cipher after success
 *
 * This class does not store biometric data.
 * Authentication is handled entirely by Android's
 * BiometricPrompt framework.
 */
object BiometricAuth {

    // =============================================================
    // AUTHENTICATION AVAILABILITY
    // =============================================================

    /**
     * Returns whether this device can currently authenticate using:
     *
     * - BIOMETRIC_STRONG
     * - DEVICE_CREDENTIAL
     *
     * Examples include:
     *
     * - fingerprint
     * - face authentication
     * - device PIN
     * - device password
     * - device pattern
     */
    fun canAuthenticate(
        activity: AppCompatActivity
    ): Boolean {

        val biometricManager =
            BiometricManager.from(
                activity
            )

        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL

        return biometricManager
            .canAuthenticate(
                authenticators
            ) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    // =============================================================
    // STANDARD AUTHENTICATION PROMPT
    // =============================================================

    /**
     * Displays a normal biometric authentication prompt.
     *
     * This flow does not bind authentication to a cryptographic
     * operation.
     *
     * Use this for actions that only require confirmation that
     * the current user successfully authenticated.
     */
    fun prompt(
        activity: AppCompatActivity,
        title: String = "Unlock",
        subtitle: String? = null,
        onSuccess: () -> Unit,
        onError: ((reason: CharSequence?) -> Unit)? = null
    ) {

        // ---------------------------------------------------------
        // Main-thread executor
        // ---------------------------------------------------------

        val executor: Executor =
            ContextCompat.getMainExecutor(
                activity
            )

        // ---------------------------------------------------------
        // Configure biometric prompt
        // ---------------------------------------------------------

        val promptInfo =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(
                    title
                )
                .apply {

                    if (
                        !subtitle.isNullOrEmpty()
                    ) {

                        setSubtitle(
                            subtitle
                        )
                    }
                }
                .setAllowedAuthenticators(
                    BiometricManager
                        .Authenticators
                        .BIOMETRIC_STRONG or
                            BiometricManager
                                .Authenticators
                                .DEVICE_CREDENTIAL
                )
                .build()

        // ---------------------------------------------------------
        // Authentication callback
        // ---------------------------------------------------------

        val biometricPrompt =
            BiometricPrompt(
                activity,
                executor,
                object :
                    BiometricPrompt.AuthenticationCallback() {

                    // -------------------------------------------------
                    // SUCCESS
                    // -------------------------------------------------

                    override fun onAuthenticationSucceeded(
                        result:
                        BiometricPrompt.AuthenticationResult
                    ) {

                        super.onAuthenticationSucceeded(
                            result
                        )

                        activity.runOnUiThread {

                            onSuccess()
                        }
                    }

                    // -------------------------------------------------
                    // TERMINAL ERROR / USER CANCELLATION
                    // -------------------------------------------------

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {

                        super.onAuthenticationError(
                            errorCode,
                            errString
                        )

                        activity.runOnUiThread {

                            onError?.invoke(
                                errString
                            )
                        }
                    }

                    // -------------------------------------------------
                    // FAILED BIOMETRIC ATTEMPT
                    // -------------------------------------------------

                    override fun onAuthenticationFailed() {

                        super.onAuthenticationFailed()

                        activity.runOnUiThread {

                            onError?.invoke(
                                "Authentication failed"
                            )
                        }
                    }
                }
            )

        // ---------------------------------------------------------
        // Launch prompt
        // ---------------------------------------------------------

        biometricPrompt.authenticate(
            promptInfo
        )
    }

    // =============================================================
    // CRYPTOGRAPHIC AUTHENTICATION PROMPT
    // =============================================================

    /**
     * Displays a biometric authentication prompt bound to a Cipher.
     *
     * Android receives the Cipher through a BiometricPrompt
     * CryptoObject.
     *
     * After successful authentication, the returned Cipher is
     * authorized for the cryptographic operation associated with
     * the Android Keystore key.
     *
     * This is intended for operations such as:
     *
     * - decrypting biometric-wrapped vault key material
     * - encrypting key material during biometric enrollment
     */
    fun promptWithCrypto(
        activity: AppCompatActivity,
        cipher: Cipher,
        title: String = "Authenticate",
        subtitle: String? = null,
        onSuccessWithCipher: (Cipher) -> Unit,
        onError: ((reason: CharSequence?) -> Unit)? = null
    ) {

        // ---------------------------------------------------------
        // Main-thread executor
        // ---------------------------------------------------------

        val executor: Executor =
            ContextCompat.getMainExecutor(
                activity
            )

        // ---------------------------------------------------------
        // Configure biometric prompt
        // ---------------------------------------------------------

        val promptInfo =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(
                    title
                )
                .apply {

                    if (
                        !subtitle.isNullOrEmpty()
                    ) {

                        setSubtitle(
                            subtitle
                        )
                    }
                }
                .setAllowedAuthenticators(
                    BiometricManager
                        .Authenticators
                        .BIOMETRIC_STRONG or
                            BiometricManager
                                .Authenticators
                                .DEVICE_CREDENTIAL
                )
                .build()

        // ---------------------------------------------------------
        // Authentication callback
        // ---------------------------------------------------------

        val biometricPrompt =
            BiometricPrompt(
                activity,
                executor,
                object :
                    BiometricPrompt.AuthenticationCallback() {

                    // -------------------------------------------------
                    // SUCCESS
                    // -------------------------------------------------

                    override fun onAuthenticationSucceeded(
                        result:
                        BiometricPrompt.AuthenticationResult
                    ) {

                        super.onAuthenticationSucceeded(
                            result
                        )

                        /*
                         * The CryptoObject returned by Android should
                         * contain the authenticated Cipher.
                         *
                         * The original Cipher is retained as a fallback
                         * in case the framework does not return one.
                         */
                        val cryptoObject =
                            result.cryptoObject

                        val authenticatedCipher =
                            cryptoObject
                                ?.cipher
                                ?: cipher

                        activity.runOnUiThread {

                            onSuccessWithCipher(
                                authenticatedCipher
                            )
                        }
                    }

                    // -------------------------------------------------
                    // TERMINAL ERROR / USER CANCELLATION
                    // -------------------------------------------------

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {

                        super.onAuthenticationError(
                            errorCode,
                            errString
                        )

                        activity.runOnUiThread {

                            onError?.invoke(
                                errString
                            )
                        }
                    }

                    // -------------------------------------------------
                    // FAILED BIOMETRIC ATTEMPT
                    // -------------------------------------------------

                    override fun onAuthenticationFailed() {

                        super.onAuthenticationFailed()

                        activity.runOnUiThread {

                            onError?.invoke(
                                "Authentication failed"
                            )
                        }
                    }
                }
            )

        // ---------------------------------------------------------
        // Bind Cipher to authentication request
        // ---------------------------------------------------------

        val cryptoObject =
            BiometricPrompt.CryptoObject(
                cipher
            )

        // ---------------------------------------------------------
        // Launch authenticated cryptographic operation
        // ---------------------------------------------------------

        biometricPrompt.authenticate(
            promptInfo,
            cryptoObject
        )
    }
}