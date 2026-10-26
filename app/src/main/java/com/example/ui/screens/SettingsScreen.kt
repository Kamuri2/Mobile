package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppSettings
import com.example.model.AppTheme
import com.example.ui.Translations
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onRescanAudio: () -> Unit,
    onRepairCovers: (() -> Unit)? = null,
    onClearCache: (() -> Unit)? = null,
    isRescanning: Boolean = false,
    isRepairingCovers: Boolean = false,
    isClearingCache: Boolean = false,
    onStartTour: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var fontInput by remember { mutableStateOf("") }
    val primaryColor = MaterialTheme.colorScheme.primary
    val lang = settings.appLanguage

    // Adaptive Theme Palette: Crisp, black text in Light Mode; elegant frosted white in Dark Mode
    val isDark = settings.isDarkMode
    val textColorPrimary = if (isDark) Color.White else Color(0xFF1E1E1E)
    val textColorSecondary = if (isDark) Color(0xB3FFFFFF) else Color(0xFF555555)
    val textColorMuted = if (isDark) Color(0x66FFFFFF) else Color(0xFF757575)
    val cardBg = if (isDark) Color(0x1A1E1E24) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0x26FFFFFF) else Color(0x1F000000)
    val dividerColor = if (isDark) Color(0x1FFFFFFF) else Color(0x14000000)
    val iconPillBg = if (isDark) Color(0x1AFFFFFF) else Color(0x0F000000)
    val iconPillBorder = if (isDark) Color(0x33FFFFFF) else Color(0x1F000000)
    val formatPillBg = if (isDark) Color(0x14FFFFFF) else Color(0x08000000)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Bar: Back, Title, Settings Icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_btn")) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = textColorPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Translations.get(lang, "settings"),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColorPrimary
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconPillBg)
                    .border(1.dp, iconPillBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = textColorPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Audio Engine Hero Card: Jetpack Media3 (ExoPlayer)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, cardBorder, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(primaryColor, primaryColor.copy(alpha = 0.5f)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = Translations.get(lang, "audio_engine_title"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColorPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(primaryColor.copy(alpha = 0.2f))
                                    .border(1.dp, primaryColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = Translations.get(lang, "audio_engine_badge"),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = Translations.get(lang, "audio_engine_subtitle"),
                            fontSize = 12.sp,
                            color = textColorSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Format pills
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val formats = listOf("FLAC 24-bit", "MP3", "AAC", "OGG", "Opus", "WAV", "ALAC", "MediaCodec")
                    formats.forEach { fmt ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(formatPillBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = fmt,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColorPrimary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Crossfade & Transitions Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, cardBorder, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(primaryColor, primaryColor.copy(alpha = 0.5f)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Translations.get(lang, "crossfade_duration"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColorPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val currentDur = settings.crossfadeDuration
                        val durLabel = if (currentDur <= 0.05f) {
                            if (lang.lowercase().startsWith("es")) "Desactivado (0 ms)" else "Disabled (0 ms)"
                        } else if (currentDur < 1.0f) {
                            "${(currentDur * 1000f).roundToInt()} ms"
                        } else {
                            String.format(java.util.Locale.US, "%.1f s", currentDur)
                        }
                        Text(
                            text = "${if (lang.lowercase().startsWith("es")) "Duración" else "Duration"}: $durLabel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Slider (0.0s to 5.0s)
                Slider(
                    value = settings.crossfadeDuration,
                    onValueChange = { newVal ->
                        val rounded = (newVal * 10f).roundToInt() / 10f
                        onUpdateSettings(settings.copy(crossfadeDuration = rounded))
                    },
                    valueRange = 0.0f..5.0f,
                    steps = 49,
                    colors = SliderDefaults.colors(
                        thumbColor = primaryColor,
                        activeTrackColor = primaryColor,
                        inactiveTrackColor = primaryColor.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick presets
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        Pair(0.0f, if (lang.lowercase().startsWith("es")) "0 ms (Off)" else "0 ms (Off)"),
                        Pair(0.2f, "200 ms"),
                        Pair(0.5f, "500 ms"),
                        Pair(1.0f, "1.0 s"),
                        Pair(2.0f, "2.0 s"),
                        Pair(3.0f, "3.0 s")
                    )
                    presets.forEach { (duration, label) ->
                        val isSelected = kotlin.math.abs(settings.crossfadeDuration - duration) < 0.05f
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) primaryColor else formatPillBg)
                                .border(1.dp, if (isSelected) primaryColor else cardBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    onUpdateSettings(settings.copy(crossfadeDuration = duration))
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else textColorPrimary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Appearance & Themes Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, cardBorder, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Translations.get(lang, "appearance_themes"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColorPrimary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Dark Mode Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (settings.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = textColorSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = Translations.get(lang, "dark_mode"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColorPrimary
                            )
                            Text(
                                text = if (settings.isDarkMode) Translations.get(lang, "dark_mode_desc_on") else Translations.get(lang, "dark_mode_desc_off"),
                                fontSize = 12.sp,
                                color = textColorMuted
                            )
                        }
                    }

                    Switch(
                        checked = settings.isDarkMode,
                        onCheckedChange = { onUpdateSettings(settings.copy(isDarkMode = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = primaryColor,
                            uncheckedThumbColor = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF888888),
                            uncheckedTrackColor = if (isDark) Color(0x33FFFFFF) else Color(0x22000000)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // App Language Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = textColorSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = Translations.get(lang, "app_language"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColorPrimary
                        )
                    }

                    var expandedLang by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(iconPillBg)
                                .border(1.dp, iconPillBorder, RoundedCornerShape(12.dp))
                                .clickable { expandedLang = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentDisplayLang = when (settings.appLanguage.lowercase()) {
                                "system", "sistema", "system default" -> Translations.get(lang, "system_default")
                                "spanish", "español", "es" -> "Español"
                                "french", "français", "fr" -> "Français"
                                "german", "deutsch", "de" -> "Deutsch"
                                "italian", "italiano", "it" -> "Italiano"
                                "portuguese", "português", "pt" -> "Português"
                                "japanese", "日本語", "ja" -> "日本語"
                                else -> "English"
                            }
                            Text(text = currentDisplayLang, color = textColorPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = textColorSecondary, modifier = Modifier.size(18.dp))
                        }

                        DropdownMenu(
                            expanded = expandedLang,
                            onDismissRequest = { expandedLang = false },
                            modifier = Modifier.background(if (isDark) Color(0xFF22222A) else Color(0xFFFFFFFF))
                        ) {
                            listOf("System", "English", "Spanish", "French", "German", "Italian", "Portuguese", "Japanese").forEach { languageOption ->
                                val displayOption = when (languageOption) {
                                    "System" -> Translations.get(lang, "system_default")
                                    "English" -> "English"
                                    "Spanish" -> "Español"
                                    "French" -> "Français"
                                    "German" -> "Deutsch"
                                    "Italian" -> "Italiano"
                                    "Portuguese" -> "Português"
                                    "Japanese" -> "日本語"
                                    else -> languageOption
                                }
                                val isSelected = if (languageOption == "System") {
                                    settings.appLanguage.equals("System", ignoreCase = true)
                                } else {
                                    settings.appLanguage.equals(languageOption, ignoreCase = true)
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = displayOption,
                                            color = if (isSelected) primaryColor else textColorPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        if (languageOption == "System") {
                                            com.example.ui.LanguageManager.cambiarIdioma("system")
                                            onUpdateSettings(settings.copy(appLanguage = "System"))
                                        } else {
                                            val code = com.example.ui.LanguageManager.getLanguageCode(languageOption)
                                            com.example.ui.LanguageManager.cambiarIdioma(code)
                                            onUpdateSettings(settings.copy(appLanguage = languageOption))
                                        }
                                        expandedLang = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Global Theme Selection: Refined Swatches Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Translations.get(lang, "global_theme"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColorPrimary
                    )
                    Text(
                        text = "${AppTheme.values().size} ${Translations.get(lang, "styles_count")}",
                        fontSize = 12.sp,
                        color = textColorMuted
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Grid of Theme Swatch Cards
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppTheme.values().forEach { theme ->
                        val isSelected = settings.selectedTheme == theme
                        val animatedBorderColor by animateColorAsState(
                            targetValue = if (isSelected) theme.primaryColor else cardBorder,
                            animationSpec = tween(250)
                        )
                        val animatedBgColor by animateColorAsState(
                            targetValue = if (isSelected) theme.primaryColor.copy(alpha = if (isDark) 0.16f else 0.12f) else formatPillBg,
                            animationSpec = tween(250)
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(animatedBgColor)
                                .border(1.dp, animatedBorderColor, RoundedCornerShape(12.dp))
                                .clickable { onUpdateSettings(settings.copy(selectedTheme = theme)) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = theme.displayName,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) (if (isDark) Color.White else primaryColor) else textColorPrimary
                            )

                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = theme.primaryColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Typography Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, cardBorder, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FontDownload,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Translations.get(lang, "typography_title"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColorPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = Translations.get(lang, "current_font"), fontSize = 12.sp, color = textColorMuted)
                        Text(
                            text = settings.fontFamilyName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColorPrimary
                        )
                    }

                    if (settings.fontFamilyName != "System Font (Default)") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33FF4D4D))
                                .border(1.dp, Color(0x66FF4D4D), RoundedCornerShape(10.dp))
                                .clickable { onUpdateSettings(settings.copy(fontFamilyName = "System Font (Default)")) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(Translations.get(lang, "reset"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B6B))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fast Font Presets
                Text(text = Translations.get(lang, "popular_fonts"), fontSize = 12.sp, color = textColorSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf("System Font (Default)", "Outfit", "Poppins", "Montserrat", "Inter", "Playfair Display", "Roboto", "Lato", "Oswald", "Raleway", "Pacifico")
                    presets.forEach { fontName ->
                        val isCur = settings.fontFamilyName == fontName
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCur) primaryColor.copy(alpha = 0.25f) else formatPillBg)
                                .border(1.dp, if (isCur) primaryColor else cardBorder, RoundedCornerShape(10.dp))
                                .clickable { onUpdateSettings(settings.copy(fontFamilyName = fontName)) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (fontName.contains("(")) Translations.get(lang, "system_default") else fontName,
                                fontSize = 12.sp,
                                fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCur) primaryColor else textColorPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom font input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = fontInput,
                        onValueChange = { fontInput = it },
                        placeholder = { Text(Translations.get(lang, "font_placeholder"), color = textColorMuted, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isDark) Color(0x1AFFFFFF) else Color(0x08000000),
                            unfocusedContainerColor = if (isDark) Color(0x14FFFFFF) else Color(0x05000000),
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = textColorPrimary,
                            unfocusedTextColor = textColorPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (fontInput.isNotBlank()) primaryColor else if (isDark) Color(0x33FFFFFF) else Color(0x22000000))
                            .clickable(enabled = fontInput.isNotBlank()) {
                                onUpdateSettings(settings.copy(fontFamilyName = fontInput.trim()))
                                fontInput = ""
                            }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = Translations.get(lang, "apply"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (fontInput.isNotBlank()) Color.Black else textColorMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Music Library Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, cardBorder, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = Translations.get(lang, "music_library"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColorPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = Translations.get(lang, "rescan_desc"),
                    fontSize = 12.sp,
                    color = textColorMuted
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(primaryColor, primaryColor.copy(alpha = 0.75f))
                            )
                        )
                        .clickable(enabled = !isRescanning && !isRepairingCovers) { onRescanAudio() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRescanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = Translations.get(lang, "rescanning_library"),
                                color = Color.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = Translations.get(lang, "rescan_now"),
                                color = Color.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (onRepairCovers != null) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color(0x22FFFFFF) else Color(0x14000000))
                            .border(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0x22000000), RoundedCornerShape(14.dp))
                            .clickable(enabled = !isRescanning && !isRepairingCovers) { onRepairCovers() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isRepairingCovers) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = primaryColor,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translations.get(lang, "repairing_covers_toast"),
                                    color = textColorPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translations.get(lang, "repair_covers_now"),
                                    color = textColorPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (onClearCache != null) {
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = Translations.get(lang, "clear_cache_desc"),
                        fontSize = 12.sp,
                        color = textColorMuted
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color(0x22EF4444) else Color(0x12EF4444))
                            .border(1.dp, if (isDark) Color(0x44EF4444) else Color(0x30EF4444), RoundedCornerShape(14.dp))
                            .clickable(enabled = !isRescanning && !isRepairingCovers && !isClearingCache) { onClearCache() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isClearingCache) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color(0xFFEF4444),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translations.get(lang, "clearing_cache_toast"),
                                    color = Color(0xFFEF4444),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translations.get(lang, "clear_cache_now"),
                                    color = if (isDark) Color(0xFFFF6B6B) else Color(0xFFDC2626),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (onStartTour != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, cardBorder, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(iconPillBg)
                                .border(1.dp, iconPillBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Translations.get(lang, "quick_guide_title"),
                                color = textColorPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = Translations.get(lang, "quick_guide_subtitle"),
                                color = textColorSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(primaryColor, primaryColor.copy(alpha = 0.8f))
                                )
                            )
                            .clickable { onStartTour() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = Translations.get(lang, "start_tour"),
                                color = Color.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}
