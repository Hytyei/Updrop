package app.updrop.data

import android.util.Base64
import org.json.JSONArray

/**
 * Reads a Farsroid app page.
 *
 * The download box is NOT in the HTML as links: the server puts the list into
 * `<div id="secure-json">` as several `<i data-j="…">` chunks (with decoy `<b hidden>` tags between).
 * Joining every data-j value in order gives one base64 string that decodes to a JSON array of
 * `{update, title, url, processor, sign, update_time}`. The page's own JS renders the list from it.
 * Rendered `data-link="…apk"` rows are used as a fallback in case the site ever server-renders them.
 */
object FarsroidParser {

    fun parse(html: String): PageInfo {
        val variants = secureJson(html).ifEmpty { renderedLinks(html) }
        return PageInfo(
            name = name(html),
            version = version(html, variants),
            packageName = Regex("""play\.google\.com/store/apps/details\?id=([\w.]+)""").find(html)?.groupValues?.get(1),
            iconUrl = meta(html, "og:image"),
            updatedAt = meta(html, "og:updated_time"),
            changelog = changelog(html),
            variants = variants,
        )
    }

    private fun secureJson(html: String): List<Variant> {
        val start = html.indexOf("id=\"secure-json\"")
        if (start < 0) return emptyList()
        val end = html.indexOf("</div>", start).let { if (it < 0) html.length else it }
        val block = html.substring(start, end)
        val b64 = Regex("""data-j="([^"]*)"""").findAll(block).joinToString("") { it.groupValues[1] }
        if (b64.isBlank()) return emptyList()
        return try {
            val json = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it) }.mapNotNull { o ->
                val url = o.optString("url").trim()
                if (url.isBlank()) null else Variant(
                    url = url,
                    title = o.optString("title").trim(),
                    processor = o.optString("processor"),
                    fresh = o.optInt("update") == 1,
                    updateTime = o.optLong("update_time"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun renderedLinks(html: String): List<Variant> =
        Regex("""data-link="(https?://[^"]+\.apk[^"]*)"""").findAll(html).map { it.groupValues[1] }
            .distinct().map { url ->
                Variant(url = decodeEntities(url), title = "", processor = "", fresh = false, updateTime = 0)
            }.toList()

    private fun name(html: String): String {
        // the English app name is the part before " - " / "-دانلود" in <title>
        val title = Regex("""<title>([^<]+)</title>""").find(html)?.groupValues?.get(1)?.let(::decodeEntities) ?: return "Unknown app"
        val en = title.split(Regex("""\s*[-–—]\s*""")).firstOrNull { it.any { c -> c in 'A'..'Z' || c in 'a'..'z' } }
        return (en ?: title).replace("دانلود", "").replace(Regex("""\s+v?\d+(\.\d+)*\s*$"""), "").trim().ifBlank { title.trim() }
    }

    private fun version(html: String, variants: List<Variant>): String? {
        val patterns = listOf(
            Regex("""نسخه نهایی</span>\s*([0-9][\w.\-]*)"""),
            Regex("""ورژن موجود:</span>\s*<span class="value">\s*([0-9][\w.\-]*)"""),
            Regex("""class="version-name">[^<0-9]*([0-9][\w.\-]*)"""),
        )
        patterns.forEach { p -> p.find(html)?.groupValues?.get(1)?.trim('.', '-')?.let { return it } }
        // last resort: the version inside the first file name
        return variants.firstNotNullOfOrNull { Regex("""\d+(\.\d+)+""").find(it.fileStem)?.value }
    }

    private fun changelog(html: String): List<String> {
        val i = html.indexOf("class=\"app-changelog\"")
        if (i < 0) return emptyList()
        val end = html.indexOf("</section>", i).let { if (it < 0) minOf(html.length, i + 4000) else it }
        return Regex("""<li>\s*([^<]+?)\s*</li>""").findAll(html.substring(i, end))
            .map { decodeEntities(it.groupValues[1]).trimStart('•', ' ').trim() }
            .filter { it.isNotBlank() }.take(20).toList()
    }

    private fun meta(html: String, prop: String): String? =
        Regex("""<meta property="$prop" content="([^"]+)"""").find(html)?.groupValues?.get(1)?.let(::decodeEntities)

    fun decodeEntities(s: String): String = s
        .replace("&amp;", "&").replace("&#038;", "&").replace("&quot;", "\"")
        .replace("&#8211;", "–").replace("&nbsp;", " ").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")
}
