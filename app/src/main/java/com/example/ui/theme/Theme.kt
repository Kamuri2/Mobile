package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.example.model.AppSettings

@Composable
fun LiquidMusicTheme(
    appSettings: AppSettings,
    content: @Composable () -> Unit,
) {
    val primaryColor = appSettings.selectedTheme.primaryColor
    
    val colorScheme = if (appSettings.isDarkMode) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.Black,
            primaryContainer = primaryColor.copy(alpha = 0.2f),
            onPrimaryContainer = primaryColor,
            secondary = PurpleAccent,
            onSecondary = Color.White,
            tertiary = OrangeGlow,
            background = LiquidDarkBg,
            onBackground = GlassTextPrimaryDark,
            surface = LiquidDarkSurface,
            onSurface = GlassTextPrimaryDark,
            surfaceVariant = LiquidGlassCard,
            onSurfaceVariant = GlassTextSecondaryDark,
            outline = LiquidGlassBorder
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.2f),
            onPrimaryContainer = primaryColor,
            secondary = PurpleAccent,
            onSecondary = Color.White,
            tertiary = OrangeGlow,
            background = Color(0xFFF7F7F9),
            onBackground = Color(0xFF111111),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF111111),
            surfaceVariant = Color(0xFFEBEBF0),
            onSurfaceVariant = Color(0xFF4A4A4A),
            outline = Color(0xFFD1D1D6)
        )
    }

    val currentFontFamily = remember(appSettings.fontFamilyName) {
        getFontFamily(appSettings.fontFamilyName)
    }

    val typography = remember(appSettings.fontFamilyName) {
        createAppTypography(appSettings.fontFamilyName)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = currentFontFamily),
            content = content
        )
    }
}
