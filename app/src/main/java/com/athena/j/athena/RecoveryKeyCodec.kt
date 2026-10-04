package com.athena.j.athena

import android.util.Base64

object RecoveryKeyCodec {
    private const val RECOVERY_KEY_BYTES = 32
    private const val GROUP_SIZE = 5
    fun encode(recoveryKey: ByteArray): String {
        require(recoveryKey.size == RECOVERY_KEY_BYTES) { "Invalid recovery key size" }
        val encoded = Base64.encodeToString(
            recoveryKey,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return encoded.chunked(GROUP_SIZE).joinToString(" ")
    }
    fun decode(recoveryCode: String): ByteArray? {
        val normalized = recoveryCode.filterNot { it.isWhitespace() }
        if (normalized.isEmpty()) return null
        return try {
            val decoded = Base64.decode(
                normalized,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            if (decoded.size != RECOVERY_KEY_BYTES) {
                decoded.fill(0)
                null
            } else {
                decoded
            }
        } catch (_: Exception) {
            null
        }
    }
}