package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val LiquidDarkBg = Color(0xFF0F0805)
val LiquidDarkSurface = Color(0x1A25140B)
val LiquidGlassCard = Color(0x14FFFFFF)
val LiquidGlassBorder = Color(0x1FFFFFFF)
val OrangeAccent = Color(0xFFFF5C00)
val OrangeGlow = Color(0xFFFF8C00)
val PurpleAccent = Color(0xFF7C3AED)
val AmberGlow = Color(0xFFFF4E00)

val GlassTextPrimaryDark = Color(0xFFFFFFFF)
val GlassTextSecondaryDark = Color(0xB3FFFFFF)
val GlassTextMutedDark = Color(0x66FFFFFF)

val GlassTextPrimaryLight = Color(0xFF111111)
val GlassTextSecondaryLight = Color(0xFF4A4A4A)
val GlassTextMutedLight = Color(0xFF757575)

val GlassTextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) GlassTextPrimaryLight else GlassTextPrimaryDark

val GlassTextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) GlassTextSecondaryLight else GlassTextSecondaryDark

val GlassTextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) GlassTextMutedLight else GlassTextMutedDark
