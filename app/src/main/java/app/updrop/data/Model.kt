package app.updrop.data

import org.json.JSONArray
import org.json.JSONObject

/** One download link from a Farsroid "download box". */
data class Variant(
    val url: String,
    val title: String,          // original Persian title from the site
    val processor: String,      // raw value from the site, e.g. "armv8a"
    val fresh: Boolean,         // the site marks this link "به‌روز شده" (just updated)
    val updateTime: Long,       // unix seconds, 0 when unknown
) {
    /** File name without the "(www.Farsroid.com).apk" tail, e.g. "Lightroom-Premium-11.6.12-Mod-Extra". */
    val fileStem: String get() = url.substringAfterLast('/').substringBefore('(').removeSuffix(".apks").removeSuffix(".apk").trim('-', '_', ' ')

    /** .apks = split bundle: Android's own installer cannot open it (needs SAI or similar). */
    val isBundle: Boolean get() = url.substringBefore('?').lowercase().let { it.endsWith(".apks") || it.endsWith(".xapk") || it.endsWith(".apkm") }

    /** Android ABI this file is built for, or null when the site gives no CPU (= universal). */
    val abi: String? get() = Abi.fromProcessor(processor)

    /** Short English label: "Premium", "Premium · Offline", "Mod-Extra"… derived from the file name. */
    val label: String get() = Labels.labelFor(this)

    /** Label with the app's own name removed, e.g. "Premium · Offline". */
    fun labelIn(appName: String): String = Labels.labelFor(this, appName)

    /** Variant "kind" without the version number, used to remember the user's preferred flavour. */
    val kind: String get() = Labels.kindFor(this)

    /** Flavour without CPU: Farsroid signs every CPU build of one flavour with the same key. */
    val flavour: String get() = kind.substringBefore('@')

    /** Size as printed in the title ("250 MB"), or null. */
    val size: String? get() = Labels.sizeFor(title)

    fun toJson() = JSONObject()
        .put("url", url).put("title", title).put("processor", processor)
        .put("fresh", fresh).put("updateTime", updateTime)

    companion object {
        fun fromJson(o: JSONObject) = Variant(
            url = o.optString("url"), title = o.optString("title"),
            processor = o.optString("processor"), fresh = o.optBoolean("fresh"),
            updateTime = o.optLong("updateTime"),
        )
    }
}

/** What one parse of a Farsroid page found. */
data class PageInfo(
    val name: String,
    val version: String?,
    val packageName: String?,
    val iconUrl: String?,
    val updatedAt: String?,     // og:updated_time, ISO text
    val changelog: List<String>,
    val variants: List<Variant>,
)

/** A page the user follows. */
data class TrackedApp(
    val pageUrl: String,
    val name: String,
    val packageName: String?,
    val iconUrl: String?,
    val latestVersion: String?,
    val updatedAt: String?,
    val changelog: List<String>,
    val variants: List<Variant>,
    val lastChecked: Long,      // ms
    val lastError: String?,
    val seenSignature: String,  // signature of the variants the user has already been told about
    val preferredKind: String?, // flavour the user pinned / last downloaded
    val addedAt: Long,
    val signers: Map<String, String> = emptyMap(), // flavour (Variant.flavour) -> signer SHA-256, learned from downloads
) {
    /** Changes whenever the site publishes a new build (new file names). */
    val signature: String get() = signatureOf(latestVersion, variants)

    /** The site has something the user has not opened yet. */
    val hasUnseen: Boolean get() = seenSignature.isNotEmpty() && seenSignature != signature

    fun toJson(): JSONObject = JSONObject()
        .put("pageUrl", pageUrl).put("name", name).put("packageName", packageName)
        .put("iconUrl", iconUrl).put("latestVersion", latestVersion).put("updatedAt", updatedAt)
        .put("changelog", JSONArray(changelog))
        .put("variants", JSONArray().apply { variants.forEach { put(it.toJson()) } })
        .put("lastChecked", lastChecked).put("lastError", lastError)
        .put("seenSignature", seenSignature).put("preferredKind", preferredKind).put("addedAt", addedAt)
        .put("signers", JSONObject().apply { signers.forEach { (k, v) -> put(k, v) } })

    companion object {
        fun signatureOf(version: String?, variants: List<Variant>) =
            (version ?: "") + "|" + variants.map { it.url }.sorted().joinToString(",")

        fun fromJson(o: JSONObject): TrackedApp {
            val cl = o.optJSONArray("changelog") ?: JSONArray()
            val vs = o.optJSONArray("variants") ?: JSONArray()
            return TrackedApp(
                pageUrl = o.getString("pageUrl"),
                name = o.optString("name"),
                packageName = o.optString("packageName").ifBlank { null }.takeIf { it != "null" },
                iconUrl = o.optString("iconUrl").ifBlank { null }.takeIf { it != "null" },
                latestVersion = o.optString("latestVersion").ifBlank { null }.takeIf { it != "null" },
                updatedAt = o.optString("updatedAt").ifBlank { null }.takeIf { it != "null" },
                changelog = (0 until cl.length()).map { cl.getString(it) },
                variants = (0 until vs.length()).map { Variant.fromJson(vs.getJSONObject(it)) },
                lastChecked = o.optLong("lastChecked"),
                lastError = o.optString("lastError").ifBlank { null }.takeIf { it != "null" },
                seenSignature = o.optString("seenSignature"),
                preferredKind = o.optString("preferredKind").ifBlank { null }.takeIf { it != "null" },
                addedAt = o.optLong("addedAt"),
                signers = o.optJSONObject("signers")?.let { j -> j.keys().asSequence().associateWith { j.getString(it) } } ?: emptyMap(),
            )
        }
    }
}

object Abi {
    fun fromProcessor(p: String): String? {
        val s = p.lowercase().replace("-", "").replace("_", "").replace(" ", "")
        return when {
            s.isBlank() || s in setOf("all", "universal", "any", "0") -> null
            s.contains("x8664") || s == "x64" -> "x86_64"
            s.contains("x86") -> "x86"
            s.contains("v8") || s.contains("arm64") || s.contains("aarch64") -> "arm64-v8a"
            s.contains("v7") || s == "arm" || s.contains("armeabi") -> "armeabi-v7a"
            else -> null
        }
    }

    /** Human label for an ABI. */
    fun pretty(abi: String?): String = when (abi) {
        null -> "Universal"
        "arm64-v8a" -> "ARM64 (v8a)"
        "armeabi-v7a" -> "ARM (v7a)"
        "x86_64" -> "x86_64"
        "x86" -> "x86"
        else -> abi
    }
}

object Labels {
    private val versionRe = Regex("""[-_ ]v?\d+(\.\d+)+[a-zA-Z]?""")

    /** Parts of the file name around the version: "Lightroom-Premium-11.6.12-Mod-Extra" -> ("Lightroom-Premium", "Mod-Extra"). */
    private fun split(stem: String): Pair<String, String> {
        val m = versionRe.find(stem) ?: return stem to ""
        return stem.substring(0, m.range.first) to stem.substring(m.range.last + 1).trim('-', '_', ' ')
    }

    private val abiToken = Regex("""(?i)[-_ ]?(arm64[-_]?v8a|armeabi[-_]?v7a|armv8a|armv7a|arm64|armv7|armeabi|x86[-_]64|x86|universal)""")

    /** Cleans the tail: drops CPU tokens, turns a bare API number (e.g. "29") into "Android 10+". */
    private fun cleanTail(tail: String): String {
        val t = abiToken.replace(tail, "").trim('-', '_', ' ')
        return t.split('-', '_', ' ').filter { it.isNotBlank() }.joinToString("-") { part ->
            val n = part.toIntOrNull()
            if (n != null && n in 21..40) "Android ${apiToAndroid(n)}+" else part
        }
    }

    private fun apiToAndroid(api: Int) = when (api) {
        21, 22 -> "5"; 23 -> "6"; 24, 25 -> "7"; 26, 27 -> "8"; 28 -> "9"
        else -> if (api >= 29) (api - 19).toString() else api.toString()
    }

    fun kindFor(v: Variant): String {
        val (head, tail) = split(v.fileStem)
        val headTag = abiToken.replace(head.substringAfter('-', ""), "")   // drop the app name, keep "Premium"
        return listOf(headTag, cleanTail(tail)).filter { it.isNotBlank() }.joinToString("-").ifBlank { "Standard" } +
            "@" + (v.abi ?: "universal")
    }

    fun labelFor(v: Variant, appName: String? = null): String {
        val (head, tail) = split(v.fileStem)
        // drop the words of the app's own name ("SD-Card-Test-Pro" for "SD Card Test Pro"); without a name, drop the first word
        val nameWords = appName?.lowercase()?.split(Regex("""[^\p{L}\p{N}]+"""))?.filter { it.isNotBlank() }?.toSet()
        val headWords = abiToken.replace(head, "").split('-', '_', ' ').filter { it.isNotBlank() }
        val headTag = (if (nameWords != null) headWords.filter { it.lowercase() !in nameWords } else headWords.drop(1)).joinToString(" ")
        val tailTag = cleanTail(tail).replace('-', ' ')
        val base = listOf(headTag, tailTag).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Standard" }
        return if (v.isBundle) "$base · APKS" else base
    }

    private val sizeRe = Regex("""(\d+(?:[.,]\d+)?)\s*(مگابایت|گیگابایت|کیلوبایت|MB|GB|KB)""", RegexOption.IGNORE_CASE)

    fun sizeFor(title: String): String? {
        val m = sizeRe.find(title) ?: return null
        val unit = when (m.groupValues[2]) {
            "مگابایت" -> "MB"; "گیگابایت" -> "GB"; "کیلوبایت" -> "KB"; else -> m.groupValues[2].uppercase()
        }
        return m.groupValues[1].replace(',', '.') + " " + unit
    }

    /** Size in MB for sorting, or null. */
    fun megabytes(title: String): Double? {
        val s = sizeFor(title) ?: return null
        val n = s.substringBefore(' ').toDoubleOrNull() ?: return null
        return when { s.endsWith("GB") -> n * 1024; s.endsWith("KB") -> n / 1024; else -> n }
    }
}

object Versions {
    /** Compares "11.6.12" style versions numerically. Unknown parts compare as 0. */
    fun compare(a: String?, b: String?): Int {
        if (a == null || b == null) return 0
        val pa = Regex("""\d+""").findAll(a).map { it.value.toBigInteger() }.toList()
        val pb = Regex("""\d+""").findAll(b).map { it.value.toBigInteger() }.toList()
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = (pa.getOrNull(i) ?: 0.toBigInteger()).compareTo(pb.getOrNull(i) ?: 0.toBigInteger())
            if (c != 0) return c
        }
        return 0
    }
}
