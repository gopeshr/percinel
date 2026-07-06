package gopesh.percinel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Recommendation(val item: SearchResult, val reason: String)

/**
 * Free, taste-based recommendations built entirely from TMDB's own "recommendations" endpoint —
 * no LLM, no cost. Seeds from your highest-rated watches, aggregates the suggestions, drops
 * anything you've already watched or watchlisted, and ranks by how often a title comes up.
 */
object Recommender {
    suspend fun forYou(repo: Repo, max: Int = 20): List<Recommendation> = withContext(Dispatchers.IO) {
        val watched = repo.list()
        val known = (watched + repo.watchlist())
            .map { it.tmdbId }
            .filter { it != 0L }
            .toSet()

        val seeds = watched.filter { it.tmdbId != 0L }
            .sortedByDescending { it.rating }
            .take(6)
        if (seeds.isEmpty()) return@withContext emptyList()

        class Agg(val item: SearchResult, var count: Int, val seed: String)
        val byId = LinkedHashMap<Long, Agg>()
        for (seed in seeds) {
            val recs = Tmdb.recommendations(seed.mediaType, seed.tmdbId)
            for (r in recs) {
                if (r.tmdbId == 0L || r.tmdbId in known) continue
                val existing = byId[r.tmdbId]
                if (existing == null) byId[r.tmdbId] = Agg(r, 1, seed.title) else existing.count++
            }
        }

        byId.values
            .sortedByDescending { it.count }
            .take(max)
            .map { Recommendation(it.item, "Because you liked ${it.seed}") }
    }
}
