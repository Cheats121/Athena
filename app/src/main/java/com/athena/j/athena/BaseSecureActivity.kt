package com.athena.j.athena

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nulabinc.zxcvbn.Zxcvbn

open class BaseSecureActivity : AppCompatActivity() {

    private companion object {
        const val MIN_MASTER_PASSWORD_LENGTH = 14
        const val MAX_MASTER_PASSWORD_LENGTH = 128
    }
    private val zxcvbn = Zxcvbn()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }
    protected fun showMasterPasswordDialog(
        title: String,
        subtitle: String,
        positiveLabel: String = "Continue",
        onPasswordEntered: (String) -> Unit
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_master_password, null)
        val titleView = view.findViewById<TextView>(R.id.dialogTitleText)
        val subtitleView = view.findViewById<TextView>(R.id.dialogSubtitleText)
        val input = view.findViewById<SecureEditText>(R.id.masterPasswordInput)
        val cancelButton = view.findViewById<TextView>(R.id.cancelButton)
        val confirmButton = view.findViewById<TextView>(R.id.confirmButton)
        titleView.text = title
        subtitleView.text = subtitle
        confirmButton.text = positiveLabel.uppercase()
        val dialog = AlertDialog.Builder(this, R.style.AthenaDialogTheme)
            .setView(view)
            .setCancelable(true)
            .create()

        fun clearDialogSecret() {
            input.text?.clear()
        }

        dialog.setOnDismissListener { clearDialogSecret() }
        dialog.setOnCancelListener { clearDialogSecret() }
        cancelButton.setOnClickListener {
            clearDialogSecret()
            dialog.dismiss()
        }

        confirmButton.setOnClickListener {
            val password = input.text.toString()

            if (password.isEmpty()) {
                input.error = "Required"
                return@setOnClickListener
            }

            input.error = null
            dialog.dismiss()
            onPasswordEntered(password)
        }

        dialog.show()
    }

    protected fun showNewVaultPasswordDialog(onPasswordReady: (String) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_new_vault_password, null)
        val titleView = view.findViewById<TextView>(R.id.dialogTitleText)
        val subtitleView = view.findViewById<TextView>(R.id.dialogSubtitleText)
        val passwordInput = view.findViewById<SecureEditText>(R.id.newMasterPasswordInput)
        val confirmInput = view.findViewById<SecureEditText>(R.id.confirmMasterPasswordInput)
        val cancelButton = view.findViewById<TextView>(R.id.cancelButton)
        val createButton = view.findViewById<TextView>(R.id.createButton)
        val lenDot = view.findViewById<View>(R.id.reqLenDot)
        val symbolDot = view.findViewById<View>(R.id.reqSymbolDot)
        val upperDot = view.findViewById<View>(R.id.reqUpperDot)
        val digitDot = view.findViewById<View>(R.id.reqDigitDot)
        val strengthText = view.findViewById<TextView>(R.id.passwordStrengthText)
        val strengthBar = view.findViewById<ProgressBar>(R.id.passwordStrengthBar)

        titleView.text = "Create master password"
        subtitleView.text = "Choose a strong master password. You will need both your master password and recovery key to restore this vault on a new device."
        passwordInput.filters = arrayOf(InputFilter.LengthFilter(MAX_MASTER_PASSWORD_LENGTH))
        confirmInput.filters = arrayOf(InputFilter.LengthFilter(MAX_MASTER_PASSWORD_LENGTH))

        val dialog = AlertDialog.Builder(this, R.style.AthenaDialogTheme)
            .setView(view)
            .setCancelable(true)
            .create()

        fun checkRequirements(password: String): PasswordRequirements {
            return PasswordRequirements(
                hasValidLength = password.length in MIN_MASTER_PASSWORD_LENGTH..MAX_MASTER_PASSWORD_LENGTH,
                hasUppercase = password.any { it.isUpperCase() },
                hasLowercase = password.any { it.isLowerCase() },
                hasDigit = password.any { it.isDigit() },
                hasSymbol = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
            )
        }

        fun calculatePasswordStrength(password: String): PasswordStrength {
            if (password.isEmpty()) return PasswordStrength.EMPTY
            val result = zxcvbn.measure(password)
            return when (result.score) {
                0 -> PasswordStrength.VERY_WEAK
                1 -> PasswordStrength.WEAK
                2 -> PasswordStrength.FAIR
                3 -> PasswordStrength.STRONG
                else -> PasswordStrength.VERY_STRONG
            }
        }

        fun setCreateEnabled(enabled: Boolean) {
            createButton.isEnabled = enabled
            createButton.alpha = if (enabled) 1f else 0.4f
        }

        fun updateIndicators(password: String) {
            val requirements = checkRequirements(password)
            lenDot.setBackgroundResource(
                if (requirements.hasValidLength) R.drawable.req_indicator_on else R.drawable.req_indicator_off
            )

            symbolDot.setBackgroundResource(
                if (requirements.hasSymbol) R.drawable.req_indicator_on else R.drawable.req_indicator_off
            )

            upperDot.setBackgroundResource(
                if (requirements.hasUppercase && requirements.hasLowercase)
                    R.drawable.req_indicator_on
                else
                    R.drawable.req_indicator_off
            )

            digitDot.setBackgroundResource(
                if (requirements.hasDigit) R.drawable.req_indicator_on else R.drawable.req_indicator_off
            )
        }

        fun updateStrength(password: String) {
            when (calculatePasswordStrength(password)) {
                PasswordStrength.EMPTY -> {
                    strengthText.text = "Strength: —"
                    strengthBar.progress = 0
                }

                PasswordStrength.VERY_WEAK -> {
                    strengthText.text = "Strength: Very weak"
                    strengthBar.progress = 1
                }

                PasswordStrength.WEAK -> {
                    strengthText.text = "Strength: Weak"
                    strengthBar.progress = 2
                }

                PasswordStrength.FAIR -> {
                    strengthText.text = "Strength: Fair"
                    strengthBar.progress = 3
                }

                PasswordStrength.STRONG -> {
                    strengthText.text = "Strength: Strong"
                    strengthBar.progress = 4
                }

                PasswordStrength.VERY_STRONG -> {
                    strengthText.text = "Strength: Very strong"
                    strengthBar.progress = 5
                }
            }
        }

        fun updateCreateButtonState() {
            val password = passwordInput.text.toString()
            val confirmation = confirmInput.text.toString()
            val requirements = checkRequirements(password)
            val validPassword = requirements.allSatisfied
            val confirmationMatches = confirmation.isNotEmpty() && confirmation == password
            setCreateEnabled(validPassword && confirmationMatches)
        }

        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val password = s?.toString().orEmpty()
                updateIndicators(password)
                updateStrength(password)
                updateCreateButtonState()
            }
        })

        confirmInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateCreateButtonState()
            }
        })

        fun clearPasswordFields() {
            passwordInput.text?.clear()
            confirmInput.text?.clear()
        }

        dialog.setOnDismissListener { clearPasswordFields() }
        dialog.setOnCancelListener { clearPasswordFields() }
        cancelButton.setOnClickListener {
            clearPasswordFields()
            dialog.dismiss()
        }

        createButton.setOnClickListener {
            val password = passwordInput.text.toString()
            val confirmation = confirmInput.text.toString()
            passwordInput.error = null
            confirmInput.error = null
            val requirements = checkRequirements(password)
            if (!requirements.allSatisfied) {
                passwordInput.error = "Password doesn't meet all requirements"
                updateIndicators(password)
                updateStrength(password)
                return@setOnClickListener
            }

            if (confirmation.isEmpty()) {
                confirmInput.error = "Please confirm"
                return@setOnClickListener
            }

            if (confirmation != password) {
                confirmInput.error = "Passwords do not match"
                return@setOnClickListener
            }

            dialog.dismiss()
            onPasswordReady(password)
        }

        setCreateEnabled(false)
        updateIndicators("")
        updateStrength("")
        dialog.show()
    }

    open fun clearSensitiveData() {}
    open fun onSecureResume() {}

    override fun onUserInteraction() {
        super.onUserInteraction()
        TimeoutManager.resetTimeout(this)
    }

    override fun onResume() {
        super.onResume()
        TimeoutManager.resetTimeout(this)
        if (!isFinishing && !isDestroyed) {
            onSecureResume()
        }
    }

    override fun onPause() {
        try {
            clearSensitiveData()
        } catch (_: Exception) {}

        TimeoutManager.stopTimer()
        super.onPause()
    }

    protected fun pushSlideTransition() {
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    protected fun popSlideTransition() {
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private data class PasswordRequirements(
        val hasValidLength: Boolean,
        val hasUppercase: Boolean,
        val hasLowercase: Boolean,
        val hasDigit: Boolean,
        val hasSymbol: Boolean
    ) {
        val allSatisfied: Boolean
            get() = hasValidLength && hasUppercase && hasLowercase && hasDigit && hasSymbol
    }

    private enum class PasswordStrength {
        EMPTY,
        VERY_WEAK,
        WEAK,
        FAIR,
        STRONG,
        VERY_STRONG
    }
}