package gopesh.percinel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import gopesh.percinel.ui.App
import gopesh.percinel.ui.PercinelTheme

class MainActivity : ComponentActivity() {
    private var sharedQuery by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        sharedQuery = extractSharedQuery(intent)
        setContent {
            PercinelTheme {
                App(sharedQuery = sharedQuery)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A share while the app is already open. Nonce a marker so the same text re-triggers.
        extractSharedQuery(intent)?.let { sharedQuery = it }
    }

    /** Turn a shared text/plain payload into a search query for the Add screen. */
    private fun extractSharedQuery(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()

        // A subject that isn't a URL is usually the cleanest title (e.g. shared from IMDb/TMDb).
        subject?.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { return it }

        val body = text ?: return subject?.takeIf { it.isNotBlank() }
        // Strip URLs; whatever text remains is the likely title.
        val withoutUrls = body.replace(Regex("https?://\\S+"), "").trim()
        if (withoutUrls.isNotEmpty()) return withoutUrls

        // Only a URL was shared — derive a title from its slug, e.g. /movie/603-the-matrix.
        val url = Regex("https?://\\S+").find(body)?.value ?: return null
        val lastSegment = url.trimEnd('/').substringAfterLast('/')
        val slug = lastSegment.substringAfter('-', "").ifBlank { lastSegment }
            .replace('-', ' ').replace('_', ' ').trim()
        return slug.ifBlank { null }
    }
}
