package com.athena.j.athena

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.documentfile.provider.DocumentFile
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class VaultActivity : BaseSecureActivity() {

    companion object {
        private const val TAG = "AthenaVault"
    }
    private lateinit var vaultRecyclerView: RecyclerView
    private lateinit var adapter: VaultAdapter
    private lateinit var addEntryButton: FloatingActionButton
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var loadingOverlay: View
    private var vaultUri: Uri? = null
    private var vaultData: JSONArray = JSONArray()
    private var initialSecureResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault)
        setupViews()
        setupToolbar()
        setupNavigation()
        setupRecyclerView()
        setupSearchBar()
        setupAddButton()
        vaultUri = VaultRuntimeSession.getVaultUri()
            ?: intent.getStringExtra("vaultUri")?.let { Uri.parse(it) }
                    ?: VaultSessionManager.getSessionUri(this)
        if (vaultUri == null || !VaultRuntimeSession.isUnlocked()) {
            Toast.makeText(this, "Vault session expired. Please unlock the vault again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        updateStaticHeader()
        showLoading(true)
        loadVault()
    }

    private fun setupViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        vaultRecyclerView = findViewById(R.id.vaultRecyclerView)
        addEntryButton = findViewById(R.id.addEntryButton)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.vaultToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.navigationIcon?.setTint(resources.getColor(android.R.color.white, theme))
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )

        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        toggle.isDrawerIndicatorEnabled = false
        toolbar.setNavigationOnClickListener {
            if (drawerLayout.isDrawerOpen(navigationView)) {
                drawerLayout.closeDrawer(navigationView)
            } else {
                drawerLayout.openDrawer(navigationView)
            }
        }
    }

    private fun setupNavigation() {
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { drawerLayout.closeDrawers(); true }
                R.id.nav_lock_vault -> {
                    drawerLayout.closeDrawers()
                    VaultLocker.lockNow(this)
                    true
                }
                R.id.nav_about -> {
                    drawerLayout.closeDrawers()
                    startActivity(Intent(this, AboutActivity::class.java))
                    pushSlideTransition()
                    true
                }
                R.id.nav_settings -> {
                    drawerLayout.closeDrawers()
                    Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        vaultRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = VaultAdapter(emptyList()) { entry, realIndex ->
            openEntryDetail(entry, realIndex)
        }

        vaultRecyclerView.adapter = adapter
        vaultRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 8 && addEntryButton.isShown) {
                    addEntryButton.hide()
                } else if (dy < -8 && !addEntryButton.isShown) {
                    addEntryButton.show()
                }
            }
        })
    }

    private fun setupSearchBar() {
        val searchBar = findViewById<EditText>(R.id.searchBar)
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                filterVault(query)
            }
        })
    }

    private fun filterVault(query: String) {
        if (query.isBlank()) {
            refreshList()
            return
        }
        val filtered = JSONArray()
        for (i in 0 until vaultData.length()) {
            val source = vaultData.optJSONObject(i) ?: continue
            if (!isPasswordEntry(source)) continue
            val hostname = source.optString("hostname")
            val username = source.optString("username")
            if (
                hostname.contains(query, ignoreCase = true) ||
                username.contains(query, ignoreCase = true)
            ) {
                val displayEntry = JSONObject(source.toString())
                displayEntry.put("realIndex", i)
                filtered.put(displayEntry)
            }
        }
        adapter.updateData(filtered)
    }

    private fun setupAddButton() {
        addEntryButton.setOnClickListener {
            if (!VaultRuntimeSession.isUnlocked()) {
                Toast.makeText(this, "Vault session expired.", Toast.LENGTH_LONG).show()
                VaultLocker.lockNow(this)
                return@setOnClickListener
            }
            startActivity(Intent(this, AddEntryActivity::class.java))
            pushSlideTransition()
        }
    }

    private fun updateStaticHeader() {
        val uri = vaultUri ?: return
        val header = navigationView.getHeaderView(0)
        val vaultNameText = header.findViewById<TextView>(R.id.vaultNameText)
        val vaultEntryCountText = header.findViewById<TextView>(R.id.vaultEntryCountText)
        val displayName = getVaultDisplayName(uri)
        vaultNameText.text = displayName.substringBeforeLast(".")
        vaultEntryCountText.text = "Loading…"
    }

    private fun updateLoadedHeader() {
        val header = navigationView.getHeaderView(0)
        val countText = header.findViewById<TextView>(R.id.vaultEntryCountText)
        val count = countPasswordEntries(vaultData)
        countText.text = if (count == 1) "1 entry" else "$count entries"
    }

    private fun getVaultDisplayName(uri: Uri): String {
        try {
            if (uri.scheme == "content") {
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (columnIndex >= 0) {
                            val name = cursor.getString(columnIndex)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve vault display name from ContentResolver", e)
        }

        try {
            if (uri.scheme == "content") {
                DocumentFile.fromSingleUri(this, uri)
                    ?.name
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
            }

        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve vault name from single DocumentFile", e)
        }

        try {
            if (uri.scheme == "content" && android.provider.DocumentsContract.isTreeUri(uri)) {
                DocumentFile.fromTreeUri(this, uri)
                    ?.name
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
            }

        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve vault name from tree DocumentFile", e)
        }

        if (uri.scheme == "file") {
            uri.lastPathSegment
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "Vault"
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun loadVault() {
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

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val data = VaultManager.loadVaultWithKey(this@VaultActivity, uri, dek)
                withContext(Dispatchers.Main) {
                    if (data == null) {
                        showLoading(false)
                        Toast.makeText(this@VaultActivity, "Vault authentication failed ❌", Toast.LENGTH_LONG).show()
                        VaultRuntimeSession.clear()
                        finish()
                        return@withContext
                    }
                    vaultData = data
                    refreshList()
                    updateLoadedHeader()
                    showLoading(false)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load encrypted vault", e)

                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@VaultActivity, "Unable to open vault ❌", Toast.LENGTH_LONG).show()
                    VaultRuntimeSession.clear()
                    finish()
                }

            } finally {
                dek.fill(0)
            }
        }
    }

    private fun isPasswordEntry(entry: JSONObject): Boolean {
        val type = entry.optString("type", "").lowercase()
        return type.isBlank() || type == "password"
    }

    private fun countPasswordEntries(array: JSONArray): Int {
        var count = 0
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue

            if (isPasswordEntry(entry)) count++
        }

        return count
    }

    private fun refreshList() {
        val displayArray = JSONArray()
        for (i in 0 until vaultData.length()) {
            val source = vaultData.optJSONObject(i) ?: continue
            if (!isPasswordEntry(source)) continue
            val displayEntry = JSONObject(source.toString())
            displayEntry.put("realIndex", i)
            displayArray.put(displayEntry)
        }

        adapter.updateData(displayArray)
    }

    private fun openEntryDetail(entry: JSONObject, realIndex: Int) {
        val uri = vaultUri ?: return
        if (!VaultRuntimeSession.isUnlocked()) {
            handleExpiredSession()
            return
        }
        val detailIntent = Intent(this, EntryDetailActivity::class.java).apply {
            putExtra("entryIndex", realIndex)
            putExtra("hostname", entry.optString("hostname"))
            putExtra("username", entry.optString("username"))
            putExtra("created", entry.optLong("created"))
            putExtra("updated", entry.optLong("updated"))
            putExtra("vaultUri", uri.toString())
        }
        startActivity(detailIntent)
        pushSlideTransition()
    }

    private fun handleExpiredSession() {
        showLoading(false)
        vaultData = JSONArray()
        VaultRuntimeSession.clear()
        Toast.makeText(this, "Vault session expired. Please unlock again.", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun clearSensitiveData() {
        vaultData = JSONArray()
    }

    override fun onSecureResume() {
        if (initialSecureResume) {
            initialSecureResume = false
            return
        }
        if (vaultUri == null || !VaultRuntimeSession.isUnlocked()) {
            handleExpiredSession()
            return
        }
        showLoading(true)
        loadVault()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val searchBar = findViewById<EditText>(R.id.searchBar)
            if (searchBar.hasFocus()) {
                val searchBounds = Rect()
                searchBar.getGlobalVisibleRect(searchBounds)
                val tappedOutside = !searchBounds.contains(
                    event.rawX.toInt(),
                    event.rawY.toInt()
                )
                if (tappedOutside) {
                    searchBar.clearFocus()

                    val inputMethodManager =
                        getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.hideSoftInputFromWindow(searchBar.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
}