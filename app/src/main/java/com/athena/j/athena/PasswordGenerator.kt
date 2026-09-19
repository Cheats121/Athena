package com.athena.j.athena

import java.security.SecureRandom


/**
 * Generates strong random passwords using SecureRandom.
 *
 * Each password contains at least one lowercase letter,
 * uppercase letter, digit, and symbol.
 */
object PasswordGenerator {

    // =============================================================
    // CHARACTER SETS
    // =============================================================

    private const val LOWERCASE =
        "abcdefghijklmnopqrstuvwxyz"

    private const val UPPERCASE =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private const val DIGITS =
        "0123456789"

    private const val SYMBOLS =
        "!@#$%^&*()-_=+[]{}|;:,.<>?/"

    // =============================================================
    // RANDOM SOURCE
    // =============================================================

    private val secureRandom =
        SecureRandom()

    // =============================================================
    // RANDOM CHARACTER
    // =============================================================

    private fun String.secureRandomChar(): Char {

        return this[
            secureRandom.nextInt(
                this.length
            )
        ]
    }

    // =============================================================
    // GENERATE PASSWORD
    // =============================================================

    fun generate(
        minLength: Int = 12,
        maxLength: Int = 25
    ): String {

        val allCharacters =
            LOWERCASE +
                    UPPERCASE +
                    DIGITS +
                    SYMBOLS

        // Choose password length securely.
        val length =
            secureRandom.nextInt(
                maxLength - minLength + 1
            ) + minLength

        val password =
            StringBuilder(
                length
            )

        // Guarantee at least one character from each category.
        password.append(
            LOWERCASE.secureRandomChar()
        )

        password.append(
            UPPERCASE.secureRandomChar()
        )

        password.append(
            DIGITS.secureRandomChar()
        )

        password.append(
            SYMBOLS.secureRandomChar()
        )

        // Fill remaining characters securely.
        repeat(
            length - 4
        ) {

            password.append(
                allCharacters.secureRandomChar()
            )
        }

        // Randomize character positions.
        return secureShuffle(
            password
        )
    }

    // =============================================================
    // SECURE SHUFFLE
    // =============================================================

    private fun secureShuffle(
        password: StringBuilder
    ): String {

        val characters =
            password
                .toString()
                .toCharArray()

        for (
        i in characters.size - 1 downTo 1
        ) {

            val j =
                secureRandom.nextInt(
                    i + 1
                )

            val temporary =
                characters[i]

            characters[i] =
                characters[j]

            characters[j] =
                temporary
        }

        return String(
            characters
        )
    }
}