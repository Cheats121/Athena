package com.athena.j.athena

import android.net.Uri

/**
 * In-memory unlocked vault session.
 *
 *
 * Never persist the master password.
 * Never retain the master password after successful unlock.
 * Keep only the random 256-bit vault DEK while unlocked.
 * Never expose the internal DEK array directly.
 * Wipe key material when the vault is locked.
 */
object VaultRuntimeSession {

    @Volatile
    private var vaultUri: Uri? = null

    @Volatile
    private var vaultDek: ByteArray? = null

    // =============================================================
    // SET SESSION
    // =============================================================

    /**
     * Starts or replaces the active unlocked vault session.
     *
     * The supplied DEK is copied immediately.
     *
     * The caller remains responsible for wiping its own copy.
     */
    @Synchronized
    fun setSession(
        uri: Uri,
        dek: ByteArray
    ) {

        require(
            dek.size == 32
        ) {
            "Vault DEK must be 256 bits"
        }

        // Wipe any previous key before replacing it.
        vaultDek?.fill(0)

        vaultUri =
            uri

        vaultDek =
            dek.copyOf()
    }

    // =============================================================
    // URI
    // =============================================================

    fun getVaultUri(): Uri? {
        return vaultUri
    }

    // =============================================================
    // DEK
    // =============================================================

    /**
     * Returns a copy of the active vault DEK.
     *
     * Never return the authoritative internal array directly.
     *
     * The caller should wipe the returned copy when finished.
     */
    @Synchronized
    fun getVaultKey(): ByteArray? {

        return vaultDek
            ?.copyOf()
    }

    /**
     * Explicit alias with more accurate terminology.
     *
     * New code should prefer getVaultDek().
     */
    @Synchronized
    fun getVaultDek(): ByteArray? {

        return vaultDek
            ?.copyOf()
    }

    // =============================================================
    // SESSION STATE
    // =============================================================

    fun isUnlocked(): Boolean {

        return vaultUri != null &&
                vaultDek != null
    }

    // =============================================================
    // CLEAR
    // =============================================================

    /**
     * Immediately destroys the in-memory unlocked session.
     */
    @Synchronized
    fun clear() {

        vaultDek
            ?.fill(0)

        vaultDek =
            null

        vaultUri =
            null
    }
}