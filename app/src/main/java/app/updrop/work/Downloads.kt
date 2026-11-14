package app.updrop.work

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Download progress for one file. */
data class DlState(val id: Long, val done: Long, val total: Long, val status: Int) {
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val finished get() = status == DownloadManager.STATUS_SUCCESSFUL
    val failed get() = status == DownloadManager.STATUS_FAILED
}

/** Thin wrapper over Android's DownloadManager: survives the app closing, shows its own notification. */
object Downloads {
    private fun dm(c: Context) = c.getSystemService(DownloadManager::class.java)
    private fun prefs(c: Context) = c.getSharedPreferences("updrop-dl", Context.MODE_PRIVATE)

    /** True for split bundles (.apks/.xapk/.apkm): Android's own installer can't open them, SAI-style installers can. */
    fun isBundle(url: String) = url.substringBefore('?').lowercase().let { it.endsWith(".apks") || it.endsWith(".xapk") || it.endsWith(".apkm") }

    private fun fileName(url: String) = Uri.parse(url).lastPathSegment?.replace(Regex("""[^\w.\-()]"""), "_") ?: "update.apk"

    fun start(c: Context, url: String): Long {
        val file = fileName(url)
        val bundle = isBundle(url)
        prefs(c).edit().remove("exported:$url").apply()
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(file)
            .setDescription("Updrop")
            .setMimeType(if (file.endsWith(".apk", true)) "application/vnd.android.package-archive" else "application/octet-stream")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (bundle) {
            // bundles go to the PUBLIC Download/Updrop folder, where SAI and file managers can see them
            // (Android/data is hidden from other apps since Android 11)
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Updrop/$file")
        } else {
            // plain APKs stay in Updrop's own folder so it can read the signing key back before installing
            req.setDestinationInExternalFilesDir(c, Environment.DIRECTORY_DOWNLOADS, file)
        }
        req
            .addRequestHeader("Referer", "https://www.farsroid.com/")
            .addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36")
        val id = dm(c).enqueue(req)
        prefs(c).edit().putLong(url, id).apply()
        return id
    }

    fun idFor(c: Context, url: String): Long? = prefs(c).getLong(url, -1L).takeIf { it >= 0 }

    fun query(c: Context, id: Long): DlState? =
        dm(c).query(DownloadManager.Query().setFilterById(id))?.use { cur ->
            if (!cur.moveToFirst()) return null
            DlState(
                id,
                cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
            )
        }

    /** Emits progress until the download ends (or disappears). */
    fun watch(c: Context, id: Long): Flow<DlState?> = flow {
        while (true) {
            val s = query(c, id)
            emit(s)
            if (s == null || s.finished || s.failed) break
            delay(400)
        }
    }

    fun cancel(c: Context, url: String) {
        idFor(c, url)?.let { dm(c).remove(it) }
        prefs(c).edit().remove(url).apply()
    }

    /** Real file path of a finished download, or null. */
    fun localPath(c: Context, id: Long): String? =
        dm(c).query(DownloadManager.Query().setFilterById(id))?.use { cur ->
            if (!cur.moveToFirst()) return null
            val uri = cur.getString(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)) ?: return null
            Uri.parse(uri).path?.takeIf { java.io.File(it).canRead() }
        }

    /** Deletes downloaded APKs older than [days] days (they are big). */
    fun prune(c: Context, days: Int = 7) {
        val dir = c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    /**
     * Content URI of a finished bundle in the public Download/Updrop folder.
     * Bundles downloaded by Updrop 1.1.0 sit in the private folder: those are copied out first.
     */
    fun publicBundleUri(c: Context, url: String, id: Long): Uri? {
        prefs(c).getString("exported:$url", null)?.let { saved ->
            val u = Uri.parse(saved)
            if (exists(c, u)) return u
        }
        val path = localPath(c, id)
        val privateDir = c.getExternalFilesDir(null)?.absolutePath
        if (path == null || privateDir == null || !path.startsWith(privateDir)) {
            return dm(c).getUriForDownloadedFile(id) // already public
        }
        val src = java.io.File(path)
        val out = exportToDownloads(c, src, fileName(url)) ?: return null
        src.delete()
        prefs(c).edit().putString("exported:$url", out.toString()).apply()
        return out
    }

    private fun exists(c: Context, u: Uri): Boolean = try {
        c.contentResolver.openFileDescriptor(u, "r")?.use { true } ?: false
    } catch (e: Exception) { false }

    /** Copies a file into Download/Updrop. Android 10+: MediaStore (no permission). Android 8-9: needs storage permission. */
    private fun exportToDownloads(c: Context, src: java.io.File, name: String): Uri? = try {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Updrop")
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            val r = c.contentResolver
            val uri = r.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            r.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out, 256 * 1024) } }
            r.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Updrop").apply { mkdirs() }
            val dst = java.io.File(dir, name)
            src.copyTo(dst, overwrite = true)
            Uri.fromFile(dst).let { androidx.core.content.FileProvider.getUriForFile(c, "${c.packageName}.files", dst) }
        }
    } catch (e: Exception) { null }

    /**
     * Hands a bundle to a split-APK installer (SAI and similar) via "Open with".
     * Falls back to the share sheet, where SAI also appears. Returns false when nothing can take it.
     */
    fun openBundle(c: Context, uri: Uri): Boolean {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/octet-stream")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val viewers = c.packageManager.queryIntentActivities(view, 0).filter { it.activityInfo.packageName != c.packageName }
        val target = if (viewers.isNotEmpty()) view else Intent(Intent.ACTION_SEND).setType("application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            c.startActivity(Intent.createChooser(target, "Install the bundle with…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (e: Exception) { false }
    }

    /** Opens the system installer for a finished download. */
    fun install(c: Context, id: Long): Boolean {
        val uri = dm(c).getUriForDownloadedFile(id) ?: return false
        return try {
            c.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ); true
        } catch (e: Exception) { false }
    }
}
