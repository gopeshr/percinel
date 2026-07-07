package gopesh.percinel

import gopesh.percinel.data.Entry
import gopesh.percinel.data.Export
import gopesh.percinel.data.Import
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ImportTest {

    // ------------------------------------------------------------ CSV parser

    @Test
    fun `csv handles quotes commas and newlines`() {
        val csv = "a,\"b, with comma\",\"line\nbreak\",\"doubled \"\"quote\"\"\"\r\nplain,1,2,3"
        val rows = Import.parseCsv(csv)
        assertEquals(2, rows.size)
        assertEquals(listOf("a", "b, with comma", "line\nbreak", "doubled \"quote\""), rows[0])
        assertEquals(listOf("plain", "1", "2", "3"), rows[1])
    }

    @Test
    fun `csv skips blank lines`() {
        val rows = Import.parseCsv("a,b\n\n\nc,d\n")
        assertEquals(2, rows.size)
    }

    // ------------------------------------------------------------ Letterboxd

    @Test
    fun `letterboxd diary maps stars to ten point scale`() {
        val csv = """
            Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date
            2024-03-01,Past Lives,2023,https://boxd.it/x,4.5,,,"2024-02-29"
            2024-03-02,Unrated Film,2020,https://boxd.it/y,,,,2024-03-02
        """.trimIndent()
        val p = Import.parse(csv.toByteArray())!!
        assertEquals("Letterboxd", p.source)
        assertEquals(1, p.rows.size)
        assertEquals(1, p.unrated)
        val r = p.rows[0]
        assertEquals("Past Lives", r.title)
        assertEquals(2023, r.year)
        assertEquals("movie", r.mediaType)
        assertEquals(9.0, r.rating, 0.001)
    }

    @Test
    fun `letterboxd ratings csv falls back to date column`() {
        val csv = """
            Date,Name,Year,Letterboxd URI,Rating
            2024-05-10,Heat,1995,https://boxd.it/z,5
        """.trimIndent()
        val p = Import.parse(csv.toByteArray())!!
        assertEquals(1, p.rows.size)
        assertEquals(10.0, p.rows[0].rating, 0.001)
    }

    // ------------------------------------------------------------ IMDb

    @Test
    fun `imdb detects series and keeps ten point rating`() {
        val csv = """
            Const,Your Rating,Date Rated,Title,URL,Title Type,IMDb Rating,Runtime (mins),Year,Genres,Num Votes,Release Date,Directors
            tt0903747,10,2021-08-15,Breaking Bad,https://www.imdb.com/title/tt0903747/,tvSeries,9.5,49,2008,"Crime, Drama",2000000,2008-01-20,
            tt1375666,9,2020-01-01,Inception,https://www.imdb.com/title/tt1375666/,movie,8.8,148,2010,"Action, Sci-Fi",2400000,2010-07-16,Christopher Nolan
        """.trimIndent()
        val p = Import.parse(csv.toByteArray())!!
        assertEquals("IMDb", p.source)
        assertEquals(2, p.rows.size)
        assertEquals("tv", p.rows[0].mediaType)
        assertEquals(10.0, p.rows[0].rating, 0.001)
        assertEquals("movie", p.rows[1].mediaType)
        assertEquals(2010, p.rows[1].year)
    }

    // ------------------------------------------------------------ xlsx round-trip

    @Test
    fun `percinel export imports back losslessly`() {
        val entries = listOf(
            Entry(1, 100, "movie", "Oppenheimer", null, 2023, 9.25, 1720000000000, "Great & <loud>", uuid = "u1", updatedAt = 1),
            Entry(2, 200, "tv", "The Bear", null, 2022, 8.5, 1720100000000, null, uuid = "u2", updatedAt = 1, season = 2),
        )
        val out = ByteArrayOutputStream()
        Export.writeXlsx(out, entries)

        val p = Import.parse(out.toByteArray())
        assertNotNull(p)
        assertEquals("a percinel export", p!!.source)
        assertEquals(2, p.rows.size)

        val opp = p.rows[0]
        assertEquals("Oppenheimer", opp.title)
        assertEquals(2023, opp.year)
        assertEquals("movie", opp.mediaType)
        assertEquals(9.25, opp.rating, 0.001)
        assertEquals("Great & <loud>", opp.notes)
        assertNull(opp.season)
        // Export prints minutes, so round-trip must agree to the minute.
        assertTrue(Math.abs(opp.watchedAt - 1720000000000L) < 60_000)

        val bear = p.rows[1]
        assertEquals("tv", bear.mediaType)
        assertEquals(2, bear.season)
        assertNull(bear.notes)
    }

    // ------------------------------------------------------------ detection & dedupe

    @Test
    fun `unknown files are rejected not crashed`() {
        assertNull(Import.parse("just some text".toByteArray()))
        assertNull(Import.parse("head1,head2\nval1,val2".toByteArray()))
        assertNull(Import.parse(ByteArray(0)))
    }

    @Test
    fun `dedupe key is same film same day`() {
        val morning = 1720000000000L
        val sameDay = morning + 3 * 60 * 60 * 1000
        assertEquals(
            Import.dedupeKey(" Heat ", 1995, morning),
            Import.dedupeKey("heat", 1995, sameDay),
        )
        assertTrue(
            Import.dedupeKey("Heat", 1995, morning) !=
                Import.dedupeKey("Heat", 1995, morning + 48 * 60 * 60 * 1000),
        )
    }
}
