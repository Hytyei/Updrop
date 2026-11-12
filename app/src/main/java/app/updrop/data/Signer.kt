package app.updrop.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/** What an APK file on disk says about itself. */
data class ArchiveInfo(val packageName: String, val versionName: String?, val versionCode: Long, val signer: String?)

/**
 * Signing-key fingerprints (SHA-256 of the first certificate).
 *
 * Android only installs an update over an app when both are signed with the same key.
 * Farsroid's flavours (Premium, Mod-Extra…) are often re-signed, so moving between
 * flavours fails with "package conflicts with an existing package". Comparing fingerprints
 * lets Updrop warn before that happens and prefer the flavour that matches.
 */
object Signer {
    private fun sha(sig: Signature): String =
        MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun firstSigner(info: PackageInfo?): String? {
        info ?: return null
        val sigs: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
            val si = info.signingInfo ?: return null
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else @Suppress("DEPRECATION") info.signatures
        return sigs?.firstOrNull()?.let(::sha)
    }

    private val flags: Int
        get() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

    fun installed(context: Context, pkg: String): String? = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else @Suppress("DEPRECATION") pm.getPackageInfo(pkg, flags)
        firstSigner(info)
    } catch (e: Exception) { null }

    /** Reads package name, version and signer from an .apk file. Null if it is not a readable APK. */
    fun archive(context: Context, path: String): ArchiveInfo? = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else @Suppress("DEPRECATION") pm.getPackageArchiveInfo(path, flags)
        info?.let {
            val code = if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
            ArchiveInfo(it.packageName, it.versionName, code, firstSigner(it))
        }
    } catch (e: Exception) { null }

    fun short(sha: String?) = sha?.take(8) ?: "?"
}
