package com.athena.j.athena

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate


/**
 * Performs local application integrity checks.
 *
 * Checks signing certificate, APK digest, debugger state,
 * debuggable state, installer source, and basic hooking signals.
 */
object TamperChecker {

    // =============================================================
    // LOGGING
    // =============================================================

    private const val TAG =
        "TamperChecker"

    // =============================================================
    // REPORT
    // =============================================================

    data class Report(
        val passed: Boolean,
        val signatureOk: Boolean?,
        val apkDigestOk: Boolean?,
        val actualCertSha256: String,
        val expectedCertSha256: String?,
        val actualApkSha256: String?,
        val expectedApkSha256: String?,
        val isDebuggable: Boolean,
        val isDebuggerAttached: Boolean,
        val hookingDetected: Boolean,
        val installerPackageName: String?
    )

    // =============================================================
    // RUN CHECKS
    // =============================================================

    fun runAllChecks(
        context: Context,
        expectedCertSha256: String? = null,
        expectedApkSha256: String? = null
    ): Report {

        val actualCertSha256 =
            getSigningCertSha256(
                context
            ) ?: ""

        val signatureOk =
            expectedCertSha256
                ?.let {

                    equalHashes(
                        actualCertSha256,
                        it
                    )
                }

        val actualApkSha256 =
            apkDigestSha256(
                context
            )

        val apkDigestOk =
            if (
                !expectedApkSha256.isNullOrBlank() &&
                actualApkSha256 != null
            ) {

                equalHashes(
                    actualApkSha256,
                    expectedApkSha256
                )

            } else {

                null
            }

        val isDebuggable =
            (
                    context.applicationInfo.flags and
                            ApplicationInfo.FLAG_DEBUGGABLE
                    ) != 0

        val isDebuggerAttached =
            Debug.isDebuggerConnected()

        val hookingDetected =
            detectHooking()

        val installer =
            getInstallerPackageName(
                context
            )

        // Use available strong checks when expected values exist.
        val strongChecks =
            listOfNotNull(
                signatureOk,
                apkDigestOk
            )

        val passed =
            if (
                strongChecks.isEmpty()
            ) {

                !isDebuggable &&
                        !isDebuggerAttached &&
                        !hookingDetected

            } else {

                strongChecks.all {
                    it
                } &&
                        !hookingDetected
            }

        return Report(
            passed =
                passed,
            signatureOk =
                signatureOk,
            apkDigestOk =
                apkDigestOk,
            actualCertSha256 =
                actualCertSha256,
            expectedCertSha256 =
                expectedCertSha256,
            actualApkSha256 =
                actualApkSha256,
            expectedApkSha256 =
                expectedApkSha256,
            isDebuggable =
                isDebuggable,
            isDebuggerAttached =
                isDebuggerAttached,
            hookingDetected =
                hookingDetected,
            installerPackageName =
                installer
        )
    }

    // =============================================================
    // SIGNING CERTIFICATE
    // =============================================================

    /**
     * Returns the installed app signing certificate SHA-256.
     */
    fun getSigningCertSha256(
        context: Context
    ): String? {

        return try {

            val packageManager =
                context.packageManager

            val packageName =
                context.packageName

            val packageInfo: PackageInfo =
                if (
                    Build.VERSION.SDK_INT >= 33
                ) {

                    packageManager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(
                            PackageManager
                                .GET_SIGNING_CERTIFICATES
                                .toLong()
                        )
                    )

                } else {

                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES
                    )
                }

            val certificateBytes: ByteArray? =
                if (
                    Build.VERSION.SDK_INT >= 28
                ) {

                    packageInfo
                        .signingInfo
                        ?.apkContentsSigners
                        ?.firstOrNull()
                        ?.toByteArray()

                } else {

                    @Suppress("DEPRECATION")
                    packageInfo
                        .signatures
                        ?.firstOrNull()
                        ?.toByteArray()
                }

            if (
                certificateBytes == null
            ) {

                Log.e(
                    TAG,
                    "Signing certificate bytes are null"
                )

                return null
            }

            val certificateFactory =
                CertificateFactory.getInstance(
                    "X.509"
                )

            val certificate =
                certificateFactory
                    .generateCertificate(
                        certificateBytes.inputStream()
                    ) as X509Certificate

            val digest =
                MessageDigest
                    .getInstance(
                        "SHA-256"
                    )
                    .digest(
                        certificate.encoded
                    )

            toHexWithColons(
                digest
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Certificate fingerprint error",
                e
            )

            null
        }
    }

    // =============================================================
    // APK DIGEST
    // =============================================================

    /**
     * Returns the SHA-256 digest of the installed APK.
     */
    fun apkDigestSha256(
        context: Context
    ): String? {

        return try {

            val apkPath =
                context
                    .applicationInfo
                    .sourceDir

            sha256File(
                apkPath
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "APK digest error",
                e
            )

            null
        }
    }

    // =============================================================
    // HOOKING DETECTION
    // =============================================================

    /**
     * Performs a basic check for known hooking framework classes.
     */
    fun detectHooking(): Boolean {

        val suspects =
            listOf(
                "de.robv.android.xposed.XposedBridge",
                "de.robv.android.xposed.XC_MethodHook",
                "lsposed.lspd.NativeBridge"
            )

        return suspects.any { className ->

            try {

                Class.forName(
                    className
                )

                true

            } catch (_: ClassNotFoundException) {

                false
            }
        }
    }

    // =============================================================
    // INSTALLER
    // =============================================================

    private fun getInstallerPackageName(
        context: Context
    ): String? {

        return try {

            val packageManager =
                context.packageManager

            if (
                Build.VERSION.SDK_INT >= 30
            ) {

                packageManager
                    .getInstallSourceInfo(
                        context.packageName
                    )
                    .installingPackageName

            } else {

                @Suppress("DEPRECATION")
                packageManager
                    .getInstallerPackageName(
                        context.packageName
                    )
            }

        } catch (_: Exception) {

            null
        }
    }

    // =============================================================
    // FILE HASH
    // =============================================================

    private fun sha256File(
        path: String
    ): String {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        FileInputStream(
            File(
                path
            )
        ).use { input ->

            val buffer =
                ByteArray(
                    16 * 1024
                )

            while (
                true
            ) {

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

        return toHexWithColons(
            digest.digest()
        )
    }

    // =============================================================
    // HASH FORMATTING
    // =============================================================

    private fun toHexWithColons(
        bytes: ByteArray
    ): String {

        return bytes.joinToString(
            ":"
        ) {

            "%02X".format(
                it
            )
        }
    }

    // =============================================================
    // HASH COMPARISON
    // =============================================================

    private fun equalHashes(
        first: String?,
        second: String?
    ): Boolean {

        if (
            first == null ||
            second == null
        ) {

            return false
        }

        fun normalize(
            value: String
        ): String {

            return value
                .replace(
                    ":",
                    ""
                )
                .lowercase()
        }

        return normalize(
            first
        ) ==
                normalize(
                    second
                )
    }

    // =============================================================
    // CERTIFICATE SETUP
    // =============================================================

    /**
     * Logs the current certificate fingerprint for setup.
     */
    fun logCurrentCertForSetup(
        context: Context
    ) {

        val fingerprint =
            getSigningCertSha256(
                context
            )

        Log.d(
            TAG,
            "Current cert SHA-256 = $fingerprint"
        )
    }
}