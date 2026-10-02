package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import com.example.data.AlbumArtExtractor
import com.example.data.ArtworkExtractor
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-definition Player Album Art con CERO flash entre canciones:
 * 1. Comprueba inmediatamente en disco local si la carátula ya fue pre-extraída (0ms).
 * 2. Si aún no está lista, mantiene la carátula anterior visible mediante Crossfade(300ms).
 * 3. Nunca muestra placeholders en blanco o iconos parpadeantes durante el cambio de canción.
 */
@Composable
fun PlayerAlbumArt(
    track: Track,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val extractor = remember { AlbumArtExtractor(context) }

    // 1. Revisión síncrona inmediata en caché de disco (0 ms de latencia) para ESTA pista
    val initialFile = remember(track.id) { extractor.getExistingArtFile(track) }

    val artFile by produceState<File?>(initialValue = initialFile, key1 = track.id) {
        value = initialFile
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                extractor.getHighResArt(track)
            }
        }
    }

    Crossfade(
        targetState = artFile,
        animationSpec = tween(300),
        modifier = modifier
    ) { file ->
        if (file != null && file.exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .size(CoilSize.ORIGINAL)
                    .crossfade(200)
                    .build(),
                contentDescription = "Portada",
                contentScale = contentScale,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            FallbackMusicIcon(modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Versión de PlayerAlbumArt para archivo directo File
 */
@Composable
fun PlayerAlbumArt(
    audioFile: File,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val extractor = remember { AlbumArtExtractor(context) }

    val initialFile = remember(audioFile.absolutePath) { extractor.getExistingArtFile(audioFile) }

    val artFile by produceState<File?>(initialValue = initialFile, key1 = audioFile.absolutePath) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                extractor.getHighResArt(audioFile)
            }
        }
    }

    val model: Any = artFile ?: MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(model)
            .size(CoilSize.ORIGINAL)
            .crossfade(300)
            .build(),
        contentDescription = "Portada",
        contentScale = contentScale,
        filterQuality = FilterQuality.High,
        modifier = modifier,
        error = {
            FallbackMusicIcon(modifier = Modifier.fillMaxSize())
        },
        loading = {
            FallbackMusicIcon(modifier = Modifier.fillMaxSize())
        }
    )
}

/**
 * Standard Coil Album Art component with crossfade and view-size downsampling.
 */
@Composable
fun AlbumArt(
    uri: Uri,
    size: Dp = 300.dp,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(uri)
            .size(CoilSize.ORIGINAL)
            .crossfade(true)
            .build(),
        contentDescription = "Portada",
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.High,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
    )
}

/**
 * TrackImage optimizado:
 * Lee de forma inmediata la carátula pre-extraída de album_art_hd o MediaStore URI
 * sin llamadas repetitivas o ruidosas a MediaMetadataRetriever.
 */
@Composable
fun TrackImage(
    track: Track,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val extractor = remember { AlbumArtExtractor(context) }
    val initialFile = remember(track.id) { extractor.getExistingArtFile(track) }
    val artFile by produceState<File?>(initialValue = initialFile, key1 = track.id) {
        value = initialFile
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                extractor.getHighResArt(track)
            }
        }
    }

    if (artFile != null && artFile!!.exists()) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(artFile)
                .crossfade(200)
                .build(),
            contentDescription = track.title,
            contentScale = contentScale,
            modifier = modifier,
            error = {
                FallbackMusicIcon(modifier = Modifier.fillMaxSize())
            }
        )
    } else {
        FallbackMusicIcon(modifier = modifier)
    }
}

@Composable
private fun FallbackOrExtractImage(
    track: Track,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = ArtworkExtractor.getCachedBitmap(track), key1 = track.id) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                ArtworkExtractor.loadArtworkBitmap(context, track, 600)
            }
        }
    }

    if (bitmap != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(bitmap)
                .crossfade(true)
                .build(),
            contentDescription = track.title,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        FallbackMusicIcon(modifier = modifier)
    }
}

@Composable
fun FallbackMusicIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0x1AFFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxSize(0.4f)
        )
    }
}
