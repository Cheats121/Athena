package com.athena.j.athena

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object VaultManager {

    private const val TAG = "AthenaVault"
    private const val FORMAT_NAME = "athena-vault"
    private const val FORMAT_VERSION = 4
    private const val KDF_NAME = "argon2id"
    private const val KDF_COMBINER_NAME = "hkdf-sha256"
    private const val KDF_INFO = "ATHENA|V4|VAULT-KEK"
    private const val CIPHER_NAME = "aes-256-gcm"
    private const val HMAC_NAME = "HmacSHA256"
    private const val ARGON_MEMORY_KIB = 65_536
    private const val ARGON_ITERATIONS = 3
    private const val ARGON_PARALLELISM = 4
    private const val MIN_ARGON_MEMORY_KIB = 32_768
    private const val MAX_ARGON_MEMORY_KIB = 262_144
    private const val MIN_ARGON_ITERATIONS = 1
    private const val MAX_ARGON_ITERATIONS = 10
    private const val MIN_ARGON_PARALLELISM = 1
    private const val MAX_ARGON_PARALLELISM = 8
    private const val KEY_BYTES = 32
    private const val RECOVERY_KEY_BYTES = 32
    private const val SALT_BYTES = 16
    private const val VAULT_ID_BYTES = 16
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val WRAPPED_DEK_BYTES = KEY_BYTES + 16
    private const val MAX_ENVELOPE_CHARS = 32 * 1024 * 1024
    private const val MAX_VAULT_CIPHERTEXT_BYTES = 16 * 1024 * 1024
    private val secureRandom = SecureRandom()
    fun generateRecoveryKey(): ByteArray {
        return randomBytes(RECOVERY_KEY_BYTES)
    }
    fun createVault(
        context: Context,
        uri: Uri,
        password: String,
        recoveryKey: ByteArray
    ) {
        require(password.isNotEmpty()) { "Master password cannot be empty" }
        require(recoveryKey.size == RECOVERY_KEY_BYTES) { "Invalid recovery key size" }
        val previousContents = readTextLimited(context, uri) ?: ""
        val vaultId = randomBytes(VAULT_ID_BYTES)
        val salt = randomBytes(SALT_BYTES)
        val dek = randomBytes(KEY_BYTES)
        val kek = deriveKek(
            password = password,
            recoveryKey = recoveryKey,
            vaultId = vaultId,
            salt = salt,
            memoryKiB = ARGON_MEMORY_KIB,
            iterations = ARGON_ITERATIONS,
            parallelism = ARGON_PARALLELISM
        )

        val wrapNonce = randomBytes(GCM_NONCE_BYTES)
        val dataNonce = randomBytes(GCM_NONCE_BYTES)
        val emptyVaultPlaintext = JSONArray()
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

        var wrappedDek: ByteArray? = null
        var encryptedVault: ByteArray? = null
        var wrapAad: ByteArray? = null
        var dataAad: ByteArray? = null

        try {
            wrapAad = buildWrapAad(
                vaultId = vaultId,
                salt = salt,
                memoryKiB = ARGON_MEMORY_KIB,
                iterations = ARGON_ITERATIONS,
                parallelism = ARGON_PARALLELISM
            )

            wrappedDek = encryptGcm(
                key = kek,
                nonce = wrapNonce,
                plaintext = dek,
                aad = wrapAad
            )

            dataAad = buildVaultAad(vaultId)
            encryptedVault = encryptGcm(
                key = dek,
                nonce = dataNonce,
                plaintext = emptyVaultPlaintext,
                aad = dataAad
            )

            val envelope = createEnvelope(
                vaultId = vaultId,
                salt = salt,
                memoryKiB = ARGON_MEMORY_KIB,
                iterations = ARGON_ITERATIONS,
                parallelism = ARGON_PARALLELISM,
                wrapNonce = wrapNonce,
                wrappedDek = wrappedDek,
                vaultNonce = dataNonce,
                vaultCiphertext = encryptedVault
            )

            writeWithRollback(
                context = context,
                uri = uri,
                newContents = envelope.toString(),
                previousContents = previousContents
            )

        } finally {
            emptyVaultPlaintext.fill(0)
            vaultId.fill(0)
            salt.fill(0)
            dek.fill(0)
            kek.fill(0)
            wrapNonce.fill(0)
            dataNonce.fill(0)
            wrappedDek?.fill(0)
            encryptedVault?.fill(0)
            wrapAad?.fill(0)
            dataAad?.fill(0)
        }
    }

    fun loadVault(
        context: Context,
        uri: Uri,
        password: String,
        recoveryKey: ByteArray
    ): JSONArray? {
        if (recoveryKey.size != RECOVERY_KEY_BYTES) return null
        var parsed: ParsedEnvelope? = null
        var kek: ByteArray? = null
        var dek: ByteArray? = null
        var wrapAad: ByteArray? = null
        var plaintext: ByteArray? = null
        var dataAad: ByteArray? = null

        return try {
            parsed = readAndParseEnvelope(context, uri) ?: return null
            kek = deriveKek(
                password = password,
                recoveryKey = recoveryKey,
                vaultId = parsed.vaultId,
                salt = parsed.kdf.salt,
                memoryKiB = parsed.kdf.memoryKiB,
                iterations = parsed.kdf.iterations,
                parallelism = parsed.kdf.parallelism
            )

            wrapAad = buildWrapAad(
                vaultId = parsed.vaultId,
                salt = parsed.kdf.salt,
                memoryKiB = parsed.kdf.memoryKiB,
                iterations = parsed.kdf.iterations,
                parallelism = parsed.kdf.parallelism
            )

            dek = decryptGcm(
                key = kek,
                nonce = parsed.wrappedDek.nonce,
                ciphertext = parsed.wrappedDek.ciphertext,
                aad = wrapAad
            )

            if (dek.size != KEY_BYTES) return null
            dataAad = buildVaultAad(parsed.vaultId)
            plaintext = decryptGcm(
                key = dek,
                nonce = parsed.vaultData.nonce,
                ciphertext = parsed.vaultData.ciphertext,
                aad = dataAad
            )

            JSONArray(String(plaintext, StandardCharsets.UTF_8))

        } catch (e: Exception) {
            Log.w(TAG, "Vault authentication/decryption failed")
            null

        } finally {
            kek?.fill(0)
            dek?.fill(0)
            wrapAad?.fill(0)
            dataAad?.fill(0)
            plaintext?.fill(0)
            parsed?.wipe()
        }
    }

    fun deriveVaultKey(
        context: Context,
        uri: Uri,
        password: String,
        recoveryKey: ByteArray
    ): ByteArray? {
        if (recoveryKey.size != RECOVERY_KEY_BYTES) return null
        var parsed: ParsedEnvelope? = null
        var kek: ByteArray? = null
        var dek: ByteArray? = null
        var wrapAad: ByteArray? = null
        var dataAad: ByteArray? = null
        var plaintext: ByteArray? = null

        try {
            parsed = readAndParseEnvelope(context, uri) ?: return null
            kek = deriveKek(
                password = password,
                recoveryKey = recoveryKey,
                vaultId = parsed.vaultId,
                salt = parsed.kdf.salt,
                memoryKiB = parsed.kdf.memoryKiB,
                iterations = parsed.kdf.iterations,
                parallelism = parsed.kdf.parallelism
            )

            wrapAad = buildWrapAad(
                vaultId = parsed.vaultId,
                salt = parsed.kdf.salt,
                memoryKiB = parsed.kdf.memoryKiB,
                iterations = parsed.kdf.iterations,
                parallelism = parsed.kdf.parallelism
            )

            dek = decryptGcm(
                key = kek,
                nonce = parsed.wrappedDek.nonce,
                ciphertext = parsed.wrappedDek.ciphertext,
                aad = wrapAad
            )

            if (dek.size != KEY_BYTES) {
                dek.fill(0)
                return null
            }

            dataAad = buildVaultAad(parsed.vaultId)
            plaintext = decryptGcm(
                key = dek,
                nonce = parsed.vaultData.nonce,
                ciphertext = parsed.vaultData.ciphertext,
                aad = dataAad
            )

            JSONArray(String(plaintext, StandardCharsets.UTF_8))

            return dek.copyOf()

        } catch (e: Exception) {
            Log.w(TAG, "Vault authentication failed")
            return null

        } finally {
            kek?.fill(0)
            dek?.fill(0)
            wrapAad?.fill(0)
            dataAad?.fill(0)
            plaintext?.fill(0)
            parsed?.wipe()
        }
    }

    fun loadVaultWithKey(
        context: Context,
        uri: Uri,
        vaultKey: ByteArray
    ): JSONArray? {
        if (vaultKey.size != KEY_BYTES) return null
        var parsed: ParsedEnvelope? = null
        var dataAad: ByteArray? = null
        var plaintext: ByteArray? = null

        return try {
            parsed = readAndParseEnvelope(context, uri) ?: return null
            dataAad = buildVaultAad(parsed.vaultId)
            plaintext = decryptGcm(
                key = vaultKey,
                nonce = parsed.vaultData.nonce,
                ciphertext = parsed.vaultData.ciphertext,
                aad = dataAad
            )

            JSONArray(String(plaintext, StandardCharsets.UTF_8))

        } catch (e: Exception) {
            Log.w(TAG, "Vault DEK authentication failed")
            null

        } finally {
            dataAad?.fill(0)
            plaintext?.fill(0)
            parsed?.wipe()
        }
    }

    fun saveVault(
        context: Context,
        uri: Uri,
        entries: JSONArray,
        password: String,
        recoveryKey: ByteArray
    ) {
        require(recoveryKey.size == RECOVERY_KEY_BYTES) { "Invalid recovery key size" }
        var parsed: ParsedEnvelope? = null
        var kek: ByteArray? = null
        var dek: ByteArray? = null
        var wrapAad: ByteArray? = null
        var verificationAad: ByteArray? = null
        var verificationPlaintext: ByteArray? = null

        try {
            val oldRaw = readTextLimited(context, uri)
                ?: throw IllegalStateException("Vault file unavailable")

            parsed = parseEnvelope(oldRaw)
                ?: throw SecurityException("Invalid vault format")

            kek = deriveKek(
                password = password,
                recoveryKey = recoveryKey,
                vaultId = parsed.vaultId,
                salt = parsed.kdf.salt,
                memoryKiB = parsed.kdf.memoryKiB,
                iterations = parsed.kdf.iterations,
                parallelism = parsed.kdf.parallelism
            )

            wrapAad = buildWrapAad(
                vaultId = parsed.vaultId,
                salt = parsed.kdf.salt,
                memoryKiB = parsed.kdf.memoryKiB,
                iterations = parsed.kdf.iterations,
                parallelism = parsed.kdf.parallelism
            )

            dek = decryptGcm(
                key = kek,
                nonce = parsed.wrappedDek.nonce,
                ciphertext = parsed.wrappedDek.ciphertext,
                aad = wrapAad
            )

            if (dek.size != KEY_BYTES) {
                throw SecurityException("Invalid DEK")
            }

            verificationAad = buildVaultAad(parsed.vaultId)
            verificationPlaintext = decryptGcm(
                key = dek,
                nonce = parsed.vaultData.nonce,
                ciphertext = parsed.vaultData.ciphertext,
                aad = verificationAad
            )

            JSONArray(String(verificationPlaintext, StandardCharsets.UTF_8))
            saveEnvelopeWithDek(
                context = context,
                uri = uri,
                oldRaw = oldRaw,
                parsed = parsed,
                entries = entries,
                dek = dek
            )

        } finally {
            kek?.fill(0)
            dek?.fill(0)
            wrapAad?.fill(0)
            verificationAad?.fill(0)
            verificationPlaintext?.fill(0)
            parsed?.wipe()
        }
    }

    fun saveVaultWithKey(
        context: Context,
        uri: Uri,
        entries: JSONArray,
        vaultKey: ByteArray
    ) {
        require(vaultKey.size == KEY_BYTES) { "Invalid vault key size" }
        var parsed: ParsedEnvelope? = null
        var verificationAad: ByteArray? = null
        var verificationPlaintext: ByteArray? = null
        try {
            val oldRaw = readTextLimited(context, uri)
                ?: throw IllegalStateException("Vault file unavailable")
            parsed = parseEnvelope(oldRaw)
                ?: throw SecurityException("Invalid vault")
            verificationAad = buildVaultAad(parsed.vaultId)
            verificationPlaintext = decryptGcm(
                key = vaultKey,
                nonce = parsed.vaultData.nonce,
                ciphertext = parsed.vaultData.ciphertext,
                aad = verificationAad
            )

            JSONArray(String(verificationPlaintext, StandardCharsets.UTF_8))
            saveEnvelopeWithDek(
                context = context,
                uri = uri,
                oldRaw = oldRaw,
                parsed = parsed,
                entries = entries,
                dek = vaultKey
            )

        } finally {
            verificationAad?.fill(0)
            verificationPlaintext?.fill(0)
            parsed?.wipe()
        }
    }

    private fun saveEnvelopeWithDek(
        context: Context,
        uri: Uri,
        oldRaw: String,
        parsed: ParsedEnvelope,
        entries: JSONArray,
        dek: ByteArray
    ) {
        val plaintext = entries.toString().toByteArray(StandardCharsets.UTF_8)
        val nonce = randomBytes(GCM_NONCE_BYTES)
        var aad: ByteArray? = null
        var ciphertext: ByteArray? = null
        try {
            aad = buildVaultAad(parsed.vaultId)
            ciphertext = encryptGcm(
                key = dek,
                nonce = nonce,
                plaintext = plaintext,
                aad = aad
            )

            if (ciphertext.size > MAX_VAULT_CIPHERTEXT_BYTES) {
                throw IllegalStateException("Vault exceeds maximum size")
            }

            val envelope = createEnvelope(
                vaultId = parsed.vaultId,
                salt = parsed.kdf.salt,
                memoryKiB = parsed.kdf.memoryKiB,
                iterations = parsed.kdf.iterations,
                parallelism = parsed.kdf.parallelism,
                wrapNonce = parsed.wrappedDek.nonce,
                wrappedDek = parsed.wrappedDek.ciphertext,
                vaultNonce = nonce,
                vaultCiphertext = ciphertext
            )

            writeWithRollback(
                context = context,
                uri = uri,
                newContents = envelope.toString(),
                previousContents = oldRaw
            )

        } finally {
            plaintext.fill(0)
            nonce.fill(0)
            aad?.fill(0)
            ciphertext?.fill(0)
        }
    }

    fun getVaultId(context: Context, uri: Uri): ByteArray? {
        var parsed: ParsedEnvelope? = null
        return try {
            parsed = readAndParseEnvelope(context, uri) ?: return null
            parsed.vaultId.copyOf()

        } finally {
            parsed?.wipe()
        }
    }

    fun verifyIntegrity(
        context: Context,
        uri: Uri,
        password: String,
        recoveryKey: ByteArray
    ): Boolean {
        return loadVault(
            context = context,
            uri = uri,
            password = password,
            recoveryKey = recoveryKey
        ) != null
    }

    fun verifyIntegrityWithKey(
        context: Context,
        uri: Uri,
        vaultKey: ByteArray
    ): Boolean {
        return loadVaultWithKey(context, uri, vaultKey) != null
    }
    private fun createEnvelope(
        vaultId: ByteArray,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
        wrapNonce: ByteArray,
        wrappedDek: ByteArray,
        vaultNonce: ByteArray,
        vaultCiphertext: ByteArray
    ): JSONObject {
        return JSONObject().apply {
            put("format", FORMAT_NAME)
            put("version", FORMAT_VERSION)
            put("vaultId", b64(vaultId))
            put(
                "kdf",
                JSONObject().apply {
                    put("name", KDF_NAME)
                    put("combiner", KDF_COMBINER_NAME)
                    put("info", KDF_INFO)
                    put("memoryKiB", memoryKiB)
                    put("iterations", iterations)
                    put("parallelism", parallelism)
                    put("salt", b64(salt))
                }
            )
            put(
                "wrappedDek",
                JSONObject().apply {
                    put("cipher", CIPHER_NAME)
                    put("nonce", b64(wrapNonce))
                    put("ciphertext", b64(wrappedDek))
                }
            )
            put(
                "vault",
                JSONObject().apply {
                    put("cipher", CIPHER_NAME)
                    put("nonce", b64(vaultNonce))
                    put("ciphertext", b64(vaultCiphertext))
                }
            )
        }
    }

    private fun readAndParseEnvelope(context: Context, uri: Uri): ParsedEnvelope? {
        val raw = readTextLimited(context, uri) ?: return null
        return parseEnvelope(raw)
    }

    private fun parseEnvelope(raw: String): ParsedEnvelope? {
        if (raw.isBlank() || raw.length > MAX_ENVELOPE_CHARS) return null
        return try {
            val root = JSONObject(raw)
            if (
                !hasExactKeys(
                    root,
                    setOf("format", "version", "vaultId", "kdf", "wrappedDek", "vault")
                )
            ) return null

            if (root.getString("format") != FORMAT_NAME) return null
            if (root.getInt("version") != FORMAT_VERSION) return null
            val vaultId = decodeB64Exact(
                root.getString("vaultId"),
                VAULT_ID_BYTES
            ) ?: return null

            val kdfJson = root.getJSONObject("kdf")
            if (
                !hasExactKeys(
                    kdfJson,
                    setOf(
                        "name",
                        "combiner",
                        "info",
                        "memoryKiB",
                        "iterations",
                        "parallelism",
                        "salt"
                    )
                )
            ) {
                vaultId.fill(0)
                return null
            }

            if (kdfJson.getString("name") != KDF_NAME) {
                vaultId.fill(0)
                return null
            }

            if (kdfJson.getString("combiner") != KDF_COMBINER_NAME) {
                vaultId.fill(0)
                return null
            }

            if (kdfJson.getString("info") != KDF_INFO) {
                vaultId.fill(0)
                return null
            }

            val memoryKiB = kdfJson.getInt("memoryKiB")
            val iterations = kdfJson.getInt("iterations")
            val parallelism = kdfJson.getInt("parallelism")
            if (memoryKiB !in MIN_ARGON_MEMORY_KIB..MAX_ARGON_MEMORY_KIB) {
                vaultId.fill(0)
                return null
            }

            if (iterations !in MIN_ARGON_ITERATIONS..MAX_ARGON_ITERATIONS) {
                vaultId.fill(0)
                return null
            }

            if (parallelism !in MIN_ARGON_PARALLELISM..MAX_ARGON_PARALLELISM) {
                vaultId.fill(0)
                return null
            }

            val salt = decodeB64Exact(
                kdfJson.getString("salt"),
                SALT_BYTES
            ) ?: run {
                vaultId.fill(0)
                return null
            }

            val wrappedJson = root.getJSONObject("wrappedDek")
            if (
                !hasExactKeys(
                    wrappedJson,
                    setOf("cipher", "nonce", "ciphertext")
                )
            ) {
                vaultId.fill(0)
                salt.fill(0)
                return null
            }

            if (wrappedJson.getString("cipher") != CIPHER_NAME) {
                vaultId.fill(0)
                salt.fill(0)
                return null
            }

            val wrapNonce = decodeB64Exact(
                wrappedJson.getString("nonce"),
                GCM_NONCE_BYTES
            ) ?: run {
                vaultId.fill(0)
                salt.fill(0)
                return null
            }

            val wrappedDek = decodeB64Exact(
                wrappedJson.getString("ciphertext"),
                WRAPPED_DEK_BYTES
            ) ?: run {
                vaultId.fill(0)
                salt.fill(0)
                wrapNonce.fill(0)
                return null
            }

            val vaultJson = root.getJSONObject("vault")
            if (
                !hasExactKeys(
                    vaultJson,
                    setOf("cipher", "nonce", "ciphertext")
                )
            ) {
                vaultId.fill(0)
                salt.fill(0)
                wrapNonce.fill(0)
                wrappedDek.fill(0)
                return null
            }

            if (vaultJson.getString("cipher") != CIPHER_NAME) {
                vaultId.fill(0)
                salt.fill(0)
                wrapNonce.fill(0)
                wrappedDek.fill(0)
                return null
            }

            val vaultNonce = decodeB64Exact(
                vaultJson.getString("nonce"),
                GCM_NONCE_BYTES
            ) ?: run {
                vaultId.fill(0)
                salt.fill(0)
                wrapNonce.fill(0)
                wrappedDek.fill(0)
                return null
            }

            val vaultCiphertextB64 = vaultJson.getString("ciphertext")
            if (
                vaultCiphertextB64.length >
                (MAX_VAULT_CIPHERTEXT_BYTES * 4 / 3) + 16
            ) {
                vaultId.fill(0)
                salt.fill(0)
                wrapNonce.fill(0)
                wrappedDek.fill(0)
                vaultNonce.fill(0)
                return null
            }

            val vaultCiphertext = b64d(vaultCiphertextB64)
            if (
                vaultCiphertext.size < 16 ||
                vaultCiphertext.size > MAX_VAULT_CIPHERTEXT_BYTES
            ) {
                vaultId.fill(0)
                salt.fill(0)
                wrapNonce.fill(0)
                wrappedDek.fill(0)
                vaultNonce.fill(0)
                vaultCiphertext.fill(0)
                return null
            }

            ParsedEnvelope(
                vaultId = vaultId,
                kdf = ParsedKdf(
                    memoryKiB = memoryKiB,
                    iterations = iterations,
                    parallelism = parallelism,
                    salt = salt
                ),
                wrappedDek = ParsedCipherBlob(
                    nonce = wrapNonce,
                    ciphertext = wrappedDek
                ),
                vaultData = ParsedCipherBlob(
                    nonce = vaultNonce,
                    ciphertext = vaultCiphertext
                )
            )

        } catch (e: Exception) {
            Log.w(TAG, "Invalid Athena v4 vault envelope")
            null
        }
    }

    private fun buildWrapAad(
        vaultId: ByteArray,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int
    ): ByteArray {
        val output = ByteArrayOutputStream()

        DataOutputStream(output).use { data ->
            data.writeUTF(FORMAT_NAME)
            data.writeInt(FORMAT_VERSION)
            data.writeUTF(KDF_NAME)
            data.writeUTF(KDF_COMBINER_NAME)
            data.writeUTF(KDF_INFO)
            data.writeUTF(CIPHER_NAME)
            data.writeInt(memoryKiB)
            data.writeInt(iterations)
            data.writeInt(parallelism)
            data.writeInt(vaultId.size)
            data.write(vaultId)
            data.writeInt(salt.size)
            data.write(salt)
        }

        return output.toByteArray()
    }

    private fun buildVaultAad(vaultId: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()

        DataOutputStream(output).use { data ->
            data.writeUTF(FORMAT_NAME)
            data.writeInt(FORMAT_VERSION)
            data.writeUTF(CIPHER_NAME)
            data.writeInt(vaultId.size)
            data.write(vaultId)
        }

        return output.toByteArray()
    }
    private fun deriveKek(
        password: String,
        recoveryKey: ByteArray,
        vaultId: ByteArray,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int
    ): ByteArray {
        require(recoveryKey.size == RECOVERY_KEY_BYTES)
        require(vaultId.size == VAULT_ID_BYTES)

        var passwordKey: ByteArray? = null
        return try {
            passwordKey = derivePasswordKey(
                password = password,
                salt = salt,
                memoryKiB = memoryKiB,
                iterations = iterations,
                parallelism = parallelism
            )

            deriveCombinedKek(
                passwordKey = passwordKey,
                recoveryKey = recoveryKey,
                vaultId = vaultId
            )

        } finally {
            passwordKey?.fill(0)
        }
    }

    private fun derivePasswordKey(
        password: String,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int
    ): ByteArray {
        require(salt.size == SALT_BYTES)
        require(memoryKiB in MIN_ARGON_MEMORY_KIB..MAX_ARGON_MEMORY_KIB)
        require(iterations in MIN_ARGON_ITERATIONS..MAX_ARGON_ITERATIONS)
        require(parallelism in MIN_ARGON_PARALLELISM..MAX_ARGON_PARALLELISM)

        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        try {
            val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(memoryKiB)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build()

            val generator = Argon2BytesGenerator()
            generator.init(parameters)

            val output = ByteArray(KEY_BYTES)

            generator.generateBytes(
                passwordBytes,
                output,
                0,
                output.size
            )

            return output

        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun deriveCombinedKek(
        passwordKey: ByteArray,
        recoveryKey: ByteArray,
        vaultId: ByteArray
    ): ByteArray {
        require(passwordKey.size == KEY_BYTES)
        require(recoveryKey.size == RECOVERY_KEY_BYTES)
        require(vaultId.size == VAULT_ID_BYTES)

        var prk: ByteArray? = null
        var expanded: ByteArray? = null
        val info = KDF_INFO.toByteArray(StandardCharsets.UTF_8)
        try {
            val extractMac = Mac.getInstance(HMAC_NAME)

            extractMac.init(
                SecretKeySpec(
                    vaultId,
                    HMAC_NAME
                )
            )

            extractMac.update(passwordKey)
            extractMac.update(recoveryKey)

            prk = extractMac.doFinal()

            val expandMac = Mac.getInstance(HMAC_NAME)
            expandMac.init(
                SecretKeySpec(
                    prk,
                    HMAC_NAME
                )
            )

            expandMac.update(info)
            expandMac.update(1.toByte())

            expanded = expandMac.doFinal()

            return expanded.copyOf(KEY_BYTES)

        } finally {
            prk?.fill(0)
            expanded?.fill(0)
            info.fill(0)
        }
    }

    private fun encryptGcm(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray
    ): ByteArray {
        require(key.size == KEY_BYTES)
        require(nonce.size == GCM_NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce)
        )

        cipher.updateAAD(aad)

        return cipher.doFinal(plaintext)
    }

    private fun decryptGcm(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray
    ): ByteArray {
        require(key.size == KEY_BYTES)
        require(nonce.size == GCM_NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce)
        )

        cipher.updateAAD(aad)

        return cipher.doFinal(ciphertext)
    }

    private fun readTextLimited(context: Context, uri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    val builder = StringBuilder()
                    val buffer = CharArray(8192)
                    var total = 0
                    while (true) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_ENVELOPE_CHARS) {
                            throw IllegalStateException("Vault file exceeds maximum size")
                        }
                        builder.append(buffer, 0, read)
                    }

                    builder.toString()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unable to read vault file", e)
            null
        }
    }

    private fun writeWithRollback(
        context: Context,
        uri: Uri,
        newContents: String,
        previousContents: String
    ) {
        writeRaw(context, uri, newContents)
        val verify = readTextLimited(context, uri)
        if (verify == newContents) return
        Log.e(TAG, "Vault write verification failed; attempting rollback")
        try {
            writeRaw(context, uri, previousContents)

        } catch (rollbackError: Exception) {
            Log.e(TAG, "Vault rollback also failed", rollbackError)
        }

        throw IllegalStateException("Vault write verification failed")
    }

    private fun writeRaw(context: Context, uri: Uri, text: String) {
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Unable to open vault file for writing")

        output.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                writer.write(text)
                writer.flush()
            }
        }
    }

    private fun hasExactKeys(obj: JSONObject, expected: Set<String>): Boolean {
        val found = mutableSetOf<String>()
        val iterator = obj.keys()
        while (iterator.hasNext()) {
            found.add(iterator.next())
        }
        return found == expected
    }

    private fun b64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun b64d(value: String): ByteArray {
        return Base64.decode(value, Base64.NO_WRAP)
    }

    private fun decodeB64Exact(value: String, expectedBytes: Int): ByteArray? {
        val expectedEncodedLength = ((expectedBytes + 2) / 3) * 4
        if (value.length != expectedEncodedLength) return null
        return try {
            val decoded = b64d(value)
            if (decoded.size != expectedBytes) {
                decoded.fill(0)
                null
            } else {
                decoded
            }

        } catch (_: Exception) {
            null
        }
    }

    private fun randomBytes(length: Int): ByteArray {
        return ByteArray(length).also {
            secureRandom.nextBytes(it)
        }
    }

    private data class ParsedKdf(
        val memoryKiB: Int,
        val iterations: Int,
        val parallelism: Int,
        val salt: ByteArray
    )

    private data class ParsedCipherBlob(
        val nonce: ByteArray,
        val ciphertext: ByteArray
    )

    private data class ParsedEnvelope(
        val vaultId: ByteArray,
        val kdf: ParsedKdf,
        val wrappedDek: ParsedCipherBlob,
        val vaultData: ParsedCipherBlob
    ) {
        fun wipe() {
            vaultId.fill(0)
            kdf.salt.fill(0)
            wrappedDek.nonce.fill(0)
            wrappedDek.ciphertext.fill(0)
            vaultData.nonce.fill(0)
            vaultData.ciphertext.fill(0)
        }
    }
}