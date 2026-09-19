package com.athena.j.athena

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.AppCompatEditText


/**
 * Secure password input field.
 *
 * Disables selection, copy/paste, autofill, and the
 * contextual action menu while masking input instantly.
 */
class SecureEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(
    context,
    attrs,
    defStyleAttr
) {

    // =============================================================
    // INITIALIZATION
    // =============================================================

    init {

        // Disable selection and long-press actions.
        isLongClickable =
            false

        setTextIsSelectable(
            false
        )

        // Disable autofill hints.
        setAutofillHints()

        // Use password input behavior.
        inputType =
            InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD

        // Mask every character immediately.
        transformationMethod =
            InstantPasswordTransformationMethod()

        // Disable contextual copy/cut/paste actions.
        customSelectionActionModeCallback =
            object : ActionMode.Callback {

                override fun onCreateActionMode(
                    mode: ActionMode?,
                    menu: Menu?
                ): Boolean {

                    return false
                }

                override fun onPrepareActionMode(
                    mode: ActionMode?,
                    menu: Menu?
                ): Boolean {

                    return false
                }

                override fun onActionItemClicked(
                    mode: ActionMode?,
                    item: MenuItem?
                ): Boolean {

                    return false
                }

                override fun onDestroyActionMode(
                    mode: ActionMode?
                ) {
                }
            }
    }

    // =============================================================
    // CONTEXT MENU
    // =============================================================

    override fun onTextContextMenuItem(
        id: Int
    ): Boolean {

        return false
    }
}