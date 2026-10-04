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

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setup() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun cleanup() {
        try {
            scenario?.close()
        } catch (_: Exception) {}

        scenario = null
    }

    private fun runOnActivity(action: (MainActivity) -> Unit) {
        val activeScenario = scenario
            ?: throw IllegalStateException("ActivityScenario is not initialized")

        activeScenario.onActivity { activity ->
            action(activity)
        }
    }

    private fun createSecureEditText(activity: MainActivity): SecureEditText {
        return SecureEditText(activity)
    }

    @Test
    fun longClick_isDisabled() {
        runOnActivity { activity ->
            val input = createSecureEditText(activity)

            assertFalse(
                "SecureEditText must disable long-click behavior",
                input.isLongClickable
            )
        }
    }

    @Test
    fun textSelection_isDisabled() {
        runOnActivity { activity ->
            val input = createSecureEditText(activity)

            assertFalse(
                "SecureEditText must not allow text selection",
                input.isTextSelectable
            )
        }
    }

    @Test
    fun inputType_isPassword() {
        runOnActivity { activity ->
            val input = createSecureEditText(activity)

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

    @Test
    fun transformationMethod_isInstantPasswordMasking() {
        runOnActivity { activity ->
            val input = createSecureEditText(activity)

            assertTrue(
                "SecureEditText must use InstantPasswordTransformationMethod",
                input.transformationMethod is InstantPasswordTransformationMethod
            )
        }
    }

    @Test
    fun enteredText_isImmediatelyMasked() {
        runOnActivity { activity ->
            val input = createSecureEditText(activity)

            input.setText("Secret123!")

            val transformed = input.transformationMethod
                .getTransformation(input.text, input)
                .toString()

            assertEquals(
                "•".repeat("Secret123!".length),
                transformed
            )
        }
    }

    @Test
    fun copyContextAction_isRejected() {
        runOnActivity { activity ->
            val input = createSecureEditText(activity)

            input.setText("secret")

            val result = input.onTextContextMenuItem(android.R.id.copy)

            assertFalse(
                "Copy context action must be rejected",
                result
            )
        }
    }

    @Test
    fun cutContextAction_isRejected() {
        runOnActivity { activity ->
            val input = createSecureEditText(activity)

            input.setText("secret")

            val result = input.onTextContextMenuItem(android.R.id.cut)

            assertFalse(
                "Cut context action must be rejected",
                result
            )
        }
    }

    @Test
    fun pasteContextAction_isRejected() {
        runOnActivity { activity ->
            val input = createSecureEditText(activity)

            val result = input.onTextContextMenuItem(android.R.id.paste)

            assertFalse(
                "Paste context action must be rejected",
                result
            )
        }
    }

    @Test
    fun selectAllContextAction_isRejected() {
        runOnActivity { activity ->
            val input = createSecureEditText(activity)

            input.setText("secret")

            val result = input.onTextContextMenuItem(android.R.id.selectAll)

            assertFalse(
                "Select-all context action must be rejected",
                result
            )
        }
    }

    @Test
    fun contextualSelectionActionMode_isDisabled() {
        runOnActivity { activity ->
            val input = createSecureEditText(activity)
            val callback = input.customSelectionActionModeCallback

            assertNotNull(
                "Custom selection action callback should exist",
                callback
            )

            val created = callback!!.onCreateActionMode(null, null)

            assertFalse(
                "Selection action mode must not be created",
                created
            )
        }
    }

    @Test
    fun contextualSelectionPrepare_isRejected() {
        runOnActivity { activity ->
            val input = createSecureEditText(activity)
            val callback = input.customSelectionActionModeCallback

            assertNotNull(callback)

            val prepared = callback!!.onPrepareActionMode(null, null)

            assertFalse(
                "Selection action mode preparation must be rejected",
                prepared
            )
        }
    }

    @Test
    fun contextualSelectionItemClick_isRejected() {
        runOnActivity { activity ->
            val input = createSecureEditText(activity)
            val callback = input.customSelectionActionModeCallback

            assertNotNull(callback)

            val handled = callback!!.onActionItemClicked(null, null)

            assertFalse(
                "Contextual selection actions must not be handled",
                handled
            )
        }
    }

    @Test
    fun emptyInput_doesNotExposeAnything() {
        runOnActivity { activity ->
            val input = createSecureEditText(activity)

            input.setText("")

            val transformed = input.transformationMethod
                .getTransformation(input.text, input)
                .toString()

            assertEquals("", transformed)
        }
    }
}