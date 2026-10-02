package com.example.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.model.Track
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Extrae el artwork a resolución original directamente del archivo.
 * NO usa MediaStore (que downscalea a 300-512px).
 * Mantiene caché en disco con clave hash/tamaño para 0 I/O en lecturas repetidas.
 */
class AlbumArtExtractor(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "album_art_hd")
    private val noArtworkCache = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        } else {
            // Eliminar archivos antiguos de caché agrupados por nombre de álbum que causaban colisiones
            try {
                cacheDir.listFiles { _, name -> name.startsWith("album_") }?.forEach { it.delete() }
            } catch (_: Exception) {}
        }
        try {
            val thumbDir = File(context.cacheDir, "thumbnails")
            if (thumbDir.exists()) {
                thumbDir.deleteRecursively()
            }
        } catch (_: Exception) {}
    }

    /**
     * Comprobación síncrona inmediata en caché de disco (0 ms de latencia).
     * Cada canción tiene su propio archivo único e independiente.
     */
    fun getExistingArtFile(track: Track): File? {
        val trackFile = File(cacheDir, "track_${track.id}.jpg")
        if (trackFile.exists() && trackFile.length() > 0) return trackFile

        if (track.path.isNotBlank()) {
            val f = File(track.path)
            val fileCache = File(cacheDir, "${f.nameWithoutExtension}_${f.length()}.jpg")
            if (fileCache.exists() && fileCache.length() > 0) return fileCache
        }
        return null
    }

    fun getExistingArtFile(audioFile: File): File? {
        val cacheFile = File(cacheDir, "${audioFile.nameWithoutExtension}_${audioFile.length()}.jpg")
        if (cacheFile.exists() && cacheFile.length() > 0) return cacheFile
        return null
    }

    /**
     * Devuelve un File con la portada a máxima resolución disponible para un archivo de audio.
     * Si ya está cacheada, devuelve el archivo cacheado (0 IO).
     */
    fun getHighResArt(audioFile: File): File? {
        if (!audioFile.exists() || !audioFile.canRead()) return null

        val cacheFile = File(cacheDir, "${audioFile.nameWithoutExtension}_${audioFile.length()}.jpg")
        if (cacheFile.exists() && cacheFile.length() > 0) return cacheFile

        if (noArtworkCache.contains(audioFile.absolutePath)) return null

        return try {
            val bytes = extractOriginalArtworkBytes(audioFile)
            if (bytes == null) {
                noArtworkCache.add(audioFile.absolutePath)
                return null
            }

            cacheDir.mkdirs()
            cacheFile.writeBytes(bytes)
            cacheFile
        } catch (e: Exception) {
            Log.w("AlbumArtExtractor", "Fallo extrayendo artwork de ${audioFile.name}: ${e.message}")
            null
        }
    }

    /**
     * Versión para Track: extrae EXCLUSIVAMENTE la portada incrustada en el archivo de ESTA pista.
     * NUNCA comparte portadas entre canciones ni usa índices compartidos de MediaStore.
     */
    fun getHighResArt(track: Track): File? {
        // 1. Revisión síncrona en disco (0 ms)
        getExistingArtFile(track)?.let { return it }

        if (track.path.isNotBlank()) {
            val file = File(track.path)
            if (file.exists() && file.canRead()) {
                val art = getHighResArt(file)
                if (art != null) {
                    val trackFile = File(cacheDir, "track_${track.id}.jpg")
                    try {
                        if (!trackFile.exists()) art.copyTo(trackFile, overwrite = false)
                    } catch (_: Exception) {}
                    return art
                }
            }
        }

        // Intento con ContentUri si no hay ruta de archivo directa (extrayendo imagen incrustada de ESTE archivo)
        if (track.contentUri != Uri.EMPTY) {
            val cacheFile = File(cacheDir, "track_${track.id}.jpg")
            if (cacheFile.exists() && cacheFile.length() > 0) return cacheFile
            val uriKey = track.contentUri.toString()
            if (noArtworkCache.contains(uriKey)) return null

            return try {
                var bytes: ByteArray? = null
                val mmr = MediaMetadataRetriever()
                try {
                    context.contentResolver.openFileDescriptor(track.contentUri, "r")?.use { pfd ->
                        mmr.setDataSource(pfd.fileDescriptor)
                        bytes = mmr.embeddedPicture
                    }
                } finally {
                    try { mmr.release() } catch (ignored: Exception) {}
                }

                if (bytes != null && bytes!!.isNotEmpty()) {
                    cacheDir.mkdirs()
                    cacheFile.writeBytes(bytes!!)
                    cacheFile
                } else {
                    noArtworkCache.add(uriKey)
                    null
                }
            } catch (e: Exception) {
                noArtworkCache.add(uriKey)
                null
            }
        }

        return null
    }

    /**
     * Versión para FLAC (el artwork va en PICTURE block, no en Vorbis Comments).
     */
    fun getFlacArt(audioFile: File): File? {
        val cacheFile = File(cacheDir, "${audioFile.nameWithoutExtension}_${audioFile.length()}.png")
        if (cacheFile.exists() && cacheFile.length() > 0) return cacheFile

        return try {
            val bytes = extractOriginalArtworkBytes(audioFile) ?: return null

            cacheDir.mkdirs()
            cacheFile.writeBytes(bytes)
            cacheFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extrae los bytes puros sin downsamplear usando JAudioTagger -> MediaMetadataRetriever -> Sidecar images.
     */
    private fun extractOriginalArtworkBytes(audioFile: File): ByteArray? {
        var fileTagChecked = false
        // 1. JAudioTagger (mantiene resolución nativa de etiquetas ID3 APIC / FLAC PICTURE / MP4 covr)
        try {
            val audio = AudioFileIO.read(audioFile)
            val tag = audio.tag
            if (tag != null) {
                fileTagChecked = true
                val pictures = tag.artworkList
                if (!pictures.isNullOrEmpty()) {
                    val best = pictures
                        .filter { it.binaryData != null && it.binaryData.isNotEmpty() }
                        .maxByOrNull { it.binaryData.size }
                    if (best?.binaryData != null && best.binaryData.isNotEmpty()) {
                        return best.binaryData
                    }
                }

                // Check Vorbis METADATA_BLOCK_PICTURE en FLAC/Ogg
                val picBase64 = tag.getFirst("METADATA_BLOCK_PICTURE")
                if (!picBase64.isNullOrBlank()) {
                    val decoded = decodeVorbisPictureBlock(picBase64)
                    if (decoded != null && decoded.isNotEmpty()) {
                        return decoded
                    }
                }

                // Check COVERART base64
                val coverBase64 = tag.getFirst("COVERART") ?: tag.getFirst("COVER_ART")
                if (!coverBase64.isNullOrBlank()) {
                    try {
                        val bytes = Base64.decode(coverBase64.trim(), Base64.DEFAULT)
                        if (bytes.isNotEmpty()) return bytes
                    } catch (ignored: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.d("AlbumArtExtractor", "JAudioTagger no encontró arte embebido: ${e.message}")
        }

        // 2. MediaMetadataRetriever nativo a resolución completa
        try {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(audioFile.absolutePath)
                val rawBytes = mmr.embeddedPicture
                if (rawBytes != null && rawBytes.isNotEmpty()) {
                    return rawBytes
                }
            } finally {
                try { mmr.release() } catch (ignored: Exception) {}
            }
        } catch (e: Exception) {
            Log.d("AlbumArtExtractor", "MMR falló para ${audioFile.name}: ${e.message}")
        }

        // 3. Sidecar cover files en el mismo directorio (SOLO si coincide exactamente con el nombre del archivo de la canción)
        try {
            val parent = audioFile.parentFile
            if (parent != null && parent.exists() && parent.isDirectory) {
                val candidateNames = listOf(
                    "${audioFile.nameWithoutExtension}.jpg",
                    "${audioFile.nameWithoutExtension}.png",
                    "${audioFile.nameWithoutExtension}.jpeg",
                    "${audioFile.nameWithoutExtension}.webp"
                )
                for (name in candidateNames) {
                    val sidecar = File(parent, name)
                    if (sidecar.exists() && sidecar.canRead() && sidecar.length() > 500) {
                        return sidecar.readBytes()
                    }
                }
            }
        } catch (ignored: Exception) {}

        return null
    }

    private fun decodeVorbisPictureBlock(base64: String): ByteArray? {
        return try {
            val raw = Base64.decode(base64.trim(), Base64.DEFAULT)
            if (raw.size < 32) return null
            val buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN)
            buf.getInt() // Picture type
            val mimeLen = buf.getInt()
            if (mimeLen < 0 || mimeLen > buf.remaining()) return null
            buf.position(buf.position() + mimeLen)
            val descLen = buf.getInt()
            if (descLen < 0 || descLen > buf.remaining()) return null
            buf.position(buf.position() + descLen)
            buf.getInt() // Width
            buf.getInt() // Height
            buf.getInt() // Color depth
            buf.getInt() // Colors used
            val dataLen = buf.getInt()
            if (dataLen <= 0 || dataLen > buf.remaining()) return null
            val imgBytes = ByteArray(dataLen)
            buf.get(imgBytes)
            imgBytes
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Limpieza periódica de artwork viejo en caché (elimina imágenes de más de 7 días).
     */
    fun purgeOldArt() {
        try {
            val cutoff = System.currentTimeMillis() - 7 * 24 * 3600 * 1000L
            cacheDir.listFiles()?.forEach {
                if (it.lastModified() < cutoff) it.delete()
            }
        } catch (e: Exception) {
            Log.w("AlbumArtExtractor", "Error purgando caché: ${e.message}")
        }
    }

    companion object {
        fun hasEmbeddedArtworkHeader(inputStream: java.io.InputStream): Boolean {
            return try {
                val header = ByteArray(256 * 1024)
                var bytesRead = 0
                while (bytesRead < header.size) {
                    val r = inputStream.read(header, bytesRead, header.size - bytesRead)
                    if (r <= 0) break
                    bytesRead += r
                }
                if (bytesRead < 4) return false

                // ID3v2 (MP3, AAC)
                if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                    val apic = "APIC".toByteArray(Charsets.US_ASCII)
                    val pic = "PIC".toByteArray(Charsets.US_ASCII)
                    return indexOfBytes(header, bytesRead, apic) != -1 || indexOfBytes(header, bytesRead, pic) != -1
                }

                // FLAC
                if (header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() && header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte()) {
                    var offset = 4
                    while (offset + 4 <= bytesRead) {
                        val headerByte = header[offset].toInt() and 0xFF
                        val isLast = (headerByte and 0x80) != 0
                        val blockType = headerByte and 0x7F
                        if (blockType == 6) return true
                        val length = ((header[offset + 1].toInt() and 0xFF) shl 16) or
                                     ((header[offset + 2].toInt() and 0xFF) shl 8) or
                                     (header[offset + 3].toInt() and 0xFF)
                        offset += 4 + length
                        if (isLast) break
                    }
                    return false
                }

                // MP4 / M4A (contains 'covr')
                val covr = "covr".toByteArray(Charsets.US_ASCII)
                if (indexOfBytes(header, bytesRead, covr) != -1) {
                    return true
                }

                // OGG / Vorbis / Opus
                val oggPic = "METADATA_BLOCK_PICTURE".toByteArray(Charsets.US_ASCII)
                val oggCover = "COVERART".toByteArray(Charsets.US_ASCII)
                if (indexOfBytes(header, bytesRead, oggPic) != -1 || indexOfBytes(header, bytesRead, oggCover) != -1) {
                    return true
                }

                false
            } catch (_: Exception) {
                false
            }
        }

        private fun indexOfBytes(source: ByteArray, sourceLength: Int, target: ByteArray): Int {
            if (target.isEmpty() || sourceLength < target.size) return -1
            val max = sourceLength - target.size
            for (i in 0..max) {
                var found = true
                for (j in target.indices) {
                    if (source[i + j] != target[j]) {
                        found = false
                        break
                    }
                }
                if (found) return i
            }
            return -1
        }
    }
}
