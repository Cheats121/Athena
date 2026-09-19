package com.athena.j.athena

import android.content.Context
import android.text.InputType
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureEditTextInstrumentedTest {

    // =============================================================
    // ACTIVITY
    // =============================================================

    private var scenario:
            ActivityScenario<MainActivity>? =
        null

    // =============================================================
    // SETUP
    // =============================================================

    @Before
    fun setup() {

        scenario =
            ActivityScenario.launch(
                MainActivity::class.java
            )
    }

    // =============================================================
    // CLEANUP
    // =============================================================

    @After
    fun cleanup() {

        try {
            scenario?.close()
        } catch (_: Exception) {
        }

        scenario =
            null
    }

    // =============================================================
    // HELPERS
    // =============================================================

    private fun runOnActivity(
        action: (
            MainActivity
        ) -> Unit
    ) {

        val activeScenario =
            scenario
                ?: throw IllegalStateException(
                    "ActivityScenario is not initialized"
                )

        activeScenario.onActivity { activity ->

            action(
                activity
            )
        }
    }

    private fun createSecureEditText(
        activity: MainActivity
    ): SecureEditText {

        return SecureEditText(
            activity
        )
    }

    // =============================================================
    // LONG CLICK
    // =============================================================

    @Test
    fun longClick_isDisabled() {

        runOnActivity { activity ->

            val input =
                createSecureEditText(
                    activity
                )

            assertFalse(
                "SecureEditText must disable long-click behavior",
                input.isLongClickable
            )
        }
    }

    // =============================================================
    // TEXT SELECTION
    // =============================================================

    @Test
    fun textSelection_isDisabled() {

        runOnActivity { activity ->

            val input =
                createSecureEditText(
                    activity
                )

            assertFalse(
                "SecureEditText must not allow text selection",
                input.isTextSelectable
            )
        }
    }

    // =============================================================
    // PASSWORD INPUT TYPE
    // =============================================================

    @Test
    fun inputType_isPassword() {

        runOnActivity { activity ->

            val input =
                createSecureEditText(
                    activity
                )

            val expected =
                InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD

            assertEquals(
                "SecureEditText should use password input type",
                expected,
                input.inputType
            )
        }
    }

    // =============================================================
    // TRANSFORMATION METHOD
    // =============================================================

    @Test
    fun transformationMethod_isInstantPasswordMasking() {

        runOnActivity { activity ->

            val input =
                createSecureEditText(
                    activity
                )

            assertTrue(
                "SecureEditText must use InstantPasswordTransformationMethod",
                input.transformationMethod is
                        InstantPasswordTransformationMethod
            )
        }
    }

    // =============================================================
    // MASKING
    // =============================================================

    @Test
    fun enteredText_isImmediatelyMasked() {

        runOnActivity { activity ->

            val input =
                createSecureEditText(
                    activity
                )

            input.setText(
                "Secret123!"
            )

            val transformed =
                input.transformationMethod
                    .getTransformation(
                        input.text,
                        input
                    )
                    .toString()

            assertEquals(
                "•".repeat(
                    "Secret123!".length
                ),
                transformed
            )
        }
    }

    // =============================================================
    // CONTEXT MENU
    // =============================================================

    @Test
    fun copyContextAction_isRejected() {

        runOnActivity { activity ->

            val input =
                createSecureEditText(
                    activity
                )

            input.setText(
                "secret"
            )

            val result =
                input.onTextContextMenuItem(
                    android.R.id.copy
                )

            assertFalse(
                "Copy context action must be rejected",
                result
            )
        }
    }

    @Test
    fun cutContextAction_isRejected() {

        runOnActivity { activity ->

            val input =
                createSecureEditText(
                    activity
                )

            input.setText(
                "secret"
            )

            val result =
                input.onTextContextMenuItem(
                    android.R.id.cut
                )

            assertFalse(
                "Cut context action must be rejected",
                result
            )
        }
    }

    @Test
    fun pasteContextAction_isRejected() {

        runOnActivity { activity ->

            val input =
                createSecureEditText(
                    activity
                )

            val result =
                input.onTextContextMenuItem(
                    android.R.id.paste
                )

            assertFalse(
                "Paste context action must be rejected",
                result
            )
        }
    }

    @Test
    fun selectAllContextAction_isRejected() {

        runOnActivity { activity ->

            val input =
                createSecureEditText(
                    activity
                )

            input.setText(
                "secret"
            )

            val result =
                input.onTextContextMenuItem(
                    android.R.id.selectAll
                )

            assertFalse(
                "Select-all context action must be rejected",
                result
            )
        }
    }

    // =============================================================
    // CONTEXT ACTION MODE CALLBACK
    // =============================================================

    @Test
    fun contextualSelectionActionMode_isDisabled() {

        runOnActivity { activity ->

            val input =
                createSecureEditText(
                    activity
                )

            val callback =
                input.customSelectionActionModeCallback

            assertNotNull(
                "Custom selection action callback should exist",
                callback
            )

            val created =
                callback!!.onCreateActionMode(
                    null,
                    null
                )

            assertFalse(
                "Selection action mode must not be created",
                created
            )
        }
    }

    // =============================================================
    // ACTION MODE PREPARE
    // =============================================================

    @Test
    fun contextualSelectionPrepare_isRejected() {

        runOnActivity { activity ->

            val input =
                createSecureEditText(
                    activity
                )

            val callback =
                input.customSelectionActionModeCallback

            assertNotNull(
                callback
            )

            val prepared =
                callback!!.onPrepareActionMode(
                    null,
                    null
                )

            assertFalse(
                "Selection action mode preparation must be rejected",
                prepared
            )
        }
    }

    // =============================================================
    // ACTION ITEM CLICK
    // =============================================================

    @Test
    fun contextualSelectionItemClick_isRejected() {

        runOnActivity { activity ->

            val input =
                createSecureEditText(
                    activity
                )

            val callback =
                input.customSelectionActionModeCallback

            assertNotNull(
                callback
            )

            val handled =
                callback!!.onActionItemClicked(
                    null,
                    null
                )

            assertFalse(
                "Contextual selection actions must not be handled",
                handled
            )
        }
    }

    // =============================================================
    // EMPTY INPUT STILL MASKS SAFELY
    // =============================================================

    @Test
    fun emptyInput_doesNotExposeAnything() {

        runOnActivity { activity ->

            val input =
                createSecureEditText(
                    activity
                )

            input.setText(
                ""
            )

            val transformed =
                input.transformationMethod
                    .getTransformation(
                        input.text,
                        input
                    )
                    .toString()

            assertEquals(
                "",
                transformed
            )
        }
    }
}