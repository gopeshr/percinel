package gopesh.percinel.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Every derived number the stats, year-recap, heatmap and "pick one for me" screens show.
 *
 * Deliberately pure Kotlin — no Android, no network — so the maths is unit-testable and the UI
 * stays a thin renderer. Anything needing TMDB facts (runtime, genre, people) takes a [Meta] map
 * and degrades gracefully when a title hasn't been enriched yet.
 */
object Insights {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun yearOf(millis: Long): Int = dayOf(millis).year

    /** Group viewings into one row per film (latest watch wins), so rewatches don't double-count. */
    fun perFilm(entries: List<Entry>): List<Entry> =
        entries.groupBy { filmKey(it) }.mapNotNull { (_, v) -> v.maxByOrNull { it.watchedAt } }

    fun filmKey(e: Entry): String = if (e.tmdbId != 0L) "t:${e.mediaType}:${e.tmdbId}" else "m:${e.id}"

    fun metaFor(e: Entry, meta: Map<String, Meta>): Meta? =
        if (e.tmdbId == 0L) null else meta[metaKey(e.mediaType, e.tmdbId)]

    /**
     * Approximate watch time for a single viewing.
     * A film is its runtime. A series watch is episode-length × episodes — for the logged season
     * where we know the season count, otherwise the whole run. Null when runtime is unknown.
     */
    fun minutesFor(e: Entry, m: Meta?): Int? {
        val per = m?.runtimeMin ?: return null
        if (e.mediaType == "movie") return per
        val episodes = m.episodes ?: return per
        val seasons = m.seasons ?: 1
        return if (e.season != null && seasons > 0) {
            per * maxOf(1, episodes / seasons)
        } else {
            per * episodes
        }
    }

    fun totalMinutes(entries: List<Entry>, meta: Map<String, Meta>): Int =
        entries.sumOf { minutesFor(it, metaFor(it, meta)) ?: 0 }

    // ------------------------------------------------------------------ counting helpers

    /** Weighted tallies of a text attribute (genre / director / actor) across films. */
    private fun tally(
        films: List<Entry>,
        meta: Map<String, Meta>,
        pick: (Meta) -> List<String>,
    ): List<Pair<String, Int>> {
        val counts = LinkedHashMap<String, Int>()
        for (f in films) {
            val m = metaFor(f, meta) ?: continue
            for (name in pick(m).map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
                counts[name] = (counts[name] ?: 0) + 1
            }
        }
        return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
    }

    fun topGenres(films: List<Entry>, meta: Map<String, Meta>, n: Int = 5) =
        tally(films, meta) { it.genres }.take(n)

    fun topDirectors(films: List<Entry>, meta: Map<String, Meta>, n: Int = 5) =
        tally(films, meta) { listOfNotNull(it.director) }.take(n)

    fun topActors(films: List<Entry>, meta: Map<String, Meta>, n: Int = 5) =
        tally(films, meta) { it.cast.take(6) }.take(n)

    /** Count per release decade, newest decade first. Films without a year are skipped. */
    fun byDecade(films: List<Entry>): List<Pair<Int, Int>> =
        films.mapNotNull { it.year }
            .groupingBy { (it / 10) * 10 }
            .eachCount()
            .entries.sortedByDescending { it.key }
            .map { it.key to it.value }

    /** Counts in rating buckets 1..10, where a bucket holds ratings [n, n+1) and 10 holds exactly 10. */
    fun ratingHistogram(films: List<Entry>): List<Pair<Int, Int>> {
        val counts = IntArray(11)
        for (f in films) {
            val b = f.rating.toInt().coerceIn(1, 10)
            counts[b]++
        }
        return (1..10).map { it to counts[it] }
    }

    // ------------------------------------------------------------------ heatmap

    /** Viewings per day for a calendar year — the input to the contribution-style grid. */
    fun heatmap(year: Int, entries: List<Entry>): Map<LocalDate, Int> {
        val out = HashMap<LocalDate, Int>()
        for (e in entries) {
            val d = dayOf(e.watchedAt)
            if (d.year != year) continue
            out[d] = (out[d] ?: 0) + 1
        }
        return out
    }

    /** Longest run of consecutive days with at least one watch, within [entries]. */
    fun longestStreak(entries: List<Entry>): Int {
        val days = entries.map { dayOf(it.watchedAt) }.distinct().sorted()
        if (days.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until days.size) {
            run = if (days[i - 1].plusDays(1) == days[i]) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    // ------------------------------------------------------------------ year recap

    data class FilmYear(
        val year: Int,
        val films: Int,
        val viewings: Int,
        val movies: Int,
        val series: Int,
        val minutes: Int,
        val avgRating: Double?,
        val best: Entry?,
        val worst: Entry?,
        val topGenres: List<Pair<String, Int>>,
        val topDirectors: List<Pair<String, Int>>,
        val topActors: List<Pair<String, Int>>,
        val busiestMonth: Pair<Int, Int>?,
        val longestStreak: Int,
        val rewatches: Int,
    ) {
        val hours: Int get() = minutes / 60
        val isEmpty: Boolean get() = viewings == 0
    }

    /** Everything the "Your Film Year" card shows, computed from one year's viewings. */
    fun filmYear(year: Int, allWatched: List<Entry>, meta: Map<String, Meta>): FilmYear {
        val viewings = allWatched.filter { yearOf(it.watchedAt) == year }
        val films = perFilm(viewings)
        val busiest = viewings
            .groupingBy { dayOf(it.watchedAt).monthValue }
            .eachCount()
            .entries.maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenByDescending { -it.key })
            ?.let { it.key to it.value }

        return FilmYear(
            year = year,
            films = films.size,
            viewings = viewings.size,
            movies = films.count { it.mediaType == "movie" },
            series = films.count { it.mediaType == "tv" },
            minutes = totalMinutes(viewings, meta),
            avgRating = films.map { it.rating }.takeIf { it.isNotEmpty() }?.average(),
            best = films.maxByOrNull { it.rating },
            worst = films.minByOrNull { it.rating },
            topGenres = topGenres(films, meta, 5),
            topDirectors = topDirectors(films, meta, 3),
            topActors = topActors(films, meta, 3),
            busiestMonth = busiest,
            longestStreak = longestStreak(viewings),
            rewatches = viewings.size - films.size,
        )
    }

    /** Years that actually have watches, newest first — the years a recap can be shown for. */
    fun yearsWithWatches(entries: List<Entry>): List<Int> =
        entries.map { yearOf(it.watchedAt) }.distinct().sortedDescending()

    // ------------------------------------------------------------------ taste + watchlist picks

    /** How much a rating pulls taste up or down. 5.5 is the neutral midpoint of a 1–10 scale. */
    private fun weight(rating: Double) = rating - 5.5

    data class Taste(
        val genres: Map<String, Double>,
        val directors: Map<String, Double>,
        val actors: Map<String, Double>,
    ) {
        val isEmpty: Boolean get() = genres.isEmpty() && directors.isEmpty() && actors.isEmpty()
    }

    /**
     * Learns preferences from your own ratings: each film nudges its genres, director and cast
     * up or down by how far the rating sits from the middle of the scale. Averaged per name so a
     * single outlier can't dominate, then damped by how many films support it.
     */
    fun taste(watched: List<Entry>, meta: Map<String, Meta>): Taste {
        fun build(pick: (Meta) -> List<String>): Map<String, Double> {
            val sums = HashMap<String, Double>()
            val counts = HashMap<String, Int>()
            for (f in perFilm(watched)) {
                val m = metaFor(f, meta) ?: continue
                for (name in pick(m).map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
                    sums[name] = (sums[name] ?: 0.0) + weight(f.rating)
                    counts[name] = (counts[name] ?: 0) + 1
                }
            }
            return sums.mapValues { (name, sum) ->
                val n = counts[name] ?: 1
                // Average, damped: one film counts for half, settling towards the true mean.
                (sum / n) * (n.toDouble() / (n + 1.0))
            }
        }
        return Taste(
            genres = build { it.genres },
            directors = build { listOfNotNull(it.director) },
            actors = build { it.cast.take(6) },
        )
    }

    data class Pick(val entry: Entry, val score: Double, val reason: String)

    /**
     * Ranks your watchlist by how well each title matches the taste in [taste]. A director you
     * rate highly counts for more than a shared genre, which counts for more than one actor.
     * Titles we know nothing about score neutral rather than being buried.
     */
    fun rankWatchlist(
        watchlist: List<Entry>,
        taste: Taste,
        meta: Map<String, Meta>,
    ): List<Pick> {
        if (watchlist.isEmpty()) return emptyList()
        val picks = watchlist.map { e ->
            val m = metaFor(e, meta)
            if (m == null) return@map Pick(e, 0.0, "Not enough about this one yet")

            var score = 0.0
            val reasons = ArrayList<Pair<Double, String>>()

            m.director?.trim()?.takeIf { it.isNotEmpty() }?.let { d ->
                taste.directors[d]?.let { w ->
                    score += w * 2.0
                    if (w > 0.4) reasons.add(abs(w) * 2.0 to "You rate $d highly")
                }
            }
            val genreHits = m.genres.mapNotNull { g -> taste.genres[g.trim()]?.let { g.trim() to it } }
            genreHits.forEach { (g, w) ->
                score += w
                if (w > 0.4) reasons.add(abs(w) to "You like $g")
            }
            m.cast.take(6).forEach { a ->
                taste.actors[a.trim()]?.let { w ->
                    score += w * 0.5
                    if (w > 0.6) reasons.add(abs(w) * 0.5 to "You rate $a highly")
                }
            }

            val reason = reasons.maxByOrNull { it.first }?.second
                ?: if (score < 0) "A change of pace for you" else "Worth a try"
            Pick(e, score, reason)
        }
        return picks.sortedWith(compareByDescending<Pick> { it.score }.thenBy { it.entry.title })
    }

    /** The single strongest suggestion from the watchlist, or null when there's nothing to pick. */
    fun pickOne(watchlist: List<Entry>, watched: List<Entry>, meta: Map<String, Meta>): Pick? =
        rankWatchlist(watchlist, taste(watched, meta), meta).firstOrNull()
}
