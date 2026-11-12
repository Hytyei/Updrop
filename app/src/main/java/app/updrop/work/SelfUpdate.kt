package app.updrop.work

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.updrop.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AppUpdate(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long, val sha256: String?)

/**
 * Updrop updating itself: reads the latest release of the public GitHub repo
 * (BuildConfig.GITHUB_REPO) and installs its .apk asset. Releases must be signed with
 * AndroidKeys\updropapp or Android refuses them as an update.
 */
object SelfUpdate {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    /** The newer release, or null when Updrop is current (or GitHub has nothing usable). */
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        val repo = BuildConfig.GITHUB_REPO
        val req = Request.Builder().url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json").build()
        client.newCall(req).execute().use { r ->
            if (r.code == 404) return@withContext null
            if (!r.isSuccessful) throw IOException("GitHub returned ${r.code}")
            val o = JSONObject(r.body?.string() ?: return@withContext null)
            if (o.optBoolean("prerelease") || o.optBoolean("draft")) return@withContext null
            val assets = o.optJSONArray("assets") ?: return@withContext null
            // only accept assets that really belong to THIS repo's releases
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull {
                it.optString("name").endsWith(".apk") &&
                    it.optString("browser_download_url").startsWith("https://github.com/$repo/releases/download/")
            } ?: return@withContext null
            val v = o.optString("tag_name").removePrefix("v")
            if (!isNewer(v, BuildConfig.VERSION_NAME)) return@withContext null
            AppUpdate(
                version = v,
                notes = plain(o.optString("body")),
                apkUrl = apk.getString("browser_download_url"),
                sizeBytes = apk.optLong("size"),
                sha256 = apk.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 },
            )
        }
    }

    /** Release notes are Markdown; the banner shows plain text ("**New**" -> "New", "- item" -> "• item", links -> their text). */
    fun plain(md: String): String = md.lines().joinToString("\n") { line ->
        line.trim()
            .replace(Regex("""^#+\s*"""), "")
            .replace(Regex("""^[-*]\s+"""), "• ")
            .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
            .replace("**", "").replace("`", "")
    }.trim()

    fun isNewer(remote: String, local: String): Boolean {
        fun parts(s: String) = s.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Downloads the APK into cache/updates, checking GitHub's SHA-256 when it publishes one. */
    suspend fun download(context: Context, u: AppUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "Updrop-${u.version}.apk")
        val md = MessageDigest.getInstance("SHA-256")
        client.newCall(Request.Builder().url(u.apkUrl).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Download failed (${r.code})")
            val body = r.body ?: throw IOException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: u.sizeBytes
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024); var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        output.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        u.sha256?.let { expected ->
            val actual = md.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expected, true)) { out.delete(); throw IOException("Download is corrupted, try again") }
        }
        out
    }

    /** Hands the APK to Android's installer. Returns false when install permission is still needed. */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
