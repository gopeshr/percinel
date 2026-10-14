package gopesh.percinel

import gopesh.percinel.data.Entry
import gopesh.percinel.data.Meta
import gopesh.percinel.data.WatchFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchFilterTest {

    private fun e(
        title: String = "Heat",
        rating: Double = 8.0,
        year: Int? = 1995,
        type: String = "movie",
        notes: String? = null,
        at: Long = 1000,
    ) = Entry(
        id = 1, tmdbId = 1, mediaType = type, title = title, posterPath = null, year = year,
        rating = rating, watchedAt = at, notes = notes,
    )

    private fun m(genres: List<String> = emptyList(), director: String? = null, cast: List<String> = emptyList()) =
        Meta(1, "movie", 120, null, null, genres, director, cast)

    @Test fun `empty filter matches everything`() {
        assertTrue(WatchFilter().matches(listOf(e()), null))
        assertEquals(0, WatchFilter().activeCount)
    }

    @Test fun `rating range is inclusive at two decimal precision`() {
        val f = WatchFilter(minRating = 8.0, maxRating = 9.0)
        assertTrue(f.matches(listOf(e(rating = 8.0)), null))
        assertTrue(f.matches(listOf(e(rating = 9.0)), null))
        assertTrue("7.995 shows as 8.00", f.matches(listOf(e(rating = 7.995)), null))
        assertFalse(f.matches(listOf(e(rating = 7.9)), null))
        assertFalse(f.matches(listOf(e(rating = 9.25)), null))
    }

    @Test fun `film is judged on its latest viewing`() {
        // newest first: latest 6.0, an older 9.5 rewatch must not let it through a 9+ filter
        val viewings = listOf(e(rating = 6.0, at = 2000), e(rating = 9.5, at = 1000))
        assertFalse(WatchFilter(minRating = 9.0).matches(viewings, null))
        assertTrue(WatchFilter(maxRating = 6.0).matches(viewings, null))
    }

    @Test fun `decades widen within, film without a year is excluded`() {
        val f = WatchFilter(decades = setOf(1990, 2010))
        assertTrue(f.matches(listOf(e(year = 1995)), null))
        assertTrue(f.matches(listOf(e(year = 2014)), null))
        assertFalse(f.matches(listOf(e(year = 2003)), null))
        assertFalse(f.matches(listOf(e(year = null)), null))
    }

    @Test fun `genres match any, need metadata`() {
        val f = WatchFilter(genres = setOf("Thriller", "Horror"))
        assertTrue(f.matches(listOf(e()), m(genres = listOf("Crime", "Thriller"))))
        assertFalse(f.matches(listOf(e()), m(genres = listOf("Drama"))))
        assertFalse("unanalysed film can't claim a genre", f.matches(listOf(e()), null))
    }

    @Test fun `categories combine with AND`() {
        val f = WatchFilter(minRating = 8.0, decades = setOf(1990), genres = setOf("Crime"))
        assertTrue(f.matches(listOf(e(rating = 8.5, year = 1995)), m(genres = listOf("Crime"))))
        assertFalse(f.matches(listOf(e(rating = 7.0, year = 1995)), m(genres = listOf("Crime"))))
        assertFalse(f.matches(listOf(e(rating = 8.5, year = 2005)), m(genres = listOf("Crime"))))
        assertEquals(3, f.activeCount)
    }

    @Test fun `search reaches title, director, cast and any viewing's notes`() {
        val film = listOf(e(title = "Heat", notes = null, at = 2000), e(notes = "that bank heist scene", at = 1000))
        val meta = m(director = "Michael Mann", cast = listOf("Al Pacino", "Robert De Niro"))
        assertTrue(WatchFilter(query = "heat").matches(film, meta))
        assertTrue(WatchFilter(query = "mann").matches(film, meta))
        assertTrue(WatchFilter(query = "pacino").matches(film, meta))
        assertTrue(WatchFilter(query = "bank heist").matches(film, meta))
        assertFalse(WatchFilter(query = "nolan").matches(film, meta))
        // without metadata, people can't be found but title still can
        assertFalse(WatchFilter(query = "pacino").matches(film, null))
        assertTrue(WatchFilter(query = "heat").matches(film, null))
    }

    @Test fun `type filter and clearing the sheet`() {
        assertFalse(WatchFilter(mediaType = "tv").matches(listOf(e(type = "movie")), null))
        val f = WatchFilter(query = "x", mediaType = "tv", minRating = 9.0, decades = setOf(1990), genres = setOf("A"))
        val c = f.clearedOfSheet()
        assertEquals(0, c.activeCount)
        assertEquals("search and type survive clearing the sheet", "x", c.query)
        assertEquals("tv", c.mediaType)
    }
}
