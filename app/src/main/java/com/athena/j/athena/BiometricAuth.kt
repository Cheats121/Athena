package com.athena.j.athena

import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import javax.crypto.Cipher

object BiometricAuth {
    fun canAuthenticate(activity: AppCompatActivity): Boolean {
        val biometricManager = BiometricManager.from(activity)
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt(
        activity: AppCompatActivity,
        title: String = "Unlock",
        subtitle: String? = null,
        onSuccess: () -> Unit,
        onError: ((reason: CharSequence?) -> Unit)? = null
    ) {
        val executor: Executor = ContextCompat.getMainExecutor(activity)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                if (!subtitle.isNullOrEmpty()) setSubtitle(subtitle)
            }
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    activity.runOnUiThread {
                        onSuccess()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)

                    activity.runOnUiThread {
                        onError?.invoke(errString)
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    activity.runOnUiThread {
                        onError?.invoke("Authentication failed")
                    }
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    fun promptWithCrypto(
        activity: AppCompatActivity,
        cipher: Cipher,
        title: String = "Authenticate",
        subtitle: String? = null,
        onSuccessWithCipher: (Cipher) -> Unit,
        onError: ((reason: CharSequence?) -> Unit)? = null
    ) {
        val executor: Executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                if (!subtitle.isNullOrEmpty()) setSubtitle(subtitle)
            }
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val cryptoObject = result.cryptoObject
                    val authenticatedCipher = cryptoObject?.cipher ?: cipher
                    activity.runOnUiThread {
                        onSuccessWithCipher(authenticatedCipher)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    activity.runOnUiThread {
                        onError?.invoke(errString)
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    activity.runOnUiThread {
                        onError?.invoke("Authentication failed")
                    }
                }
            }
        )

        val cryptoObject = BiometricPrompt.CryptoObject(cipher)
        biometricPrompt.authenticate(promptInfo, cryptoObject)
    }
}