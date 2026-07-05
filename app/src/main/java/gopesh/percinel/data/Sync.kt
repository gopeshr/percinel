package gopesh.percinel.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialization + merge for cloud sync. Pure logic, no Android/network deps, so it's unit-testable.
 * The merge rule is the whole safety story: union by [Entry.uuid], newest [Entry.updatedAt] wins,
 * nothing is ever dropped. Local autoincrement ids are never serialized.
 */
object Sync {
    const val SCHEMA = 1

    fun serialize(entries: List<Entry>): String {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject().apply {
                    put("uuid", e.uuid)
                    put("tmdbId", e.tmdbId)
                    put("mediaType", e.mediaType)
                    put("title", e.title)
                    put("posterPath", e.posterPath ?: JSONObject.NULL)
                    put("year", e.year ?: JSONObject.NULL)
                    put("rating", e.rating)
                    put("watchedAt", e.watchedAt)
                    put("notes", e.notes ?: JSONObject.NULL)
                    put("status", e.status)
                    put("updatedAt", e.updatedAt)
                },
            )
        }
        return JSONObject().apply {
            put("schema", SCHEMA)
            put("entries", arr)
        }.toString()
    }

    fun parse(json: String): List<Entry> {
        val out = ArrayList<Entry>()
        val arr = JSONObject(json).optJSONArray("entries") ?: return out
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val uuid = o.optString("uuid", "")
            if (uuid.isBlank()) continue
            out.add(
                Entry(
                    id = 0,
                    uuid = uuid,
                    tmdbId = o.optLong("tmdbId", 0),
                    mediaType = o.optString("mediaType", "movie"),
                    title = o.optString("title", ""),
                    posterPath = if (o.isNull("posterPath")) null else o.optString("posterPath").ifBlank { null },
                    year = if (o.isNull("year")) null else o.optInt("year"),
                    rating = o.optDouble("rating", 0.0),
                    watchedAt = o.optLong("watchedAt", 0),
                    notes = if (o.isNull("notes")) null else o.optString("notes").ifBlank { null },
                    status = o.optString("status", STATUS_WATCHED),
                    updatedAt = o.optLong("updatedAt", 0),
                ),
            )
        }
        return out
    }

    /** Union by uuid; the version with the newer updatedAt wins. Never drops an entry. */
    fun merge(local: List<Entry>, remote: List<Entry>): List<Entry> {
        val byUuid = LinkedHashMap<String, Entry>()
        for (e in local) if (e.uuid.isNotBlank()) byUuid[e.uuid] = e
        for (r in remote) {
            if (r.uuid.isBlank()) continue
            val cur = byUuid[r.uuid]
            if (cur == null || r.updatedAt > cur.updatedAt) byUuid[r.uuid] = r
        }
        return byUuid.values.toList()
    }
}
