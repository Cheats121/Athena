package com.athena.j.athena

import java.security.SecureRandom

object PasswordGenerator {
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?/"
    private val secureRandom = SecureRandom()
    private fun String.secureRandomChar(): Char {
        return this[secureRandom.nextInt(this.length)]
    }
    fun generate(minLength: Int = 12, maxLength: Int = 25): String {
        val allCharacters = LOWERCASE + UPPERCASE + DIGITS + SYMBOLS
        val length = secureRandom.nextInt(maxLength - minLength + 1) + minLength
        val password = StringBuilder(length)
        password.append(LOWERCASE.secureRandomChar())
        password.append(UPPERCASE.secureRandomChar())
        password.append(DIGITS.secureRandomChar())
        password.append(SYMBOLS.secureRandomChar())
        repeat(length - 4) {
            password.append(allCharacters.secureRandomChar())
        }
        return secureShuffle(password)
    }
    private fun secureShuffle(password: StringBuilder): String {
        val characters = password.toString().toCharArray()
        for (i in characters.size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val temporary = characters[i]
            characters[i] = characters[j]
            characters[j] = temporary
        }
        return String(characters)
    }
}