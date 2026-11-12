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

    fun start(c: Context, url: String): Long {
        val file = Uri.parse(url).lastPathSegment?.replace(Regex("""[^\w.\-()]"""), "_") ?: "update.apk"
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(file)
            .setDescription("Updrop")
            .setMimeType(if (file.endsWith(".apk", true)) "application/vnd.android.package-archive" else "application/octet-stream")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            // app-specific folder (Android/data/app.updrop/files/Download): Updrop can read the file back to check its signing key
            .setDestinationInExternalFilesDir(c, Environment.DIRECTORY_DOWNLOADS, file)
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
