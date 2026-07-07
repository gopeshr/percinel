package gopesh.percinel.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

const val STATUS_WATCHED = "watched"
const val STATUS_WATCHLIST = "watchlist"

data class Entry(
    val id: Long,
    val tmdbId: Long,
    val mediaType: String,
    val title: String,
    val posterPath: String?,
    val year: Int?,
    val rating: Double,
    val watchedAt: Long,
    val notes: String?,
    val status: String = STATUS_WATCHED,
    // Sync identity: [uuid] is stable across devices/reinstalls; [updatedAt] drives
    // newest-wins merge. Blank uuid / 0 updatedAt mean "not yet assigned" — the repo
    // fills them on insert. Local autoincrement [id] is never synced.
    val uuid: String = "",
    val updatedAt: Long = 0L,
    // For series: which season this watch was of (null = whole series / unspecified).
    val season: Int? = null,
)

private class DbHelper(ctx: Context) : SQLiteOpenHelper(ctx, "percinel.db", null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE entries(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL,
                tmdb_id INTEGER NOT NULL,
                media_type TEXT NOT NULL,
                title TEXT NOT NULL,
                poster_path TEXT,
                year INTEGER,
                rating REAL NOT NULL,
                watched_at INTEGER NOT NULL,
                notes TEXT,
                status TEXT NOT NULL DEFAULT '$STATUS_WATCHED',
                updated_at INTEGER NOT NULL DEFAULT 0,
                season INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX idx_uuid ON entries(uuid)")
        db.execSQL("CREATE INDEX idx_watched ON entries(watched_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE entries ADD COLUMN status TEXT NOT NULL DEFAULT '$STATUS_WATCHED'")
        }
        if (oldVersion < 3) {
            // Add sync columns, then backfill every existing row with a stable UUID and a
            // fresh updated_at so the first cloud sync treats them as current, real records.
            db.execSQL("ALTER TABLE entries ADD COLUMN uuid TEXT")
            db.execSQL("ALTER TABLE entries ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")

            val now = System.currentTimeMillis()
            val ids = ArrayList<Long>()
            db.rawQuery("SELECT id FROM entries WHERE uuid IS NULL OR uuid = ''", null).use { c ->
                while (c.moveToNext()) ids.add(c.getLong(0))
            }
            for (id in ids) {
                val v = ContentValues().apply {
                    put("uuid", UUID.randomUUID().toString())
                    put("updated_at", now)
                }
                db.update("entries", v, "id = ?", arrayOf(id.toString()))
            }
            db.execSQL("CREATE UNIQUE INDEX idx_uuid ON entries(uuid)")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE entries ADD COLUMN season INTEGER")
        }
    }
}

class Repo(ctx: Context) {
    private val helper = DbHelper(ctx.applicationContext)

    fun list(): List<Entry> {
        val out = ArrayList<Entry>()
        helper.readableDatabase
            .rawQuery("SELECT * FROM entries WHERE status = ? ORDER BY watched_at DESC, id DESC", arrayOf(STATUS_WATCHED))
            .use { c -> while (c.moveToNext()) out.add(c.toEntry()) }
        return out
    }

    fun watchlist(): List<Entry> {
        val out = ArrayList<Entry>()
        helper.readableDatabase
            .rawQuery("SELECT * FROM entries WHERE status = ? ORDER BY id DESC", arrayOf(STATUS_WATCHLIST))
            .use { c -> while (c.moveToNext()) out.add(c.toEntry()) }
        return out
    }

    /** Move a watchlist item into watches, capturing rating/date/notes now. */
    fun markWatched(id: Long, rating: Double, watchedAt: Long, notes: String?, season: Int? = null) {
        val v = ContentValues().apply {
            put("status", STATUS_WATCHED)
            put("rating", rating)
            put("watched_at", watchedAt)
            put("notes", notes)
            put("season", season)
            put("updated_at", System.currentTimeMillis())
        }
        helper.writableDatabase.update("entries", v, "id = ?", arrayOf(id.toString()))
    }

    fun get(id: Long): Entry? =
        helper.readableDatabase
            .rawQuery("SELECT * FROM entries WHERE id = ?", arrayOf(id.toString()))
            .use { c -> if (c.moveToNext()) c.toEntry() else null }

    fun insert(e: Entry): Long {
        // Preserve an existing uuid/updatedAt (e.g. undo-restore of a synced row); otherwise
        // mint fresh sync identity for a brand-new watch.
        val withMeta = e.copy(
            uuid = e.uuid.ifBlank { UUID.randomUUID().toString() },
            updatedAt = if (e.updatedAt == 0L) System.currentTimeMillis() else e.updatedAt,
        )
        return helper.writableDatabase.insert("entries", null, withMeta.toValues())
    }

    fun update(e: Entry) {
        val v = e.toValues().apply { put("updated_at", System.currentTimeMillis()) }
        helper.writableDatabase.update("entries", v, "id = ?", arrayOf(e.id.toString()))
    }

    fun delete(id: Long) {
        helper.writableDatabase.delete("entries", "id = ?", arrayOf(id.toString()))
    }

    fun clearAll() {
        helper.writableDatabase.delete("entries", null, null)
    }

    /** All watched viewings of the same film (by TMDb id), newest first. Manual entries (no
     *  tmdbId) can't be grouped reliably, so they stand alone. */
    fun viewingsFor(entry: Entry): List<Entry> {
        if (entry.tmdbId == 0L) return listOf(entry)
        val out = ArrayList<Entry>()
        helper.readableDatabase.rawQuery(
            "SELECT * FROM entries WHERE status = ? AND tmdb_id = ? AND media_type = ? ORDER BY watched_at DESC, id DESC",
            arrayOf(STATUS_WATCHED, entry.tmdbId.toString(), entry.mediaType),
        ).use { c -> while (c.moveToNext()) out.add(c.toEntry()) }
        return out
    }

    /** Every row regardless of status — the full set to sync. */
    fun allForSync(): List<Entry> {
        val out = ArrayList<Entry>()
        helper.readableDatabase.rawQuery("SELECT * FROM entries", null)
            .use { c -> while (c.moveToNext()) out.add(c.toEntry()) }
        return out
    }

    /** Apply a merged set to the local DB, keyed by uuid: insert new rows, update rows whose
     *  incoming updatedAt is newer. Never deletes. */
    fun applyMerge(merged: List<Entry>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (e in merged) {
                if (e.uuid.isBlank()) continue
                val cur = db.rawQuery("SELECT id, updated_at FROM entries WHERE uuid = ?", arrayOf(e.uuid))
                    .use { if (it.moveToNext()) it.getLong(0) to it.getLong(1) else null }
                if (cur == null) {
                    db.insert("entries", null, e.toValues())
                } else if (e.updatedAt > cur.second) {
                    db.update("entries", e.toValues(), "id = ?", arrayOf(cur.first.toString()))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Attach TMDb identity to an entry (used to enrich a manual entry). Rating/date/notes untouched. */
    fun linkTmdb(id: Long, tmdbId: Long, mediaType: String, title: String, posterPath: String?, year: Int?) {
        val v = ContentValues().apply {
            put("tmdb_id", tmdbId)
            put("media_type", mediaType)
            put("title", title)
            put("poster_path", posterPath)
            put("year", year)
            put("updated_at", System.currentTimeMillis())
        }
        helper.writableDatabase.update("entries", v, "id = ?", arrayOf(id.toString()))
    }
}

private fun Cursor.toEntry(): Entry {
    fun idx(name: String) = getColumnIndexOrThrow(name)
    return Entry(
        id = getLong(idx("id")),
        tmdbId = getLong(idx("tmdb_id")),
        mediaType = getString(idx("media_type")),
        title = getString(idx("title")),
        posterPath = idx("poster_path").let { if (isNull(it)) null else getString(it) },
        year = idx("year").let { if (isNull(it)) null else getInt(it) },
        rating = getDouble(idx("rating")),
        watchedAt = getLong(idx("watched_at")),
        notes = idx("notes").let { if (isNull(it)) null else getString(it) },
        status = idx("status").let { if (isNull(it)) STATUS_WATCHED else getString(it) },
        uuid = idx("uuid").let { if (isNull(it)) "" else getString(it) },
        updatedAt = getLong(idx("updated_at")),
        season = idx("season").let { if (isNull(it)) null else getInt(it) },
    )
}

private fun Entry.toValues() = ContentValues().apply {
    put("uuid", uuid)
    put("tmdb_id", tmdbId)
    put("media_type", mediaType)
    put("title", title)
    put("poster_path", posterPath)
    put("year", year)
    put("rating", rating)
    put("watched_at", watchedAt)
    put("notes", notes)
    put("status", status)
    put("updated_at", updatedAt)
    put("season", season)
}
