package com.athena.j.athena

import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class RecoveryKeyCodecTest {

    private companion object {
        const val RECOVERY_KEY_BYTES = 32
    }

    private val secureRandom = SecureRandom()

    private fun randomRecoveryKey(): ByteArray {
        return ByteArray(RECOVERY_KEY_BYTES).also {
            secureRandom.nextBytes(it)
        }
    }

    @Test
    fun encodeThenDecode_returnsOriginalKey() {
        val recoveryKey = randomRecoveryKey()

        try {
            val encoded = RecoveryKeyCodec.encode(recoveryKey)
            val decoded = RecoveryKeyCodec.decode(encoded)

            assertNotNull(decoded)

            try {
                assertArrayEquals(recoveryKey, decoded)
            } finally {
                decoded?.fill(0)
            }

        } finally {
            recoveryKey.fill(0)
        }
    }

    @Test
    fun decode_ignoresSpacesAndLineBreaks() {
        val recoveryKey = randomRecoveryKey()

        try {
            val encoded = RecoveryKeyCodec.encode(recoveryKey)
            val modified = encoded.replace(" ", "\n")
            val decoded = RecoveryKeyCodec.decode(modified)

            assertNotNull(decoded)

            try {
                assertArrayEquals(recoveryKey, decoded)
            } finally {
                decoded?.fill(0)
            }

        } finally {
            recoveryKey.fill(0)
        }
    }

    @Test
    fun decode_emptyInput_returnsNull() {
        assertNull(RecoveryKeyCodec.decode(""))
    }

    @Test
    fun decode_whitespaceOnly_returnsNull() {
        assertNull(RecoveryKeyCodec.decode("     \n\t"))
    }

    @Test
    fun decode_invalidCharacters_returnsNull() {
        assertNull(
            RecoveryKeyCodec.decode("%%%THIS-IS-NOT-VALID%%%")
        )
    }

    @Test
    fun decode_wrongLength_returnsNull() {
        val invalid = ByteArray(16) {
            0x42.toByte()
        }

        try {
            val encoded = android.util.Base64.encodeToString(
                invalid,
                android.util.Base64.URL_SAFE or
                        android.util.Base64.NO_WRAP or
                        android.util.Base64.NO_PADDING
            )

            assertNull(RecoveryKeyCodec.decode(encoded))

        } finally {
            invalid.fill(0)
        }
    }

    @Test
    fun encode_doesNotModifyInputKey() {
        val recoveryKey = randomRecoveryKey()
        val expected = recoveryKey.copyOf()

        try {
            RecoveryKeyCodec.encode(recoveryKey)

            assertArrayEquals(
                expected,
                recoveryKey
            )

        } finally {
            recoveryKey.fill(0)
            expected.fill(0)
        }
    }

    @Test
    fun encode_addsReadableGrouping() {
        val recoveryKey = ByteArray(RECOVERY_KEY_BYTES) {
            it.toByte()
        }

        try {
            val encoded = RecoveryKeyCodec.encode(recoveryKey)

            assertTrue(
                "Recovery code should contain grouping spaces",
                encoded.contains(" ")
            )

            val compact = encoded.replace(" ", "")

            assertEquals(
                "32-byte URL-safe Base64 without padding should be 43 characters",
                43,
                compact.length
            )

        } finally {
            recoveryKey.fill(0)
        }
    }
}