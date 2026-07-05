package ai.ligaments.percinel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Silver = Color(0xFFE8E4D8)
val Bg = Color(0xFF131313)
val Elev = Color(0xFF211F1D)

private val Scheme = darkColorScheme(
    primary = Silver,
    onPrimary = Color(0xFF1B1A16),
    secondary = Silver,
    onSecondary = Color(0xFF1B1A16),
    background = Bg,
    onBackground = Color(0xFFF2F1EC),
    surface = Bg,
    onSurface = Color(0xFFF2F1EC),
    surfaceVariant = Elev,
    onSurfaceVariant = Color(0xFFB5B2A9),
    outline = Color(0xFF494742),
)

@Composable
fun PercinelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
