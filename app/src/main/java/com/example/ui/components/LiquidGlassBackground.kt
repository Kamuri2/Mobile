package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.ArtworkExtractor
import com.example.model.Track
import com.example.ui.theme.LiquidDarkBg
import com.example.ui.theme.PurpleAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Authentic album artwork background degradation:
 * - 100% OPAQUE base so no underlying content from other screens ever bleeds through.
 * - Naturally degrades the actual playing song's artwork image with gaussian blur.
 * - Smooth vertical gradient transition fading the artwork colors into the solid background.
 * - If no cover image exists, uses an opaque dominant color gradation without fake simulated orbs.
 */
@Composable
fun LiquidGlassBackground(
    isPlaying: Boolean,
    currentTrack: Track? = null,
    modifier: Modifier = Modifier,
    primaryGlowColor: Color = MaterialTheme.colorScheme.primary,
    secondaryGlowColor: Color = PurpleAccent,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val solidBgColor = if (isLight) Color(0xFFF7F8FA) else LiquidDarkBg

    // Load actual song cover artwork bitmap directly
    val artBitmap by produceState<Bitmap?>(
        initialValue = currentTrack?.let { ArtworkExtractor.getCachedBitmap(it) },
        key1 = currentTrack?.id
    ) {
        if (currentTrack != null) {
            value = withContext(Dispatchers.IO) {
                ArtworkExtractor.loadArtworkBitmap(context, currentTrack, 400)
            }
        } else {
            value = null
        }
    }

    // 1. Root Box is strictly 100% OPAQUE: ensures nothing underneath can ever be seen
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(solidBgColor)
    ) {
        if (artBitmap != null) {
            // 2. Real image of the song blurred in the background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                Image(
                    bitmap = artBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.35f
                            scaleY = 1.35f
                        }
                        .blur(48.dp)
                )
            }

            // 3. Smooth vertical degradation over the song's image into the solid background
            val veilGradient = if (isLight) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.20f),
                        solidBgColor.copy(alpha = 0.50f),
                        solidBgColor.copy(alpha = 0.88f),
                        solidBgColor
                    )
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.30f),
                        Color.Black.copy(alpha = 0.55f),
                        solidBgColor.copy(alpha = 0.88f),
                        solidBgColor
                    )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(veilGradient)
            )
        } else {
            // 4. When no artwork exists on track: 100% opaque color gradation (no fake orbs, no bleed-through)
            val fallbackGradient = if (isLight) {
                Brush.verticalGradient(
                    colors = listOf(
                        primaryGlowColor.copy(alpha = 0.22f).compositeOver(solidBgColor),
                        solidBgColor.copy(alpha = 0.80f).compositeOver(solidBgColor),
                        solidBgColor
                    )
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        primaryGlowColor.copy(alpha = 0.35f).compositeOver(solidBgColor),
                        solidBgColor.copy(alpha = 0.85f).compositeOver(solidBgColor),
                        solidBgColor
                    )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fallbackGradient)
            )
        }

        content()
    }
}
