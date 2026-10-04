package com.athena.j.athena

import android.net.Uri

object VaultRuntimeSession {
    @Volatile
    private var vaultUri: Uri? = null
    @Volatile
    private var vaultDek: ByteArray? = null
    @Synchronized
    fun setSession(uri: Uri, dek: ByteArray) {
        require(dek.size == 32) { "Vault DEK must be 256 bits" }
        vaultDek?.fill(0)
        vaultUri = uri
        vaultDek = dek.copyOf()
    }

    fun getVaultUri(): Uri? {
        return vaultUri
    }

    @Synchronized
    fun getVaultKey(): ByteArray? {
        return vaultDek?.copyOf()
    }

    @Synchronized
    fun getVaultDek(): ByteArray? {
        return vaultDek?.copyOf()
    }

    fun isUnlocked(): Boolean {
        return vaultUri != null && vaultDek != null
    }

    @Synchronized
    fun clear() {
        vaultDek?.fill(0)
        vaultDek = null
        vaultUri = null
    }
}