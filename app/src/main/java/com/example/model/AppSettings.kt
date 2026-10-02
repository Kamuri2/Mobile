package com.example.model

import androidx.compose.ui.graphics.Color

enum class AppTheme(val displayName: String, val primaryColor: Color, val bgGradientStart: Color, val bgGradientEnd: Color) {
    MIDNIGHT_OLED("Midnight (OLED)", Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFF0D0D0D)),
    MINT("Mint", Color(0xFF00F5A0), Color(0xFF051B14), Color(0xFF000A06)),
    CYBERPUNK("Cyberpunk", Color(0xFF00F0FF), Color(0xFF1B0028), Color(0xFF090014)),
    OCEAN("Ocean", Color(0xFF00B4D8), Color(0xFF031926), Color(0xFF010B13)),
    SUNSET("Sunset", Color(0xFFFF5C00), Color(0xFF1E0C05), Color(0xFF0A0402)),
    LAVENDER("Lavender", Color(0xFFC77DFF), Color(0xFF190B28), Color(0xFF0B0414)),
    GLASS_BLUE("Glass Blue", Color(0xFF3A86FF), Color(0xFF0B1B3D), Color(0xFF040A1A)),
    DRACULA("Dracula", Color(0xFFFF79C6), Color(0xFF282A36), Color(0xFF191A21)),
    NORDIC_ICE("Nordic Ice", Color(0xFF80FFEA), Color(0xFF0F2027), Color(0xFF203A43)),
    MATCHA_TEA("Matcha Tea", Color(0xFFA3E635), Color(0xFF14200B), Color(0xFF080D04)),
    SYNTHWAVE("Synthwave", Color(0xFFFF007F), Color(0xFF24002E), Color(0xFF0F0014)),
    CRIMSON_RUBY("Crimson Ruby", Color(0xFFE63946), Color(0xFF2B0A0D), Color(0xFF120305)),
    FOREST("Forest", Color(0xFF2A9D8F), Color(0xFF0A1F1C), Color(0xFF030D0B)),
    AMETHYST("Amethyst", Color(0xFF9D4EDD), Color(0xFF1C0A2A), Color(0xFF0C0314)),
    VOLCANO("Volcano", Color(0xFFFF4500), Color(0xFF2A0900), Color(0xFF120300)),
    COFFEE("Coffee", Color(0xFFD4A373), Color(0xFF221811), Color(0xFF0F0A07)),
    GOLD("Gold", Color(0xFFFFD700), Color(0xFF261F00), Color(0xFF120E00)),
    NEON("Neon", Color(0xFF39FF14), Color(0xFF072100), Color(0xFF020E00)),
    MONOCHROME("Monochrome", Color(0xFFE0E0E0), Color(0xFF1A1A1A), Color(0xFF0A0A0A))
}

data class AppSettings(
    val isDarkMode: Boolean = true,
    val appLanguage: String = "English",
    val selectedTheme: AppTheme = AppTheme.SUNSET,
    val fontFamilyName: String = "System Font (Default)",
    val lyricsFontSizePercent: Int = 110,
    val isLyricsTranslationEnabled: Boolean = false,
    val targetTranslationLanguage: String = "Spanish",
    val crossfadeDuration: Float = 0.1f,
    val fadeOutDuration: Float = 0.1f,
    val fadeInDuration: Float = 0.1f
)
