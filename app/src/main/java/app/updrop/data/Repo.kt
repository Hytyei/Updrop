package app.updrop.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class Settings(
    val intervalHours: Int = 6,     // 0 = background checks off
    val wifiOnly: Boolean = false,
    val notify: Boolean = true,
)

/** Result of one check of one page. */
data class CheckResult(val app: TrackedApp, val newBuild: Boolean)

/** All state lives in one SharedPreferences file as JSON. Small and dependency-free. */
class Repo private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("updrop", Context.MODE_PRIVATE)
    private val _apps = MutableStateFlow(load())
    val apps: StateFlow<List<TrackedApp>> = _apps.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true).build()

    private fun load(): List<TrackedApp> = try {
        val arr = JSONArray(prefs.getString("apps", "[]"))
        (0 until arr.length()).map { TrackedApp.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    private fun loadSettings() = Settings(
        intervalHours = prefs.getInt("interval", 6),
        wifiOnly = prefs.getBoolean("wifiOnly", false),
        notify = prefs.getBoolean("notify", true),
    )

    @Synchronized
    private fun save(list: List<TrackedApp>) {
        prefs.edit().putString("apps", JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()).apply()
        _apps.value = list
    }

    @Synchronized
    private fun update(url: String, f: (TrackedApp) -> TrackedApp) {
        save(_apps.value.map { if (it.pageUrl == url) f(it) else it })
    }

    fun saveSettings(s: Settings) {
        prefs.edit().putInt("interval", s.intervalHours).putBoolean("wifiOnly", s.wifiOnly).putBoolean("notify", s.notify).apply()
        _settings.value = s
    }

    fun get(url: String) = _apps.value.firstOrNull { it.pageUrl == url }

    suspend fun fetch(url: String): PageInfo = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            // desktop UA: that layout carries the "نسخه نهایی" version field
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36")
            .header("Accept-Language", "fa,en;q=0.8")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            FarsroidParser.parse(r.body?.string() ?: error("Empty page"))
        }
    }

    /** Adds a page. Throws with a readable message when it is not a usable Farsroid app page. */
    suspend fun add(rawUrl: String): TrackedApp {
        val url = normalize(rawUrl) ?: error("That is not a farsroid.com link")
        get(url)?.let { return it }
        val p = fetch(url)
        if (p.variants.isEmpty()) error("No download box found on that page")
        val app = TrackedApp(
            pageUrl = url, name = p.name, packageName = p.packageName, iconUrl = p.iconUrl,
            latestVersion = p.version, updatedAt = p.updatedAt, changelog = p.changelog, variants = p.variants,
            lastChecked = System.currentTimeMillis(), lastError = null,
            seenSignature = TrackedApp.signatureOf(p.version, p.variants), preferredKind = null,
            addedAt = System.currentTimeMillis(),
        )
        synchronized(this) { save(listOf(app) + _apps.value) }
        return app
    }

    suspend fun check(url: String): CheckResult? {
        val old = get(url) ?: return null
        return try {
            val p = fetch(url)
            if (p.variants.isEmpty()) error("Download box is empty")
            val sig = TrackedApp.signatureOf(p.version, p.variants)
            val newBuild = sig != old.signature
            var result: TrackedApp? = null
            update(url) {
                it.copy(
                    name = p.name.ifBlank { it.name }, packageName = p.packageName ?: it.packageName,
                    iconUrl = p.iconUrl ?: it.iconUrl, latestVersion = p.version ?: it.latestVersion,
                    updatedAt = p.updatedAt, changelog = p.changelog, variants = p.variants,
                    lastChecked = System.currentTimeMillis(), lastError = null,
                ).also { n -> result = n }
            }
            result?.let { CheckResult(it, newBuild) }
        } catch (e: Exception) {
            update(url) { it.copy(lastChecked = System.currentTimeMillis(), lastError = e.message ?: e.javaClass.simpleName) }
            null
        }
    }

    /** Remembers which key a flavour is signed with (learned from a downloaded file). */
    fun learnSigner(url: String, flavour: String, sha: String) = update(url) {
        if (it.signers[flavour] == sha) it else it.copy(signers = it.signers + (flavour to sha))
    }

    fun markSeen(url: String) = update(url) { it.copy(seenSignature = it.signature) }
    fun setPreferred(url: String, kind: String?) = update(url) { it.copy(preferredKind = kind) }
    fun remove(url: String) = synchronized(this) { save(_apps.value.filterNot { it.pageUrl == url }) }

    /** Background notification bookkeeping: signature already notified for each page. */
    fun notifiedSignature(url: String): String? = prefs.getString("notified:$url", null)
    fun setNotified(url: String, sig: String) = prefs.edit().putString("notified:$url", sig).apply()

    companion object {
        @Volatile private var instance: Repo? = null
        fun get(context: Context): Repo = instance ?: synchronized(this) {
            instance ?: Repo(context.applicationContext).also { instance = it }
        }

        /** Pulls the first farsroid.com page link out of any text (shared text often has a title before the URL). */
        fun normalize(text: String): String? {
            val m = Regex("""https?://(?:www\.)?farsroid\.com/[^\s"'<>]+""", RegexOption.IGNORE_CASE).find(text.trim())
                ?: Regex("""(?:www\.)?farsroid\.com/[^\s"'<>]+""", RegexOption.IGNORE_CASE).find(text.trim())?.let { return normalize("https://" + it.value) }
                ?: return null
            var u = m.value.substringBefore('#').substringBefore('?')
            u = u.replace(Regex("""^http://""", RegexOption.IGNORE_CASE), "https://")
                .replace(Regex("""https://farsroid\.com""", RegexOption.IGNORE_CASE), "https://www.farsroid.com")
            if (!u.endsWith("/")) u += "/"
            return u
        }
    }
}
