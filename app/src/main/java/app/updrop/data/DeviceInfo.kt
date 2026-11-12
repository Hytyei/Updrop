package app.updrop.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.zip.ZipFile

/** What is installed on this phone for one package. */
data class Installed(
    val versionName: String,
    val versionCode: Long,
    val runningAbi: String?,        // ABI the app's native code runs as (null = no native code)
    val apkAbis: List<String>,      // every ABI shipped inside the installed APK(s)
    val signer: String? = null,     // SHA-256 of the signing certificate
) {
    val isUniversal: Boolean get() = apkAbis.size > 1
}

object DeviceInfo {
    /** ABIs this phone can run, best first (e.g. arm64-v8a, armeabi-v7a, armeabi). */
    val supportedAbis: List<String> get() = Build.SUPPORTED_ABIS.toList()
    val primaryAbi: String get() = supportedAbis.firstOrNull() ?: "arm64-v8a"
    val model: String get() = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    val android: String get() = "Android ${Build.VERSION.RELEASE}"

    fun installed(context: Context, pkg: String?): Installed? {
        if (pkg.isNullOrBlank()) return null
        val pm = context.packageManager
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            else @Suppress("DEPRECATION") pm.getPackageInfo(pkg, 0)
            val ai = info.applicationInfo ?: return null
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
            val apks = listOfNotNull(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())
            val abis = apks.flatMap(::abisIn).distinct()
                .sortedBy { supportedAbis.indexOf(it).let { i -> if (i < 0) 99 else i } }
            Installed(
                versionName = info.versionName ?: code.toString(),
                versionCode = code,
                runningAbi = abiFromLibDir(ai.nativeLibraryDir) ?: abis.firstOrNull { it in supportedAbis },
                apkAbis = abis,
                signer = Signer.installed(context, pkg),
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /** "…/lib/arm64" -> arm64-v8a. */
    private fun abiFromLibDir(dir: String?): String? = when (dir?.substringAfterLast('/')) {
        "arm64" -> "arm64-v8a"
        "arm" -> "armeabi-v7a"
        "x86_64" -> "x86_64"
        "x86" -> "x86"
        else -> null
    }

    /** ABIs that have native libraries inside an APK (base or split, e.g. split_config.arm64_v8a.apk). */
    private fun abisIn(path: String): List<String> {
        val name = path.substringAfterLast('/')
        Regex("""config\.(arm64_v8a|armeabi_v7a|armeabi|x86_64|x86)\.apk""").find(name)?.let {
            return listOf(it.groupValues[1].replace('_', '-').replace("arm64-v8a", "arm64-v8a").let { a -> if (a == "x86-64") "x86_64" else a })
        }
        return try {
            ZipFile(path).use { zip ->
                zip.entries().asSequence().map { it.name }
                    .filter { it.startsWith("lib/") && it.endsWith(".so") }
                    .map { it.removePrefix("lib/").substringBefore('/') }
                    .distinct().toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
