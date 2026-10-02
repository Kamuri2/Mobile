package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.OrangeGlow
import com.example.ui.theme.PurpleAccent
import kotlin.math.sin

@Composable
fun LiquidEqualizerView(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    barCount: Int = 24
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq_waves")
    val animFactor by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "eq_factor"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val width = size.width
        val canvasH = size.height
        val barWidth = (width / (barCount * 1.6f)).coerceAtLeast(4f)
        val spacing = (width - (barCount * barWidth)) / (barCount + 1)

        for (i in 0 until barCount) {
            val phase = i * 0.45f
            val baseHeight = if (isPlaying) {
                val wave = (sin(animFactor + phase) + 1f) / 2f
                (wave * 0.75f + 0.15f) * canvasH
            } else {
                canvasH * 0.12f
            }

            val x = spacing + i * (barWidth + spacing)
            val y = canvasH - baseHeight

            val barColor = when (i % 4) {
                0 -> primaryColor
                1 -> OrangeGlow
                2 -> PurpleAccent
                else -> AmberGlow
            }

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        barColor,
                        barColor.copy(alpha = 0.35f)
                    )
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, baseHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
