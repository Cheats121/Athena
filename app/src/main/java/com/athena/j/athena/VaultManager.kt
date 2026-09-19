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
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Athena Vault Format v3
 *
 * Password:
 *
 *      master password
 *            ↓
 *        Argon2id
 *            ↓
 *       256-bit KEK
 *            ↓
 *      unwrap random DEK
 *
 *
 * Vault data:
 *
 *      random 256-bit DEK
 *            ↓
 *       AES-256-GCM
 *            ↓
 *      encrypted vault
 *
 *
 * The DEK is random and independent of the master password.
 *
 * Changing the master password therefore only requires
 * re-wrapping the DEK, not re-encrypting every vault entry.
 *
 * There is intentionally:
 *
 * - no AES-CBC
 * - no legacy v1/v2 support
 * - no redundant HMAC
 *
 * AES-GCM provides authenticated encryption.
 */
object VaultManager {

    private const val TAG = "AthenaVault"

    // =============================================================
    // FORMAT
    // =============================================================

    private const val FORMAT_NAME =
        "athena-vault"

    private const val FORMAT_VERSION =
        3

    private const val KDF_NAME =
        "argon2id"

    private const val CIPHER_NAME =
        "aes-256-gcm"

    // =============================================================
    // ARGON2ID
    // =============================================================

    /*
     * Current creation parameters.
     *
     * 64 MiB memory
     * 3 iterations
     * 4 lanes
     */
    private const val ARGON_MEMORY_KIB =
        65_536

    private const val ARGON_ITERATIONS =
        3

    private const val ARGON_PARALLELISM =
        4

    /*
     * Strict upper/lower bounds when reading vault files.
     *
     * Never blindly trust attacker-controlled KDF parameters.
     */
    private const val MIN_ARGON_MEMORY_KIB =
        32_768

    private const val MAX_ARGON_MEMORY_KIB =
        262_144

    private const val MIN_ARGON_ITERATIONS =
        1

    private const val MAX_ARGON_ITERATIONS =
        10

    private const val MIN_ARGON_PARALLELISM =
        1

    private const val MAX_ARGON_PARALLELISM =
        8

    // =============================================================
    // CRYPTO SIZES
    // =============================================================

    private const val KEY_BYTES =
        32

    private const val SALT_BYTES =
        16

    private const val VAULT_ID_BYTES =
        16

    private const val GCM_NONCE_BYTES =
        12

    private const val GCM_TAG_BITS =
        128

    /*
     * 32-byte DEK + 16-byte GCM tag.
     */
    private const val WRAPPED_DEK_BYTES =
        KEY_BYTES + 16

    // =============================================================
    // FILE LIMITS
    // =============================================================

    /*
     * Protect against malicious/corrupt files attempting
     * to exhaust application memory.
     */
    private const val MAX_ENVELOPE_CHARS =
        32 * 1024 * 1024

    private const val MAX_VAULT_CIPHERTEXT_BYTES =
        16 * 1024 * 1024

    // =============================================================
    // RANDOM
    // =============================================================

    private val secureRandom =
        SecureRandom()

    // =============================================================
    // CREATE VAULT
    // =============================================================

    /**
     * Creates a brand-new Athena v3 vault.
     *
     * This method:
     *
     * 1. creates a random vault identifier
     * 2. creates a random 256-bit DEK
     * 3. derives a password KEK using Argon2id
     * 4. wraps the DEK using AES-GCM
     * 5. encrypts an empty JSONArray using the DEK
     */
    fun createVault(
        context: Context,
        uri: Uri,
        password: String
    ) {

        require(
            password.isNotEmpty()
        ) {
            "Master password cannot be empty"
        }

        val previousContents =
            readTextLimited(
                context,
                uri
            ) ?: ""

        val vaultId =
            randomBytes(
                VAULT_ID_BYTES
            )

        val salt =
            randomBytes(
                SALT_BYTES
            )

        val dek =
            randomBytes(
                KEY_BYTES
            )

        val kek =
            deriveKek(
                password = password,
                salt = salt,
                memoryKiB = ARGON_MEMORY_KIB,
                iterations = ARGON_ITERATIONS,
                parallelism = ARGON_PARALLELISM
            )

        val wrapNonce =
            randomBytes(
                GCM_NONCE_BYTES
            )

        val dataNonce =
            randomBytes(
                GCM_NONCE_BYTES
            )

        val emptyVaultPlaintext =
            JSONArray()
                .toString()
                .toByteArray(
                    StandardCharsets.UTF_8
                )

        var wrappedDek: ByteArray? =
            null

        var encryptedVault: ByteArray? =
            null

        var wrapAad: ByteArray? =
            null

        var dataAad: ByteArray? =
            null

        try {

            wrapAad =
                buildWrapAad(
                    vaultId = vaultId,
                    salt = salt,
                    memoryKiB = ARGON_MEMORY_KIB,
                    iterations = ARGON_ITERATIONS,
                    parallelism = ARGON_PARALLELISM
                )

            wrappedDek =
                encryptGcm(
                    key = kek,
                    nonce = wrapNonce,
                    plaintext = dek,
                    aad = wrapAad
                )

            dataAad =
                buildVaultAad(
                    vaultId
                )

            encryptedVault =
                encryptGcm(
                    key = dek,
                    nonce = dataNonce,
                    plaintext = emptyVaultPlaintext,
                    aad = dataAad
                )

            val envelope =
                createEnvelope(
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

    // =============================================================
    // LOAD WITH PASSWORD
    // =============================================================

    /**
     * Opens a vault using the master password.
     */
    fun loadVault(
        context: Context,
        uri: Uri,
        password: String
    ): JSONArray? {

        var parsed: ParsedEnvelope? =
            null

        var kek: ByteArray? =
            null

        var dek: ByteArray? =
            null

        var wrapAad: ByteArray? =
            null

        var plaintext: ByteArray? =
            null

        var dataAad: ByteArray? =
            null

        return try {

            parsed =
                readAndParseEnvelope(
                    context,
                    uri
                )
                    ?: return null

            kek =
                deriveKek(
                    password = password,
                    salt = parsed.kdf.salt,
                    memoryKiB = parsed.kdf.memoryKiB,
                    iterations = parsed.kdf.iterations,
                    parallelism = parsed.kdf.parallelism
                )

            wrapAad =
                buildWrapAad(
                    vaultId = parsed.vaultId,
                    salt = parsed.kdf.salt,
                    memoryKiB = parsed.kdf.memoryKiB,
                    iterations = parsed.kdf.iterations,
                    parallelism = parsed.kdf.parallelism
                )

            dek =
                decryptGcm(
                    key = kek,
                    nonce = parsed.wrappedDek.nonce,
                    ciphertext = parsed.wrappedDek.ciphertext,
                    aad = wrapAad
                )

            if (
                dek.size != KEY_BYTES
            ) {

                return null
            }

            dataAad =
                buildVaultAad(
                    parsed.vaultId
                )

            plaintext =
                decryptGcm(
                    key = dek,
                    nonce = parsed.vaultData.nonce,
                    ciphertext = parsed.vaultData.ciphertext,
                    aad = dataAad
                )

            val json =
                String(
                    plaintext,
                    StandardCharsets.UTF_8
                )

            JSONArray(
                json
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Vault authentication/decryption failed"
            )

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

    // =============================================================
    // GET DEK FROM PASSWORD
    // =============================================================

    /**
     * Returns the random vault DEK after successfully verifying
     * the master password and the vault.
     *
     * Despite the historical method name, this no longer returns
     * the password-derived key.
     *
     * It now returns the random 256-bit Vault DEK.
     */
    fun deriveVaultKey(
        context: Context,
        uri: Uri,
        password: String
    ): ByteArray? {

        var parsed: ParsedEnvelope? =
            null

        var kek: ByteArray? =
            null

        var dek: ByteArray? =
            null

        var wrapAad: ByteArray? =
            null

        var dataAad: ByteArray? =
            null

        var plaintext: ByteArray? =
            null

        try {

            parsed =
                readAndParseEnvelope(
                    context,
                    uri
                )
                    ?: return null

            kek =
                deriveKek(
                    password = password,
                    salt = parsed.kdf.salt,
                    memoryKiB = parsed.kdf.memoryKiB,
                    iterations = parsed.kdf.iterations,
                    parallelism = parsed.kdf.parallelism
                )

            wrapAad =
                buildWrapAad(
                    vaultId = parsed.vaultId,
                    salt = parsed.kdf.salt,
                    memoryKiB = parsed.kdf.memoryKiB,
                    iterations = parsed.kdf.iterations,
                    parallelism = parsed.kdf.parallelism
                )

            dek =
                decryptGcm(
                    key = kek,
                    nonce = parsed.wrappedDek.nonce,
                    ciphertext = parsed.wrappedDek.ciphertext,
                    aad = wrapAad
                )

            if (
                dek.size != KEY_BYTES
            ) {

                dek.fill(0)

                return null
            }

            /*
             * Verify the DEK against the actual vault ciphertext
             * before returning it.
             */
            dataAad =
                buildVaultAad(
                    parsed.vaultId
                )

            plaintext =
                decryptGcm(
                    key = dek,
                    nonce = parsed.vaultData.nonce,
                    ciphertext = parsed.vaultData.ciphertext,
                    aad = dataAad
                )

            /*
             * Ensure plaintext is actually valid vault JSON.
             */
            JSONArray(
                String(
                    plaintext,
                    StandardCharsets.UTF_8
                )
            )

            /*
             * Return a defensive copy.
             *
             * The local copy will be wiped below.
             */
            return dek.copyOf()

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Master password verification failed"
            )

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

    // =============================================================
    // LOAD USING EXISTING DEK
    // =============================================================

    /**
     * Used after the vault has already been unlocked.
     *
     * biometric unlock also eventually supplies this same DEK.
     */
    fun loadVaultWithKey(
        context: Context,
        uri: Uri,
        vaultKey: ByteArray
    ): JSONArray? {

        if (
            vaultKey.size != KEY_BYTES
        ) {

            return null
        }

        var parsed: ParsedEnvelope? =
            null

        var dataAad: ByteArray? =
            null

        var plaintext: ByteArray? =
            null

        return try {

            parsed =
                readAndParseEnvelope(
                    context,
                    uri
                )
                    ?: return null

            dataAad =
                buildVaultAad(
                    parsed.vaultId
                )

            plaintext =
                decryptGcm(
                    key = vaultKey,
                    nonce = parsed.vaultData.nonce,
                    ciphertext = parsed.vaultData.ciphertext,
                    aad = dataAad
                )

            JSONArray(
                String(
                    plaintext,
                    StandardCharsets.UTF_8
                )
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Vault DEK authentication failed"
            )

            null

        } finally {

            dataAad?.fill(0)
            plaintext?.fill(0)

            parsed?.wipe()
        }
    }

    // =============================================================
    // SAVE WITH PASSWORD
    // =============================================================

    fun saveVault(
        context: Context,
        uri: Uri,
        entries: JSONArray,
        password: String
    ) {

        var parsed: ParsedEnvelope? =
            null

        var kek: ByteArray? =
            null

        var dek: ByteArray? =
            null

        var wrapAad: ByteArray? =
            null

        var verificationAad: ByteArray? =
            null

        var verificationPlaintext: ByteArray? =
            null

        try {

            val oldRaw =
                readTextLimited(
                    context,
                    uri
                )
                    ?: throw IllegalStateException(
                        "Vault file unavailable"
                    )

            parsed =
                parseEnvelope(
                    oldRaw
                )
                    ?: throw SecurityException(
                        "Invalid vault format"
                    )

            kek =
                deriveKek(
                    password = password,
                    salt = parsed.kdf.salt,
                    memoryKiB = parsed.kdf.memoryKiB,
                    iterations = parsed.kdf.iterations,
                    parallelism = parsed.kdf.parallelism
                )

            wrapAad =
                buildWrapAad(
                    vaultId = parsed.vaultId,
                    salt = parsed.kdf.salt,
                    memoryKiB = parsed.kdf.memoryKiB,
                    iterations = parsed.kdf.iterations,
                    parallelism = parsed.kdf.parallelism
                )

            dek =
                decryptGcm(
                    key = kek,
                    nonce = parsed.wrappedDek.nonce,
                    ciphertext = parsed.wrappedDek.ciphertext,
                    aad = wrapAad
                )

            if (
                dek.size != KEY_BYTES
            ) {

                throw SecurityException(
                    "Invalid DEK"
                )
            }

            /*
             * Verify current vault before allowing an overwrite.
             */
            verificationAad =
                buildVaultAad(
                    parsed.vaultId
                )

            verificationPlaintext =
                decryptGcm(
                    key = dek,
                    nonce = parsed.vaultData.nonce,
                    ciphertext = parsed.vaultData.ciphertext,
                    aad = verificationAad
                )

            JSONArray(
                String(
                    verificationPlaintext,
                    StandardCharsets.UTF_8
                )
            )

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

    // =============================================================
    // SAVE WITH EXISTING DEK
    // =============================================================

    fun saveVaultWithKey(
        context: Context,
        uri: Uri,
        entries: JSONArray,
        vaultKey: ByteArray
    ) {

        require(
            vaultKey.size == KEY_BYTES
        ) {
            "Invalid vault key size"
        }

        var parsed: ParsedEnvelope? =
            null

        var verificationAad: ByteArray? =
            null

        var verificationPlaintext: ByteArray? =
            null

        try {

            val oldRaw =
                readTextLimited(
                    context,
                    uri
                )
                    ?: throw IllegalStateException(
                        "Vault file unavailable"
                    )

            parsed =
                parseEnvelope(
                    oldRaw
                )
                    ?: throw SecurityException(
                        "Invalid vault"
                    )

            /*
             * CRITICAL:
             *
             * Never save using a DEK until we prove that DEK
             * authenticates the existing vault.
             *
             * Otherwise a wrong key could permanently overwrite
             * the vault with data the password-wrapped DEK
             * could never decrypt.
             */
            verificationAad =
                buildVaultAad(
                    parsed.vaultId
                )

            verificationPlaintext =
                decryptGcm(
                    key = vaultKey,
                    nonce = parsed.vaultData.nonce,
                    ciphertext = parsed.vaultData.ciphertext,
                    aad = verificationAad
                )

            JSONArray(
                String(
                    verificationPlaintext,
                    StandardCharsets.UTF_8
                )
            )

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

    // =============================================================
    // SAVE INTERNAL
    // =============================================================

    private fun saveEnvelopeWithDek(
        context: Context,
        uri: Uri,
        oldRaw: String,
        parsed: ParsedEnvelope,
        entries: JSONArray,
        dek: ByteArray
    ) {

        val plaintext =
            entries
                .toString()
                .toByteArray(
                    StandardCharsets.UTF_8
                )

        val nonce =
            randomBytes(
                GCM_NONCE_BYTES
            )

        var aad: ByteArray? =
            null

        var ciphertext: ByteArray? =
            null

        try {

            aad =
                buildVaultAad(
                    parsed.vaultId
                )

            ciphertext =
                encryptGcm(
                    key = dek,
                    nonce = nonce,
                    plaintext = plaintext,
                    aad = aad
                )

            if (
                ciphertext.size >
                MAX_VAULT_CIPHERTEXT_BYTES
            ) {

                throw IllegalStateException(
                    "Vault exceeds maximum size"
                )
            }

            /*
             * Password-wrapped DEK stays unchanged.
             *
             * Only the vault data gets a new nonce and ciphertext.
             */
            val envelope =
                createEnvelope(
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

    // =============================================================
    // INTEGRITY
    // =============================================================

    fun verifyIntegrity(
        context: Context,
        uri: Uri,
        password: String
    ): Boolean {

        return loadVault(
            context,
            uri,
            password
        ) != null
    }

    fun verifyIntegrityWithKey(
        context: Context,
        uri: Uri,
        vaultKey: ByteArray
    ): Boolean {

        return loadVaultWithKey(
            context,
            uri,
            vaultKey
        ) != null
    }

    // =============================================================
    // CREATE JSON ENVELOPE
    // =============================================================

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

            put(
                "format",
                FORMAT_NAME
            )

            put(
                "version",
                FORMAT_VERSION
            )

            put(
                "vaultId",
                b64(
                    vaultId
                )
            )

            put(
                "kdf",
                JSONObject().apply {

                    put(
                        "name",
                        KDF_NAME
                    )

                    put(
                        "memoryKiB",
                        memoryKiB
                    )

                    put(
                        "iterations",
                        iterations
                    )

                    put(
                        "parallelism",
                        parallelism
                    )

                    put(
                        "salt",
                        b64(
                            salt
                        )
                    )
                }
            )

            put(
                "wrappedDek",
                JSONObject().apply {

                    put(
                        "cipher",
                        CIPHER_NAME
                    )

                    put(
                        "nonce",
                        b64(
                            wrapNonce
                        )
                    )

                    put(
                        "ciphertext",
                        b64(
                            wrappedDek
                        )
                    )
                }
            )

            put(
                "vault",
                JSONObject().apply {

                    put(
                        "cipher",
                        CIPHER_NAME
                    )

                    put(
                        "nonce",
                        b64(
                            vaultNonce
                        )
                    )

                    put(
                        "ciphertext",
                        b64(
                            vaultCiphertext
                        )
                    )
                }
            )
        }
    }

    // =============================================================
    // PARSE
    // =============================================================

    private fun readAndParseEnvelope(
        context: Context,
        uri: Uri
    ): ParsedEnvelope? {

        val raw =
            readTextLimited(
                context,
                uri
            )
                ?: return null

        return parseEnvelope(
            raw
        )
    }

    /**
     * Strict parser.
     *
     * A malicious vault file is treated as attacker-controlled input.
     */
    private fun parseEnvelope(
        raw: String
    ): ParsedEnvelope? {

        if (
            raw.isBlank() ||
            raw.length > MAX_ENVELOPE_CHARS
        ) {

            return null
        }

        return try {

            val root =
                JSONObject(
                    raw
                )

            if (
                !hasExactKeys(
                    root,
                    setOf(
                        "format",
                        "version",
                        "vaultId",
                        "kdf",
                        "wrappedDek",
                        "vault"
                    )
                )
            ) {

                return null
            }

            if (
                root.getString(
                    "format"
                ) != FORMAT_NAME
            ) {

                return null
            }

            if (
                root.getInt(
                    "version"
                ) != FORMAT_VERSION
            ) {

                return null
            }

            val vaultId =
                decodeB64Exact(
                    root.getString(
                        "vaultId"
                    ),
                    VAULT_ID_BYTES
                )
                    ?: return null

            val kdfJson =
                root.getJSONObject(
                    "kdf"
                )

            if (
                !hasExactKeys(
                    kdfJson,
                    setOf(
                        "name",
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

            if (
                kdfJson.getString(
                    "name"
                ) != KDF_NAME
            ) {

                vaultId.fill(0)

                return null
            }

            val memoryKiB =
                kdfJson.getInt(
                    "memoryKiB"
                )

            val iterations =
                kdfJson.getInt(
                    "iterations"
                )

            val parallelism =
                kdfJson.getInt(
                    "parallelism"
                )

            if (
                memoryKiB !in
                MIN_ARGON_MEMORY_KIB..
                MAX_ARGON_MEMORY_KIB
            ) {

                vaultId.fill(0)

                return null
            }

            if (
                iterations !in
                MIN_ARGON_ITERATIONS..
                MAX_ARGON_ITERATIONS
            ) {

                vaultId.fill(0)

                return null
            }

            if (
                parallelism !in
                MIN_ARGON_PARALLELISM..
                MAX_ARGON_PARALLELISM
            ) {

                vaultId.fill(0)

                return null
            }

            val salt =
                decodeB64Exact(
                    kdfJson.getString(
                        "salt"
                    ),
                    SALT_BYTES
                )
                    ?: run {

                        vaultId.fill(0)

                        return null
                    }

            val wrappedJson =
                root.getJSONObject(
                    "wrappedDek"
                )

            if (
                !hasExactKeys(
                    wrappedJson,
                    setOf(
                        "cipher",
                        "nonce",
                        "ciphertext"
                    )
                )
            ) {

                vaultId.fill(0)
                salt.fill(0)

                return null
            }

            if (
                wrappedJson.getString(
                    "cipher"
                ) != CIPHER_NAME
            ) {

                vaultId.fill(0)
                salt.fill(0)

                return null
            }

            val wrapNonce =
                decodeB64Exact(
                    wrappedJson.getString(
                        "nonce"
                    ),
                    GCM_NONCE_BYTES
                )
                    ?: run {

                        vaultId.fill(0)
                        salt.fill(0)

                        return null
                    }

            val wrappedDek =
                decodeB64Exact(
                    wrappedJson.getString(
                        "ciphertext"
                    ),
                    WRAPPED_DEK_BYTES
                )
                    ?: run {

                        vaultId.fill(0)
                        salt.fill(0)
                        wrapNonce.fill(0)

                        return null
                    }

            val vaultJson =
                root.getJSONObject(
                    "vault"
                )

            if (
                !hasExactKeys(
                    vaultJson,
                    setOf(
                        "cipher",
                        "nonce",
                        "ciphertext"
                    )
                )
            ) {

                vaultId.fill(0)
                salt.fill(0)
                wrapNonce.fill(0)
                wrappedDek.fill(0)

                return null
            }

            if (
                vaultJson.getString(
                    "cipher"
                ) != CIPHER_NAME
            ) {

                vaultId.fill(0)
                salt.fill(0)
                wrapNonce.fill(0)
                wrappedDek.fill(0)

                return null
            }

            val vaultNonce =
                decodeB64Exact(
                    vaultJson.getString(
                        "nonce"
                    ),
                    GCM_NONCE_BYTES
                )
                    ?: run {

                        vaultId.fill(0)
                        salt.fill(0)
                        wrapNonce.fill(0)
                        wrappedDek.fill(0)

                        return null
                    }

            val vaultCiphertextB64 =
                vaultJson.getString(
                    "ciphertext"
                )

            /*
             * Reject obviously huge Base64 before decoding it.
             */
            if (
                vaultCiphertextB64.length >
                (
                        MAX_VAULT_CIPHERTEXT_BYTES *
                                4 / 3
                        ) + 16
            ) {

                vaultId.fill(0)
                salt.fill(0)

                wrapNonce.fill(0)
                wrappedDek.fill(0)
                vaultNonce.fill(0)

                return null
            }

            val vaultCiphertext =
                b64d(
                    vaultCiphertextB64
                )

            if (
                vaultCiphertext.size < 16 ||
                vaultCiphertext.size >
                MAX_VAULT_CIPHERTEXT_BYTES
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

            Log.w(
                TAG,
                "Invalid Athena vault envelope"
            )

            null
        }
    }

    // =============================================================
    // AAD
    // =============================================================

    /**
     * Authenticated metadata used while wrapping the DEK.
     *
     * Binary encoding avoids relying on JSONObject key order.
     */
    private fun buildWrapAad(
        vaultId: ByteArray,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int
    ): ByteArray {

        val output =
            ByteArrayOutputStream()

        DataOutputStream(
            output
        ).use { data ->

            data.writeUTF(
                FORMAT_NAME
            )

            data.writeInt(
                FORMAT_VERSION
            )

            data.writeUTF(
                KDF_NAME
            )

            data.writeUTF(
                CIPHER_NAME
            )

            data.writeInt(
                memoryKiB
            )

            data.writeInt(
                iterations
            )

            data.writeInt(
                parallelism
            )

            data.writeInt(
                vaultId.size
            )

            data.write(
                vaultId
            )

            data.writeInt(
                salt.size
            )

            data.write(
                salt
            )
        }

        return output.toByteArray()
    }

    /**
     * Metadata authenticated with the encrypted vault contents.
     *
     * This intentionally does not contain password-specific
     * wrapping data, allowing the master password to be changed
     * later without re-encrypting all vault contents.
     */
    private fun buildVaultAad(
        vaultId: ByteArray
    ): ByteArray {

        val output =
            ByteArrayOutputStream()

        DataOutputStream(
            output
        ).use { data ->

            data.writeUTF(
                FORMAT_NAME
            )

            data.writeInt(
                FORMAT_VERSION
            )

            data.writeUTF(
                CIPHER_NAME
            )

            data.writeInt(
                vaultId.size
            )

            data.write(
                vaultId
            )
        }

        return output.toByteArray()
    }

    // =============================================================
    // ARGON2ID
    // =============================================================

    private fun deriveKek(
        password: String,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int
    ): ByteArray {

        require(
            salt.size == SALT_BYTES
        )

        require(
            memoryKiB in
                    MIN_ARGON_MEMORY_KIB..
                    MAX_ARGON_MEMORY_KIB
        )

        require(
            iterations in
                    MIN_ARGON_ITERATIONS..
                    MAX_ARGON_ITERATIONS
        )

        require(
            parallelism in
                    MIN_ARGON_PARALLELISM..
                    MAX_ARGON_PARALLELISM
        )

        val passwordBytes =
            password.toByteArray(
                StandardCharsets.UTF_8
            )

        try {

            val parameters =
                Argon2Parameters
                    .Builder(
                        Argon2Parameters.ARGON2_id
                    )
                    .withSalt(
                        salt
                    )
                    .withMemoryAsKB(
                        memoryKiB
                    )
                    .withIterations(
                        iterations
                    )
                    .withParallelism(
                        parallelism
                    )
                    .build()

            val generator =
                Argon2BytesGenerator()

            generator.init(
                parameters
            )

            val output =
                ByteArray(
                    KEY_BYTES
                )

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

    // =============================================================
    // AES-256-GCM
    // =============================================================

    private fun encryptGcm(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray
    ): ByteArray {

        require(
            key.size == KEY_BYTES
        )

        require(
            nonce.size == GCM_NONCE_BYTES
        )

        val cipher =
            Cipher.getInstance(
                "AES/GCM/NoPadding"
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(
                key,
                "AES"
            ),
            GCMParameterSpec(
                GCM_TAG_BITS,
                nonce
            )
        )

        cipher.updateAAD(
            aad
        )

        return cipher.doFinal(
            plaintext
        )
    }

    private fun decryptGcm(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray
    ): ByteArray {

        require(
            key.size == KEY_BYTES
        )

        require(
            nonce.size == GCM_NONCE_BYTES
        )

        val cipher =
            Cipher.getInstance(
                "AES/GCM/NoPadding"
            )

        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(
                key,
                "AES"
            ),
            GCMParameterSpec(
                GCM_TAG_BITS,
                nonce
            )
        )

        cipher.updateAAD(
            aad
        )

        return cipher.doFinal(
            ciphertext
        )
    }

    // =============================================================
    // FILE IO
    // =============================================================

    /**
     * Reads with an explicit size limit.
     */
    private fun readTextLimited(
        context: Context,
        uri: Uri
    ): String? {

        return try {

            val input =
                context
                    .contentResolver
                    .openInputStream(
                        uri
                    )
                    ?: return null

            input.use { stream ->

                InputStreamReader(
                    stream,
                    Charsets.UTF_8
                ).use { reader ->

                    val builder =
                        StringBuilder()

                    val buffer =
                        CharArray(
                            8192
                        )

                    var total =
                        0

                    while (true) {

                        val read =
                            reader.read(
                                buffer
                            )

                        if (
                            read < 0
                        ) {
                            break
                        }

                        total +=
                            read

                        if (
                            total >
                            MAX_ENVELOPE_CHARS
                        ) {

                            throw IllegalStateException(
                                "Vault file exceeds maximum size"
                            )
                        }

                        builder.append(
                            buffer,
                            0,
                            read
                        )
                    }

                    builder.toString()
                }
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Unable to read vault file",
                e
            )

            null
        }
    }

    /**
     * Writes and verifies the exact encrypted envelope.
     *
     * If verification fails, Athena attempts to restore the
     * previous encrypted vault contents.
     */
    private fun writeWithRollback(
        context: Context,
        uri: Uri,
        newContents: String,
        previousContents: String
    ) {

        writeRaw(
            context,
            uri,
            newContents
        )

        val verify =
            readTextLimited(
                context,
                uri
            )

        if (
            verify == newContents
        ) {

            return
        }

        Log.e(
            TAG,
            "Vault write verification failed; attempting rollback"
        )

        try {

            writeRaw(
                context,
                uri,
                previousContents
            )

        } catch (rollbackError: Exception) {

            Log.e(
                TAG,
                "Vault rollback also failed",
                rollbackError
            )
        }

        throw IllegalStateException(
            "Vault write verification failed"
        )
    }

    private fun writeRaw(
        context: Context,
        uri: Uri,
        text: String
    ) {

        val output =
            context
                .contentResolver
                .openOutputStream(
                    uri,
                    "wt"
                )
                ?: throw IllegalStateException(
                    "Unable to open vault file for writing"
                )

        output.use { stream ->

            OutputStreamWriter(
                stream,
                Charsets.UTF_8
            ).use { writer ->

                writer.write(
                    text
                )

                writer.flush()
            }
        }
    }

    // =============================================================
    // STRICT JSON KEYS
    // =============================================================

    private fun hasExactKeys(
        obj: JSONObject,
        expected: Set<String>
    ): Boolean {

        val found =
            mutableSetOf<String>()

        val iterator =
            obj.keys()

        while (
            iterator.hasNext()
        ) {

            found.add(
                iterator.next()
            )
        }

        return found ==
                expected
    }

    // =============================================================
    // BASE64
    // =============================================================

    private fun b64(
        bytes: ByteArray
    ): String {

        return Base64.encodeToString(
            bytes,
            Base64.NO_WRAP
        )
    }

    private fun b64d(
        value: String
    ): ByteArray {

        return Base64.decode(
            value,
            Base64.NO_WRAP
        )
    }

    private fun decodeB64Exact(
        value: String,
        expectedBytes: Int
    ): ByteArray? {

        return try {

            val decoded =
                b64d(
                    value
                )

            if (
                decoded.size !=
                expectedBytes
            ) {

                decoded.fill(0)

                null

            } else {

                decoded
            }

        } catch (_: Exception) {

            null
        }
    }

    // =============================================================
    // RANDOM
    // =============================================================

    private fun randomBytes(
        length: Int
    ): ByteArray {

        return ByteArray(
            length
        ).also {

            secureRandom.nextBytes(
                it
            )
        }
    }

    // =============================================================
    // PARSED TYPES
    // =============================================================

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