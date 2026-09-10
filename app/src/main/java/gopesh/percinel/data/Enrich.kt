package gopesh.percinel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fills the [Meta] cache for films that don't have it yet, so the stats and year-recap screens
 * have runtimes, genres and people to work with. One TMDB call per title, resumable — anything
 * that fails is simply left for next time rather than aborting the run.
 */
object Enrich {

    /** How many titles still need a lookup. */
    fun pending(repo: Repo): Int = repo.filmsMissingMeta().size

    suspend fun run(
        repo: Repo,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        val missing = repo.filmsMissingMeta()
        if (missing.isEmpty()) return@withContext 0
        var ok = 0
        missing.forEachIndexed { i, (type, id) ->
            runCatching {
                val d = Tmdb.details(type, id)
                repo.putMeta(
                    Meta(
                        tmdbId = id,
                        mediaType = type,
                        runtimeMin = d.runtimeMinutes,
                        episodes = d.episodes,
                        seasons = d.seasons,
                        genres = d.genres,
                        director = d.director,
                        cast = d.cast.map { it.name }.filter { it.isNotBlank() }.take(8),
                    ),
                )
            }.onSuccess { ok++ }
            onProgress(i + 1, missing.size)
        }
        ok
    }

    /** Cache what a detail screen already fetched, so browsing warms the cache for free. */
    fun remember(repo: Repo, mediaType: String, tmdbId: Long, d: Details) {
        if (tmdbId == 0L) return
        runCatching {
            repo.putMeta(
                Meta(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    runtimeMin = d.runtimeMinutes,
                    episodes = d.episodes,
                    seasons = d.seasons,
                    genres = d.genres,
                    director = d.director,
                    cast = d.cast.map { it.name }.filter { it.isNotBlank() }.take(8),
                ),
            )
        }
    }
}
