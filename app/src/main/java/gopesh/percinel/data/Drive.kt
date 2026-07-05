package gopesh.percinel.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Google Drive client for the hidden per-app `appDataFolder`. Talks the Drive REST API
 * directly over HttpURLConnection (same lightweight style as [Tmdb]) — deliberately avoids the
 * multi-megabyte official Drive SDK. Only the OAuth access [token] is needed.
 */
class Drive(private val token: String) {

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15000
            readTimeout = 20000
        }

    private fun HttpURLConnection.readBody(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        val body = stream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        if (responseCode !in 200..299) {
            throw RuntimeException("Drive HTTP $responseCode: ${body.take(300)}")
        }
        return body
    }

    /** File id of percinel.json in appDataFolder, or null if it doesn't exist yet. */
    fun findFileId(): String? {
        val q = URLEncoder.encode("name = '$FILE_NAME'", "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id)"
        val files = JSONObject(open(url, "GET").readBody()).optJSONArray("files") ?: return null
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    fun download(fileId: String): String =
        open("https://www.googleapis.com/drive/v3/files/$fileId?alt=media", "GET").readBody()

    /** Create the appdata file with [content]; returns its new id. */
    fun create(content: String): String {
        val boundary = "percinelSync${System.identityHashCode(this)}"
        val c = open("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        val meta = JSONObject().apply {
            put("name", FILE_NAME)
            put("parents", JSONArray().put("appDataFolder"))
        }
        val body = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(meta.toString()).append("\r\n")
            append("--$boundary\r\n")
            append("Content-Type: application/json\r\n\r\n")
            append(content).append("\r\n")
            append("--$boundary--")
        }
        OutputStreamWriter(c.outputStream, Charsets.UTF_8).use { it.write(body) }
        return JSONObject(c.readBody()).getString("id")
    }

    /** Overwrite the appdata file's contents. Uses POST + method-override because
     * HttpURLConnection can't send PATCH directly. */
    fun update(fileId: String, content: String) {
        val c = open("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media", "POST")
        c.doOutput = true
        c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        OutputStreamWriter(c.outputStream, Charsets.UTF_8).use { it.write(content) }
        c.readBody()
    }

    private companion object {
        const val FILE_NAME = "percinel.json"
    }
}
