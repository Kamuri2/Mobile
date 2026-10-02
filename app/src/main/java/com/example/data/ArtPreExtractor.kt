package com.example.data

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

                val seenAlbums = mutableSetOf<Long>()

                // 1. If tracks provided, process them directly
                if (!tracks.isNullOrEmpty()) {
                    for (track in tracks) {
                        val albumKey = if (track.album.isNotBlank()) track.album.lowercase().trim().hashCode().toLong() else -1L
                        val albumFile = if (albumKey != -1L) File(artDir, "album_$albumKey.jpg") else null
                        val trackFile = File(artDir, "track_${track.id}.jpg")

                        if ((albumFile != null && albumFile.exists() && albumFile.length() > 0) ||
                            (trackFile.exists() && trackFile.length() > 0)
                        ) {
                            if (albumKey != -1L) seenAlbums.add(albumKey)
                            continue
                        }

                        if (albumKey != -1L && albumKey in seenAlbums) continue

                        if (track.path.isNotBlank()) {
                            tryExtractFromPath(track.path, albumFile, trackFile, albumKey, seenAlbums)
                        }
                    }
                }

                // 2. Scan MediaStore for any other audio tracks
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.ALBUM_ID
                )

                val cursor = try {
                    context.contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        null
                    )
                } catch (e: Exception) {
                    null
                }

                cursor?.use { c ->
                    val idCol = c.getColumnIndex(MediaStore.Audio.Media._ID)
                    val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val albumCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)

                    while (c.moveToNext()) {
                        val trackId = if (idCol != -1) c.getLong(idCol) else continue
                        val albumId = if (albumCol != -1) c.getLong(albumCol) else -1L
                        val dataPath = if (dataCol != -1) c.getString(dataCol) else null

                        if (dataPath.isNullOrBlank()) continue

                        val albumFile = if (albumId > 0) File(artDir, "album_$albumId.jpg") else null
                        val trackFile = File(artDir, "track_$trackId.jpg")

                        if ((albumFile != null && albumFile.exists() && albumFile.length() > 0) ||
                            (trackFile.exists() && trackFile.length() > 0)
                        ) {
                            if (albumId > 0) seenAlbums.add(albumId)
                            continue
                        }

                        if (albumId > 0 && albumId in seenAlbums) continue

                        tryExtractFromPath(dataPath, albumFile, trackFile, albumId, seenAlbums)
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
        path: String,
        albumFile: File?,
        trackFile: File,
        albumId: Long,
        seenAlbums: MutableSet<Long>
    ) {
        try {
            val audioFile = File(path)
            if (!audioFile.exists() || !audioFile.canRead()) return

            val audio = AudioFileIO.read(audioFile)
            val pictures = audio.tag?.artworkList
            val best = pictures?.maxByOrNull { it.binaryData?.size ?: 0 }

            if (best?.binaryData != null && best.binaryData.isNotEmpty()) {
                val data = best.binaryData
                if (albumFile != null) {
                    albumFile.writeBytes(data)
                    seenAlbums.add(albumId)
                }
                trackFile.writeBytes(data)
            }
        } catch (_: Exception) {}
    }
}
