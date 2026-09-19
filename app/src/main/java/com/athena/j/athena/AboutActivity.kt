package com.athena.j.athena

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.documentfile.provider.DocumentFile
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest


/**
 * Athena About / Security screen.
 *
 * This screen reports:
 *
 * APPLICATION INTEGRITY
 * - app version and build number
 * - installed APK SHA-256 checksum
 * - signing certificate fingerprint
 * - release / debuggable state
 * - debugger state
 * - installer source
 * - tamper diagnostics
 *
 * SECURITY
 * - vault encryption
 * - password KDF
 * - biometric quick unlock
 * - hardware-backed Keystore
 * - screen capture protection
 * - auto-lock timeout
 *
 * It never requires or retains the master password.
 */
class AboutActivity : BaseSecureActivity() {

    companion object {

        private const val TAG =
            "AthenaAbout"

        /*
         * Production signing certificate SHA-256.
         *
         * This can be compared against the currently installed
         * application's signing certificate by TamperChecker.
         */
        private const val EXPECTED_CERT_SHA256 =
            "6F:92:6C:79:8F:D2:2D:44:94:8C:1B:44:02:ED:D3:A8:BD:CE:0F:46:FA:C0:F0:5D:D8:CB:99:53:00:C3:2E:64"

        /*
         * Athena calculates the installed APK checksum locally
         * and displays it to the user so they can compare it
         * against the official checksum published independently.
         */
        private const val EXPECTED_APK_SHA256 =
            ""

    }

    // =============================================================
    // NAVIGATION
    // =============================================================

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    // =============================================================
    // ABOUT UI
    // =============================================================

    private lateinit var verifyButton: Button
    private lateinit var integritySummary: TextView
    private lateinit var integrityDetails: TextView
    private lateinit var versionText: TextView
    private lateinit var appNameText: TextView
    private lateinit var appLogo: ImageView

    private lateinit var securityDetails: TextView

    private lateinit var applicationDetails: TextView

    // =============================================================
    // CREATE
    // =============================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_about
        )

        if (
            !VaultRuntimeSession.isUnlocked()
        ) {

            Toast.makeText(
                this,
                "Vault session expired. Please unlock again.",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        setupViews()
        setupToolbar()
        setupNavigation()
        setupAboutInfo()

        updateVaultHeader()
        updateAboutInfo()
    }

    // =============================================================
    // VIEW SETUP
    // =============================================================

    private fun setupViews() {

        drawerLayout =
            findViewById(
                R.id.drawerLayout
            )

        navigationView =
            findViewById(
                R.id.navigationView
            )

        appLogo =
            findViewById(
                R.id.appLogo
            )

        appNameText =
            findViewById(
                R.id.appName
            )

        versionText =
            findViewById(
                R.id.appVersion
            )

        integritySummary =
            findViewById(
                R.id.integritySummary
            )

        verifyButton =
            findViewById(
                R.id.verifyButton
            )

        integrityDetails =
            findViewById(
                R.id.integrityDetails
            )

        applicationDetails =
            findViewById(
                R.id.applicationDetails
            )

        securityDetails =
            findViewById(
                R.id.securityDetails
            )
    }

    // =============================================================
    // TOOLBAR
    // =============================================================

    private fun setupToolbar() {

        val toolbar =
            findViewById<MaterialToolbar>(
                R.id.aboutToolbar
            )

        setSupportActionBar(
            toolbar
        )

        supportActionBar
            ?.setDisplayShowTitleEnabled(
                false
            )

        toolbar.setNavigationIcon(
            R.drawable.ic_menu
        )

        toolbar.navigationIcon
            ?.setTint(
                resources.getColor(
                    android.R.color.white,
                    theme
                )
            )

        val toggle =
            ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
            )

        drawerLayout.addDrawerListener(
            toggle
        )

        toggle.syncState()

        toggle.isDrawerIndicatorEnabled =
            false

        toolbar.setNavigationOnClickListener {

            if (
                drawerLayout.isDrawerOpen(
                    navigationView
                )
            ) {

                drawerLayout.closeDrawer(
                    navigationView
                )

            } else {

                drawerLayout.openDrawer(
                    navigationView
                )
            }
        }
    }

    // =============================================================
    // NAVIGATION
    // =============================================================

    private fun setupNavigation() {

        navigationView
            .setNavigationItemSelectedListener { item ->

                when (
                    item.itemId
                ) {

                    R.id.nav_home -> {

                        drawerLayout.closeDrawers()

                        startActivity(
                            Intent(
                                this,
                                VaultActivity::class.java
                            ).apply {

                                flags =
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                        )

                        pushSlideTransition()

                        true
                    }

                    R.id.nav_lock_vault -> {

                        drawerLayout.closeDrawers()

                        VaultLocker.lockNow(
                            this
                        )

                        true
                    }

                    R.id.nav_about -> {

                        drawerLayout.closeDrawers()

                        true
                    }

                    R.id.nav_settings -> {

                        drawerLayout.closeDrawers()

                        Toast.makeText(
                            this,
                            "Backup coming soon",
                            Toast.LENGTH_SHORT
                        ).show()

                        true
                    }

                    else ->
                        false
                }
            }
    }

    // =============================================================
    // ABOUT INFO
    // =============================================================

    private fun setupAboutInfo() {

        versionText.text =
            try {

                val info =
                    packageManager
                        .getPackageInfo(
                            packageName,
                            0
                        )

                val versionName =
                    info.versionName
                        ?: "1.0"

                val versionCode =
                    info.longVersionCode

                "v$versionName"

            } catch (_: Exception) {

                "v1"
            }

        verifyButton
            .setOnClickListener {

                runApplicationIntegrityCheck()
            }
    }

    // =============================================================
    // VAULT HEADER
    // =============================================================

    private fun updateVaultHeader() {

        val uri =
            VaultRuntimeSession
                .getVaultUri()
                ?: return

        val header =
            navigationView
                .getHeaderView(
                    0
                )

        val vaultNameText =
            header.findViewById<TextView>(
                R.id.vaultNameText
            )

        val vaultEntryCountText =
            header.findViewById<TextView>(
                R.id.vaultEntryCountText
            )

        val displayName =
            getVaultDisplayName(
                uri
            )

        vaultNameText.text =
            displayName.substringBeforeLast(
                "."
            )

        vaultEntryCountText.text =
            "Loading…"

        val dek =
            VaultRuntimeSession
                .getVaultDek()

        if (
            dek == null
        ) {

            vaultEntryCountText.text =
                "—"

            return
        }

        lifecycleScope.launch(
            Dispatchers.IO
        ) {

            try {

                val vault =
                    VaultManager
                        .loadVaultWithKey(
                            this@AboutActivity,
                            uri,
                            dek
                        )

                withContext(
                    Dispatchers.Main
                ) {

                    if (
                        vault == null
                    ) {

                        vaultEntryCountText.text =
                            "Unavailable"

                    } else {

                        val count =
                            vault.length()

                        vaultEntryCountText.text =
                            if (
                                count == 1
                            ) {

                                "1 entry"

                            } else {

                                "$count entries"
                            }
                    }
                }

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Unable to load vault information for About screen",
                    e
                )

                withContext(
                    Dispatchers.Main
                ) {

                    vaultEntryCountText.text =
                        "Unavailable"
                }

            } finally {

                dek.fill(0)
            }
        }
    }

    // =============================================================
    // ABOUT / SECURITY INFO
    // =============================================================

    private fun updateAboutInfo() {

        val biometricAvailable =
            BiometricStore
                .isBiometricAvailable(
                    this
                )

        val biometricEnabled =
            VaultSessionManager
                .isBiometricEnabled(
                    this
                ) &&
                    BiometricStore
                        .hasWrappedDek(
                            this
                        )

        val hardwareBacked =
            BiometricStore
                .isHardwareBacked(
                    this
                )

        applicationDetails.text =
            buildString {

                appendLine(
                    "Version: ${getVersionDisplay()}"
                )

                appendLine(
                    "Package: $packageName"
                )

                append(
                    "Installer: ${getInstallerPackageName()}"
                )
            }

        securityDetails.text =
            buildString {

                appendLine(
                    "Vault encryption: AES-256-GCM"
                )

                appendLine(
                    "Password derivation: Argon2id"
                )

                appendLine(
                    "Biometric available: ${
                        if (biometricAvailable) {
                            "Yes"
                        } else {
                            "No"
                        }
                    }"
                )

                appendLine(
                    "Biometric quick unlock: ${
                        if (biometricEnabled) {
                            "Enabled"
                        } else {
                            "Disabled"
                        }
                    }"
                )

                appendLine(
                    "Hardware-backed Keystore: ${
                        if (hardwareBacked) {
                            "Available"
                        } else {
                            "Unavailable"
                        }
                    }"
                )

                appendLine(
                    "Screen capture protection: Enabled"
                )

                append(
                    "Auto-lock: ${
                        TimeoutManager
                            .getTimeoutDurationMs() /
                                60_000L
                    } minutes"
                )
            }
    }

    // =============================================================
    // APP INTEGRITY CHECK
    // =============================================================

    private fun runApplicationIntegrityCheck() {

        verifyButton.isEnabled =
            false

        val originalButtonText =
            verifyButton.text

        verifyButton.text =
            "Verifying…"

        integritySummary.text =
            "Application integrity: checking…"

        lifecycleScope.launch {

            val checksum =
                withContext(
                    Dispatchers.IO
                ) {

                    calculateInstalledApkSha256()
                }

            val report =
                withContext(
                    Dispatchers.IO
                ) {

                    TamperChecker
                        .runAllChecks(
                            context =
                                this@AboutActivity,

                            expectedCertSha256 =
                                EXPECTED_CERT_SHA256,

                            expectedApkSha256 =
                                EXPECTED_APK_SHA256
                        )
                }

            val certificateOk =
                report.signatureOk == true

            val debuggable =
                report.isDebuggable

            val debuggerAttached =
                report.isDebuggerAttached

            val hookingDetected =
                report.hookingDetected

            val passed =
                certificateOk &&
                        !debuggable &&
                        !debuggerAttached &&
                        !hookingDetected

            integritySummary.text =
                if (
                    passed
                ) {

                    "Application integrity: ✅ Verified"

                } else {

                    "Application integrity: ⚠️ Review recommended"
                }

            integrityDetails.text =
                buildString {

                    appendLine(
                        "APPLICATION INTEGRITY"
                    )

                    appendLine()

                    appendLine(
                        "Signing certificate: ${
                            if (certificateOk) {
                                "Verified"
                            } else {
                                "Mismatch"
                            }
                        }"
                    )

                    appendLine(
                        "Release build: ${
                            if (!debuggable) {
                                "Yes"
                            } else {
                                "No"
                            }
                        }"
                    )

                    appendLine(
                        "Debugger attached: ${
                            if (debuggerAttached) {
                                "Yes"
                            } else {
                                "No"
                            }
                        }"
                    )

                    appendLine(
                        "Hooking framework detected: ${
                            if (hookingDetected) {
                                "Yes"
                            } else {
                                "No"
                            }
                        }"
                    )

                    appendLine(
                        "Installer: ${
                            report.installerPackageName
                                ?: "Unknown"
                        }"
                    )

                    appendLine()

                    appendLine(
                        "APK SHA-256:"
                    )

                    appendLine(
                        checksum
                            ?.let {
                                formatChecksum(
                                    it
                                )
                            }
                            ?: "Unavailable"
                    )

                    appendLine()

                    appendLine(
                        "Signing certificate SHA-256:"
                    )

                    appendLine(
                        report.actualCertSha256
                            ?: "Unavailable"
                    )

                    appendLine()

                    append(
                        "Compare the APK checksum with the official checksum published by Athena."
                    )
                }

            verifyButton.text =
                originalButtonText

            verifyButton.isEnabled =
                true
        }
    }

    // =============================================================
    // CHECKSUM
    // =============================================================

    private fun calculateInstalledApkSha256(): String? {

        return try {

            val apkFile =
                File(
                    applicationInfo.sourceDir
                )

            val digest =
                MessageDigest.getInstance(
                    "SHA-256"
                )

            FileInputStream(
                apkFile
            ).use { input ->

                val buffer =
                    ByteArray(
                        8192
                    )

                while (true) {

                    val bytesRead =
                        input.read(
                            buffer
                        )

                    if (
                        bytesRead <= 0
                    ) {
                        break
                    }

                    digest.update(
                        buffer,
                        0,
                        bytesRead
                    )
                }
            }

            digest
                .digest()
                .joinToString(
                    separator = ""
                ) { byte ->

                    "%02X".format(
                        byte
                    )
                }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Unable to calculate installed APK SHA-256",
                e
            )

            null
        }
    }

    private fun formatChecksum(
        checksum: String
    ): String {

        return checksum
            .chunked(
                4
            )
            .joinToString(
                separator = " "
            )
    }

    // =============================================================
    // VERSION / INSTALLER
    // =============================================================

    private fun getVersionDisplay(): String {

        return try {

            val info =
                packageManager
                    .getPackageInfo(
                        packageName,
                        0
                    )

            val versionName =
                info.versionName
                    ?: "1.0"

            val versionCode =
                info.longVersionCode

            "$versionName"

        } catch (_: Exception) {

            "Unknown"
        }
    }

    private fun getInstallerPackageName(): String {

        return try {

            if (
                android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.R
            ) {

                packageManager
                    .getInstallSourceInfo(
                        packageName
                    )
                    .installingPackageName
                    ?: "Unknown"

            } else {

                @Suppress("DEPRECATION")
                packageManager
                    .getInstallerPackageName(
                        packageName
                    )
                    ?: "Unknown"
            }

        } catch (_: Exception) {

            "Unknown"
        }
    }

    // =============================================================
    // VAULT DISPLAY NAME
    // =============================================================

    private fun getVaultDisplayName(
        uri: Uri
    ): String {

        try {

            contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->

                if (
                    cursor.moveToFirst()
                ) {

                    val index =
                        cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME
                        )

                    if (
                        index >= 0
                    ) {

                        val value =
                            cursor.getString(
                                index
                            )

                        if (
                            !value.isNullOrBlank()
                        ) {

                            return value
                        }
                    }
                }
            }

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Unable to resolve vault display name",
                e
            )
        }

        DocumentFile
            .fromSingleUri(
                this,
                uri
            )
            ?.name
            ?.let {

                return it
            }

        DocumentFile
            .fromTreeUri(
                this,
                uri
            )
            ?.name
            ?.let {

                return it
            }

        return uri.lastPathSegment
            ?: "Vault"
    }

    // =============================================================
    // RESUME
    // =============================================================

    override fun onSecureResume() {

        if (
            !VaultRuntimeSession.isUnlocked()
        ) {

            finish()
            return
        }

        updateVaultHeader()
        updateAboutInfo()
    }

    // =============================================================
    // BACK
    // =============================================================

    @Deprecated(
        "Deprecated in Java"
    )
    override fun onBackPressed() {

        super.onBackPressed()

        popSlideTransition()
    }
}