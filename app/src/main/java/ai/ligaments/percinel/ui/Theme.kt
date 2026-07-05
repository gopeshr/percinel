package ai.ligaments.percinel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFF6B21B)
val Bg = Color(0xFF15131A)
val Elev = Color(0xFF211E28)

private val Scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF201700),
    secondary = Amber,
    onSecondary = Color(0xFF201700),
    background = Bg,
    onBackground = Color(0xFFF3F2F5),
    surface = Bg,
    onSurface = Color(0xFFF3F2F5),
    surfaceVariant = Elev,
    onSurfaceVariant = Color(0xFFB9B6C0),
    outline = Color(0xFF48454F),
)

@Composable
fun PercinelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
