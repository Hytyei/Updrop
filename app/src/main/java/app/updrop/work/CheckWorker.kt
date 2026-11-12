package app.updrop.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.updrop.MainActivity
import app.updrop.R
import app.updrop.data.DeviceInfo
import app.updrop.data.Recommender
import app.updrop.data.Repo
import app.updrop.data.Settings
import app.updrop.data.TrackedApp
import app.updrop.data.Versions
import java.util.concurrent.TimeUnit

/** Periodic background check of every tracked page. */
class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = Repo.get(applicationContext)
        repo.apps.value.forEach { app ->
            val r = repo.check(app.pageUrl) ?: return@forEach
            val sig = r.app.signature
            val already = repo.notifiedSignature(app.pageUrl)
            if (r.newBuild && already != sig && repo.settings.value.notify) {
                Notifier.newBuild(applicationContext, r.app)
            }
            repo.setNotified(app.pageUrl, sig)
        }
        // Updrop's own update: one notification per new version
        try {
            SelfUpdate.check()?.let { u ->
                val prefs = applicationContext.getSharedPreferences("updrop", Context.MODE_PRIVATE)
                if (prefs.getString("selfNotified", null) != u.version && repo.settings.value.notify) {
                    Notifier.selfUpdate(applicationContext, u)
                    prefs.edit().putString("selfNotified", u.version).apply()
                }
            }
        } catch (_: Exception) {}
        return Result.success()
    }

    companion object {
        private const val NAME = "updrop-check"

        fun schedule(context: Context, s: Settings) {
            val wm = WorkManager.getInstance(context)
            if (s.intervalHours <= 0) { wm.cancelUniqueWork(NAME); return }
            val req = PeriodicWorkRequestBuilder<CheckWorker>(s.intervalHours.toLong(), TimeUnit.HOURS, 20, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (s.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build()
                ).build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}

object Notifier {
    private const val CHANNEL = "builds"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "New builds", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A page you follow on Farsroid got a new version"
            })
        }
    }

    fun selfUpdate(context: Context, u: AppUpdate) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat).setColor(0xFF107C10.toInt())
            .setContentTitle("Updrop ${u.version} is available")
            .setContentText("Open Updrop and tap the banner to update")
            .setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(1, n) } catch (_: SecurityException) {}
    }

    fun newBuild(context: Context, app: TrackedApp) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val installed = DeviceInfo.installed(context, app.packageName)
        val rec = Recommender.pick(app, installed)
        val title = "${app.name} ${app.latestVersion ?: ""}".trim()
        val text = buildString {
            if (installed != null) {
                append(if (Versions.compare(app.latestVersion, installed.versionName) > 0) "Update from ${installed.versionName}" else "New build on Farsroid")
            } else append("New build on Farsroid")
            rec?.let { append(" · get ${it.variant.labelIn(app.name)}${it.variant.size?.let { s -> " ($s)" } ?: ""}") }
        }
        val open = PendingIntent.getActivity(
            context, app.pageUrl.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, app.pageUrl)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF107C10.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(app.pageUrl.hashCode(), n) } catch (_: SecurityException) {}
    }
}
