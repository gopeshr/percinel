package gopesh.percinel.data

import gopesh.percinel.BuildConfig
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

data class CastMember(val name: String, val character: String, val profilePath: String?)

data class Details(
    val overview: String,
    val backdropPath: String?,
    val genres: List<String>,
    val tagline: String,
    val runtimeText: String?,
    val cast: List<CastMember>,
)

object Tmdb {
    fun posterUrl(path: String?, size: String = "w185"): String? =
        if (path.isNullOrEmpty()) null else "https://image.tmdb.org/t/p/$size$path"

    fun backdropUrl(path: String?, size: String = "w780"): String? =
        if (path.isNullOrEmpty()) null else "https://image.tmdb.org/t/p/$size$path"

    suspend fun details(mediaType: String, tmdbId: Long): Details = withContext(Dispatchers.IO) {
        val token = BuildConfig.TMDB_TOKEN
        if (token.isBlank()) throw IllegalStateException("Missing TMDb token")
        val url = URL(
            "https://api.themoviedb.org/3/$mediaType/$tmdbId" +
                "?language=en-US&append_to_response=credits"
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
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })

            val genres = o.optJSONArray("genres")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optString("name").takeIf(String::isNotBlank) }
            } ?: emptyList()

            val runtimeText = if (mediaType == "movie") {
                val m = o.optInt("runtime")
                if (m > 0) "${m / 60}h ${m % 60}m".replace("0h ", "") else null
            } else {
                val seasons = o.optInt("number_of_seasons")
                if (seasons > 0) "$seasons season${if (seasons == 1) "" else "s"}" else null
            }

            val cast = o.optJSONObject("credits")?.optJSONArray("cast")?.let { arr ->
                (0 until minOf(arr.length(), 12)).map { i ->
                    val c = arr.getJSONObject(i)
                    CastMember(
                        name = c.optString("name"),
                        character = c.optString("character"),
                        profilePath = c.optString("profile_path").takeIf { it.isNotBlank() && it != "null" },
                    )
                }
            } ?: emptyList()

            Details(
                overview = o.optString("overview"),
                backdropPath = o.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" },
                genres = genres,
                tagline = o.optString("tagline"),
                runtimeText = runtimeText,
                cast = cast,
            )
        } finally {
            conn.disconnect()
        }
    }

    /** TMDB's own "recommended" titles for a given movie/series. Returns [] on any failure. */
    suspend fun recommendations(mediaType: String, tmdbId: Long): List<SearchResult> = withContext(Dispatchers.IO) {
        val token = BuildConfig.TMDB_TOKEN
        if (token.isBlank() || tmdbId == 0L) return@withContext emptyList()
        val url = URL("https://api.themoviedb.org/3/$mediaType/$tmdbId/recommendations?language=en-US&page=1")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("accept", "application/json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (conn.responseCode != 200) return@withContext emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
            val out = ArrayList<SearchResult>(results.length())
            for (i in 0 until results.length()) {
                val o = results.getJSONObject(i)
                val mt = o.optString("media_type").ifBlank { mediaType }
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
                    ),
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

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
