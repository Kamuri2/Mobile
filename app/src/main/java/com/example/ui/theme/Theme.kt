package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004D69),
    onPrimaryContainer = Color(0xFFC2E8FF),
    secondary = NeonPurple,
    onSecondary = Color(0xFF381E72),
    secondaryContainer = Color(0xFF4F378B),
    onSecondaryContainer = Color(0xFFE8DDFF),
    tertiary = NeonTeal,
    background = MidnightBackground,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = TextSecondary
)

@Composable
fun LiquidMusicTheme(
    appSettings: com.example.model.AppSettings? = null,
    content: @Composable () -> Unit
) {
    val theme = appSettings?.selectedTheme ?: com.example.model.AppTheme.SUNSET
    val isDark = appSettings?.isDarkMode ?: true
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = theme.primaryColor,
            background = theme.bgGradientStart,
            surface = theme.bgGradientEnd,
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = theme.primaryColor,
            background = Color(0xFFF7F8FA),
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = Color(0xFF111111),
            onSurface = Color(0xFF111111)
        )
    }

    val fontName = appSettings?.fontFamilyName ?: "System Font (Default)"
    val currentFontFamily = getFontFamily(fontName)
    val typography = createAppTypography(fontName)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography
    ) {
        ProvideTextStyle(
            value = LocalTextStyle.current.copy(fontFamily = currentFontFamily)
        ) {
            content()
        }
    }
}
