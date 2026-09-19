package com.athena.j.athena

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Base64
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.time.Instant

object VaultCrypto {
    // ---- KDF / Crypto params ----
    private const val ARGON_TIME = 4               // slight bump for extra hardness
    private const val ARGON_MEMORY_KIB = 65_536    // 64 MiB
    private const val ARGON_PARALLELISM = 4
    private const val KEY_LEN = 32                 // 32 bytes = 256-bit AES key
    private const val AES_NONCE_LEN = 12           // 96-bit nonce for GCM
    private const val GCM_TAG_BITS = 128

    // --- helpers ---
    private fun b64enc(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun b64dec(s: String): ByteArray =
        Base64.decode(s, Base64.NO_WRAP)

    fun deriveKey(masterPasswordUtf8: ByteArray, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withIterations(ARGON_TIME)
            .withMemoryAsKB(ARGON_MEMORY_KIB)
            .withParallelism(ARGON_PARALLELISM)
            .build()
        val gen = Argon2BytesGenerator()
        gen.init(params)
        val out = ByteArray(KEY_LEN)
        gen.generateBytes(masterPasswordUtf8, out, 0, KEY_LEN)
        return out
    }

    data class EncResult(val nonce: ByteArray, val ciphertext: ByteArray)

    fun encrypt(key: ByteArray, plaintext: ByteArray): EncResult {
        require(key.size == KEY_LEN) { "key must be 32 bytes" }
        val nonce = ByteArray(AES_NONCE_LEN)
        SecureRandom().nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce)
        )
        val ct = cipher.doFinal(plaintext)
        return EncResult(nonce, ct)
    }

    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        require(key.size == KEY_LEN) { "key must be 32 bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce)
        )
        return cipher.doFinal(ciphertext)
    }

    // JSON envelope compatible with your Python format
    private fun makeContainer(salt: ByteArray, nonce: ByteArray, ct: ByteArray): JSONObject {
        val kdf = JSONObject()
            .put("type", "argon2id")
            .put("time", ARGON_TIME)
            .put("memory_kib", ARGON_MEMORY_KIB)
            .put("parallelism", ARGON_PARALLELISM)
            .put("salt", b64enc(salt))
        return JSONObject()
            .put("version", 1)
            .put("kdf", kdf)
            .put("nonce", b64enc(nonce))
            .put("entries_ciphertext", b64enc(ct))
            .put("created_at", Instant.now().toString())
    }

    private fun readAllText(cr: ContentResolver, uri: Uri): String {
        cr.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open input stream for $uri" }
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { br ->
                val sb = StringBuilder()
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    sb.append(line).append('\n')
                }
                return sb.toString()
            }
        }
    }

    private fun writeAllText(cr: ContentResolver, uri: Uri, text: String) {
        cr.openOutputStream(uri, "w").use { out ->
            requireNotNull(out) { "Cannot open output stream for $uri" }
            OutputStreamWriter(out, StandardCharsets.UTF_8).use { w ->
                w.write(text)
                w.flush()
            }
        }
    }

    // --------- Public API: create vault file ----------
    fun createVaultFile(context: Context, uri: Uri, masterPassword: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)

        // work with explicit byte array so we can zero it after use
        val pwBytes = masterPassword.toByteArray(StandardCharsets.UTF_8)
        val key = deriveKey(pwBytes, salt)

        val entriesPlain = "[]".toByteArray(StandardCharsets.UTF_8)
        val enc = encrypt(key, entriesPlain)
        val container = makeContainer(salt, enc.nonce, enc.ciphertext)
        writeAllText(context.contentResolver, uri, container.toString(2))

        // 🔒 zero sensitive arrays
        try {
            pwBytes.fill(0)
            key.fill(0)
            salt.fill(0)
            enc.nonce.fill(0)
            enc.ciphertext.fill(0)
            entriesPlain.fill(0)
        } catch (_: Exception) { /* ignore */ }
    }

    // --------- Session ----------
    class VaultSession(private val context: Context, private val uri: Uri) {
        var container: JSONObject? = null
            private set
        var entries: MutableList<JSONObject> = mutableListOf()
            private set
        private var key: ByteArray? = null
        var unlocked: Boolean = false
            private set

        fun unlock(masterPassword: String) {
            // load container
            val text = readAllText(context.contentResolver, uri)
            val obj = JSONObject(text)

            val kdf = obj.getJSONObject("kdf")
            val salt = b64dec(kdf.getString("salt"))
            val nonce = b64dec(obj.getString("nonce"))
            val ct = b64dec(obj.getString("entries_ciphertext"))

            // derive with explicit password bytes so we can wipe them
            val pwBytes = masterPassword.toByteArray(StandardCharsets.UTF_8)
            val derived = deriveKey(pwBytes, salt)
            val pt = decrypt(derived, nonce, ct)
            val arr = JSONArray(String(pt, StandardCharsets.UTF_8))

            // set state
            container = obj
            entries = MutableList(arr.length()) { i -> arr.getJSONObject(i) }
            key = derived
            unlocked = true

            // 🔒 zero temporaries (do not zero 'derived' because it's the live key)
            try {
                pwBytes.fill(0)
                pt.fill(0)
                salt.fill(0)
                nonce.fill(0)
                ct.fill(0)
            } catch (_: Exception) { }
        }

        fun addEntry(hostname: String, username: String, password: String) {
            check(unlocked) { "Vault is locked" }
            val entry = JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("hostname", hostname)
                .put("username", username)
                .put("password", password)
                .put("created_at", Instant.now().toString())
            entries.add(entry)
        }

        fun listEntries(): List<JSONObject> {
            check(unlocked) { "Vault is locked" }
            return entries.toList()
        }

        fun save() {
            check(unlocked && key != null && container != null) { "Vault is not unlocked" }
            val plaintext = JSONArray(entries).toString().toByteArray(StandardCharsets.UTF_8)
            val enc = encrypt(key!!, plaintext)

            container!!.put("nonce", b64enc(enc.nonce))
            container!!.put("entries_ciphertext", b64enc(enc.ciphertext))
            container!!.put("modified_at", Instant.now().toString())

            writeAllText(context.contentResolver, uri, container!!.toString(2))

            // 🔒 zero temporaries
            try {
                plaintext.fill(0)
                enc.nonce.fill(0)
                enc.ciphertext.fill(0)
            } catch (_: Exception) { }
        }

        fun lock() {
            // 🔒 wipe live key bytes before dropping references
            try { key?.fill(0) } catch (_: Exception) { }
            key = null
            entries.clear()
            container = null
            unlocked = false
        }
    }
}
