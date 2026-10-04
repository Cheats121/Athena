package com.athena.j.athena

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class RecoveryKeyStoreInstrumentedTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        const val VAULT_ID_BYTES = 16
        const val RECOVERY_KEY_BYTES = 32
    }

    private val secureRandom = SecureRandom()

    @Before
    fun setup() {
        RecoveryKeyStore.clearAll(context)
    }

    @After
    fun cleanup() {
        RecoveryKeyStore.clearAll(context)
    }

    private fun randomVaultId(): ByteArray {
        return ByteArray(VAULT_ID_BYTES).also {
            secureRandom.nextBytes(it)
        }
    }

    private fun randomRecoveryKey(): ByteArray {
        return ByteArray(RECOVERY_KEY_BYTES).also {
            secureRandom.nextBytes(it)
        }
    }

    @Test
    fun saveThenLoad_returnsOriginalRecoveryKey() {
        val vaultId = randomVaultId()
        val recoveryKey = randomRecoveryKey()

        try {
            val stored = RecoveryKeyStore.save(
                context = context,
                vaultId = vaultId,
                recoveryKey = recoveryKey
            )

            assertTrue(
                "Recovery key should store successfully",
                stored
            )

            val loaded = RecoveryKeyStore.load(
                context = context,
                vaultId = vaultId
            )

            assertNotNull(
                "Stored recovery key should load successfully",
                loaded
            )

            try {
                assertArrayEquals(
                    "Loaded recovery key must equal original key",
                    recoveryKey,
                    loaded
                )
            } finally {
                loaded?.fill(0)
            }

        } finally {
            vaultId.fill(0)
            recoveryKey.fill(0)
        }
    }

    @Test
    fun contains_returnsFalseBeforeSave() {
        val vaultId = randomVaultId()

        try {
            assertFalse(
                RecoveryKeyStore.contains(
                    context = context,
                    vaultId = vaultId
                )
            )
        } finally {
            vaultId.fill(0)
        }
    }

    @Test
    fun contains_returnsTrueAfterSave() {
        val vaultId = randomVaultId()
        val recoveryKey = randomRecoveryKey()

        try {
            assertTrue(
                RecoveryKeyStore.save(
                    context = context,
                    vaultId = vaultId,
                    recoveryKey = recoveryKey
                )
            )

            assertTrue(
                RecoveryKeyStore.contains(
                    context = context,
                    vaultId = vaultId
                )
            )

        } finally {
            vaultId.fill(0)
            recoveryKey.fill(0)
        }
    }

    @Test
    fun clear_removesOnlyRequestedVault() {
        val firstVaultId = randomVaultId()
        val secondVaultId = randomVaultId()
        val firstRecoveryKey = randomRecoveryKey()
        val secondRecoveryKey = randomRecoveryKey()

        try {
            assertTrue(
                RecoveryKeyStore.save(
                    context = context,
                    vaultId = firstVaultId,
                    recoveryKey = firstRecoveryKey
                )
            )

            assertTrue(
                RecoveryKeyStore.save(
                    context = context,
                    vaultId = secondVaultId,
                    recoveryKey = secondRecoveryKey
                )
            )

            RecoveryKeyStore.clear(
                context = context,
                vaultId = firstVaultId
            )

            assertFalse(
                RecoveryKeyStore.contains(
                    context = context,
                    vaultId = firstVaultId
                )
            )

            assertNull(
                RecoveryKeyStore.load(
                    context = context,
                    vaultId = firstVaultId
                )
            )

            assertTrue(
                "Clearing one vault must not remove another vault's key",
                RecoveryKeyStore.contains(
                    context = context,
                    vaultId = secondVaultId
                )
            )

            val secondLoaded = RecoveryKeyStore.load(
                context = context,
                vaultId = secondVaultId
            )

            assertNotNull(secondLoaded)

            try {
                assertArrayEquals(
                    secondRecoveryKey,
                    secondLoaded
                )
            } finally {
                secondLoaded?.fill(0)
            }

        } finally {
            firstVaultId.fill(0)
            secondVaultId.fill(0)
            firstRecoveryKey.fill(0)
            secondRecoveryKey.fill(0)
        }
    }

    @Test
    fun clearAll_removesAllRecoveryKeys() {
        val firstVaultId = randomVaultId()
        val secondVaultId = randomVaultId()
        val firstRecoveryKey = randomRecoveryKey()
        val secondRecoveryKey = randomRecoveryKey()

        try {
            assertTrue(
                RecoveryKeyStore.save(
                    context = context,
                    vaultId = firstVaultId,
                    recoveryKey = firstRecoveryKey
                )
            )

            assertTrue(
                RecoveryKeyStore.save(
                    context = context,
                    vaultId = secondVaultId,
                    recoveryKey = secondRecoveryKey
                )
            )

            RecoveryKeyStore.clearAll(context)

            assertFalse(
                RecoveryKeyStore.contains(
                    context = context,
                    vaultId = firstVaultId
                )
            )

            assertFalse(
                RecoveryKeyStore.contains(
                    context = context,
                    vaultId = secondVaultId
                )
            )

            assertNull(
                RecoveryKeyStore.load(
                    context = context,
                    vaultId = firstVaultId
                )
            )

            assertNull(
                RecoveryKeyStore.load(
                    context = context,
                    vaultId = secondVaultId
                )
            )

        } finally {
            firstVaultId.fill(0)
            secondVaultId.fill(0)
            firstRecoveryKey.fill(0)
            secondRecoveryKey.fill(0)
        }
    }

    @Test
    fun differentVaultId_cannotLoadRecoveryKey() {
        val correctVaultId = randomVaultId()
        val wrongVaultId = randomVaultId()
        val recoveryKey = randomRecoveryKey()

        try {
            assertTrue(
                RecoveryKeyStore.save(
                    context = context,
                    vaultId = correctVaultId,
                    recoveryKey = recoveryKey
                )
            )

            assertNull(
                "Recovery key must not be returned for another vault ID",
                RecoveryKeyStore.load(
                    context = context,
                    vaultId = wrongVaultId
                )
            )

        } finally {
            correctVaultId.fill(0)
            wrongVaultId.fill(0)
            recoveryKey.fill(0)
        }
    }

    @Test
    fun load_returnsDefensiveCopy() {
        val vaultId = randomVaultId()
        val recoveryKey = randomRecoveryKey()

        try {
            assertTrue(
                RecoveryKeyStore.save(
                    context = context,
                    vaultId = vaultId,
                    recoveryKey = recoveryKey
                )
            )

            val first = RecoveryKeyStore.load(
                context = context,
                vaultId = vaultId
            )

            assertNotNull(first)

            first!!.fill(0)

            val second = RecoveryKeyStore.load(
                context = context,
                vaultId = vaultId
            )

            assertNotNull(second)

            try {
                assertArrayEquals(
                    "Modifying one loaded copy must not corrupt stored key",
                    recoveryKey,
                    second
                )
            } finally {
                second?.fill(0)
            }

        } finally {
            vaultId.fill(0)
            recoveryKey.fill(0)
        }
    }

    @Test
    fun save_doesNotModifyCallerRecoveryKey() {
        val vaultId = randomVaultId()
        val recoveryKey = randomRecoveryKey()
        val expected = recoveryKey.copyOf()

        try {
            assertTrue(
                RecoveryKeyStore.save(
                    context = context,
                    vaultId = vaultId,
                    recoveryKey = recoveryKey
                )
            )

            assertArrayEquals(
                "RecoveryKeyStore must not modify caller-owned recovery key",
                expected,
                recoveryKey
            )

        } finally {
            vaultId.fill(0)
            recoveryKey.fill(0)
            expected.fill(0)
        }
    }

    @Test
    fun load_invalidVaultIdSize_returnsNull() {
        val invalidVaultId = ByteArray(15)

        assertNull(
            RecoveryKeyStore.load(
                context = context,
                vaultId = invalidVaultId
            )
        )

        invalidVaultId.fill(0)
    }

    @Test
    fun contains_invalidVaultIdSize_returnsFalse() {
        val invalidVaultId = ByteArray(17)

        assertFalse(
            RecoveryKeyStore.contains(
                context = context,
                vaultId = invalidVaultId
            )
        )

        invalidVaultId.fill(0)
    }

    @Test
    fun savingNewRecoveryKey_replacesPreviousKeyForSameVault() {
        val vaultId = randomVaultId()
        val firstRecoveryKey = randomRecoveryKey()
        val secondRecoveryKey = randomRecoveryKey()

        try {
            assertTrue(
                RecoveryKeyStore.save(
                    context = context,
                    vaultId = vaultId,
                    recoveryKey = firstRecoveryKey
                )
            )

            assertTrue(
                RecoveryKeyStore.save(
                    context = context,
                    vaultId = vaultId,
                    recoveryKey = secondRecoveryKey
                )
            )

            val loaded = RecoveryKeyStore.load(
                context = context,
                vaultId = vaultId
            )

            assertNotNull(loaded)

            try {
                assertArrayEquals(
                    "Newest recovery key should replace old key",
                    secondRecoveryKey,
                    loaded
                )

                assertFalse(
                    "Old recovery key must no longer be stored",
                    firstRecoveryKey.contentEquals(loaded)
                )

            } finally {
                loaded?.fill(0)
            }

        } finally {
            vaultId.fill(0)
            firstRecoveryKey.fill(0)
            secondRecoveryKey.fill(0)
        }
    }
}