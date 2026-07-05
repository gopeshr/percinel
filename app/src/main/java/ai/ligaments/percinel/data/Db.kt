package ai.ligaments.percinel.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

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
)

private class DbHelper(ctx: Context) : SQLiteOpenHelper(ctx, "percinel.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE entries(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tmdb_id INTEGER NOT NULL,
                media_type TEXT NOT NULL,
                title TEXT NOT NULL,
                poster_path TEXT,
                year INTEGER,
                rating REAL NOT NULL,
                watched_at INTEGER NOT NULL,
                notes TEXT,
                status TEXT NOT NULL DEFAULT '$STATUS_WATCHED'
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_watched ON entries(watched_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE entries ADD COLUMN status TEXT NOT NULL DEFAULT '$STATUS_WATCHED'")
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
    fun markWatched(id: Long, rating: Double, watchedAt: Long, notes: String?) {
        val v = ContentValues().apply {
            put("status", STATUS_WATCHED)
            put("rating", rating)
            put("watched_at", watchedAt)
            put("notes", notes)
        }
        helper.writableDatabase.update("entries", v, "id = ?", arrayOf(id.toString()))
    }

    fun get(id: Long): Entry? =
        helper.readableDatabase
            .rawQuery("SELECT * FROM entries WHERE id = ?", arrayOf(id.toString()))
            .use { c -> if (c.moveToNext()) c.toEntry() else null }

    fun insert(e: Entry): Long =
        helper.writableDatabase.insert("entries", null, e.toValues())

    fun update(e: Entry) {
        helper.writableDatabase.update("entries", e.toValues(), "id = ?", arrayOf(e.id.toString()))
    }

    fun delete(id: Long) {
        helper.writableDatabase.delete("entries", "id = ?", arrayOf(id.toString()))
    }

    fun clearAll() {
        helper.writableDatabase.delete("entries", null, null)
    }

    /** Attach TMDb identity to an entry (used to enrich a manual entry). Rating/date/notes untouched. */
    fun linkTmdb(id: Long, tmdbId: Long, mediaType: String, title: String, posterPath: String?, year: Int?) {
        val v = ContentValues().apply {
            put("tmdb_id", tmdbId)
            put("media_type", mediaType)
            put("title", title)
            put("poster_path", posterPath)
            put("year", year)
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
    )
}

private fun Entry.toValues() = ContentValues().apply {
    put("tmdb_id", tmdbId)
    put("media_type", mediaType)
    put("title", title)
    put("poster_path", posterPath)
    put("year", year)
    put("rating", rating)
    put("watched_at", watchedAt)
    put("notes", notes)
    put("status", status)
}
