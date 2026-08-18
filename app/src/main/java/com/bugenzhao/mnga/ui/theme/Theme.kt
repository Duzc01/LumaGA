package com.bugenzhao.mnga.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.bugenzhao.mnga.storage.ColorSchemeMode
import com.bugenzhao.mnga.storage.ThemeColor

private fun tint(base: Color, factor: Float): Color = Color(
    red = (base.red * factor).coerceIn(0f, 1f),
    green = (base.green * factor).coerceIn(0f, 1f),
    blue = (base.blue * factor).coerceIn(0f, 1f),
    alpha = base.alpha,
)

private fun blend(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/**
 * Build a Material3 scheme around a single accent color, approximating the
 * iOS tint-color theming (the accent colors the interactive elements while
 * neutral grays stay platform-like).
 */
private fun scheme(accent: Color, dark: Boolean): ColorScheme {
    val onAccent = if (accent.luminance() > 0.5f && !dark) Color(0xFF141414) else Color.White
    val container = if (dark) tint(accent, 0.28f) else blend(accent, Color.White, 0.82f)
    val onContainer = if (dark) blend(accent, Color.White, 0.85f) else tint(accent, 0.35f).copy(alpha = 1f).let { blend(it, Color.Black, 0.25f) }
    return if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = blend(accent, Color(0xFF938F99), 0.6f),
            tertiary = blend(accent, Color(0xFFEFB8C8), 0.5f),
            background = Color(0xFF000000),
            onBackground = Color(0xFFF2F2F7),
            surface = Color(0xFF1C1C1E),
            onSurface = Color(0xFFF2F2F7),
            surfaceVariant = Color(0xFF2C2C2E),
            onSurfaceVariant = Color(0xFFAEAEB2),
            surfaceContainer = Color(0xFF2C2C2E),
            surfaceContainerHigh = Color(0xFF3A3A3C),
            surfaceContainerHighest = Color(0xFF48484A),
            outline = Color(0xFF545458),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = blend(accent, Color(0xFF6E6E73), 0.55f),
            tertiary = blend(accent, Color(0xFFB36B7F), 0.5f),
            background = Color(0xFFF2F2F7),
            onBackground = Color(0xFF1C1C1E),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFFE5E5EA),
            onSurfaceVariant = Color(0xFF3A3A3C),
            surfaceContainer = Color(0xFFF2F2F7),
            surfaceContainerHigh = Color(0xFFE5E5EA),
            surfaceContainerHighest = Color(0xFFD1D1D6),
            outline = Color(0xFFC7C7CC),
        )
    }
}

private fun Color.luminance(): Float =
    (0.2126f * red + 0.7152f * green + 0.0722f * blue)

@Composable
fun LumaGATheme(
    themeColor: ThemeColor,
    colorSchemeMode: ColorSchemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (colorSchemeMode) {
        ColorSchemeMode.AUTO -> isSystemInDarkTheme()
        ColorSchemeMode.LIGHT -> false
        ColorSchemeMode.DARK -> true
    }
    val accent = Color(if (dark) themeColor.darkColor else themeColor.lightColor)
    MaterialTheme(
        colorScheme = scheme(accent, dark),
        typography = LumaGATypography,
        content = content,
    )
}
