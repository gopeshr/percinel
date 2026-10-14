package gopesh.percinel.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Dark: a cinema at night — near-black, silver highlights.
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE8E4D8),
    onPrimary = Color(0xFF1B1A16),
    secondary = Color(0xFFE8E4D8),
    onSecondary = Color(0xFF1B1A16),
    background = Color(0xFF131313),
    onBackground = Color(0xFFF2F1EC),
    surface = Color(0xFF131313),
    onSurface = Color(0xFFF2F1EC),
    surfaceVariant = Color(0xFF211F1D),
    onSurfaceVariant = Color(0xFFB5B2A9),
    outline = Color(0xFF494742),
    outlineVariant = Color(0xFF34322F),
    // Material's defaults for these are lavender-tinted; dialogs, menus, sheets and switch
    // tracks read them, so they're pinned to the same warm greys.
    primaryContainer = Color(0xFF34322F),
    onPrimaryContainer = Color(0xFFF2F1EC),
    secondaryContainer = Color(0xFF34322F),
    onSecondaryContainer = Color(0xFFF2F1EC),
    surfaceTint = Color(0xFF131313),
    surfaceDim = Color(0xFF131313),
    surfaceBright = Color(0xFF2E2C29),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1A1918),
    surfaceContainer = Color(0xFF1E1D1B),
    surfaceContainerHigh = Color(0xFF252321),
    surfaceContainerHighest = Color(0xFF2E2C29),
    inverseSurface = Color(0xFFF2F1EC),
    inverseOnSurface = Color(0xFF1B1A16),
    inversePrimary = Color(0xFF1B1A16),
)

// Light: the same warmth inverted — paper and ink rather than clinical white.
// Every text/background pairing here clears WCAG AA (4.5:1).
private val LightScheme = lightColorScheme(
    primary = Color(0xFF1B1A16),
    onPrimary = Color(0xFFF5F3EE),
    secondary = Color(0xFF1B1A16),
    onSecondary = Color(0xFFF5F3EE),
    background = Color(0xFFF5F3EE),
    onBackground = Color(0xFF1B1A16),
    surface = Color(0xFFF5F3EE),
    onSurface = Color(0xFF1B1A16),
    surfaceVariant = Color(0xFFE9E5DB),
    onSurfaceVariant = Color(0xFF5C584F),
    outline = Color(0xFFCBC6BA),
    outlineVariant = Color(0xFFDCD7CC),
    primaryContainer = Color(0xFFE3DED3),
    onPrimaryContainer = Color(0xFF1B1A16),
    secondaryContainer = Color(0xFFE3DED3),
    onSecondaryContainer = Color(0xFF1B1A16),
    surfaceTint = Color(0xFFF5F3EE),
    surfaceDim = Color(0xFFDCD7CC),
    surfaceBright = Color(0xFFF5F3EE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1EEE7),
    surfaceContainer = Color(0xFFEDE9E1),
    surfaceContainerHigh = Color(0xFFF1EEE7),
    surfaceContainerHighest = Color(0xFFE3DED3),
    inverseSurface = Color(0xFF2E2C27),
    inverseOnSurface = Color(0xFFF2F1EC),
    inversePrimary = Color(0xFFE8E4D8),
)

enum class ThemeMode(val label: String) { SYSTEM("Match my phone"), LIGHT("Light"), DARK("Dark") }

/**
 * The user's appearance choice. Held as snapshot state so changing it in Settings re-themes the
 * whole app instantly, and persisted so it survives restarts.
 */
object Appearance {
    private const val KEY = "theme_mode"

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun load(context: Context) {
        val saved = prefs(context).getString(KEY, null)
        mode = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
        tellSystem(context)
    }

    fun set(context: Context, value: ThemeMode) {
        mode = value
        prefs(context).edit().putString(KEY, value.name).apply()
        tellSystem(context)
    }

    // Android 12+ remembers a per-app night mode, so the splash screen (drawn before any of our
    // code runs) matches the choice too. Older versions just follow the phone for the splash.
    private fun tellSystem(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            }
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("percinel", Context.MODE_PRIVATE)
}

@Composable
fun PercinelTheme(content: @Composable () -> Unit) {
    val dark = when (Appearance.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) DarkScheme else LightScheme

    // Status/navigation bars and the window behind Compose follow the in-app choice, not just
    // the system's, so a forced theme never shows the other theme's colours around the edges.
    val view = LocalView.current
    val window = (LocalContext.current as? Activity)?.window
    if (window != null && !view.isInEditMode) {
        SideEffect {
            val bg = scheme.background.toArgb()
            window.setBackgroundDrawable(ColorDrawable(bg))
            window.statusBarColor = bg
            // API 26 can't draw dark nav-bar icons, so keep a dark bar there rather than
            // white icons on a light bar.
            window.navigationBarColor =
                if (!dark && Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) android.graphics.Color.BLACK else bg
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
