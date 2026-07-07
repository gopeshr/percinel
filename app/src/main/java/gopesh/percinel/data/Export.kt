package gopesh.percinel.data

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds export files with no third-party dependencies. An .xlsx is just a ZIP of
 * XML parts, so we hand-write the minimal set that Excel / Sheets / Numbers accept.
 */
object Export {

    private val dateFmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

    private data class Col(val header: String, val value: (Entry) -> Any?)

    private val columns = listOf(
        Col("Title") { it.title },
        Col("Year") { it.year },
        Col("Type") { if (it.mediaType == "tv") "Series" else "Movie" },
        Col("Season") { it.season },
        Col("Rating") { it.rating },
        Col("Watched on") { dateFmt.format(Date(it.watchedAt)) },
        Col("Notes") { it.notes ?: "" },
    )

    /** Suggested filename for saved exports. */
    const val FILENAME = "percinel-watches.xlsx"

    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** Writes a complete .xlsx into [out]. The stream is fully consumed but not closed. */
    fun writeXlsx(out: OutputStream, entries: List<Entry>) {
        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", CONTENT_TYPES)
            zip.put("_rels/.rels", RELS)
            zip.put("xl/workbook.xml", WORKBOOK)
            zip.put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.put("xl/worksheets/sheet1.xml", sheet(entries))
        }
    }

    /** Writes an .xlsx into the app cache and returns a shareable content:// URI. */
    fun xlsx(context: Context, entries: List<Entry>): android.net.Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, FILENAME)
        file.outputStream().buffered().use { writeXlsx(it, entries) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun sheet(entries: List<Entry>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")

        // Header row
        sb.append("""<row r="1">""")
        columns.forEachIndexed { c, col -> sb.append(strCell(ref(c, 1), col.header)) }
        sb.append("</row>")

        // Data rows
        entries.forEachIndexed { i, e ->
            val r = i + 2
            sb.append("""<row r="$r">""")
            columns.forEachIndexed { c, col ->
                val v = col.value(e)
                val cellRef = ref(c, r)
                when (v) {
                    is Number -> sb.append("""<c r="$cellRef"><v>$v</v></c>""")
                    else -> sb.append(strCell(cellRef, v?.toString() ?: ""))
                }
            }
            sb.append("</row>")
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun strCell(ref: String, text: String) =
        """<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${xml(text)}</t></is></c>"""

    private fun ref(col: Int, row: Int): String {
        var c = col
        val name = StringBuilder()
        do {
            name.insert(0, ('A' + c % 26))
            c = c / 26 - 1
        } while (c >= 0)
        return "$name$row"
    }

    private fun xml(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private const val CONTENT_TYPES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""

    private const val RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val WORKBOOK =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="My watches" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private const val WORKBOOK_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
}
