package com.athena.j.athena

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaseSecureActivityInstrumentedTest {

    // =============================================================
    // CONSTANTS
    // =============================================================

    private companion object {

        /*
         * Exactly 14 characters.
         *
         * Contains:
         * - uppercase
         * - lowercase
         * - digit
         * - symbol
         */
        const val VALID_PASSWORD =
            "Aa1!bcdefghijk"

        const val WEAK_PREDICTABLE_PASSWORD =
            "PasswordPassword1!"

        const val STRONG_PASSWORD =
            "N7!vQ2@kL9#xR4\$mT8^pW6&z"
    }

    // =============================================================
    // TEST STATE
    // =============================================================

    private var scenario:
            ActivityScenario<TestBaseSecureActivity>? =
        null

    // =============================================================
    // SETUP
    // =============================================================

    @Before
    fun setup() {

        scenario =
            ActivityScenario.launch(
                TestBaseSecureActivity::class.java
            )

        scenario!!
            .moveToState(
                Lifecycle.State.RESUMED
            )

        scenario!!.onActivity { activity ->

            activity.showCreatePasswordDialog()
        }

        InstrumentationRegistry
            .getInstrumentation()
            .waitForIdleSync()
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

    private fun dialogView(
        id: Int
    ) =
        onView(
            withId(
                id
            )
        )
            .inRoot(
                isDialog()
            )

    private fun enterPassword(
        password: String
    ) {

        dialogView(
            R.id.newMasterPasswordInput
        )
            .perform(
                replaceText(
                    password
                )
            )
    }

    private fun enterConfirmation(
        password: String
    ) {

        dialogView(
            R.id.confirmMasterPasswordInput
        )
            .perform(
                replaceText(
                    password
                )
            )
    }

    private fun readPasswordLength(): Int {

        var length =
            -1

        dialogView(
            R.id.newMasterPasswordInput
        )
            .check { view, noViewFoundException ->

                if (
                    noViewFoundException != null
                ) {

                    throw noViewFoundException
                }

                val input =
                    view as SecureEditText

                length =
                    input
                        .text
                        ?.length
                        ?: 0
            }

        return length
    }

    private fun readStrengthProgress(): Int {

        var progress =
            -1

        dialogView(
            R.id.passwordStrengthBar
        )
            .check { view, noViewFoundException ->

                if (
                    noViewFoundException != null
                ) {

                    throw noViewFoundException
                }

                val progressBar =
                    view as android.widget.ProgressBar

                progress =
                    progressBar.progress
            }

        return progress
    }

    private fun readStrengthMax(): Int {

        var max =
            -1

        dialogView(
            R.id.passwordStrengthBar
        )
            .check { view, noViewFoundException ->

                if (
                    noViewFoundException != null
                ) {

                    throw noViewFoundException
                }

                val progressBar =
                    view as android.widget.ProgressBar

                max =
                    progressBar.max
            }

        return max
    }

    private fun readStrengthText(): String {

        var value =
            ""

        dialogView(
            R.id.passwordStrengthText
        )
            .check { view, noViewFoundException ->

                if (
                    noViewFoundException != null
                ) {

                    throw noViewFoundException
                }

                value =
                    (view as android.widget.TextView)
                        .text
                        .toString()
            }

        return value
    }

    // =============================================================
    // INITIAL STATE
    // =============================================================

    @Test
    fun initialState_createButtonDisabled() {

        dialogView(
            R.id.createButton
        )
            .check(
                matches(
                    not(
                        isEnabled()
                    )
                )
            )
    }

    @Test
    fun initialState_strengthMeterIsEmpty() {

        assertEquals(
            0,
            readStrengthProgress()
        )

        dialogView(
            R.id.passwordStrengthText
        )
            .check(
                matches(
                    withText(
                        "Strength: —"
                    )
                )
            )
    }

    @Test
    fun strengthMeter_hasFiveLevels() {

        assertEquals(
            5,
            readStrengthMax()
        )
    }

    // =============================================================
    // MINIMUM LENGTH
    // =============================================================

    @Test
    fun passwordShorterThan14Characters_disablesCreate() {

        val password =
            "Aa1!abcdefgh"

        enterPassword(
            password
        )

        enterConfirmation(
            password
        )

        dialogView(
            R.id.createButton
        )
            .check(
                matches(
                    not(
                        isEnabled()
                    )
                )
            )
    }

    @Test
    fun passwordExactly14Characters_canEnableCreate() {

        assertEquals(
            14,
            VALID_PASSWORD.length
        )

        enterPassword(
            VALID_PASSWORD
        )

        enterConfirmation(
            VALID_PASSWORD
        )

        dialogView(
            R.id.createButton
        )
            .check(
                matches(
                    isEnabled()
                )
            )
    }

    // =============================================================
    // MAXIMUM LENGTH
    // =============================================================

    @Test
    fun passwordLongerThan128Characters_isCappedAt128() {

        val longPassword =
            buildString {

                append(
                    "Aa1!"
                )

                repeat(
                    146
                ) {

                    append(
                        'x'
                    )
                }
            }

        assertTrue(
            longPassword.length > 128
        )

        enterPassword(
            longPassword
        )

        assertEquals(
            "Password input should be capped at 128 characters",
            128,
            readPasswordLength()
        )
    }

    // =============================================================
    // UPPERCASE
    // =============================================================

    @Test
    fun missingUppercase_disablesCreate() {

        val password =
            "aa1!bcdefghijk"

        enterPassword(
            password
        )

        enterConfirmation(
            password
        )

        dialogView(
            R.id.createButton
        )
            .check(
                matches(
                    not(
                        isEnabled()
                    )
                )
            )
    }

    // =============================================================
    // LOWERCASE
    // =============================================================

    @Test
    fun missingLowercase_disablesCreate() {

        val password =
            "AA1!BCDEFGHIJK"

        enterPassword(
            password
        )

        enterConfirmation(
            password
        )

        dialogView(
            R.id.createButton
        )
            .check(
                matches(
                    not(
                        isEnabled()
                    )
                )
            )
    }

    // =============================================================
    // DIGIT
    // =============================================================

    @Test
    fun missingDigit_disablesCreate() {

        val password =
            "Aa!bcdefghijkl"

        enterPassword(
            password
        )

        enterConfirmation(
            password
        )

        dialogView(
            R.id.createButton
        )
            .check(
                matches(
                    not(
                        isEnabled()
                    )
                )
            )
    }

    // =============================================================
    // SYMBOL
    // =============================================================

    @Test
    fun missingSymbol_disablesCreate() {

        val password =
            "Aa1bcdefghijkl"

        enterPassword(
            password
        )

        enterConfirmation(
            password
        )

        dialogView(
            R.id.createButton
        )
            .check(
                matches(
                    not(
                        isEnabled()
                    )
                )
            )
    }

    @Test
    fun whitespaceDoesNotCountAsSymbol() {

        val password =
            "Aa1 bcdefghijkl"

        enterPassword(
            password
        )

        enterConfirmation(
            password
        )

        dialogView(
            R.id.createButton
        )
            .check(
                matches(
                    not(
                        isEnabled()
                    )
                )
            )
    }

    // =============================================================
    // CONFIRMATION
    // =============================================================

    @Test
    fun validPassword_withoutConfirmation_disablesCreate() {

        enterPassword(
            VALID_PASSWORD
        )

        dialogView(
            R.id.createButton
        )
            .check(
                matches(
                    not(
                        isEnabled()
                    )
                )
            )
    }

    @Test
    fun confirmationMismatch_disablesCreate() {

        enterPassword(
            VALID_PASSWORD
        )

        enterConfirmation(
            "Aa1!differentPassword"
        )

        dialogView(
            R.id.createButton
        )
            .check(
                matches(
                    not(
                        isEnabled()
                    )
                )
            )
    }

    @Test
    fun matchingValidConfirmation_enablesCreate() {

        enterPassword(
            VALID_PASSWORD
        )

        enterConfirmation(
            VALID_PASSWORD
        )

        dialogView(
            R.id.createButton
        )
            .check(
                matches(
                    isEnabled()
                )
            )
    }

    // =============================================================
    // CALLBACK
    // =============================================================

    @Test
    fun validPassword_createPassesExactPasswordToCallback() {

        enterPassword(
            VALID_PASSWORD
        )

        enterConfirmation(
            VALID_PASSWORD
        )

        dialogView(
            R.id.createButton
        )
            .perform(
                click()
            )

        InstrumentationRegistry
            .getInstrumentation()
            .waitForIdleSync()

        scenario!!.onActivity { activity ->

            assertTrue(
                "Password callback should be invoked",
                activity.passwordCallbackInvoked
            )

            assertEquals(
                "Callback must receive password exactly as entered",
                VALID_PASSWORD,
                activity.receivedPassword
            )
        }
    }

    // =============================================================
    // ZXCVBN METER
    // =============================================================

    @Test
    fun predictablePassword_updatesStrengthMeter() {

        enterPassword(
            WEAK_PREDICTABLE_PASSWORD
        )

        val progress =
            readStrengthProgress()

        val text =
            readStrengthText()

        assertTrue(
            "Non-empty password should produce a non-zero strength score",
            progress > 0
        )

        assertTrue(
            "Strength progress must remain within configured range",
            progress in 1..5
        )

        assertNotEquals(
            "Strength: —",
            text
        )
    }

    @Test
    fun strongPassword_updatesStrengthMeter() {

        enterPassword(
            STRONG_PASSWORD
        )

        val progress =
            readStrengthProgress()

        assertTrue(
            "Strong password should produce strength feedback",
            progress > 0
        )

        assertNotEquals(
            "Strength: —",
            readStrengthText()
        )
    }

    // =============================================================
    // STRENGTH DOES NOT GATE CREATION
    // =============================================================

    @Test
    fun zxcvbnStrength_doesNotGateValidPasswordCreation() {

        /*
         * This password is intentionally predictable, but it
         * satisfies Athena's deterministic hard requirements.
         *
         * zxcvbn is advisory only.
         */
        enterPassword(
            WEAK_PREDICTABLE_PASSWORD
        )

        enterConfirmation(
            WEAK_PREDICTABLE_PASSWORD
        )

        assertTrue(
            readStrengthProgress() > 0
        )

        dialogView(
            R.id.createButton
        )
            .check(
                matches(
                    isEnabled()
                )
            )
    }
}