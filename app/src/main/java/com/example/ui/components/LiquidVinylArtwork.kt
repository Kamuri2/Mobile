package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.components.TrackImage
import com.example.model.Track
import com.example.ui.theme.AmberGlow

import com.example.ui.theme.OrangeGlow
import com.example.ui.theme.PurpleAccent

@Composable
fun LiquidVinylArtwork(
    track: Track?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotation")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val currentRotation = if (isPlaying) rotationAngle else 0f

    val artworkBitmap: androidx.compose.ui.graphics.ImageBitmap? = null

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 24.dp,
                shape = CircleShape,
                ambientColor = MaterialTheme.colorScheme.primary,
                spotColor = PurpleAccent
            ),
        contentAlignment = Alignment.Center
    ) {
        // Outer Glass Halo / Vinyl Base Disk
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(currentRotation)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E140F),
                            Color(0xFF140D09),
                            Color(0xFF0F0805)
                        )
                    )
                )
                .border(2.dp, Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), PurpleAccent.copy(alpha = 0.6f))), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Concentric Vinyl Micro-Grooves
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.toPx() / 2f
                val strokeW = 1.2f
                val grooveColor = Color.White.copy(alpha = 0.08f)

                for (r in listOf(0.92f, 0.85f, 0.78f, 0.71f, 0.64f, 0.57f)) {
                    drawCircle(
                        color = grooveColor,
                        radius = radius * r,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
                    )
                }
            }

            // Inner Album Cover Artwork
            Box(
                modifier = Modifier
                    .size(size * 0.62f)
                    .clip(CircleShape)
                    .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when {
                    artworkBitmap != null -> {
                        Image(
                            bitmap = artworkBitmap,
                            contentDescription = track?.album ?: "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    track != null -> {
                        TrackImage(
                            track = track,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        // Default High Density Album Art Fallback Gradient
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            PurpleAccent.copy(alpha = 0.9f),
                                            AmberGlow.copy(alpha = 0.8f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                }
            }

            // Vinyl Center Spindle Hole
            Box(
                modifier = Modifier
                    .size(size * 0.12f)
                    .clip(CircleShape)
                    .background(Color(0xFF0F0805))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            )

            // Gloss Reflection Glare Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.10f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.toPx(), size.toPx())
                    ),
                    radius = size.toPx() / 2f
                )
            }
        }
    }
}

