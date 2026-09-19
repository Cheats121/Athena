package com.athena.j.athena

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ClipboardUtilsInstrumentedTest {

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

        scenario!!
            .moveToState(
                Lifecycle.State.RESUMED
            )

        Thread.sleep(
            200L
        )

        clearClipboard()

        Thread.sleep(
            50L
        )
    }

    // =============================================================
    // CLEANUP
    // =============================================================

    @After
    fun cleanup() {

        try {

            scenario?.onActivity { activity ->

                ClipboardUtils.clearPendingSensitiveClipboard(
                    activity
                )
            }

        } catch (_: Exception) {
        }

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

    private fun clearClipboard() {

        runOnActivity { activity ->

            val clipboard =
                activity.getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

            try {

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.P
                ) {

                    if (
                        clipboard.hasPrimaryClip()
                    ) {

                        clipboard.clearPrimaryClip()
                    }

                } else {

                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "",
                            ""
                        )
                    )
                }

            } catch (_: Exception) {
            }
        }
    }

    private fun readClipboardText(): String? {

        var result: String? =
            null

        runOnActivity { activity ->

            val clipboard =
                activity.getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

            if (
                !clipboard.hasPrimaryClip()
            ) {

                result =
                    null

                return@runOnActivity
            }

            val clip =
                clipboard.primaryClip

            if (
                clip == null ||
                clip.itemCount <= 0
            ) {

                result =
                    null

                return@runOnActivity
            }

            result =
                clip
                    .getItemAt(
                        0
                    )
                    .coerceToText(
                        activity
                    )
                    ?.toString()
        }

        return result
    }

    private fun readClipboardLabel(): String? {

        var result: String? =
            null

        runOnActivity { activity ->

            val clipboard =
                activity.getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

            result =
                clipboard
                    .primaryClipDescription
                    ?.label
                    ?.toString()
        }

        return result
    }

    private fun isClipboardMarkedSensitive(): Boolean {

        var result =
            false

        runOnActivity { activity ->

            val clipboard =
                activity.getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

            val description =
                clipboard
                    .primaryClipDescription

            val extras =
                description
                    ?.extras

            result =
                extras
                    ?.getBoolean(
                        ClipDescription.EXTRA_IS_SENSITIVE,
                        false
                    )
                    ?: false
        }

        return result
    }

    // =============================================================
    // COPY
    // =============================================================

    @Test
    fun copySensitive_placesTextOnClipboard() {

        val secret =
            "SuperSecretPassword123!"

        runOnActivity { activity ->

            ClipboardUtils.copySensitive(
                context = activity,
                text = secret,
                clearAfterMs = 5_000L
            )
        }

        Thread.sleep(
            100L
        )

        assertEquals(
            "Sensitive text should be placed on clipboard",
            secret,
            readClipboardText()
        )
    }

    // =============================================================
    // CUSTOM LABEL
    // =============================================================

    @Test
    fun copySensitive_usesProvidedLabel() {

        val label =
            "Athena Password"

        runOnActivity { activity ->

            ClipboardUtils.copySensitive(
                context = activity,
                label = label,
                text = "secret",
                clearAfterMs = 5_000L
            )
        }

        Thread.sleep(
            100L
        )

        assertEquals(
            "Clipboard label should match supplied label",
            label,
            readClipboardLabel()
        )
    }

    // =============================================================
    // DEFAULT LABEL
    // =============================================================

    @Test
    fun copySensitive_defaultLabelPath_copiesSuccessfully() {

        runOnActivity { activity ->

            ClipboardUtils.copySensitive(
                context = activity,
                text = "secret",
                clearAfterMs = 5_000L
            )
        }

        Thread.sleep(
            100L
        )

        assertEquals(
            "Default-label copy path should place text on clipboard",
            "secret",
            readClipboardText()
        )
    }

    // =============================================================
    // EMPTY STRING
    // =============================================================

    @Test
    fun copySensitive_allowsEmptyString() {

        runOnActivity { activity ->

            ClipboardUtils.copySensitive(
                context = activity,
                text = "",
                clearAfterMs = 5_000L
            )
        }

        Thread.sleep(
            100L
        )

        assertEquals(
            "",
            readClipboardText()
        )
    }

    // =============================================================
    // REPLACE
    // =============================================================

    @Test
    fun secondCopy_replacesFirstClipboardValue() {

        runOnActivity { activity ->

            ClipboardUtils.copySensitive(
                context = activity,
                text = "first-secret",
                clearAfterMs = 5_000L
            )
        }

        Thread.sleep(
            100L
        )

        assertEquals(
            "first-secret",
            readClipboardText()
        )

        runOnActivity { activity ->

            ClipboardUtils.copySensitive(
                context = activity,
                text = "second-secret",
                clearAfterMs = 5_000L
            )
        }

        Thread.sleep(
            100L
        )

        assertEquals(
            "Second sensitive copy should replace first",
            "second-secret",
            readClipboardText()
        )
    }

    // =============================================================
    // CLEAR AFTER DELAY
    // =============================================================

    @Test
    fun copySensitive_clearsClipboardAfterConfiguredDelay() {

        val secret =
            "TemporarySecret"

        val latch =
            CountDownLatch(
                1
            )

        runOnActivity { activity ->

            ClipboardUtils.copySensitive(
                context = activity,
                text = secret,
                clearAfterMs = 300L,
                onCleared = {

                    latch.countDown()
                }
            )
        }

        Thread.sleep(
            100L
        )

        assertEquals(
            "Clipboard should contain secret before timeout",
            secret,
            readClipboardText()
        )

        val callbackTriggered =
            latch.await(
                3,
                TimeUnit.SECONDS
            )

        assertTrue(
            "Clipboard clear callback should execute",
            callbackTriggered
        )

        Thread.sleep(
            100L
        )

        val after =
            readClipboardText()

        assertTrue(
            "Clipboard should be empty after timeout",
            after.isNullOrEmpty()
        )
    }

    // =============================================================
    // CALLBACK
    // =============================================================

    @Test
    fun copySensitive_invokesOnClearedCallback() {

        val latch =
            CountDownLatch(
                1
            )

        runOnActivity { activity ->

            ClipboardUtils.copySensitive(
                context = activity,
                text = "secret",
                clearAfterMs = 200L,
                onCleared = {

                    latch.countDown()
                }
            )
        }

        val callbackTriggered =
            latch.await(
                3,
                TimeUnit.SECONDS
            )

        assertTrue(
            "onCleared callback should execute",
            callbackTriggered
        )
    }

    // =============================================================
    // CALLBACK NOT IMMEDIATE
    // =============================================================

    @Test
    fun onCleared_doesNotRunImmediately() {

        val latch =
            CountDownLatch(
                1
            )

        runOnActivity { activity ->

            ClipboardUtils.copySensitive(
                context = activity,
                text = "secret",
                clearAfterMs = 500L,
                onCleared = {

                    latch.countDown()
                }
            )
        }

        val ranTooEarly =
            latch.await(
                100L,
                TimeUnit.MILLISECONDS
            )

        assertFalse(
            "Clear callback must not run immediately",
            ranTooEarly
        )

        val eventuallyRan =
            latch.await(
                2,
                TimeUnit.SECONDS
            )

        assertTrue(
            "Clear callback should execute after configured delay",
            eventuallyRan
        )
    }

    // =============================================================
    // CLEAR WITHOUT CALLBACK
    // =============================================================

    @Test
    fun clipboardClearsWithoutOnClearedCallback() {

        runOnActivity { activity ->

            ClipboardUtils.copySensitive(
                context = activity,
                text = "secret",
                clearAfterMs = 200L
            )
        }

        Thread.sleep(
            100L
        )

        assertEquals(
            "secret",
            readClipboardText()
        )

        Thread.sleep(
            400L
        )

        val result =
            readClipboardText()

        assertTrue(
            "Clipboard should clear even without callback",
            result.isNullOrEmpty()
        )
    }

    // =============================================================
    // SENSITIVE FLAG
    // =============================================================

    @Test
    fun copySensitive_marksClipboardSensitive_onApi33AndAbove() {

        if (
            Build.VERSION.SDK_INT < 33
        ) {

            return
        }

        runOnActivity { activity ->

            ClipboardUtils.copySensitive(
                context = activity,
                text = "secret",
                clearAfterMs = 5_000L
            )
        }

        Thread.sleep(
            100L
        )

        assertTrue(
            "Clipboard should be marked sensitive on API 33+",
            isClipboardMarkedSensitive()
        )
    }
}