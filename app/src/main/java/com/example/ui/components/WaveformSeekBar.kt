package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Modern Wavy Progress Bar (Barra de progreso con ondas).
 *
 * Implements an undulating, living audio wave for the played portion
 * of the track, transitioning to a clean sleek track line for the remaining duration.
 * Animates fluidly when music is playing and supports seamless dragging and tapping
 * without interrupting audio playback.
 */
@Composable
fun WaveformSeekBar(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    trackId: Long = 0L,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = Color.White.copy(alpha = 0.22f),
    height: Dp = 48.dp,
    barCount: Int = 54
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavy_progress_transition")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavy_anim_phase"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val initialProgress = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onProgressChange(initialProgress)
                    
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                        val currentProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onProgressChange(currentProgress)
                    }
                    onProgressChangeFinished?.invoke()
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val canvasH = size.height
            if (width <= 0 || canvasH <= 0) return@Canvas

            val centerY = canvasH / 2f
            val clampedProgress = progress.coerceIn(0f, 1f)
            val playheadX = clampedProgress * width

            val waveLength = 26.dp.toPx()
            val waveAmplitude = 5.5.dp.toPx()
            val strokeW = 3.5.dp.toPx()

            // 1. Unplayed Track Line (from playhead to the end)
            val unplayedStart = (playheadX + 2.dp.toPx()).coerceAtMost(width)
            if (unplayedStart < width) {
                val trackH = 3.dp.toPx()
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(unplayedStart, centerY - trackH / 2f),
                    size = Size(width - unplayedStart, trackH),
                    cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
                )
            }

            // 2. Active Wavy Progress Line (from 0 to playheadX)
            if (playheadX > 1f) {
                val wavePath = Path()
                wavePath.moveTo(0f, centerY)

                val phase = if (isPlaying) animPhase else 0f
                val stepPx = 2.dp.toPx()
                var x = 0f

                while (x <= playheadX) {
                    val normProgress = (x / playheadX).coerceIn(0f, 1f)
                    // Smooth envelope so wave starts and ends cleanly without sharp breaks
                    val startDamp = (x / 20.dp.toPx()).coerceIn(0f, 1f)
                    val endDamp = ((playheadX - x) / 16.dp.toPx()).coerceIn(0f, 1f)
                    val envelope = startDamp * endDamp

                    val angle = (x / waveLength) * (2 * Math.PI).toFloat() - phase
                    val yOffset = sin(angle) * waveAmplitude * envelope
                    val y = centerY + yOffset

                    wavePath.lineTo(x, y)
                    x += stepPx
                }

                // Ensure the path connects right into the playhead center
                wavePath.lineTo(playheadX, centerY)

                // Soft outer aura / glow for active wavy stream
                drawPath(
                    path = wavePath,
                    color = activeColor.copy(alpha = 0.28f),
                    style = Stroke(
                        width = strokeW + 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Core luminous wavy stroke
                drawPath(
                    path = wavePath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            activeColor.copy(alpha = 0.75f),
                            activeColor,
                            Color.White.copy(alpha = 0.95f)
                        ),
                        startX = 0f,
                        endX = playheadX.coerceAtLeast(1f)
                    ),
                    style = Stroke(
                        width = strokeW,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 3. Playhead Glowing Thumb
            val thumbRadius = 6.dp.toPx()
            val thumbGlowRadius = 11.5.dp.toPx()
            val thumbCenter = Offset(playheadX.coerceIn(thumbRadius, width - thumbRadius), centerY)

            // Outer soft glow halo
            drawCircle(
                color = activeColor.copy(alpha = 0.35f),
                radius = thumbGlowRadius,
                center = thumbCenter
            )

            // Mid accent ring
            drawCircle(
                color = activeColor.copy(alpha = 0.85f),
                radius = thumbRadius + 1.8.dp.toPx(),
                center = thumbCenter
            )

            // Core solid white thumb
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = thumbCenter
            )
        }
    }
}
