package gopesh.percinel.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Turning a piece of on-screen UI into a shareable image, in one place.
 *
 * Wrap the card in [Shareable] and hand the returned layer to [shareImage] — the shared PNG is
 * literally the pixels that were drawn, so what you see is what lands in the chat or story.
 */
@Composable
fun rememberShareLayer(): GraphicsLayer = rememberGraphicsLayer()

/** Records whatever it wraps so it can be exported as a PNG. */
@Composable
fun Shareable(layer: GraphicsLayer, content: @Composable () -> Unit) {
    Box(
        Modifier.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        },
    ) { content() }
}

/**
 * Same as [Shareable], but takes up no space on screen: the card is measured and drawn at its
 * real size (so the capture is complete) then placed out of view. Lets a screen offer "Share"
 * without showing a preview it doesn't need.
 */
@Composable
fun OffscreenShareable(layer: GraphicsLayer, content: @Composable () -> Unit) {
    Layout(content = { Shareable(layer, content) }) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minHeight = 0))
        // Parked a full screen to the right: a vertical scroller clips horizontally, so it
        // never shows, while still being measured and drawn at full size for the capture.
        layout(constraints.maxWidth, 0) { placeable.place(constraints.maxWidth * 2, 0) }
    }
}

/**
 * Exports [layer] to a PNG and opens the system share sheet — the same sheet Instagram, WhatsApp
 * and the rest hook into, so no per-app integrations are needed. Returns false if it couldn't.
 */
suspend fun shareImage(
    context: Context,
    layer: GraphicsLayer,
    fileName: String,
    caption: String,
    chooserTitle: String,
    /** Send straight to one app instead of showing the chooser. */
    targetPackage: String? = null,
): Boolean = try {
    val bitmap = layer.toImageBitmap().asAndroidBitmap()
    val uri = withContext(Dispatchers.IO) { savePng(context, bitmap, fileName) }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, caption)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        if (targetPackage != null) send.apply { setPackage(targetPackage) }
        else Intent.createChooser(send, chooserTitle),
    )
    true
} catch (_: Exception) {
    Toast.makeText(context, "Couldn't make the image", Toast.LENGTH_SHORT).show()
    false
}

/** Is an app installed? Used to only offer targets that actually exist on the phone. */
fun isInstalled(context: Context, pkg: String): Boolean =
    runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

/**
 * percinel's own Facebook App ID, which Instagram has required on story shares since Jan 2023.
 * It identifies the app, not the person — it's public, ships in every build, and is the same for
 * everyone. Blank until one is registered, which just hides the Instagram option.
 */
const val IG_APP_ID = ""

const val PKG_INSTAGRAM = "com.instagram.android"
const val PKG_WHATSAPP = "com.whatsapp"

/**
 * Posts the card straight to an Instagram story: the image rides as an interactive sticker over a
 * gradient in percinel's own colours, so it lands looking deliberate rather than letterboxed.
 *
 * Instagram wants an explicit URI grant because this bypasses the chooser. Returns false if
 * Instagram isn't installed or rejects it, so the caller can fall back to the normal sheet.
 */
suspend fun shareToInstagramStory(context: Context, layer: GraphicsLayer, fileName: String): Boolean {
    if (!canShareToInstagramStory(context)) return false
    return try {
        val bitmap = layer.toImageBitmap().asAndroidBitmap()
        val uri = withContext(Dispatchers.IO) { savePng(context, bitmap, fileName) }
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setPackage(PKG_INSTAGRAM)
            // Required by Instagram since Jan 2023 — the share is rejected without it.
            putExtra("source_application", IG_APP_ID)
            putExtra("interactive_asset_uri", uri)
            putExtra("top_background_color", "#211F1D")
            putExtra("bottom_background_color", "#131313")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(context.packageManager) == null) return false
        // Flags alone aren't enough for a URI handed to another app outside the chooser.
        context.grantUriPermission(PKG_INSTAGRAM, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

/** Instagram stories need both the app and a configured Facebook App ID; without either we
 *  don't advertise the option rather than offering a button that quietly does nothing. */
fun canShareToInstagramStory(context: Context): Boolean =
    IG_APP_ID.isNotBlank() && isInstalled(context, PKG_INSTAGRAM)

private fun savePng(context: Context, bitmap: Bitmap, fileName: String): android.net.Uri {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "$fileName.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * The "Share" affordance every shareable screen uses: straight to an Instagram story or WhatsApp
 * when they're installed, otherwise the system sheet. One menu so the screens stay thin.
 */
@Composable
fun ShareMenu(
    layer: GraphicsLayer,
    fileName: String,
    caption: String,
    chooserTitle: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val hasInstagram = remember { canShareToInstagramStory(context) }
    val hasWhatsApp = remember { isInstalled(context, PKG_WHATSAPP) }

    fun run(block: suspend () -> Unit) {
        open = false
        busy = true
        scope.launch { block(); busy = false }
    }

    Box {
        TextButton(enabled = !busy, onClick = { if (hasInstagram || hasWhatsApp) open = true else run { shareSheet(context, layer, fileName, caption, chooserTitle) } }) {
            Text(
                if (busy) "…" else "Share",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (hasInstagram) {
                DropdownMenuItem(
                    text = { Text("Instagram story") },
                    onClick = {
                        run {
                            // Falls back to the sheet if Instagram refuses the story intent.
                            if (!shareToInstagramStory(context, layer, fileName)) {
                                shareSheet(context, layer, fileName, caption, chooserTitle)
                            }
                        }
                    },
                )
            }
            if (hasWhatsApp) {
                DropdownMenuItem(
                    text = { Text("WhatsApp") },
                    onClick = { run { shareSheet(context, layer, fileName, caption, chooserTitle, PKG_WHATSAPP) } },
                )
            }
            DropdownMenuItem(
                text = { Text("More…") },
                onClick = { run { shareSheet(context, layer, fileName, caption, chooserTitle) } },
            )
        }
    }
}

private suspend fun shareSheet(
    context: Context,
    layer: GraphicsLayer,
    fileName: String,
    caption: String,
    chooserTitle: String,
    targetPackage: String? = null,
) {
    shareImage(context, layer, fileName, caption, chooserTitle, targetPackage)
}
