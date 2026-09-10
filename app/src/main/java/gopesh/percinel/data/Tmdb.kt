package gopesh.percinel.data

import gopesh.percinel.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

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
    // Numeric facts kept for the stats/recap features, which can't re-fetch per film.
    val runtimeMinutes: Int?,
    val episodes: Int?,
    val seasons: Int?,
    /** Director for a film; the creator(s) for a series. */
    val director: String?,
)

/** Where a title can be streamed, for the viewer's region. */
data class Providers(val region: String, val flatrate: List<String>, val rent: List<String>, val link: String?)

object Tmdb {
    fun posterUrl(path: String?, size: String = "w185"): String? =
        if (path.isNullOrEmpty()) null else "https://image.tmdb.org/t/p/$size$path"

    fun backdropUrl(path: String?, size: String = "w780"): String? =
        if (path.isNullOrEmpty()) null else "https://image.tmdb.org/t/p/$size$path"

    private fun get(url: String): String? {
        val token = BuildConfig.TMDB_TOKEN
        if (token.isBlank()) throw IllegalStateException("Missing TMDb token")
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("accept", "application/json")
            .build()
        Net.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("TMDb ${resp.code}")
            return resp.body?.string()
        }
    }

    suspend fun details(mediaType: String, tmdbId: Long): Details = withContext(Dispatchers.IO) {
        val body = get(
            "https://api.themoviedb.org/3/$mediaType/$tmdbId" +
                "?language=en-US&append_to_response=credits"
        ) ?: throw RuntimeException("Empty TMDb response")
        val o = JSONObject(body)

        val genres = o.optJSONArray("genres")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optString("name").takeIf(String::isNotBlank) }
        } ?: emptyList()

        val isMovie = mediaType == "movie"
        val runtimeMinutes = if (isMovie) {
            o.optInt("runtime").takeIf { it > 0 }
        } else {
            o.optJSONArray("episode_run_time")?.let { arr ->
                (0 until arr.length()).map { arr.optInt(it) }.filter { it > 0 }.average().takeIf { !it.isNaN() }?.toInt()
            }
        }
        val episodes = if (isMovie) null else o.optInt("number_of_episodes").takeIf { it > 0 }
        val seasons = if (isMovie) null else o.optInt("number_of_seasons").takeIf { it > 0 }

        val runtimeText = if (isMovie) {
            runtimeMinutes?.let { "${it / 60}h ${it % 60}m".replace("0h ", "") }
        } else {
            seasons?.let { "$it season${if (it == 1) "" else "s"}" }
        }

        val credits = o.optJSONObject("credits")
        val cast = credits?.optJSONArray("cast")?.let { arr ->
            (0 until minOf(arr.length(), 12)).map { i ->
                val c = arr.getJSONObject(i)
                CastMember(
                    name = c.optString("name"),
                    character = c.optString("character"),
                    profilePath = c.optString("profile_path").takeIf { it.isNotBlank() && it != "null" },
                )
            }
        } ?: emptyList()

        val director = if (isMovie) {
            credits?.optJSONArray("crew")?.let { arr ->
                (0 until arr.length())
                    .map { arr.getJSONObject(it) }
                    .firstOrNull { it.optString("job") == "Director" }
                    ?.optString("name")
                    ?.takeIf { it.isNotBlank() }
            }
        } else {
            o.optJSONArray("created_by")?.let { arr ->
                (0 until arr.length())
                    .mapNotNull { arr.getJSONObject(it).optString("name").takeIf(String::isNotBlank) }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ")
            }
        }

        Details(
            overview = o.optString("overview"),
            backdropPath = o.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" },
            genres = genres,
            tagline = o.optString("tagline"),
            runtimeText = runtimeText,
            cast = cast,
            runtimeMinutes = runtimeMinutes,
            episodes = episodes,
            seasons = seasons,
            director = director,
        )
    }

    /** Streaming availability for the viewer's country. Null when TMDB has nothing for the region. */
    suspend fun providers(mediaType: String, tmdbId: Long, region: String = defaultRegion()): Providers? =
        withContext(Dispatchers.IO) {
            if (tmdbId == 0L) return@withContext null
            val body = runCatching {
                get("https://api.themoviedb.org/3/$mediaType/$tmdbId/watch/providers")
            }.getOrNull() ?: return@withContext null
            val results = JSONObject(body).optJSONObject("results") ?: return@withContext null
            val r = results.optJSONObject(region) ?: return@withContext null
            fun names(key: String): List<String> =
                r.optJSONArray(key)?.let { arr ->
                    (0 until arr.length()).mapNotNull {
                        arr.getJSONObject(it).optString("provider_name").takeIf(String::isNotBlank)
                    }
                }?.distinct() ?: emptyList()
            val flatrate = names("flatrate")
            val rent = (names("rent") + names("buy")).distinct()
            if (flatrate.isEmpty() && rent.isEmpty()) return@withContext null
            Providers(
                region = region,
                flatrate = flatrate,
                rent = rent,
                link = r.optString("link").takeIf { it.isNotBlank() && it != "null" },
            )
        }

    fun defaultRegion(): String =
        Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.US) ?: "US"

    /** TMDB's own "recommended" titles for a given movie/series. Returns [] on any failure. */
    suspend fun recommendations(mediaType: String, tmdbId: Long): List<SearchResult> = withContext(Dispatchers.IO) {
        if (BuildConfig.TMDB_TOKEN.isBlank() || tmdbId == 0L) return@withContext emptyList()
        val body = runCatching {
            get("https://api.themoviedb.org/3/$mediaType/$tmdbId/recommendations?language=en-US&page=1")
        }.getOrNull() ?: return@withContext emptyList()
        parseResults(body, fallbackType = mediaType)
    }

    /** Returns results, or throws on network/auth failure so the UI can show a message. */
    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val body = get(
            "https://api.themoviedb.org/3/search/multi" +
                "?include_adult=false&language=en-US&page=1&query=" +
                URLEncoder.encode(q, "UTF-8")
        ) ?: return@withContext emptyList()
        parseResults(body, fallbackType = null)
    }

    private fun parseResults(body: String, fallbackType: String?): List<SearchResult> {
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<SearchResult>(results.length())
        for (i in 0 until results.length()) {
            val o = results.getJSONObject(i)
            val mt = o.optString("media_type").ifBlank { fallbackType ?: "" }
            if (mt != "movie" && mt != "tv") continue
            val dateStr = if (mt == "movie") o.optString("release_date") else o.optString("first_air_date")
            val year = if (dateStr.length >= 4) dateStr.substring(0, 4).toIntOrNull() else null
            val title = (if (mt == "movie") o.optString("title") else o.optString("name"))
                .ifBlank { o.optString("name").ifBlank { o.optString("title") } }
            out.add(
                SearchResult(
                    tmdbId = o.optLong("id"),
                    mediaType = mt,
                    title = title.ifBlank { "Untitled" },
                    year = year,
                    posterPath = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" },
                    overview = o.optString("overview"),
                ),
            )
        }
        return out
    }
}
