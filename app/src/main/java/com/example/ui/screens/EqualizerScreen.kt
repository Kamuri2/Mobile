package com.example.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.EqPreset
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidEqualizerView
import com.example.ui.theme.GlassTextMuted
import com.example.ui.theme.GlassTextPrimary
import com.example.ui.theme.GlassTextSecondary

import com.example.ui.theme.OrangeGlow
import com.example.ui.theme.PurpleAccent

@Composable
fun EqualizerScreen(
    isPlaying: Boolean,
    selectedPreset: EqPreset,
    volume: Float,
    onEqPresetSelect: (EqPreset) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Top Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Equalizer,
                contentDescription = null,
                tint = PurpleAccent,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column {
                Text(
                    text = "Ecualizador Liquid Audio",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlassTextPrimary
                )
                Text(
                    text = "Ajusta perfiles de sonido y efectos de cristal",
                    fontSize = 12.sp,
                    color = GlassTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Visualizer Canvas
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0x1AFFFFFF),
            glowColor = PurpleAccent
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Frecuencia de Salida",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = selectedPreset.displayName,
                        fontSize = 12.sp,
                        color = GlassTextMuted
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LiquidEqualizerView(isPlaying = isPlaying, height = 54.dp, barCount = 28)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Master Volume Glass Control
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0x18FFFFFF)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Volumen Master: ${(volume * 100).toInt()}%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GlassTextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Equalizer Presets
        Text(
            text = "Perfiles de Sonido Prestablecidos",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = GlassTextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        EqPreset.values().forEach { preset ->
            val isSelected = preset == selectedPreset
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = if (isSelected) Color(0x357C3AED) else Color(0x12FFFFFF),
                borderColor = if (isSelected) PurpleAccent else Color.White.copy(alpha = 0.15f),
                onClick = { onEqPresetSelect(preset) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onEqPresetSelect(preset) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = PurpleAccent,
                                unselectedColor = Color.White.copy(alpha = 0.4f)
                            )
                        )
                        Text(
                            text = preset.displayName,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) PurpleAccent else GlassTextPrimary
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (isSelected) PurpleAccent else GlassTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

