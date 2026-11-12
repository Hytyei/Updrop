package app.updrop.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.updrop.data.DeviceInfo
import app.updrop.data.Installed
import app.updrop.data.Repo
import app.updrop.data.Settings
import app.updrop.data.TrackedApp
import app.updrop.data.Versions
import app.updrop.work.CheckWorker
import app.updrop.work.AppUpdate
import app.updrop.work.Downloads
import app.updrop.work.SelfUpdate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class Status { UPDATE, NEW_BUILD, CURRENT, NOT_INSTALLED, ERROR }

fun statusOf(app: TrackedApp, installed: Installed?): Status = when {
    app.lastError != null && app.variants.isEmpty() -> Status.ERROR
    installed != null && Versions.compare(app.latestVersion, installed.versionName) > 0 -> Status.UPDATE
    app.hasUnseen -> Status.NEW_BUILD
    installed == null -> Status.NOT_INSTALLED
    else -> Status.CURRENT
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repo.get(app)
    val apps: StateFlow<List<TrackedApp>> = repo.apps
    val settings: StateFlow<Settings> = repo.settings

    private val _installed = MutableStateFlow<Map<String, Installed?>>(emptyMap())
    val installed = _installed.asStateFlow()

    private val _checking = MutableStateFlow<Set<String>>(emptySet())
    val checking = _checking.asStateFlow()

    /** Bumped when a manual check finds a new build: drives the burst effect. */
    private val _burst = MutableStateFlow(0)
    val burst = _burst.asStateFlow()

    private val _open = MutableStateFlow<String?>(null)
    val open = _open.asStateFlow()

    /** Newer Updrop release on GitHub, if any. */
    private val _selfUpdate = MutableStateFlow<AppUpdate?>(null)
    val selfUpdate = _selfUpdate.asStateFlow()
    /** null = idle, 0..1 = downloading Updrop's update. */
    private val _selfProgress = MutableStateFlow<Float?>(null)
    val selfProgress = _selfProgress.asStateFlow()
    private val _selfError = MutableStateFlow<String?>(null)
    val selfError = _selfError.asStateFlow()
    private var downloadedSelf: java.io.File? = null

    fun checkSelf() {
        viewModelScope.launch {
            try { _selfUpdate.value = SelfUpdate.check() } catch (_: Exception) { /* offline: try again later */ }
        }
    }

    fun installSelf(ctx: android.content.Context) {
        val u = _selfUpdate.value ?: return
        if (_selfProgress.value != null) return
        downloadedSelf?.takeIf { it.exists() && it.name.contains(u.version) }?.let { SelfUpdate.install(ctx, it); return }
        _selfError.value = null
        _selfProgress.value = 0f
        viewModelScope.launch {
            try {
                val f = SelfUpdate.download(ctx, u) { p -> _selfProgress.value = p }
                downloadedSelf = f
                _selfProgress.value = null
                SelfUpdate.install(ctx, f)
            } catch (e: Exception) {
                _selfProgress.value = null
                _selfError.value = e.message ?: "Update failed"
            }
        }
    }

    init {
        refreshInstalled()
        CheckWorker.schedule(app, repo.settings.value)
        checkSelf()
        Downloads.prune(app)
    }

    fun refreshInstalled() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _installed.value = repo.apps.value.associate { it.pageUrl to DeviceInfo.installed(getApplication(), it.packageName) }
        }
    }

    fun openApp(url: String?) {
        _open.value = url
        if (url != null) repo.markSeen(url)
    }

    fun checkAll() {
        if (_checking.value.isNotEmpty()) return
        val urls = repo.apps.value.map { it.pageUrl }
        checkSelf()
        if (urls.isEmpty()) return
        _checking.value = urls.toSet()
        viewModelScope.launch {
            val gate = Semaphore(3)
            val found = urls.map { u ->
                async {
                    gate.withPermit {
                        val r = repo.check(u)
                        _checking.value = _checking.value - u
                        r?.newBuild == true
                    }
                }
            }.awaitAll()
            if (found.any { it }) _burst.value++
            refreshInstalled()
        }
    }

    fun check(url: String) {
        if (url in _checking.value) return
        _checking.value = _checking.value + url
        viewModelScope.launch {
            val r = repo.check(url)
            _checking.value = _checking.value - url
            if (r?.newBuild == true) _burst.value++
            if (_open.value == url) repo.markSeen(url)
            refreshInstalled()
        }
    }

    /** Adds a page; [done] gets null on success or an error message. */
    fun add(text: String, done: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val a = repo.add(text)
                refreshInstalled()
                done(null)
                _open.value = a.pageUrl
            } catch (e: Exception) {
                done(e.message ?: "Could not read that page")
            }
        }
    }

    fun remove(url: String) {
        if (_open.value == url) _open.value = null
        repo.remove(url)
    }

    fun setPreferred(url: String, kind: String?) = repo.setPreferred(url, kind)
    fun learnSigner(url: String, flavour: String, sha: String) = repo.learnSigner(url, flavour, sha)

    /** Package being uninstalled so a conflicting file can be installed afterwards, with its download id. */
    private var pending: Pair<String, Long>? = null

    fun uninstallThenInstall(ctx: android.content.Context, pkg: String, downloadId: Long) {
        pending = pkg to downloadId
        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg")))
    }

    /** Called when the app comes back to the front: finishes a pending uninstall-then-install. */
    fun onResume(ctx: android.content.Context) {
        refreshInstalled()
        val (pkg, id) = pending ?: return
        pending = null
        // the uninstall can finish a moment after its dialog closes, so watch for a few seconds
        viewModelScope.launch {
            repeat(12) {
                if (DeviceInfo.installed(ctx, pkg) == null) { install(ctx, id); refreshInstalled(); return@launch }
                kotlinx.coroutines.delay(500)
            }
            // still installed: the user cancelled the uninstall
        }
    }

    fun saveSettings(s: Settings) {
        repo.saveSettings(s)
        CheckWorker.schedule(getApplication(), s)
    }
}
