package com.athena.j.athena

import org.junit.Assert.*
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun generatedPassword_hasValidLength() {
        repeat(1000) {
            val password = PasswordGenerator.generate()

            assertTrue(password.length in 12..25)
        }
    }

    @Test
    fun generatedPassword_containsLowercase() {
        repeat(100) {
            val password = PasswordGenerator.generate()

            assertTrue(password.any { it.isLowerCase() })
        }
    }

    @Test
    fun generatedPassword_containsUppercase() {
        repeat(100) {
            val password = PasswordGenerator.generate()

            assertTrue(password.any { it.isUpperCase() })
        }
    }

    @Test
    fun generatedPassword_containsDigit() {
        repeat(100) {
            val password = PasswordGenerator.generate()

            assertTrue(password.any { it.isDigit() })
        }
    }

    @Test
    fun generatedPassword_containsSymbol() {
        repeat(100) {
            val password = PasswordGenerator.generate()

            assertTrue(password.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun generatedPasswords_areNotRepeated() {
        val passwords =
            (1..1000)
                .map { PasswordGenerator.generate() }

        assertEquals(
            passwords.size,
            passwords.toSet().size
        )
    }
}