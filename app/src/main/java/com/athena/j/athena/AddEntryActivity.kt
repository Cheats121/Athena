package com.athena.j.athena

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AddEntryActivity : BaseSecureActivity() {
    companion object {
        private const val TAG = "AthenaVault"
    }
    private var vaultUri = VaultRuntimeSession.getVaultUri()
    private lateinit var hostnameInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: SecureEditText
    private lateinit var cancelButton: TextView
    private lateinit var addButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_entry)

        vaultUri = VaultRuntimeSession.getVaultUri()

        if (vaultUri == null || !VaultRuntimeSession.isUnlocked()) {
            Toast.makeText(this, "Vault session expired. Please unlock again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupToolbar()
        setupViews()
        setupButtons()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.addEntryToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    private fun setupViews() {
        hostnameInput = findViewById(R.id.hostnameInput)
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        cancelButton = findViewById(R.id.cancelButton)
        addButton = findViewById(R.id.addButton)
    }

    private fun setupButtons() {
        cancelButton.setOnClickListener { finish() }
        addButton.setOnClickListener { addCredential() }
    }

    private fun addCredential() {
        val hostname = normalizeHostname(hostnameInput.text.toString())
        val username = usernameInput.text.toString().trim()
        var password = passwordInput.text.toString()

        if (hostname.isBlank()) {
            Toast.makeText(this, "Website or app name is required.", Toast.LENGTH_SHORT).show()
            return
        }
        if (username.isBlank()) {
            Toast.makeText(this, "Username or email is required.", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.isBlank()) {
            password = PasswordGenerator.generate()
            passwordInput.setText(password)
            Toast.makeText(this, "Generated a strong password 🔐", Toast.LENGTH_SHORT).show()
        }

        saveEntry(hostname = hostname, username = username, password = password)
    }

    private fun normalizeHostname(raw: String): String {
        var value = raw.trim()
        if (value.isBlank()) return ""
        value = value.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        if (value.contains("/")) value = value.substringBefore("/")
        return value.trim()
    }

    private fun saveEntry(hostname: String, username: String, password: String) {
        val uri = vaultUri
        if (uri == null) {
            handleExpiredSession()
            return
        }

        val dek = VaultRuntimeSession.getVaultDek()
        if (dek == null) {
            handleExpiredSession()
            return
        }

        val now = System.currentTimeMillis()
        val newEntry = JSONObject().apply {
            put("type", "password")
            put("hostname", hostname)
            put("username", username)
            put("password", password)
            put("created", now)
            put("updated", now)
        }

        addButton.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentVault = VaultManager.loadVaultWithKey(this@AddEntryActivity, uri, dek)
                    ?: throw SecurityException("Vault authentication failed")

                currentVault.put(newEntry)
                VaultManager.saveVaultWithKey(this@AddEntryActivity, uri, currentVault, dek)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddEntryActivity, "Entry added ✅", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    passwordInput.text?.clear()
                    finish()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to add credential", e)
                withContext(Dispatchers.Main) {
                    addButton.isEnabled = true
                    Toast.makeText(this@AddEntryActivity, "Failed to save entry ❌", Toast.LENGTH_LONG).show()
                }

            } finally {
                dek.fill(0)
            }
        }
    }

    private fun handleExpiredSession() {
        VaultRuntimeSession.clear()
        Toast.makeText(this, "Vault session expired. Please unlock again.", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun clearSensitiveData() {
        if (::passwordInput.isInitialized) passwordInput.text?.clear()
    }
}