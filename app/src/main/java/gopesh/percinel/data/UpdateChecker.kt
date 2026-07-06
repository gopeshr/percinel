package gopesh.percinel.data

import gopesh.percinel.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val downloadUrl: String, val notes: String)

/**
 * Checks GitHub Releases for a newer version than the installed one. Free, no auth (public API).
 * Returns the newest release's version + APK download URL if it's newer than [BuildConfig.VERSION_NAME],
 * otherwise null. Any failure (offline, rate-limited) is a silent null.
 */
object UpdateChecker {
    private const val LATEST = "https://api.github.com/repos/gopeshr/percinel/releases/latest"

    /** @param force when true, treat the latest release as an update even if it isn't newer
     *  (used by the hidden "test update" dev toggle). */
    suspend fun check(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                // GitHub's API rejects requests without a User-Agent.
                setRequestProperty("User-Agent", "percinel-android")
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (conn.responseCode != 200) return@withContext null
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = o.optString("tag_name").removePrefix("v").trim()
            if (tag.isEmpty() || (!force && !isNewer(tag, BuildConfig.VERSION_NAME))) return@withContext null

            // Prefer the direct .apk asset; fall back to the release page.
            var url = o.optString("html_url")
            o.optJSONArray("assets")?.let { assets ->
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        url = a.optString("browser_download_url")
                        break
                    }
                }
            }
            UpdateInfo(version = tag, downloadUrl = url, notes = cleanNotes(o.optString("body")))
        } catch (_: Exception) {
            null
        }
    }

    /** Flatten a markdown release body into a short, single-line teaser (the banner clamps to 2 lines). */
    private fun cleanNotes(raw: String): String {
        if (raw.isBlank()) return ""
        var s = raw
            .substringBefore("🤖")
            .substringBefore("Generated with")
            .replace(Regex("(?m)^#{1,6}\\s*"), "")          // headers
            .replace(Regex("(?m)^\\s*[-*]\\s+"), "")         // bullets
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1") // [text](url) -> text
            .replace(Regex("[*_`>]"), "")                    // emphasis / code / quote marks
            .replace(Regex("\\s+"), " ")                     // collapse whitespace + newlines
            .trim()
        if (s.length > 160) s = s.take(157).trimEnd() + "…"
        return s
    }

    /** Dotted numeric compare, e.g. "1.24" > "1.23". Internal so it can be unit-tested. */
    internal fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.trim().toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }
}
