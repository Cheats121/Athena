package com.athena.j.athena

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Base Activity for Athena's security-sensitive screens.
 *
 * Responsibilities:
 *
 * - blocks screenshots / screen recording
 * - centralizes inactivity timeout handling
 * - provides secure password dialogs
 * - gives child Activities a consistent cleanup hook
 * - ensures timeout/session enforcement occurs BEFORE
 *   child Activities reload sensitive data
 */
open class BaseSecureActivity : AppCompatActivity() {

    // =============================================================
    // CREATE
    // =============================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        // ---------------------------------------------------------
        // Block screenshots and most screen capture paths
        // ---------------------------------------------------------

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    // =============================================================
    // MASTER PASSWORD DIALOG
    // =============================================================

    /**
     * Used for step-up authentication.
     *
     * The password is passed to the callback only after the
     * dialog is dismissed.
     *
     * UI buffers are cleared immediately after use.
     */
    protected fun showMasterPasswordDialog(
        title: String,
        subtitle: String,
        positiveLabel: String = "Continue",
        onPasswordEntered: (String) -> Unit
    ) {

        val view =
            layoutInflater.inflate(
                R.layout.dialog_master_password,
                null
            )

        val titleView =
            view.findViewById<TextView>(
                R.id.dialogTitleText
            )

        val subtitleView =
            view.findViewById<TextView>(
                R.id.dialogSubtitleText
            )

        val input =
            view.findViewById<SecureEditText>(
                R.id.masterPasswordInput
            )

        val cancelButton =
            view.findViewById<TextView>(
                R.id.cancelButton
            )

        val confirmButton =
            view.findViewById<TextView>(
                R.id.confirmButton
            )

        titleView.text =
            title

        subtitleView.text =
            subtitle

        confirmButton.text =
            positiveLabel.uppercase()

        val dialog =
            AlertDialog
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

        // ---------------------------------------------------------
        // CLEANUP
        // ---------------------------------------------------------

        fun clearDialogSecret() {

            input.text
                ?.clear()
        }

        dialog.setOnDismissListener {

            clearDialogSecret()
        }

        dialog.setOnCancelListener {

            clearDialogSecret()
        }

        // ---------------------------------------------------------
        // CANCEL
        // ---------------------------------------------------------

        cancelButton
            .setOnClickListener {

                clearDialogSecret()

                dialog.dismiss()
            }

        // ---------------------------------------------------------
        // CONFIRM
        // ---------------------------------------------------------

        confirmButton
            .setOnClickListener {

                /*
                 * Do not trim passwords.
                 *
                 * A user may intentionally choose leading or
                 * trailing whitespace as part of the master
                 * password.
                 */
                val password =
                    input
                        .text
                        .toString()

                if (
                    password.isEmpty()
                ) {

                    input.error =
                        "Required"

                    return@setOnClickListener
                }

                input.error =
                    null

                /*
                 * Dismiss first so the UI buffer is cleared.
                 *
                 * The String passed below cannot be reliably
                 * zeroized on the JVM, so its lifetime should be
                 * kept short by the caller.
                 */
                dialog.dismiss()

                onPasswordEntered(
                    password
                )
            }

        dialog.show()
    }

    // =============================================================
    // CREATE MASTER PASSWORD DIALOG
    // =============================================================

    protected fun showNewVaultPasswordDialog(
        onPasswordReady: (String) -> Unit
    ) {

        val view =
            layoutInflater.inflate(
                R.layout.dialog_new_vault_password,
                null
            )

        val titleView =
            view.findViewById<TextView>(
                R.id.dialogTitleText
            )

        val subtitleView =
            view.findViewById<TextView>(
                R.id.dialogSubtitleText
            )

        val passwordInput =
            view.findViewById<SecureEditText>(
                R.id.newMasterPasswordInput
            )

        val confirmInput =
            view.findViewById<SecureEditText>(
                R.id.confirmMasterPasswordInput
            )

        val cancelButton =
            view.findViewById<TextView>(
                R.id.cancelButton
            )

        val createButton =
            view.findViewById<TextView>(
                R.id.createButton
            )

        val lenDot =
            view.findViewById<View>(
                R.id.reqLenDot
            )

        val symbolDot =
            view.findViewById<View>(
                R.id.reqSymbolDot
            )

        val upperDot =
            view.findViewById<View>(
                R.id.reqUpperDot
            )

        val digitDot =
            view.findViewById<View>(
                R.id.reqDigitDot
            )

        titleView.text =
            "Create master password"

        subtitleView.text =
            "This password protects your vault. If you lose it, your data cannot be recovered."

        val dialog =
            AlertDialog
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

        // =========================================================
        // REQUIREMENTS
        // =========================================================

        fun checkRequirements(
            password: String
        ): PasswordRequirements {

            return PasswordRequirements(
                hasMinimumLength =
                    password.length >= 12,

                hasUppercase =
                    password.any {
                        it.isUpperCase()
                    },

                hasLowercase =
                    password.any {
                        it.isLowerCase()
                    },

                hasDigit =
                    password.any {
                        it.isDigit()
                    },

                hasSymbol =
                    password.any {
                        !it.isLetterOrDigit()
                    }
            )
        }

        fun setCreateEnabled(
            enabled: Boolean
        ) {

            createButton.isEnabled =
                enabled

            createButton.alpha =
                if (
                    enabled
                ) {

                    1f

                } else {

                    0.4f
                }
        }

        fun updateIndicators(
            password: String
        ) {

            val requirements =
                checkRequirements(
                    password
                )

            lenDot.setBackgroundResource(
                if (
                    requirements.hasMinimumLength
                ) {
                    R.drawable.req_indicator_on
                } else {
                    R.drawable.req_indicator_off
                }
            )

            symbolDot.setBackgroundResource(
                if (
                    requirements.hasSymbol
                ) {
                    R.drawable.req_indicator_on
                } else {
                    R.drawable.req_indicator_off
                }
            )

            upperDot.setBackgroundResource(
                if (
                    requirements.hasUppercase
                ) {
                    R.drawable.req_indicator_on
                } else {
                    R.drawable.req_indicator_off
                }
            )

            digitDot.setBackgroundResource(
                if (
                    requirements.hasDigit
                ) {
                    R.drawable.req_indicator_on
                } else {
                    R.drawable.req_indicator_off
                }
            )
        }

        fun updateCreateButtonState() {

            val password =
                passwordInput
                    .text
                    .toString()

            val confirmation =
                confirmInput
                    .text
                    .toString()

            val requirements =
                checkRequirements(
                    password
                )

            val validPassword =
                requirements.allSatisfied

            val confirmationMatches =
                confirmation.isNotEmpty() &&
                        confirmation == password

            setCreateEnabled(
                validPassword &&
                        confirmationMatches
            )
        }

        // =========================================================
        // TEXT WATCHERS
        // =========================================================

        passwordInput
            .addTextChangedListener(
                object :
                    TextWatcher {

                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) = Unit

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) = Unit

                    override fun afterTextChanged(
                        s: Editable?
                    ) {

                        val password =
                            s
                                ?.toString()
                                .orEmpty()

                        updateIndicators(
                            password
                        )

                        updateCreateButtonState()
                    }
                }
            )

        confirmInput
            .addTextChangedListener(
                object :
                    TextWatcher {

                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) = Unit

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) = Unit

                    override fun afterTextChanged(
                        s: Editable?
                    ) {

                        updateCreateButtonState()
                    }
                }
            )

        // =========================================================
        // CLEANUP
        // =========================================================

        fun clearPasswordFields() {

            passwordInput.text
                ?.clear()

            confirmInput.text
                ?.clear()
        }

        dialog.setOnDismissListener {

            clearPasswordFields()
        }

        dialog.setOnCancelListener {

            clearPasswordFields()
        }

        // =========================================================
        // CANCEL
        // =========================================================

        cancelButton
            .setOnClickListener {

                clearPasswordFields()

                dialog.dismiss()
            }

        // =========================================================
        // CREATE
        // =========================================================

        createButton
            .setOnClickListener {

                val password =
                    passwordInput
                        .text
                        .toString()

                val confirmation =
                    confirmInput
                        .text
                        .toString()

                passwordInput.error =
                    null

                confirmInput.error =
                    null

                val requirements =
                    checkRequirements(
                        password
                    )

                if (
                    !requirements.allSatisfied
                ) {

                    passwordInput.error =
                        "Password doesn't meet all requirements"

                    updateIndicators(
                        password
                    )

                    return@setOnClickListener
                }

                if (
                    confirmation.isEmpty()
                ) {

                    confirmInput.error =
                        "Please confirm"

                    return@setOnClickListener
                }

                if (
                    confirmation != password
                ) {

                    confirmInput.error =
                        "Passwords do not match"

                    return@setOnClickListener
                }

                /*
                 * Dismiss first to clear UI buffers.
                 */
                dialog.dismiss()

                onPasswordReady(
                    password
                )
            }

        setCreateEnabled(
            false
        )

        updateIndicators(
            ""
        )

        dialog.show()
    }

    // =============================================================
    // CHILD SECURITY HOOKS
    // =============================================================

    /**
     * Child Activities override this to remove:
     *
     * - revealed passwords
     * - decrypted JSON references
     * - other temporary sensitive UI state
     */
    open fun clearSensitiveData() {
        // Default no-op.
    }

    /**
     * Child Activities override this to refresh their secure state
     * AFTER timeout/session enforcement succeeds.
     */
    open fun onSecureResume() {
        // Default no-op.
    }

    // =============================================================
    // USER INTERACTION
    // =============================================================

    override fun onUserInteraction() {

        super.onUserInteraction()

        /*
         * Every interaction extends the unlocked inactivity window.
         */
        TimeoutManager.resetTimeout(
            this
        )
    }

    // =============================================================
    // RESUME
    // =============================================================

    override fun onResume() {

        super.onResume()

        /*
         * SECURITY ORDER:
         *
         * 1. timeout/session policy runs first
         * 2. only then may child Activities reload sensitive data
         *
         * If TimeoutManager detects an expired session, it can
         * destroy the DEK before onSecureResume() executes.
         */
        TimeoutManager.resetTimeout(
            this
        )

        /*
         * Only secure child screens should proceed if this Activity
         * is still alive and not being closed by timeout handling.
         */
        if (
            !isFinishing &&
            !isDestroyed
        ) {

            onSecureResume()
        }
    }

    // =============================================================
    // PAUSE
    // =============================================================

    override fun onPause() {

        /*
         * Clear immediately visible sensitive state before Athena
         * leaves the foreground.
         *
         * The runtime DEK remains available until TimeoutManager's
         * configured background timeout expires.
         */
        try {

            clearSensitiveData()

        } catch (_: Exception) {
            // Lock/timeout security must continue regardless.
        }

        /*
         * stopTimer() no longer disables locking.
         *
         * It marks Athena as backgrounded while preserving the
         * countdown.
         */
        TimeoutManager.stopTimer()

        super.onPause()
    }

    // =============================================================
    // TRANSITIONS
    // =============================================================

    protected fun pushSlideTransition() {

        overridePendingTransition(
            R.anim.slide_in_right,
            R.anim.slide_out_left
        )
    }

    protected fun popSlideTransition() {

        overridePendingTransition(
            R.anim.slide_in_left,
            R.anim.slide_out_right
        )
    }

    // =============================================================
    // PASSWORD REQUIREMENTS MODEL
    // =============================================================

    private data class PasswordRequirements(
        val hasMinimumLength: Boolean,
        val hasUppercase: Boolean,
        val hasLowercase: Boolean,
        val hasDigit: Boolean,
        val hasSymbol: Boolean
    ) {

        val allSatisfied: Boolean
            get() =
                hasMinimumLength &&
                        hasUppercase &&
                        hasLowercase &&
                        hasDigit &&
                        hasSymbol
    }
}