package gopesh.percinel.data

import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Parses watch history from other apps so people can switch without losing anything.
 * Pure JVM (no Android classes) so every parser is unit-testable.
 *
 * Supported, auto-detected by content:
 *  - Letterboxd CSV exports (diary.csv / ratings.csv from letterboxd.com/settings/data)
 *  - IMDb ratings CSV export
 *  - percinel's own .xlsx export (full round-trip, including seasons)
 */
object Import {

    /** One watch parsed out of a file, not yet an [Entry]. */
    data class Row(
        val title: String,
        val year: Int?,
        val mediaType: String, // "movie" | "tv"
        val rating: Double, // 1..10
        val watchedAt: Long,
        val notes: String?,
        val season: Int?,
    )

    data class Parsed(
        val source: String, // "Letterboxd" | "IMDb" | "a percinel export"
        val rows: List<Row>,
        val unrated: Int, // rows skipped because they carry no rating
    )

    /** Detects the format and parses. Returns null when the file isn't one we understand. */
    fun parse(bytes: ByteArray): Parsed? {
        if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            return parseXlsx(bytes)
        }
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val table = parseCsv(text)
        if (table.size < 2) return null
        val header = table.first().map { it.trim().lowercase() }
        return when {
            "letterboxd uri" in header -> fromLetterboxd(header, table.drop(1))
            "const" in header && "your rating" in header -> fromImdb(header, table.drop(1))
            else -> null
        }
    }

    /** Duplicate identity: same film watched on the same day is the same watch. */
    fun dedupeKey(title: String, year: Int?, watchedAt: Long): String {
        val day = Instant.ofEpochMilli(watchedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        return "${title.trim().lowercase()}|${year ?: ""}|$day"
    }

    // ---------------------------------------------------------------- CSV

    /** RFC-4180-ish: quoted fields, doubled quotes, commas and newlines inside quotes. */
    fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                when {
                    ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    ch == '"' -> inQuotes = false
                    else -> field.append(ch)
                }
            } else when (ch) {
                '"' -> inQuotes = true
                ',' -> { row.add(field.toString()); field.setLength(0) }
                '\r' -> {} // swallow; \n ends the row
                '\n' -> { row.add(field.toString()); field.setLength(0); rows.add(row); row = ArrayList() }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    // ---------------------------------------------------------------- Letterboxd

    private fun fromLetterboxd(header: List<String>, data: List<List<String>>): Parsed {
        fun col(name: String) = header.indexOf(name)
        val iName = col("name")
        val iYear = col("year")
        val iRating = col("rating")
        // diary.csv has the day you watched; ratings.csv only has the day you rated.
        val iDate = col("watched date").takeIf { it >= 0 } ?: col("date")
        if (iName < 0) return Parsed("Letterboxd", emptyList(), 0)

        var unrated = 0
        val rows = data.mapNotNull { r ->
            val title = r.getOrNull(iName)?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            val stars = r.getOrNull(iRating)?.trim()?.toDoubleOrNull()
            if (stars == null || stars <= 0.0) { unrated++; return@mapNotNull null }
            Row(
                title = title,
                year = r.getOrNull(iYear)?.trim()?.toIntOrNull(),
                mediaType = "movie", // Letterboxd tracks films only
                rating = (stars * 2).coerceIn(1.0, 10.0),
                watchedAt = parseIsoDay(r.getOrNull(iDate)) ?: System.currentTimeMillis(),
                notes = null,
                season = null,
            )
        }
        return Parsed("Letterboxd", rows, unrated)
    }

    // ---------------------------------------------------------------- IMDb

    private fun fromImdb(header: List<String>, data: List<List<String>>): Parsed {
        fun col(name: String) = header.indexOf(name)
        val iTitle = col("title")
        val iYear = col("year")
        val iRating = col("your rating")
        val iDate = col("date rated")
        val iType = col("title type")
        if (iTitle < 0) return Parsed("IMDb", emptyList(), 0)

        var unrated = 0
        val rows = data.mapNotNull { r ->
            val title = r.getOrNull(iTitle)?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            val rating = r.getOrNull(iRating)?.trim()?.toDoubleOrNull()
            if (rating == null || rating <= 0.0) { unrated++; return@mapNotNull null }
            val type = r.getOrNull(iType)?.trim()?.lowercase().orEmpty()
            Row(
                title = title,
                year = r.getOrNull(iYear)?.trim()?.toIntOrNull(),
                mediaType = if ("series" in type || type == "tvepisode") "tv" else "movie",
                rating = rating.coerceIn(1.0, 10.0),
                watchedAt = parseIsoDay(r.getOrNull(iDate)) ?: System.currentTimeMillis(),
                notes = null,
                season = null,
            )
        }
        return Parsed("IMDb", rows, unrated)
    }

    /** "2024-03-12" → epoch millis at local noon (noon avoids timezone edge flips). */
    private fun parseIsoDay(s: String?): Long? {
        val t = s?.trim().orEmpty()
        if (t.isEmpty()) return null
        return runCatching {
            LocalDate.parse(t.take(10)).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    // ---------------------------------------------------------------- percinel .xlsx

    private fun parseXlsx(bytes: ByteArray): Parsed? {
        var sheet: String? = null
        var shared: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                when {
                    e.name == "xl/sharedStrings.xml" -> shared = zip.readBytes().toString(Charsets.UTF_8)
                    e.name.startsWith("xl/worksheets/") && e.name.endsWith(".xml") && sheet == null ->
                        sheet = zip.readBytes().toString(Charsets.UTF_8)
                }
                e = zip.nextEntry
            }
        }
        val sheetXml = sheet ?: return null
        val sharedStrings = shared?.let { xml ->
            Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si ->
                Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(si.groupValues[1]).joinToString("") { unxml(it.groupValues[1]) }
            }.toList()
        } ?: emptyList()

        val table = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(sheetXml).map { row ->
            val cells = HashMap<Int, String>()
            Regex("""<c ([^>]*?)(?:/>|>(.*?)</c>)""", RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[1])
                .forEach { m ->
                    val attrs = m.groupValues[1]
                    val body = m.groupValues[2]
                    val ref = Regex("""r="([A-Z]+)\d+"""").find(attrs)?.groupValues?.get(1) ?: return@forEach
                    val type = Regex("""t="([^"]+)"""").find(attrs)?.groupValues?.get(1)
                    val value = when (type) {
                        "inlineStr" -> Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                            .findAll(body).joinToString("") { unxml(it.groupValues[1]) }
                        "s" -> Regex("<v>(.*?)</v>").find(body)?.groupValues?.get(1)?.trim()?.toIntOrNull()
                            ?.let { sharedStrings.getOrNull(it) } ?: ""
                        else -> Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(body)
                            ?.let { unxml(it.groupValues[1]) } ?: ""
                    }
                    cells[colIndex(ref)] = value
                }
            cells
        }.toList()
        if (table.size < 2) return null

        val headerCells = table.first()
        fun col(name: String) = headerCells.entries.firstOrNull { it.value.trim().lowercase() == name }?.key ?: -1
        val iTitle = col("title")
        val iYear = col("year")
        val iType = col("type")
        val iSeason = col("season") // absent in exports made before seasons existed
        val iRating = col("rating")
        val iWatched = col("watched on")
        val iNotes = col("notes")
        if (iTitle < 0 || iRating < 0) return null

        var unrated = 0
        val rows = table.drop(1).mapNotNull { r ->
            val title = r[iTitle]?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            val rating = r[iRating]?.trim()?.toDoubleOrNull()
            if (rating == null || rating <= 0.0) { unrated++; return@mapNotNull null }
            Row(
                title = title,
                year = r[iYear]?.trim()?.toDoubleOrNull()?.toInt(),
                mediaType = if (r[iType]?.trim().equals("Series", ignoreCase = true)) "tv" else "movie",
                rating = rating.coerceIn(1.0, 10.0),
                watchedAt = parseExportDate(r[iWatched]) ?: System.currentTimeMillis(),
                notes = r[iNotes]?.trim()?.ifBlank { null },
                season = r[iSeason]?.trim()?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 },
            )
        }
        return Parsed("a percinel export", rows, unrated)
    }

    /** Matches Export's "d MMM yyyy, h:mm a"; tries the device locale then English. */
    private fun parseExportDate(s: String?): Long? {
        val t = s?.trim().orEmpty()
        if (t.isEmpty()) return null
        for (locale in listOf(Locale.getDefault(), Locale.ENGLISH)) {
            runCatching { return SimpleDateFormat("d MMM yyyy, h:mm a", locale).parse(t)?.time }
        }
        return parseIsoDay(t)
    }

    private fun colIndex(letters: String): Int {
        var n = 0
        for (ch in letters) n = n * 26 + (ch - 'A' + 1)
        return n - 1
    }

    private fun unxml(s: String) = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&amp;", "&")
}
