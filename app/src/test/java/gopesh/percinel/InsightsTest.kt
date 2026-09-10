package gopesh.percinel

import gopesh.percinel.data.Entry
import gopesh.percinel.data.Insights
import gopesh.percinel.data.Meta
import gopesh.percinel.data.metaKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** The maths behind stats, the year recap, the heatmap and watchlist picks. */
class InsightsTest {

    private fun at(y: Int, m: Int, d: Int, hour: Int = 12): Long =
        LocalDateTime.of(y, m, d, hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private var nextId = 1L
    private fun e(
        title: String,
        rating: Double = 8.0,
        watchedAt: Long = at(2026, 1, 1),
        type: String = "movie",
        tmdbId: Long = 0L,
        year: Int? = 2020,
        season: Int? = null,
    ) = Entry(
        id = nextId++, tmdbId = tmdbId, mediaType = type, title = title, posterPath = null,
        year = year, rating = rating, watchedAt = watchedAt, notes = null, season = season,
        uuid = "u$title$nextId", updatedAt = 1,
    )

    private fun meta(
        tmdbId: Long,
        type: String = "movie",
        runtime: Int? = 120,
        episodes: Int? = null,
        seasons: Int? = null,
        genres: List<String> = emptyList(),
        director: String? = null,
        cast: List<String> = emptyList(),
    ) = Meta(tmdbId, type, runtime, episodes, seasons, genres, director, cast)

    private fun metaMap(vararg m: Meta) = m.associateBy { metaKey(it.mediaType, it.tmdbId) }

    // ---------------------------------------------------------------- runtime

    @Test
    fun `movie minutes are its runtime`() {
        val film = e("A", tmdbId = 1)
        assertEquals(120, Insights.minutesFor(film, meta(1, runtime = 120)))
    }

    @Test
    fun `series without a season counts the whole run`() {
        val show = e("S", type = "tv", tmdbId = 2)
        val m = meta(2, type = "tv", runtime = 50, episodes = 20, seasons = 4)
        assertEquals(50 * 20, Insights.minutesFor(show, m))
    }

    @Test
    fun `series with a season counts only that season`() {
        val show = e("S", type = "tv", tmdbId = 2, season = 2)
        val m = meta(2, type = "tv", runtime = 50, episodes = 20, seasons = 4)
        assertEquals(50 * 5, Insights.minutesFor(show, m)) // 20 episodes / 4 seasons = 5
    }

    @Test
    fun `unknown runtime yields null and is skipped in totals`() {
        val film = e("A", tmdbId = 1)
        assertNull(Insights.minutesFor(film, meta(1, runtime = null)))
        assertNull(Insights.minutesFor(film, null))
        val total = Insights.totalMinutes(listOf(film), emptyMap())
        assertEquals(0, total)
    }

    // ---------------------------------------------------------------- simple aggregates

    @Test
    fun `decades group by release year newest first`() {
        val films = listOf(e("a", year = 1994), e("b", year = 1999), e("c", year = 2021), e("d", year = null))
        assertEquals(listOf(2020 to 1, 1990 to 2), Insights.byDecade(films))
    }

    @Test
    fun `rating histogram buckets by whole number`() {
        val films = listOf(e("a", 8.0), e("b", 8.9), e("c", 10.0), e("d", 1.0))
        val h = Insights.ratingHistogram(films).toMap()
        assertEquals(2, h[8])
        assertEquals(1, h[10])
        assertEquals(1, h[1])
        assertEquals(0, h[5])
        assertEquals(10, Insights.ratingHistogram(films).size)
    }

    @Test
    fun `heatmap counts viewings per day and ignores other years`() {
        val list = listOf(
            e("a", watchedAt = at(2026, 3, 4)),
            e("b", watchedAt = at(2026, 3, 4, hour = 22)),
            e("c", watchedAt = at(2025, 3, 4)),
        )
        val hm = Insights.heatmap(2026, list)
        assertEquals(1, hm.size)
        assertEquals(2, hm[LocalDate.of(2026, 3, 4)])
    }

    @Test
    fun `longest streak counts consecutive days once per day`() {
        val list = listOf(
            e("a", watchedAt = at(2026, 5, 1)),
            e("b", watchedAt = at(2026, 5, 2)),
            e("c", watchedAt = at(2026, 5, 2, hour = 20)), // same day, no extra
            e("d", watchedAt = at(2026, 5, 3)),
            e("e", watchedAt = at(2026, 5, 9)), // gap breaks it
        )
        assertEquals(3, Insights.longestStreak(list))
        assertEquals(0, Insights.longestStreak(emptyList()))
    }

    @Test
    fun `perFilm collapses rewatches of the same tmdb title`() {
        val a1 = e("Heat", tmdbId = 5, watchedAt = at(2026, 1, 1))
        val a2 = e("Heat", tmdbId = 5, watchedAt = at(2026, 6, 1))
        val b = e("Other", tmdbId = 6)
        val films = Insights.perFilm(listOf(a1, a2, b))
        assertEquals(2, films.size)
        // keeps the latest viewing of the repeated film
        assertEquals(at(2026, 6, 1), films.first { it.tmdbId == 5L }.watchedAt)
    }

    // ---------------------------------------------------------------- year recap

    @Test
    fun `film year separates films from viewings and reports the extras`() {
        val watched = listOf(
            e("Heat", 9.0, at(2026, 1, 5), tmdbId = 1),
            e("Heat", 9.0, at(2026, 2, 5), tmdbId = 1), // rewatch
            e("Klaus", 7.0, at(2026, 2, 6), tmdbId = 2),
            e("Old", 6.0, at(2025, 2, 6), tmdbId = 3), // different year
        )
        val m = metaMap(
            meta(1, runtime = 170, genres = listOf("Crime"), director = "Michael Mann", cast = listOf("Pacino")),
            meta(2, runtime = 96, genres = listOf("Animation"), director = "Sergio Pablos"),
        )
        val fy = Insights.filmYear(2026, watched, m)

        assertEquals(2026, fy.year)
        assertEquals(3, fy.viewings)
        assertEquals(2, fy.films)
        assertEquals(1, fy.rewatches)
        assertEquals(2, fy.movies)
        assertEquals(0, fy.series)
        // minutes counts every viewing, including the rewatch
        assertEquals(170 + 170 + 96, fy.minutes)
        assertEquals("Heat", fy.best?.title)
        assertEquals("Klaus", fy.worst?.title)
        assertEquals(8.0, fy.avgRating!!, 0.001)
        assertEquals(2 to 2, fy.busiestMonth) // February had 2 viewings
        assertTrue(fy.topGenres.isNotEmpty())
        assertEquals("Michael Mann", fy.topDirectors.first().first)
    }

    @Test
    fun `film year on an empty year is empty not a crash`() {
        val fy = Insights.filmYear(1999, listOf(e("a", watchedAt = at(2026, 1, 1))), emptyMap())
        assertTrue(fy.isEmpty)
        assertEquals(0, fy.films)
        assertNull(fy.avgRating)
        assertNull(fy.best)
    }

    @Test
    fun `years with watches are listed newest first`() {
        val list = listOf(
            e("a", watchedAt = at(2024, 1, 1)),
            e("b", watchedAt = at(2026, 1, 1)),
            e("c", watchedAt = at(2026, 5, 1)),
        )
        assertEquals(listOf(2026, 2024), Insights.yearsWithWatches(list))
    }

    // ---------------------------------------------------------------- taste + picks

    @Test
    fun `taste is positive for loved genres and negative for disliked ones`() {
        val watched = listOf(
            e("t1", 9.0, tmdbId = 1),
            e("t2", 9.5, tmdbId = 2),
            e("h1", 2.0, tmdbId = 3),
        )
        val m = metaMap(
            meta(1, genres = listOf("Thriller")),
            meta(2, genres = listOf("Thriller")),
            meta(3, genres = listOf("Horror")),
        )
        val t = Insights.taste(watched, m)
        assertTrue("thriller should be liked", t.genres["Thriller"]!! > 0)
        assertTrue("horror should be disliked", t.genres["Horror"]!! < 0)
    }

    @Test
    fun `watchlist ranks a loved director above an unrelated title`() {
        val watched = listOf(
            e("m1", 9.5, tmdbId = 1),
            e("m2", 9.0, tmdbId = 2),
        )
        val wl = listOf(
            e("byFav", tmdbId = 10),
            e("unrelated", tmdbId = 11),
        )
        val m = metaMap(
            meta(1, director = "Nolan", genres = listOf("Sci-Fi")),
            meta(2, director = "Nolan", genres = listOf("Sci-Fi")),
            meta(10, director = "Nolan", genres = listOf("Sci-Fi")),
            meta(11, director = "Someone Else", genres = listOf("Romance")),
        )
        val ranked = Insights.rankWatchlist(wl, Insights.taste(watched, m), m)
        assertEquals("byFav", ranked.first().entry.title)
        assertTrue(ranked.first().score > ranked.last().score)
        assertTrue("reason should name the director", ranked.first().reason.contains("Nolan"))
    }

    @Test
    fun `watchlist items with no metadata score neutral and still appear`() {
        val wl = listOf(e("mystery", tmdbId = 99))
        val ranked = Insights.rankWatchlist(wl, Insights.taste(emptyList(), emptyMap()), emptyMap())
        assertEquals(1, ranked.size)
        assertEquals(0.0, ranked.first().score, 0.0001)
    }

    @Test
    fun `pickOne returns the best match or null when the watchlist is empty`() {
        assertNull(Insights.pickOne(emptyList(), emptyList(), emptyMap()))
        val watched = listOf(e("m1", 9.5, tmdbId = 1))
        val wl = listOf(e("match", tmdbId = 10), e("no", tmdbId = 11))
        val m = metaMap(
            meta(1, genres = listOf("Drama")),
            meta(10, genres = listOf("Drama")),
            meta(11, genres = listOf("Western")),
        )
        assertEquals("match", Insights.pickOne(wl, watched, m)?.entry?.title)
    }
}
