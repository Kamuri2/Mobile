package com.example.data

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jaudiotagger.audio.AudioFileIO
import java.io.File

/**
 * Pre-extracción en segundo plano de carátulas en alta resolución
 * a la carpeta persistente context.cacheDir/album_art_hd.
 * 
 * Permite que al reproducir o cambiar de canción, la imagen se lea
 * en ~1-2ms desde el almacenamiento local sin latencia y sin bloqueos de UI.
 */
object ArtPreExtractor {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    fun startPreExtraction(context: Context, tracks: List<Track>? = null) {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                val artDir = File(context.cacheDir, "album_art_hd")
                if (!artDir.exists()) artDir.mkdirs()

                val seenAlbums = mutableSetOf<String>()

                // Only process up to 20 immediate tracks for smooth start
                if (!tracks.isNullOrEmpty()) {
                    val batch = tracks.take(20)
                    for (track in batch) {
                        if (!isActive) break
                        val albumKey = AlbumArtExtractor.getAlbumKey(track)
                        val albumFile = if (albumKey != null) File(artDir, "${albumKey}.jpg") else null
                        val trackFile = File(artDir, "track_${track.id}.jpg")

                        if ((albumFile != null && albumFile.exists() && albumFile.length() > 500) ||
                            (trackFile.exists() && trackFile.length() > 500)
                        ) {
                            if (albumKey != null) seenAlbums.add(albumKey)
                            continue
                        }

                        if (albumKey != null && albumKey in seenAlbums) continue

                        if (track.path.isNotBlank()) {
                            tryExtractFromPath(context, track.path, albumFile, trackFile, albumKey, seenAlbums)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("ArtPreExtractor", "Pre-extraction note: ${e.message}")
            } finally {
                isRunning = false
            }
        }
    }

    private fun tryExtractFromPath(
        context: Context,
        path: String,
        albumFile: File?,
        trackFile: File,
        albumKey: String?,
        seenAlbums: MutableSet<String>
    ) {
        try {
            val audioFile = File(path)
            if (!audioFile.exists() || !audioFile.canRead()) return

            val artFile = AlbumArtExtractor(context).getHighResArt(audioFile)
            if (artFile != null && AlbumArtExtractor.isValidImageFile(artFile)) {
                if (albumFile != null && !albumFile.exists()) {
                    artFile.copyTo(albumFile, overwrite = false)
                    if (albumKey != null) {
                        seenAlbums.add(albumKey)
                        AlbumArtExtractor.linkArtworkToAlbum(albumKey, albumFile)
                    }
                }
                if (!trackFile.exists()) {
                    artFile.copyTo(trackFile, overwrite = false)
                }
            }
        } catch (_: Exception) {}
    }
}
