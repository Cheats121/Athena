package com.athena.j.athena

import org.junit.Assert.assertEquals
import org.junit.Test

class InstantPasswordTransformationMethodTest {

    @Test
    fun nullInput_returnsEmptyString() {

        val method =
            InstantPasswordTransformationMethod()

        val transformed =
            method.getTransformation(
                null,
                null
            )

        assertEquals(
            "",
            transformed.toString()
        )
    }

    @Test
    fun emptyInput_returnsEmptyString() {

        val method =
            InstantPasswordTransformationMethod()

        val transformed =
            method.getTransformation(
                "",
                null
            )

        assertEquals(
            "",
            transformed.toString()
        )
    }

    @Test
    fun singleCharacter_isMaskedImmediately() {

        val method =
            InstantPasswordTransformationMethod()

        val transformed =
            method.getTransformation(
                "A",
                null
            )

        assertEquals(
            "•",
            transformed.toString()
        )
    }

    @Test
    fun allCharacters_areMasked() {

        val method =
            InstantPasswordTransformationMethod()

        val transformed =
            method.getTransformation(
                "Hunter2!Test",
                null
            )

        assertEquals(
            "••••••••••••",
            transformed.toString()
        )
    }

    @Test
    fun transformedLength_matchesOriginalLength() {

        val method =
            InstantPasswordTransformationMethod()

        val input =
            "VerySecret123!"

        val transformed =
            method.getTransformation(
                input,
                null
            )

        assertEquals(
            input.length,
            transformed.length
        )
    }

    @Test
    fun individualCharacterAccess_returnsBullet() {

        val method =
            InstantPasswordTransformationMethod()

        val transformed =
            method.getTransformation(
                "secret",
                null
            )

        for (
        index in 0 until transformed.length
        ) {

            assertEquals(
                '•',
                transformed[index]
            )
        }
    }

    @Test
    fun subsequence_isAlsoMasked() {

        val method =
            InstantPasswordTransformationMethod()

        val transformed =
            method.getTransformation(
                "abcdefgh",
                null
            )

        val subsection =
            transformed.subSequence(
                2,
                6
            )

        assertEquals(
            "••••",
            subsection.toString()
        )
    }

    @Test
    fun plaintext_isNeverReturnedByToString() {

        val method =
            InstantPasswordTransformationMethod()

        val secret =
            "SuperSecretPassword123!"

        val transformed =
            method.getTransformation(
                secret,
                null
            )

        assertEquals(
            "•".repeat(secret.length),
            transformed.toString()
        )
    }
}