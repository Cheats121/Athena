package com.athena.j.athena

import android.content.Context
import android.net.Uri
import android.util.Base64
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

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val temporaryFiles = mutableListOf<File>()
    private val recoveryKeys = mutableListOf<ByteArray>()

    private fun createTempVaultUri(): Uri {
        val file = File(
            context.cacheDir,
            "test-vault-${System.nanoTime()}.json"
        )

        file.createNewFile()
        temporaryFiles.add(file)

        return Uri.fromFile(file)
    }

    private fun createRecoveryKey(): ByteArray {
        val key = VaultManager.generateRecoveryKey()
        recoveryKeys.add(key)
        return key
    }

    private fun fileFromUri(uri: Uri): File {
        val path = requireNotNull(uri.path) {
            "Test URI has no file path"
        }

        return File(path)
    }

    private fun readVaultJson(uri: Uri): JSONObject {
        val file = fileFromUri(uri)
        return JSONObject(file.readText())
    }

    private fun writeVaultJson(uri: Uri, root: JSONObject) {
        fileFromUri(uri).writeText(root.toString())
    }

    private fun mutateBase64(value: String): String {
        require(value.isNotEmpty())

        val replacement =
            if (value[0] == 'A') {
                'B'
            } else {
                'A'
            }

        return replacement + value.substring(1)
    }

    @After
    fun cleanup() {
        VaultRuntimeSession.clear()

        recoveryKeys.forEach { key ->
            try {
                key.fill(0)
            } catch (_: Exception) {}
        }

        recoveryKeys.clear()

        temporaryFiles.forEach { file ->
            try {
                file.delete()
            } catch (_: Exception) {}
        }

        temporaryFiles.clear()
    }

    @Test
    fun createVault_correctPasswordAndRecoveryKey_unlocksSuccessfully() {
        val uri = createTempVaultUri()
        val password = "TestPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val dek = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNotNull(
            "Correct password and recovery key should return vault DEK",
            dek
        )

        assertEquals(
            "Vault DEK must be 256 bits",
            32,
            dek!!.size
        )

        dek.fill(0)
    }

    @Test
    fun unlockVault_wrongPassword_fails() {
        val uri = createTempVaultUri()
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = "CorrectPassword!123",
            recoveryKey = recoveryKey
        )

        val dek = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = "WrongPassword!123",
            recoveryKey = recoveryKey
        )

        assertNull(
            "Wrong master password must not produce a DEK",
            dek
        )
    }

    @Test
    fun unlockVault_wrongRecoveryKey_fails() {
        val uri = createTempVaultUri()
        val correctRecoveryKey = createRecoveryKey()
        val wrongRecoveryKey = createRecoveryKey()
        val password = "CorrectPassword!123"

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = correctRecoveryKey
        )

        val dek = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = wrongRecoveryKey
        )

        assertNull(
            "Correct password with wrong recovery key must not produce a DEK",
            dek
        )
    }

    @Test
    fun recoveryKey_isNotStoredInsideVaultFile() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val raw = fileFromUri(uri).readText()

        val standardBase64 = Base64.encodeToString(
            recoveryKey,
            Base64.NO_WRAP
        )

        val urlSafeBase64 = Base64.encodeToString(
            recoveryKey,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        assertFalse(
            "Raw recovery key must never appear in vault JSON",
            raw.contains(standardBase64)
        )

        assertFalse(
            "URL-safe recovery key must never appear in vault JSON",
            raw.contains(urlSafeBase64)
        )

        assertFalse(
            "Vault envelope must not contain a recoveryKey field",
            raw.contains("\"recoveryKey\"")
        )
    }

    @Test
    fun saveThenLoad_preservesCredential() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val dek = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNotNull(dek)

        val vault = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("type", "password")
                    put("hostname", "github.com")
                    put("username", "test@example.com")
                    put("password", "secret123")
                    put("created", 123456789L)
                    put("updated", 123456789L)
                }
            )
        }

        try {
            VaultManager.saveVaultWithKey(
                context = context,
                uri = uri,
                entries = vault,
                vaultKey = dek!!
            )

            val loaded = VaultManager.loadVaultWithKey(
                context = context,
                uri = uri,
                vaultKey = dek
            )

            assertNotNull(
                "Saved vault should decrypt with correct DEK",
                loaded
            )

            assertEquals(1, loaded!!.length())

            val entry = loaded.getJSONObject(0)

            assertEquals("password", entry.getString("type"))
            assertEquals("github.com", entry.getString("hostname"))
            assertEquals("test@example.com", entry.getString("username"))
            assertEquals("secret123", entry.getString("password"))

        } finally {
            dek?.fill(0)
        }
    }

    @Test
    fun modifiedVaultCiphertext_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)
        val vault = root.getJSONObject("vault")
        val ciphertext = vault.getString("ciphertext")

        vault.put(
            "ciphertext",
            mutateBase64(ciphertext)
        )

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Modified vault ciphertext must fail authentication",
            result
        )
    }

    @Test
    fun modifiedVaultNonce_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)
        val vault = root.getJSONObject("vault")

        vault.put(
            "nonce",
            mutateBase64(vault.getString("nonce"))
        )

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Modified vault nonce must fail authentication",
            result
        )
    }

    @Test
    fun modifiedWrappedDekCiphertext_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)
        val wrappedDek = root.getJSONObject("wrappedDek")

        wrappedDek.put(
            "ciphertext",
            mutateBase64(wrappedDek.getString("ciphertext"))
        )

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Modified wrapped DEK must fail authentication",
            result
        )
    }

    @Test
    fun modifiedWrappedDekNonce_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)
        val wrappedDek = root.getJSONObject("wrappedDek")

        wrappedDek.put(
            "nonce",
            mutateBase64(wrappedDek.getString("nonce"))
        )

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Modified wrapped DEK nonce must fail authentication",
            result
        )
    }

    @Test
    fun modifiedVaultId_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)

        root.put(
            "vaultId",
            mutateBase64(root.getString("vaultId"))
        )

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Modified vault ID must invalidate authenticated metadata",
            result
        )
    }

    @Test
    fun unsupportedVaultVersion_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)

        root.put("version", 999)

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Unsupported vault versions must be rejected",
            result
        )
    }

    @Test
    fun invalidFormatName_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)

        root.put("format", "not-athena")

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Unknown vault format must be rejected",
            result
        )
    }

    @Test
    fun missingRequiredEnvelopeKey_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)

        root.remove("wrappedDek")

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Vault missing a required envelope key must be rejected",
            result
        )
    }

    @Test
    fun unexpectedEnvelopeKey_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)

        root.put(
            "unexpectedField",
            "malicious"
        )

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Unexpected envelope fields must be rejected",
            result
        )
    }

    @Test
    fun invalidBase64VaultId_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)

        root.put(
            "vaultId",
            "%%%NOT_BASE64%%%"
        )

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Invalid Base64 must be rejected without opening vault",
            result
        )
    }

    @Test
    fun malformedJson_isRejectedWithoutCrash() {
        val uri = createTempVaultUri()
        val recoveryKey = createRecoveryKey()
        val file = fileFromUri(uri)

        file.writeText("{ this is not valid json")

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = "Password!123",
            recoveryKey = recoveryKey
        )

        assertNull(
            "Malformed vault data must be rejected",
            result
        )
    }

    @Test
    fun emptyVaultFile_isRejected() {
        val uri = createTempVaultUri()
        val recoveryKey = createRecoveryKey()

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = "Password!123",
            recoveryKey = recoveryKey
        )

        assertNull(
            "Empty vault file must not authenticate",
            result
        )
    }

    @Test
    fun excessiveArgonMemory_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)

        root.getJSONObject("kdf")
            .put("memoryKiB", 999_999_999)

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Attacker-controlled excessive Argon2 memory must be rejected",
            result
        )
    }

    @Test
    fun insufficientArgonMemory_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)

        root.getJSONObject("kdf")
            .put("memoryKiB", 1)

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Unsafe Argon2 memory settings must be rejected",
            result
        )
    }

    @Test
    fun excessiveArgonIterations_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)

        root.getJSONObject("kdf")
            .put("iterations", 999_999)

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Excessive Argon2 iteration count must be rejected",
            result
        )
    }

    @Test
    fun zeroArgonIterations_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)

        root.getJSONObject("kdf")
            .put("iterations", 0)

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Zero Argon2 iterations must be rejected",
            result
        )
    }

    @Test
    fun excessiveArgonParallelism_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)

        root.getJSONObject("kdf")
            .put("parallelism", 999)

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Excessive Argon2 parallelism must be rejected",
            result
        )
    }

    @Test
    fun zeroArgonParallelism_isRejected() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val root = readVaultJson(uri)

        root.getJSONObject("kdf")
            .put("parallelism", 0)

        writeVaultJson(uri, root)

        val result = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNull(
            "Zero Argon2 parallelism must be rejected",
            result
        )
    }

    @Test
    fun loadVaultWithWrongDek_fails() {
        val uri = createTempVaultUri()
        val password = "StrongMasterPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val wrongDek = ByteArray(32) {
            0x42.toByte()
        }

        try {
            val result = VaultManager.loadVaultWithKey(
                context = context,
                uri = uri,
                vaultKey = wrongDek
            )

            assertNull(
                "Wrong DEK must not decrypt vault contents",
                result
            )

        } finally {
            wrongDek.fill(0)
        }
    }

    @Test
    fun saveWithWrongDek_doesNotOverwriteVault() {
        val uri = createTempVaultUri()
        val password = "CorrectPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val file = fileFromUri(uri)
        val original = file.readText()

        val wrongDek = ByteArray(32) {
            0x42.toByte()
        }

        val entries = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("type", "password")
                    put("hostname", "evil.example")
                    put("username", "attacker")
                    put("password", "evil")
                }
            )
        }

        var exceptionThrown = false

        try {
            VaultManager.saveVaultWithKey(
                context = context,
                uri = uri,
                entries = entries,
                vaultKey = wrongDek
            )

        } catch (_: Exception) {
            exceptionThrown = true

        } finally {
            wrongDek.fill(0)
        }

        assertTrue(
            "Saving with wrong DEK should fail",
            exceptionThrown
        )

        val after = file.readText()

        assertEquals(
            "Wrong DEK must not alter original encrypted vault",
            original,
            after
        )
    }

    @Test
    fun failedWrongDekSave_doesNotCorruptOriginalVault() {
        val uri = createTempVaultUri()
        val password = "CorrectPassword!123"
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        val realDek = VaultManager.deriveVaultKey(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        )

        assertNotNull(realDek)

        val wrongDek = ByteArray(32) {
            0x33.toByte()
        }

        try {
            try {
                VaultManager.saveVaultWithKey(
                    context = context,
                    uri = uri,
                    entries = JSONArray(),
                    vaultKey = wrongDek
                )

                fail("Expected wrong-DEK save to fail")

            } catch (_: Exception) {}

            val loaded = VaultManager.loadVaultWithKey(
                context = context,
                uri = uri,
                vaultKey = realDek!!
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

    @Test
    fun getVaultId_returnsStableDefensiveCopy() {
        val uri = createTempVaultUri()
        val recoveryKey = createRecoveryKey()

        VaultManager.createVault(
            context = context,
            uri = uri,
            password = "Password!123",
            recoveryKey = recoveryKey
        )

        val first = VaultManager.getVaultId(
            context = context,
            uri = uri
        )

        val second = VaultManager.getVaultId(
            context = context,
            uri = uri
        )

        assertNotNull(first)
        assertNotNull(second)

        assertEquals(16, first!!.size)

        assertArrayEquals(
            "Vault ID should remain stable for the same vault",
            first,
            second
        )

        first.fill(0)

        val third = VaultManager.getVaultId(
            context = context,
            uri = uri
        )

        assertNotNull(third)

        assertFalse(
            "Mutating returned vault ID must not alter stored vault ID",
            third!!.all { it.toInt() == 0 }
        )

        second?.fill(0)
        third.fill(0)
    }

    @Test
    fun session_copiesInputDek() {
        val uri = Uri.parse("content://athena/test")

        val original = ByteArray(32) {
            7
        }

        VaultRuntimeSession.setSession(uri, original)

        original.fill(0)

        val stored = VaultRuntimeSession.getVaultDek()

        assertNotNull(stored)

        assertTrue(
            "Runtime session must retain its own defensive DEK copy",
            stored!!.all { it.toInt() == 7 }
        )

        stored.fill(0)
    }

    @Test
    fun getVaultDek_returnsCopy() {
        val uri = Uri.parse("content://athena/test")

        val dek = ByteArray(32) {
            5
        }

        VaultRuntimeSession.setSession(uri, dek)

        val first = VaultRuntimeSession.getVaultDek()!!

        first.fill(0)

        val second = VaultRuntimeSession.getVaultDek()!!

        assertTrue(
            "Modifying returned DEK must not modify internal DEK",
            second.all { it.toInt() == 5 }
        )

        first.fill(0)
        second.fill(0)
        dek.fill(0)
    }

    @Test
    fun clearSession_locksVault() {
        val uri = Uri.parse("content://athena/test")

        val dek = ByteArray(32) {
            9
        }

        VaultRuntimeSession.setSession(uri, dek)

        dek.fill(0)

        assertTrue(
            "Session should initially be unlocked",
            VaultRuntimeSession.isUnlocked()
        )

        assertNotNull(VaultRuntimeSession.getVaultUri())
        assertNotNull(VaultRuntimeSession.getVaultDek())

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

    @Test
    fun setSession_replacesPreviousSession() {
        val firstUri = Uri.parse("content://athena/first")
        val secondUri = Uri.parse("content://athena/second")

        val firstDek = ByteArray(32) {
            1
        }

        val secondDek = ByteArray(32) {
            2
        }

        VaultRuntimeSession.setSession(firstUri, firstDek)
        VaultRuntimeSession.setSession(secondUri, secondDek)

        firstDek.fill(0)
        secondDek.fill(0)

        assertEquals(
            secondUri,
            VaultRuntimeSession.getVaultUri()
        )

        val activeDek = VaultRuntimeSession.getVaultDek()

        assertNotNull(activeDek)

        assertTrue(
            "New session must contain replacement DEK",
            activeDek!!.all { it.toInt() == 2 }
        )

        activeDek.fill(0)
    }
}