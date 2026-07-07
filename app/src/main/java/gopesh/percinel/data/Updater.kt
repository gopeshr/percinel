package gopesh.percinel.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a release APK in-app and hands it to Android's package installer. Sideloaded apps
 * have no auto-update, and opening the download URL in a browser tends to hang — this keeps the
 * whole flow inside percinel: download with our own progress, then pop the "install update?" prompt.
 */
object Updater {

    /** Whether the user has allowed percinel to install apps (Android 8+ gates this per-source). */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Send the user to the system screen to allow installs from percinel. */
    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Ceiling on the whole download. Socket timeouts don't cover DNS lookups or a
     *  trickling connection, and some OEM network layers stall instead of failing —
     *  this guarantees the UI always gets an answer. */
    const val TIMEOUT_MS = 120_000L

    /**
     * Downloads the APK to the app cache and returns the file. Follows GitHub's redirect.
     * Reports progress as 0..100 (or -1 while the size is unknown) via [onProgress].
     */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit = {},
    ): File = withTimeout(TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "percinel-update.apk")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 30000
            }
            try {
                conn.inputStream.use { input ->
                    val total = conn.contentLengthLong
                    var copied = 0L
                    val buffer = ByteArray(64 * 1024)
                    file.outputStream().use { out ->
                        while (true) {
                            ensureActive() // honor the outer timeout between reads
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            copied += n
                            onProgress(if (total > 0) ((copied * 100) / total).toInt() else -1)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            file
        }
    }

    /** Launches the system installer for the downloaded APK. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
