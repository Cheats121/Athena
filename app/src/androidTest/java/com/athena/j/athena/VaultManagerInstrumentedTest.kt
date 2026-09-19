package com.athena.j.athena

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VaultManagerInstrumentedTest {

    // =============================================================
    // CONTEXT
    // =============================================================

    private val context: Context
        get() =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

    // =============================================================
    // TEST FILE TRACKING
    // =============================================================

    private val temporaryFiles =
        mutableListOf<File>()

    // =============================================================
    // HELPERS
    // =============================================================

    private fun createTempVaultUri(): Uri {

        val file =
            File(
                context.cacheDir,
                "test-vault-${System.nanoTime()}.json"
            )

        file.createNewFile()

        temporaryFiles.add(
            file
        )

        return Uri.fromFile(
            file
        )
    }

    private fun fileFromUri(
        uri: Uri
    ): File {

        val path =
            requireNotNull(
                uri.path
            ) {
                "Test URI has no file path"
            }

        return File(
            path
        )
    }

    private fun readVaultJson(
        uri: Uri
    ): JSONObject {

        val file =
            fileFromUri(
                uri
            )

        return JSONObject(
            file.readText()
        )
    }

    private fun writeVaultJson(
        uri: Uri,
        root: JSONObject
    ) {

        fileFromUri(
            uri
        ).writeText(
            root.toString()
        )
    }

    /**
     * Changes one Base64 character while keeping the value
     * syntactically valid Base64.
     */
    private fun mutateBase64(
        value: String
    ): String {

        require(
            value.isNotEmpty()
        )

        val replacement =
            if (
                value[0] == 'A'
            ) {
                'B'
            } else {
                'A'
            }

        return replacement +
                value.substring(
                    1
                )
    }

    // =============================================================
    // CLEANUP
    // =============================================================

    @After
    fun cleanup() {

        /*
         * Ensure no test leaves an unlocked DEK behind.
         */
        VaultRuntimeSession.clear()

        /*
         * Remove all temporary vault files.
         */
        temporaryFiles.forEach { file ->

            try {
                file.delete()
            } catch (_: Exception) {
            }
        }

        temporaryFiles.clear()
    }

    // =============================================================
    // CREATE / UNLOCK
    // =============================================================

    @Test
    fun createVault_correctPassword_unlocksSuccessfully() {

        val uri =
            createTempVaultUri()

        val password =
            "TestPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val dek =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNotNull(
            "Correct master password should return the vault DEK",
            dek
        )

        assertEquals(
            "Vault DEK must be 256 bits",
            32,
            dek!!.size
        )

        dek.fill(0)
    }

    // =============================================================
    // WRONG PASSWORD
    // =============================================================

    @Test
    fun unlockVault_wrongPassword_fails() {

        val uri =
            createTempVaultUri()

        VaultManager.createVault(
            context,
            uri,
            "CorrectPassword!123"
        )

        val dek =
            VaultManager.deriveVaultKey(
                context,
                uri,
                "WrongPassword!123"
            )

        assertNull(
            "Wrong master password must not produce a DEK",
            dek
        )
    }

    // =============================================================
    // SAVE / LOAD ROUND TRIP
    // =============================================================

    @Test
    fun saveThenLoad_preservesCredential() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val dek =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNotNull(
            dek
        )

        val vault =
            JSONArray().apply {

                put(
                    JSONObject().apply {

                        put(
                            "type",
                            "password"
                        )

                        put(
                            "hostname",
                            "github.com"
                        )

                        put(
                            "username",
                            "test@example.com"
                        )

                        put(
                            "password",
                            "secret123"
                        )

                        put(
                            "created",
                            123456789L
                        )

                        put(
                            "updated",
                            123456789L
                        )
                    }
                )
            }

        try {

            VaultManager.saveVaultWithKey(
                context,
                uri,
                vault,
                dek!!
            )

            val loaded =
                VaultManager.loadVaultWithKey(
                    context,
                    uri,
                    dek
                )

            assertNotNull(
                "Saved vault should decrypt with the correct DEK",
                loaded
            )

            assertEquals(
                1,
                loaded!!.length()
            )

            val entry =
                loaded.getJSONObject(
                    0
                )

            assertEquals(
                "password",
                entry.getString(
                    "type"
                )
            )

            assertEquals(
                "github.com",
                entry.getString(
                    "hostname"
                )
            )

            assertEquals(
                "test@example.com",
                entry.getString(
                    "username"
                )
            )

            assertEquals(
                "secret123",
                entry.getString(
                    "password"
                )
            )

        } finally {

            dek?.fill(0)
        }
    }

    // =============================================================
    // VAULT CIPHERTEXT TAMPERING
    // =============================================================

    @Test
    fun modifiedVaultCiphertext_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        val vault =
            root.getJSONObject(
                "vault"
            )

        val ciphertext =
            vault.getString(
                "ciphertext"
            )

        vault.put(
            "ciphertext",
            mutateBase64(
                ciphertext
            )
        )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Modified vault ciphertext must fail authentication",
            result
        )
    }

    // =============================================================
    // VAULT NONCE TAMPERING
    // =============================================================

    @Test
    fun modifiedVaultNonce_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        val vault =
            root.getJSONObject(
                "vault"
            )

        vault.put(
            "nonce",
            mutateBase64(
                vault.getString(
                    "nonce"
                )
            )
        )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Modified vault nonce must fail authentication",
            result
        )
    }

    // =============================================================
    // WRAPPED DEK TAMPERING
    // =============================================================

    @Test
    fun modifiedWrappedDekCiphertext_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        val wrappedDek =
            root.getJSONObject(
                "wrappedDek"
            )

        wrappedDek.put(
            "ciphertext",
            mutateBase64(
                wrappedDek.getString(
                    "ciphertext"
                )
            )
        )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Modified wrapped DEK must fail authentication",
            result
        )
    }

    // =============================================================
    // WRAPPED DEK NONCE TAMPERING
    // =============================================================

    @Test
    fun modifiedWrappedDekNonce_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        val wrappedDek =
            root.getJSONObject(
                "wrappedDek"
            )

        wrappedDek.put(
            "nonce",
            mutateBase64(
                wrappedDek.getString(
                    "nonce"
                )
            )
        )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Modified wrapped DEK nonce must fail authentication",
            result
        )
    }

    // =============================================================
    // VAULT ID TAMPERING
    // =============================================================

    @Test
    fun modifiedVaultId_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        root.put(
            "vaultId",
            mutateBase64(
                root.getString(
                    "vaultId"
                )
            )
        )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Modified vault ID must invalidate authenticated metadata",
            result
        )
    }

    // =============================================================
    // INVALID VERSION
    // =============================================================

    @Test
    fun unsupportedVaultVersion_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        root.put(
            "version",
            999
        )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Unsupported vault versions must be rejected",
            result
        )
    }

    // =============================================================
    // WRONG FORMAT NAME
    // =============================================================

    @Test
    fun invalidFormatName_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        root.put(
            "format",
            "not-athena"
        )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Unknown vault format must be rejected",
            result
        )
    }

    // =============================================================
    // MISSING REQUIRED KEY
    // =============================================================

    @Test
    fun missingRequiredEnvelopeKey_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        root.remove(
            "wrappedDek"
        )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Vault missing a required envelope key must be rejected",
            result
        )
    }

    // =============================================================
    // UNEXPECTED EXTRA KEY
    // =============================================================

    @Test
    fun unexpectedEnvelopeKey_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        root.put(
            "unexpectedField",
            "malicious"
        )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Unexpected envelope fields must be rejected",
            result
        )
    }

    // =============================================================
    // INVALID BASE64
    // =============================================================

    @Test
    fun invalidBase64VaultId_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        root.put(
            "vaultId",
            "%%%NOT_BASE64%%%"
        )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Invalid Base64 must be rejected without opening the vault",
            result
        )
    }

    // =============================================================
    // MALFORMED JSON
    // =============================================================

    @Test
    fun malformedJson_isRejectedWithoutCrash() {

        val uri =
            createTempVaultUri()

        val file =
            fileFromUri(
                uri
            )

        file.writeText(
            "{ this is not valid json"
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                "Password!123"
            )

        assertNull(
            "Malformed vault data must be rejected",
            result
        )
    }

    // =============================================================
    // EMPTY VAULT FILE
    // =============================================================

    @Test
    fun emptyVaultFile_isRejected() {

        val uri =
            createTempVaultUri()

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                "Password!123"
            )

        assertNull(
            "Empty vault file must not authenticate",
            result
        )
    }

    // =============================================================
    // ARGON2 MEMORY - TOO HIGH
    // =============================================================

    @Test
    fun excessiveArgonMemory_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        root
            .getJSONObject(
                "kdf"
            )
            .put(
                "memoryKiB",
                999_999_999
            )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Attacker-controlled excessive Argon2 memory must be rejected",
            result
        )
    }

    // =============================================================
    // ARGON2 MEMORY - TOO LOW
    // =============================================================

    @Test
    fun insufficientArgonMemory_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        root
            .getJSONObject(
                "kdf"
            )
            .put(
                "memoryKiB",
                1
            )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Unsafe Argon2 memory settings must be rejected",
            result
        )
    }

    // =============================================================
    // ARGON2 ITERATIONS
    // =============================================================

    @Test
    fun excessiveArgonIterations_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        root
            .getJSONObject(
                "kdf"
            )
            .put(
                "iterations",
                999_999
            )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Excessive Argon2 iteration count must be rejected",
            result
        )
    }

    @Test
    fun zeroArgonIterations_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        root
            .getJSONObject(
                "kdf"
            )
            .put(
                "iterations",
                0
            )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Zero Argon2 iterations must be rejected",
            result
        )
    }

    // =============================================================
    // ARGON2 PARALLELISM
    // =============================================================

    @Test
    fun excessiveArgonParallelism_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        root
            .getJSONObject(
                "kdf"
            )
            .put(
                "parallelism",
                999
            )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Excessive Argon2 parallelism must be rejected",
            result
        )
    }

    @Test
    fun zeroArgonParallelism_isRejected() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val root =
            readVaultJson(
                uri
            )

        root
            .getJSONObject(
                "kdf"
            )
            .put(
                "parallelism",
                0
            )

        writeVaultJson(
            uri,
            root
        )

        val result =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNull(
            "Zero Argon2 parallelism must be rejected",
            result
        )
    }

    // =============================================================
    // WRONG DEK CANNOT READ VAULT
    // =============================================================

    @Test
    fun loadVaultWithWrongDek_fails() {

        val uri =
            createTempVaultUri()

        val password =
            "StrongMasterPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val wrongDek =
            ByteArray(
                32
            ) {
                0x42.toByte()
            }

        try {

            val result =
                VaultManager.loadVaultWithKey(
                    context,
                    uri,
                    wrongDek
                )

            assertNull(
                "Wrong DEK must not decrypt vault contents",
                result
            )

        } finally {

            wrongDek.fill(0)
        }
    }

    // =============================================================
    // WRONG DEK CANNOT OVERWRITE VAULT
    // =============================================================

    @Test
    fun saveWithWrongDek_doesNotOverwriteVault() {

        val uri =
            createTempVaultUri()

        val password =
            "CorrectPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val file =
            fileFromUri(
                uri
            )

        val original =
            file.readText()

        val wrongDek =
            ByteArray(
                32
            ) {
                0x42.toByte()
            }

        val entries =
            JSONArray().apply {

                put(
                    JSONObject().apply {

                        put(
                            "type",
                            "password"
                        )

                        put(
                            "hostname",
                            "evil.example"
                        )

                        put(
                            "username",
                            "attacker"
                        )

                        put(
                            "password",
                            "evil"
                        )
                    }
                )
            }

        var exceptionThrown =
            false

        try {

            VaultManager.saveVaultWithKey(
                context,
                uri,
                entries,
                wrongDek
            )

        } catch (_: Exception) {

            exceptionThrown =
                true

        } finally {

            wrongDek.fill(0)
        }

        assertTrue(
            "Saving with the wrong DEK should fail",
            exceptionThrown
        )

        val after =
            file.readText()

        assertEquals(
            "Wrong DEK must not alter the original encrypted vault",
            original,
            after
        )
    }

    // =============================================================
    // CORRECT DEK STILL WORKS AFTER WRONG DEK ATTEMPT
    // =============================================================

    @Test
    fun failedWrongDekSave_doesNotCorruptOriginalVault() {

        val uri =
            createTempVaultUri()

        val password =
            "CorrectPassword!123"

        VaultManager.createVault(
            context,
            uri,
            password
        )

        val realDek =
            VaultManager.deriveVaultKey(
                context,
                uri,
                password
            )

        assertNotNull(
            realDek
        )

        val wrongDek =
            ByteArray(
                32
            ) {
                0x33.toByte()
            }

        try {

            try {

                VaultManager.saveVaultWithKey(
                    context,
                    uri,
                    JSONArray(),
                    wrongDek
                )

                fail(
                    "Expected wrong-DEK save to fail"
                )

            } catch (_: Exception) {
                // Expected.
            }

            val loaded =
                VaultManager.loadVaultWithKey(
                    context,
                    uri,
                    realDek!!
                )

            assertNotNull(
                "Original vault must remain readable using its real DEK",
                loaded
            )

        } finally {

            wrongDek.fill(0)
            realDek?.fill(0)
        }
    }

    // =============================================================
    // RUNTIME SESSION - INPUT COPY
    // =============================================================

    @Test
    fun session_copiesInputDek() {

        val uri =
            Uri.parse(
                "content://athena/test"
            )

        val original =
            ByteArray(
                32
            ) {
                7
            }

        VaultRuntimeSession.setSession(
            uri,
            original
        )

        /*
         * Destroy the caller's copy.
         */
        original.fill(0)

        val stored =
            VaultRuntimeSession
                .getVaultDek()

        assertNotNull(
            stored
        )

        assertTrue(
            "Runtime session must retain its own defensive DEK copy",
            stored!!.all {
                it.toInt() == 7
            }
        )

        stored.fill(0)
    }

    // =============================================================
    // RUNTIME SESSION - OUTPUT COPY
    // =============================================================

    @Test
    fun getVaultDek_returnsCopy() {

        val uri =
            Uri.parse(
                "content://athena/test"
            )

        val dek =
            ByteArray(
                32
            ) {
                5
            }

        VaultRuntimeSession.setSession(
            uri,
            dek
        )

        val first =
            VaultRuntimeSession
                .getVaultDek()!!

        /*
         * Destroy the returned array.
         *
         * If VaultRuntimeSession exposed its internal array,
         * this would destroy the authoritative session DEK.
         */
        first.fill(0)

        val second =
            VaultRuntimeSession
                .getVaultDek()!!

        assertTrue(
            "Modifying one returned DEK must not modify the internal DEK",
            second.all {
                it.toInt() == 5
            }
        )

        first.fill(0)
        second.fill(0)
        dek.fill(0)
    }

    // =============================================================
    // RUNTIME SESSION - CLEAR
    // =============================================================

    @Test
    fun clearSession_locksVault() {

        val uri =
            Uri.parse(
                "content://athena/test"
            )

        val dek =
            ByteArray(
                32
            ) {
                9
            }

        VaultRuntimeSession.setSession(
            uri,
            dek
        )

        dek.fill(0)

        assertTrue(
            "Session should initially be unlocked",
            VaultRuntimeSession.isUnlocked()
        )

        assertNotNull(
            VaultRuntimeSession.getVaultUri()
        )

        assertNotNull(
            VaultRuntimeSession.getVaultDek()
        )

        VaultRuntimeSession.clear()

        assertFalse(
            "clear() must mark runtime session as locked",
            VaultRuntimeSession.isUnlocked()
        )

        assertNull(
            "Vault URI must be removed after clear()",
            VaultRuntimeSession.getVaultUri()
        )

        assertNull(
            "Vault DEK must be unavailable after clear()",
            VaultRuntimeSession.getVaultDek()
        )
    }

    // =============================================================
    // REPLACING SESSION
    // =============================================================

    @Test
    fun setSession_replacesPreviousSession() {

        val firstUri =
            Uri.parse(
                "content://athena/first"
            )

        val secondUri =
            Uri.parse(
                "content://athena/second"
            )

        val firstDek =
            ByteArray(
                32
            ) {
                1
            }

        val secondDek =
            ByteArray(
                32
            ) {
                2
            }

        VaultRuntimeSession.setSession(
            firstUri,
            firstDek
        )

        VaultRuntimeSession.setSession(
            secondUri,
            secondDek
        )

        firstDek.fill(0)
        secondDek.fill(0)

        assertEquals(
            secondUri,
            VaultRuntimeSession.getVaultUri()
        )

        val activeDek =
            VaultRuntimeSession
                .getVaultDek()

        assertNotNull(
            activeDek
        )

        assertTrue(
            "New session must contain the replacement DEK",
            activeDek!!.all {
                it.toInt() == 2
            }
        )

        activeDek.fill(0)
    }
}