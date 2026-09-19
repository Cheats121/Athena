package com.athena.j.athena

import android.content.Context
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimeoutManagerInstrumentedTest {

    private val context: Context
        get() =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

    private var scenario:
            ActivityScenario<MainActivity>? =
        null

    @Before
    fun setup() {

        VaultRuntimeSession.clear()

        TimeoutManager.clear()

        TimeoutManager.setTimeoutDurationForTesting(
            300L
        )

        scenario =
            ActivityScenario.launch(
                MainActivity::class.java
            )

        scenario!!
            .moveToState(
                Lifecycle.State.RESUMED
            )
    }

    @After
    fun cleanup() {

        try {
            TimeoutManager.clear()
        } catch (_: Exception) {
        }

        try {
            TimeoutManager.resetTimeoutDurationForTesting()
        } catch (_: Exception) {
        }

        try {
            VaultRuntimeSession.clear()
        } catch (_: Exception) {
        }

        try {
            scenario?.close()
        } catch (_: Exception) {
        }

        scenario =
            null
    }

    private fun runOnActivity(
        action: (
            MainActivity
        ) -> Unit
    ) {

        val activeScenario =
            scenario
                ?: throw IllegalStateException(
                    "ActivityScenario not initialized"
                )

        activeScenario.onActivity { activity ->

            action(
                activity
            )
        }
    }

    private fun unlockRuntimeSession() {

        val uri =
            Uri.parse(
                "content://athena/timeout-test"
            )

        val dek =
            ByteArray(
                32
            ) {
                0x11
            }

        VaultRuntimeSession.setSession(
            uri,
            dek
        )

        dek.fill(0)
    }

    @Test
    fun resetTimeout_keepsSessionUnlockedBeforeDeadline() {

        unlockRuntimeSession()

        runOnActivity { activity ->

            TimeoutManager.resetTimeout(
                activity
            )
        }

        Thread.sleep(
            150L
        )

        assertTrue(
            "Session should remain unlocked before timeout expires",
            VaultRuntimeSession.isUnlocked()
        )
    }

    @Test
    fun foregroundInactivity_locksVaultAfterTimeout() {

        unlockRuntimeSession()

        runOnActivity { activity ->

            TimeoutManager.resetTimeout(
                activity
            )
        }

        Thread.sleep(
            700L
        )

        assertFalse(
            "Foreground inactivity should destroy runtime session",
            VaultRuntimeSession.isUnlocked()
        )

        assertNull(
            VaultRuntimeSession.getVaultDek()
        )
    }

    @Test
    fun userInteraction_resetsCountdown() {

        unlockRuntimeSession()

        runOnActivity { activity ->

            TimeoutManager.resetTimeout(
                activity
            )
        }

        Thread.sleep(
            200L
        )

        runOnActivity { activity ->

            TimeoutManager.resetTimeout(
                activity
            )
        }

        Thread.sleep(
            200L
        )

        assertTrue(
            "Second interaction should extend the timeout window",
            VaultRuntimeSession.isUnlocked()
        )

        Thread.sleep(
            300L
        )

        assertFalse(
            "Session should eventually lock after no further interaction",
            VaultRuntimeSession.isUnlocked()
        )
    }

    @Test
    fun stopTimer_doesNotDisableTimeout() {

        unlockRuntimeSession()

        runOnActivity { activity ->

            TimeoutManager.resetTimeout(
                activity
            )
        }

        TimeoutManager.stopTimer()

        Thread.sleep(
            700L
        )

        assertFalse(
            "Backgrounding must not disable inactivity locking",
            VaultRuntimeSession.isUnlocked()
        )
    }

    @Test
    fun backgroundTimeout_destroysDek() {

        unlockRuntimeSession()

        runOnActivity { activity ->

            TimeoutManager.resetTimeout(
                activity
            )
        }

        TimeoutManager.stopTimer()

        Thread.sleep(
            700L
        )

        assertNull(
            "Background timeout must destroy DEK",
            VaultRuntimeSession.getVaultDek()
        )

        assertFalse(
            VaultRuntimeSession.isUnlocked()
        )
    }

    @Test
    fun clear_cancelsScheduledTimeoutState() {

        unlockRuntimeSession()

        runOnActivity { activity ->

            TimeoutManager.resetTimeout(
                activity
            )
        }

        TimeoutManager.clear()

        Thread.sleep(
            500L
        )

        assertTrue(
            "Clearing TimeoutManager state should cancel pending timeout runnable",
            VaultRuntimeSession.isUnlocked()
        )
    }

    @Test
    fun resetTimeout_whenNoVaultUnlocked_doesNotCreateSession() {

        VaultRuntimeSession.clear()

        runOnActivity { activity ->

            TimeoutManager.resetTimeout(
                activity
            )
        }

        Thread.sleep(
            400L
        )

        assertFalse(
            VaultRuntimeSession.isUnlocked()
        )

        assertNull(
            VaultRuntimeSession.getVaultDek()
        )
    }

    @Test
    fun timeoutDurationOverride_isApplied() {

        TimeoutManager.setTimeoutDurationForTesting(
            1234L
        )

        assertEquals(
            1234L,
            TimeoutManager.getTimeoutDurationMs()
        )
    }
}