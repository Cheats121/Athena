package com.athena.j.athena

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object RecoveryKeyStore {

    private const val TAG = "AthenaRecoveryKey"
    private const val PREFS_NAME = "athena_recovery_key_store_v4"
    private const val ENTRY_PREFIX = "vault_"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "athena_v4_recovery_key_cache"
    private const val RECOVERY_KEY_BYTES = 32
    private const val VAULT_ID_BYTES = 16
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val ENCRYPTED_RECOVERY_KEY_BYTES = RECOVERY_KEY_BYTES + 16
    private const val AAD_CONTEXT = "ATHENA|V4|RECOVERY-KEY"

    fun save(context: Context, vaultId: ByteArray, recoveryKey: ByteArray): Boolean {
        require(vaultId.size == VAULT_ID_BYTES) { "Invalid vault ID size" }
        require(recoveryKey.size == RECOVERY_KEY_BYTES) { "Invalid recovery key size" }
        var nonce: ByteArray? = null
        var ciphertext: ByteArray? = null
        var aad: ByteArray? = null
        return try {
            val secretKey = getOrCreateKeystoreKey(context)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            nonce = cipher.iv
            if (nonce.size != GCM_NONCE_BYTES) {
                throw IllegalStateException("Unexpected GCM nonce size")
            }
            aad = buildAad(vaultId)
            cipher.updateAAD(aad)
            ciphertext = cipher.doFinal(recoveryKey)
            if (ciphertext.size != ENCRYPTED_RECOVERY_KEY_BYTES) {
                throw IllegalStateException("Unexpected encrypted recovery key size")
            }
            val value = StoredRecoveryKey(nonce = nonce, ciphertext = ciphertext).encode()
            val stored = preferences(context)
                .edit()
                .putString(preferenceKey(vaultId), value)
                .commit()
            if (!stored) {
                Log.w(TAG, "Unable to persist encrypted recovery key")
            }
            stored
        } catch (e: Exception) {
            Log.w(TAG, "Unable to store recovery key")
            false
        } finally {
            nonce?.fill(0)
            ciphertext?.fill(0)
            aad?.fill(0)
        }
    }

    fun load(context: Context, vaultId: ByteArray): ByteArray? {
        if (vaultId.size != VAULT_ID_BYTES) return null
        val encoded = preferences(context)
            .getString(preferenceKey(vaultId), null)
            ?: return null
        var stored: StoredRecoveryKey? = null
        var aad: ByteArray? = null
        var plaintext: ByteArray? = null
        return try {
            stored = StoredRecoveryKey.decode(encoded) ?: return null
            val secretKey = getExistingKeystoreKey() ?: return null
            aad = buildAad(vaultId)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(GCM_TAG_BITS, stored.nonce)
            )
            cipher.updateAAD(aad)
            plaintext = cipher.doFinal(stored.ciphertext)
            if (plaintext.size != RECOVERY_KEY_BYTES) {
                plaintext.fill(0)
                return null
            }
            plaintext.copyOf()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to load recovery key")
            null
        } finally {
            stored?.wipe()
            aad?.fill(0)
            plaintext?.fill(0)
        }
    }

    fun contains(context: Context, vaultId: ByteArray): Boolean {
        if (vaultId.size != VAULT_ID_BYTES) return false
        return preferences(context).contains(preferenceKey(vaultId))
    }

    fun clear(context: Context, vaultId: ByteArray) {
        if (vaultId.size != VAULT_ID_BYTES) return
        val success = preferences(context)
            .edit()
            .remove(preferenceKey(vaultId))
            .commit()
        if (!success) {
            Log.w(TAG, "Unable to remove cached recovery key")
        }
    }

    fun clearAll(context: Context) {
        try {
            val success = preferences(context).edit().clear().commit()
            if (!success) {
                Log.w(TAG, "Unable to clear recovery key preferences")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to clear recovery key preferences", e)
        }
        try {
            val keyStore = getKeyStore()
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to remove recovery key Keystore entry", e)
        }
    }

    private fun getOrCreateKeystoreKey(context: Context): SecretKey {
        getExistingKeystoreKey()?.let { return it }
        return createKeystoreKey(
            context = context,
            requestStrongBox = shouldRequestStrongBox(context)
        )
    }

    private fun getExistingKeystoreKey(): SecretKey? {
        val keyStore = getKeyStore()
        val key = keyStore.getKey(KEY_ALIAS, null)
        return key as? SecretKey
    }

    private fun createKeystoreKey(context: Context, requestStrongBox: Boolean): SecretKey {
        return try {
            generateKeystoreKey(requestStrongBox = requestStrongBox)
        } catch (strongBoxError: StrongBoxUnavailableException) {
            generateKeystoreKey(requestStrongBox = false)
        }
    }

    private fun generateKeystoreKey(requestStrongBox: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && requestStrongBox) {
            builder.setIsStrongBoxBacked(true)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun shouldRequestStrongBox(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_STRONGBOX_KEYSTORE
        )
    }

    private fun getKeyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    private fun buildAad(vaultId: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeUTF(AAD_CONTEXT)
            data.writeInt(vaultId.size)
            data.write(vaultId)
        }
        return output.toByteArray()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private fun preferenceKey(vaultId: ByteArray): String {
        val encoded = Base64.encodeToString(
            vaultId,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return ENTRY_PREFIX + encoded
    }
    private data class StoredRecoveryKey(
        val nonce: ByteArray,
        val ciphertext: ByteArray
    ) {
        fun encode(): String {
            val nonceBase64 = Base64.encodeToString(nonce, Base64.NO_WRAP)
            val ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            return "$nonceBase64.$ciphertextBase64"
        }

        fun wipe() {
            nonce.fill(0)
            ciphertext.fill(0)
        }

        companion object {
            fun decode(encoded: String): StoredRecoveryKey? {
                val parts = encoded.split('.', limit = 2)
                if (parts.size != 2) return null
                var nonce: ByteArray? = null
                var ciphertext: ByteArray? = null
                return try {
                    nonce = Base64.decode(parts[0], Base64.NO_WRAP)
                    ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
                    if (nonce.size != GCM_NONCE_BYTES || ciphertext.size != ENCRYPTED_RECOVERY_KEY_BYTES) {
                        nonce.fill(0)
                        ciphertext.fill(0)
                        null
                    } else {
                        StoredRecoveryKey(nonce = nonce, ciphertext = ciphertext)
                    }
                } catch (_: Exception) {
                    nonce?.fill(0)
                    ciphertext?.fill(0)
                    null
                }
            }
        }
    }
}