package gopesh.percinel.data

/**
 * Everything the Watches list can be narrowed by. Applied per *film* (a group of viewings), so a
 * rewatched film is judged on the card the user actually sees — its latest viewing — and its
 * rewatch count is never distorted by the filter.
 *
 * Within a category choices widen (2000s OR 2010s); across categories they narrow (AND).
 */
data class WatchFilter(
    val query: String = "",
    /** "movie", "tv", or null for both. */
    val mediaType: String? = null,
    val minRating: Double = MIN_RATING,
    val maxRating: Double = MAX_RATING,
    val decades: Set<Int> = emptySet(),
    val genres: Set<String> = emptySet(),
) {
    val ratingActive: Boolean get() = minRating > MIN_RATING || maxRating < MAX_RATING

    /** Filters set in the sheet — excludes the always-visible search box and type chips. */
    val activeCount: Int
        get() = (if (ratingActive) 1 else 0) + (if (decades.isNotEmpty()) 1 else 0) + (if (genres.isNotEmpty()) 1 else 0)

    fun clearedOfSheet() = copy(minRating = MIN_RATING, maxRating = MAX_RATING, decades = emptySet(), genres = emptySet())

    /**
     * [viewings] are one film's watches, newest first. [meta] is that film's cached TMDB facts,
     * if any — without it the film can still match on everything except genre and people.
     */
    fun matches(viewings: List<Entry>, meta: Meta?): Boolean {
        val latest = viewings.firstOrNull() ?: return false

        if (mediaType != null && latest.mediaType != mediaType) return false

        // Compare at the precision ratings are stored and shown (two decimals) so a 7.995 bound
        // can't exclude a 8.00 the user can see.
        val r = Math.round(latest.rating * 100) / 100.0
        if (r < minRating - 1e-9 || r > maxRating + 1e-9) return false

        if (decades.isNotEmpty()) {
            val year = latest.year ?: return false
            if (decadeOf(year) !in decades) return false
        }

        if (genres.isNotEmpty()) {
            val g = meta?.genres ?: return false
            if (g.none { it in genres }) return false
        }

        val q = query.trim()
        if (q.isNotEmpty()) {
            val hit = latest.title.contains(q, ignoreCase = true) ||
                meta?.director?.contains(q, ignoreCase = true) == true ||
                meta?.cast?.any { it.contains(q, ignoreCase = true) } == true ||
                viewings.any { it.notes?.contains(q, ignoreCase = true) == true }
            if (!hit) return false
        }
        return true
    }

    companion object {
        const val MIN_RATING = 1.0
        const val MAX_RATING = 10.0
        fun decadeOf(year: Int) = (year / 10) * 10
    }
}
