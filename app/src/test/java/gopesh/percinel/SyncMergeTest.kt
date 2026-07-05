package gopesh.percinel

import gopesh.percinel.data.Entry
import gopesh.percinel.data.STATUS_WATCHED
import gopesh.percinel.data.Sync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Proves the sync merge never loses data and resolves conflicts newest-wins. */
class SyncMergeTest {

    private fun e(uuid: String, title: String, updatedAt: Long, status: String = STATUS_WATCHED) =
        Entry(
            id = 0, uuid = uuid, tmdbId = 0, mediaType = "movie", title = title,
            posterPath = null, year = 2000, rating = 8.0, watchedAt = 1000, notes = null,
            status = status, updatedAt = updatedAt,
        )

    @Test
    fun union_keeps_everything_and_newest_wins() {
        // Device A (local): a, b, and an OLD copy of c
        val local = listOf(e("a", "A", 1), e("b", "B", 1), e("c", "C-old", 1))
        // Cloud (from device B): a NEWER copy of c, plus a brand-new d
        val remote = listOf(e("c", "C-new", 5), e("d", "D", 1))

        val merged = Sync.merge(local, remote).associateBy { it.uuid }

        assertEquals("nothing lost — all 4 uuids present", 4, merged.size)
        assertEquals("local-only kept", "A", merged["a"]!!.title)
        assertEquals("local-only kept", "B", merged["b"]!!.title)
        assertEquals("remote-only added", "D", merged["d"]!!.title)
        assertEquals("conflict resolved newest-wins", "C-new", merged["c"]!!.title)
    }

    @Test
    fun reinstall_restores_without_deleting_new_local() {
        // The user's story: reinstall after a year. Local has only what was logged since reinstall;
        // cloud has the old library. Result must contain BOTH.
        val freshLocal = listOf(e("new1", "Just Watched", 100))
        val oldCloud = listOf(e("old1", "Old Fav", 1), e("old2", "Another", 1))

        val merged = Sync.merge(freshLocal, oldCloud).map { it.title }.toSet()

        assertTrue(merged.containsAll(setOf("Just Watched", "Old Fav", "Another")))
        assertEquals(3, merged.size)
    }

    @Test
    fun idempotent_no_duplicates_on_repeat() {
        val a = listOf(e("a", "A", 1), e("b", "B", 1))
        val once = Sync.merge(a, a)
        val twice = Sync.merge(once, once)
        assertEquals(2, once.size)
        assertEquals(2, twice.size)
    }

    @Test
    fun serialize_roundtrip_preserves_fields() {
        val list = listOf(e("a", "A", 7), e("b", "B", 9, status = "watchlist"))
        val back = Sync.parse(Sync.serialize(list)).associateBy { it.uuid }
        assertEquals(2, back.size)
        assertEquals("A", back["a"]!!.title)
        assertEquals(7L, back["a"]!!.updatedAt)
        assertEquals("watchlist", back["b"]!!.status)
    }
}
