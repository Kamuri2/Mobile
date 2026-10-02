package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import android.graphics.BitmapFactory
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.components.TrackImage
import com.example.model.Track
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.LiquidDarkBg

import com.example.ui.theme.PurpleAccent
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LiquidGlassBackground(
    isPlaying: Boolean,
    currentTrack: Track? = null,
    modifier: Modifier = Modifier,
    primaryGlowColor: Color = MaterialTheme.colorScheme.primary,
    secondaryGlowColor: Color = PurpleAccent,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_bg_anim")

    val pulseSpeed = if (isPlaying) 5000 else 12000

    val orb1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(pulseSpeed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb1"
    )

    val orb2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween((pulseSpeed * 1.3).toInt(), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb2"
    )

    val orbScale by infiniteTransition.animateFloat(
        initialValue = 1.15f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_scale"
    )

    val bgOffsetX = sin(orb1Offset) * 25f
    val bgOffsetY = cos(orb2Offset) * 25f

    val artworkBitmap: androidx.compose.ui.graphics.ImageBitmap? = null

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val baseBgColor = if (isLight) Color(0xFFF7F7F9) else LiquidDarkBg

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBgColor)
    ) {
        // Blurred Album Artwork in Motion
        if (currentTrack != null) {
            when {
                artworkBitmap != null -> {
                    Image(
                        bitmap = artworkBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = orbScale
                                scaleY = orbScale
                                translationX = bgOffsetX
                                translationY = bgOffsetY
                            }
                            .blur(75.dp)
                    )
                }
                true -> {
                    TrackImage(
                        track = currentTrack,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = orbScale
                                scaleY = orbScale
                                translationX = bgOffsetX
                                translationY = bgOffsetY
                            }
                            .blur(75.dp)
                    )
                }
            }
        }

        // Dynamic Fluid Canvas with Orbiting Ambient Glow Orbs over blurred cover art
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Orb 1: Electric Glow
            val orb1X = width * 0.3f + cos(orb1Offset) * (width * 0.22f)
            val orb1Y = height * 0.35f + sin(orb1Offset) * (height * 0.18f)
            val orb1Radius = (width * 0.55f) * (orbScale / 1.15f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlowColor.copy(alpha = 0.28f),
                        primaryGlowColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(orb1X, orb1Y),
                    radius = orb1Radius
                ),
                center = Offset(orb1X, orb1Y),
                radius = orb1Radius
            )

            // Orb 2: Ambient Accent Glow
            val orb2X = width * 0.7f + sin(orb2Offset) * (width * 0.25f)
            val orb2Y = height * 0.65f + cos(orb2Offset) * (height * 0.2f)
            val orb2Radius = (width * 0.6f) * (2f - (orbScale / 1.15f))

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondaryGlowColor.copy(alpha = 0.25f),
                        secondaryGlowColor.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(orb2X, orb2Y),
                    radius = orb2Radius
                ),
                center = Offset(orb2X, orb2Y),
                radius = orb2Radius
            )

            // Orb 3: Deep Warm Amber Glow
            val orb3X = width * 0.5f + sin(orb1Offset * 0.7f) * (width * 0.15f)
            val orb3Y = height * 0.85f + cos(orb2Offset * 0.8f) * (height * 0.12f)
            val orb3Radius = width * 0.7f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AmberGlow.copy(alpha = 0.18f),
                        PurpleAccent.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(orb3X, orb3Y),
                    radius = orb3Radius
                ),
                center = Offset(orb3X, orb3Y),
                radius = orb3Radius
            )
        }

        // Adaptive Scrim Gradient for Readability (frosted light in light mode, deep dark in dark mode)
        val scrimColors = if (isLight) {
            listOf(
                Color.White.copy(alpha = 0.40f),
                Color.White.copy(alpha = 0.65f),
                Color.White.copy(alpha = 0.88f)
            )
        } else {
            listOf(
                Color.Black.copy(alpha = 0.50f),
                Color.Black.copy(alpha = 0.65f),
                Color.Black.copy(alpha = 0.85f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = scrimColors
                    )
                )
        )

        content()
    }
}
