package com.example.svoi.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.svoi.data.local.SvoiAccent
import com.example.svoi.data.local.ThemeMode

private fun buildLightScheme(p: AccentPalette) = lightColorScheme(
    primary            = p.primary,
    onPrimary          = Color.White,
    primaryContainer   = p.container,
    onPrimaryContainer = p.onContainer,
    secondary          = p.onContainer,
    onSecondary        = Color.White,
    background         = Background,
    onBackground       = TextPrimary,
    surface            = Surface,
    onSurface          = TextPrimary,
    surfaceVariant     = SurfaceVariant,
    onSurfaceVariant   = TextSecondary,
    outline            = Divider,
    error              = Error,
    onError            = Color.White,
)

private fun buildDarkScheme(p: AccentPalette) = darkColorScheme(
    primary            = p.primaryDark,
    onPrimary          = Color.White,
    primaryContainer   = p.onContainer,
    onPrimaryContainer = p.container,
    secondary          = p.primaryDark,
    onSecondary        = Color.White,
    background         = DarkBackground,
    onBackground       = DarkTextPrimary,
    surface            = DarkSurface,
    onSurface          = DarkTextPrimary,
    surfaceVariant     = DarkSurfaceVariant,
    onSurfaceVariant   = DarkTextSecondary,
    outline            = DarkDivider,
    error              = Error,
    onError            = Color.White,
)

@Composable
fun SvoiTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: SvoiAccent = SvoiAccent.BLUE,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = accentPalette(accent)
    val colorScheme = if (isDark) buildDarkScheme(palette) else buildLightScheme(palette)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
