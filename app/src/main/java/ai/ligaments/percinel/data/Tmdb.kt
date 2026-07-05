package ai.ligaments.percinel.data

import ai.ligaments.percinel.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SearchResult(
    val tmdbId: Long,
    val mediaType: String,
    val title: String,
    val year: Int?,
    val posterPath: String?,
    val overview: String,
)

object Tmdb {
    fun posterUrl(path: String?, size: String = "w185"): String? =
        if (path.isNullOrEmpty()) null else "https://image.tmdb.org/t/p/$size$path"

    /** Returns results, or throws on network/auth failure so the UI can show a message. */
    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val token = BuildConfig.TMDB_TOKEN
        if (token.isBlank()) throw IllegalStateException("Missing TMDb token")

        val url = URL(
            "https://api.themoviedb.org/3/search/multi" +
                "?include_adult=false&language=en-US&page=1&query=" +
                URLEncoder.encode(q, "UTF-8")
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("accept", "application/json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (conn.responseCode != 200) throw RuntimeException("TMDb ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
            val out = ArrayList<SearchResult>(results.length())
            for (i in 0 until results.length()) {
                val o = results.getJSONObject(i)
                val mt = o.optString("media_type")
                if (mt != "movie" && mt != "tv") continue
                val dateStr = if (mt == "movie") o.optString("release_date") else o.optString("first_air_date")
                val year = if (dateStr.length >= 4) dateStr.substring(0, 4).toIntOrNull() else null
                val title = (if (mt == "movie") o.optString("title") else o.optString("name"))
                    .ifBlank { o.optString("name").ifBlank { o.optString("title") } }
                val poster = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                out.add(
                    SearchResult(
                        tmdbId = o.optLong("id"),
                        mediaType = mt,
                        title = title.ifBlank { "Untitled" },
                        year = year,
                        posterPath = poster,
                        overview = o.optString("overview"),
                    )
                )
            }
            out
        } finally {
            conn.disconnect()
        }
    }
}
