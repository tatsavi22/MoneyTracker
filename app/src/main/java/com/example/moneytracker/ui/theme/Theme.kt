package com.example.moneytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand colours from PROJECT_SPEC section 9 ────────────────────────────────

val Background = Color(0xFFF6F7FB)
val BackgroundDark = Color(0xFF12141C)

val Surface = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF1B1E2A)

val OnBackground = Color(0xFF16213E)
val OnBackgroundDark = Color(0xFFE9EBF5)

/** Green used for money-in amounts */
val MoneyIn = Color(0xFF1B9A6C)

/** Dark grey used for debited / money-out amounts in light theme */
val MoneyOut = Color(0xFF2D3748)

/** Soft slate grey used for debited / money-out amounts in dark theme */
val MoneyOutDark = Color(0xFFCBD5E1)

/** Returns the appropriate debited amount color based on current theme (dark grey in light mode, slate in dark mode) */
val DebitColor: Color
    @Composable
    get() = if (isSystemInDarkTheme()) MoneyOutDark else MoneyOut

/** Destructive red used for delete / danger actions */
val DangerRed = Color(0xFFE53935)

val Primary = Color(0xFF3D4DB7)
val PrimaryDark = Color(0xFF9AA5FF)

// ── Material 3 colour schemes ─────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    background = Background,
    surface = Surface,
    onBackground = OnBackground,
    onSurface = OnBackground,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    onBackground = OnBackgroundDark,
    onSurface = OnBackgroundDark,
)

/**
 * MoneyTracker application theme.
 *
 * Follows the system light/dark preference. Dynamic colour is intentionally
 * NOT used so the design tokens in PROJECT_SPEC section 9 are always applied.
 */
@Composable
fun MoneyTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
