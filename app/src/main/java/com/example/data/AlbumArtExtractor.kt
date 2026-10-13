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
class AlbumArtExtractor private constructor(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        @Volatile
        private var INSTANCE: AlbumArtExtractor? = null

        fun getInstance(context: Context): AlbumArtExtractor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlbumArtExtractor(context.applicationContext).also { INSTANCE = it }
            }
        }

        operator fun invoke(context: Context): AlbumArtExtractor = getInstance(context)

        val albumFileCache = java.util.concurrent.ConcurrentHashMap<String, File>()

        fun linkArtworkToAlbum(albumKey: String, file: File) {
            albumFileCache[albumKey] = file
        }

        fun getAlbumKey(track: Track): String? {
            val album = track.album.trim()
            val artist = track.artist.trim()
            val isGeneric = album.isBlank() ||
                    album.equals("<unknown>", ignoreCase = true) ||
                    album.equals("unknown", ignoreCase = true) ||
                    album.equals("unknown album", ignoreCase = true) ||
                    album.equals("desconocido", ignoreCase = true) ||
                    album.equals("álbum desconocido", ignoreCase = true)

            if (!isGeneric) {
                val cleanArtist = artist.lowercase().filter { it.isLetterOrDigit() }
                val cleanAlbum = album.lowercase().filter { it.isLetterOrDigit() }
                return "album_${cleanArtist}_$cleanAlbum"
            }

            // Fallback: Si el álbum es genérico, agrupar por carpeta contenedora
            if (track.path.isNotBlank()) {
                try {
                    val parent = File(track.path).parentFile
                    if (parent != null && parent.exists() && parent.name.isNotBlank()) {
                        val folderName = parent.name.lowercase().filter { it.isLetterOrDigit() }
                        return "folder_${folderName}_${parent.absolutePath.hashCode()}"
                    }
                } catch (_: Exception) {}
            }
            return null
        }

        fun hasEmbeddedArtworkHeader(inputStream: java.io.InputStream): Boolean {
            return true
        }

        fun isValidImageFile(file: File?): Boolean {
            return file != null && file.exists() && file.length() >= 500L
        }

        fun verifyBitmapHeader(file: File?): Boolean {
            if (file == null || !file.exists() || file.length() < 500L) return false
            return try {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
                opts.outWidth > 0 && opts.outHeight > 0
            } catch (_: Exception) {
                false
            }
        }

        fun verifyBitmapFully(file: File?): Boolean {
            if (!isValidImageFile(file)) return false
            return try {
                val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                val bmp = android.graphics.BitmapFactory.decodeFile(file!!.absolutePath, decodeOpts)
                val isValid = bmp != null && bmp.width > 0 && bmp.height > 0
                bmp?.recycle()
                isValid
            } catch (_: Exception) {
                false
            }
        }
    }

    private val cacheDir = File(appContext.cacheDir, "album_art_hd")
    private val noArtworkCache = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun purgeCorruptCache() {
        try {
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("tmp_") || file.length() < 500L) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Limpia completamente los negativos de carátula en memoria y archivos corruptos/incompletos
     * para permitir que una función de re-escaneo o reparación extraiga portadas desde cero.
     */
    fun resetArtworkCaches(purgeAllArtFiles: Boolean = false) {
        noArtworkCache.clear()
        albumFileCache.clear()
        try {
            cacheDir.listFiles()?.forEach { file ->
                if (purgeAllArtFiles || file.name.startsWith("tmp_") || !isValidImageFile(file)) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Comprobación síncrona inmediata en caché de disco (0 ms de latencia).
     * Revisa tanto el archivo de la pista como la carátula compartida del álbum.
     */
    fun getExistingArtFile(track: Track): File? {
        val albumKey = getAlbumKey(track)
        if (albumKey != null) {
            albumFileCache[albumKey]?.let { 
                if (isValidImageFile(it)) return it else it.delete() 
            }
            val albumFile = File(cacheDir, "${albumKey}.jpg")
            if (albumFile.exists()) {
                if (isValidImageFile(albumFile)) {
                    albumFileCache[albumKey] = albumFile
                    return albumFile
                } else {
                    albumFile.delete()
                }
            }
        }

        val trackFile = File(cacheDir, "track_${track.id}.jpg")
        if (trackFile.exists()) {
            if (isValidImageFile(trackFile)) return trackFile else trackFile.delete()
        }

        if (track.path.isNotBlank()) {
            val f = File(track.path)
            val fileCache = File(cacheDir, "${f.nameWithoutExtension}_${f.length()}.jpg")
            if (fileCache.exists()) {
                if (isValidImageFile(fileCache)) return fileCache else fileCache.delete()
            }
        }
        return null
    }

    fun getExistingArtFile(audioFile: File): File? {
        val cacheFile = File(cacheDir, "${audioFile.nameWithoutExtension}_${audioFile.length()}.jpg")
        if (cacheFile.exists()) {
            if (isValidImageFile(cacheFile)) return cacheFile else cacheFile.delete()
        }
        return null
    }

    /**
     * Devuelve un File con la portada a máxima resolución disponible para un archivo de audio.
     * Si ya está cacheada, devuelve el archivo cacheado (0 IO).
     */
    fun getHighResArt(audioFile: File): File? {
        if (!audioFile.exists() || !audioFile.canRead()) return null

        val cacheFile = File(cacheDir, "${audioFile.nameWithoutExtension}_${audioFile.length()}.jpg")
        if (cacheFile.exists() && isValidImageFile(cacheFile)) return cacheFile

        return try {
            val bytes = extractOriginalArtworkBytes(audioFile) ?: return null

            cacheDir.mkdirs()
            val tempFile = File(cacheDir, "tmp_${System.currentTimeMillis()}_${cacheFile.name}")
            tempFile.writeBytes(bytes)
            if (isValidImageFile(tempFile)) {
                if (cacheFile.exists()) cacheFile.delete()
                if (tempFile.renameTo(cacheFile)) {
                    cacheFile
                } else {
                    tempFile.copyTo(cacheFile, overwrite = true)
                    tempFile.delete()
                    cacheFile
                }
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.w("AlbumArtExtractor", "Fallo extrayendo artwork de ${audioFile.name}: ${e.message}")
            null
        }
    }

    /**
     * Versión para Track: si el álbum ya tiene portada procesada, la reutiliza inmediatamente.
     * Al procesar una canción, la liga al álbum completo para que las demás canciones no reescaneen.
     */
    fun getHighResArt(track: Track): File? {
        // 1. Revisión síncrona en disco (0 ms) - revisa pista y álbum compartido
        getExistingArtFile(track)?.let { return it }

        val albumKey = getAlbumKey(track)

        if (track.path.isNotBlank()) {
            val file = File(track.path)
            if (file.exists() && file.canRead()) {
                val art = getHighResArt(file)
                if (art != null && isValidImageFile(art)) {
                    val trackFile = File(cacheDir, "track_${track.id}.jpg")
                    try {
                        if (!trackFile.exists()) art.copyTo(trackFile, overwrite = false)
                    } catch (_: Exception) {}

                    // Ligar la portada al álbum completo para compartirla con todas sus canciones
                    if (albumKey != null) {
                        val albumFile = File(cacheDir, "${albumKey}.jpg")
                        try {
                            if (!albumFile.exists()) art.copyTo(albumFile, overwrite = false)
                            albumFileCache[albumKey] = albumFile
                        } catch (_: Exception) {
                            albumFileCache[albumKey] = art
                        }
                    }
                    return art
                } else {
                    noArtworkCache.add(track.path)
                    noArtworkCache.add("track_${track.id}")
                    if (track.contentUri != Uri.EMPTY) noArtworkCache.add(track.contentUri.toString())
                    if (track.albumArtUri == null) return null
                }
            }
        }

        // Intento con albumArtUri si ya existe en metadatos
        if (track.albumArtUri != null) {
            try {
                appContext.contentResolver.openInputStream(track.albumArtUri)?.use { input ->
                    val tempFile = File(cacheDir, "tmp_${System.currentTimeMillis()}_track_${track.id}.jpg")
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    val trackFile = File(cacheDir, "track_${track.id}.jpg")
                    if (isValidImageFile(tempFile)) {
                        if (trackFile.exists()) trackFile.delete()
                        if (!tempFile.renameTo(trackFile)) {
                            tempFile.copyTo(trackFile, overwrite = true)
                            tempFile.delete()
                        }
                        if (albumKey != null) {
                            val albumFile = File(cacheDir, "${albumKey}.jpg")
                            try { trackFile.copyTo(albumFile, overwrite = false) } catch (_: Exception) {}
                            albumFileCache[albumKey] = albumFile
                        }
                        return trackFile
                    } else {
                        tempFile.delete()
                    }
                }
            } catch (_: Exception) {}
        }

        // Intento con ContentUri
        if (track.contentUri != Uri.EMPTY) {
            val trackUriKey = "track_${track.id}"
            if (noArtworkCache.contains(trackUriKey) || noArtworkCache.contains(track.contentUri.toString())) return null

            val cacheFile = File(cacheDir, "track_${track.id}.jpg")
            if (cacheFile.exists() && isValidImageFile(cacheFile)) return cacheFile

            return try {
                var bytes: ByteArray? = null
                val mmr = MediaMetadataRetriever()
                try {
                    appContext.contentResolver.openFileDescriptor(track.contentUri, "r")?.use { pfd ->
                        mmr.setDataSource(pfd.fileDescriptor)
                        bytes = mmr.embeddedPicture
                    }
                } catch (_: Exception) {
                    try {
                        mmr.setDataSource(appContext, track.contentUri)
                        bytes = mmr.embeddedPicture
                    } catch (_: Exception) {}
                } finally {
                    try { mmr.release() } catch (ignored: Exception) {}
                }

                if (bytes != null && bytes!!.isNotEmpty()) {
                    cacheDir.mkdirs()
                    val tempFile = File(cacheDir, "tmp_${System.currentTimeMillis()}_${cacheFile.name}")
                    tempFile.writeBytes(bytes!!)
                    if (isValidImageFile(tempFile)) {
                        if (cacheFile.exists()) cacheFile.delete()
                        if (!tempFile.renameTo(cacheFile)) {
                            tempFile.copyTo(cacheFile, overwrite = true)
                            tempFile.delete()
                        }
                        if (albumKey != null) {
                            val albumFile = File(cacheDir, "${albumKey}.jpg")
                            try {
                                if (!albumFile.exists()) cacheFile.copyTo(albumFile, overwrite = false)
                                albumFileCache[albumKey] = albumFile
                            } catch (_: Exception) {}
                        }
                        cacheFile
                    } else {
                        tempFile.delete()
                        null
                    }
                } else {
                    noArtworkCache.add(trackUriKey)
                    noArtworkCache.add(track.contentUri.toString())
                    null
                }
            } catch (e: Exception) {
                noArtworkCache.add(trackUriKey)
                noArtworkCache.add(track.contentUri.toString())
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
        if (cacheFile.exists() && isValidImageFile(cacheFile)) return cacheFile

        return try {
            val bytes = extractOriginalArtworkBytes(audioFile) ?: return null

            cacheDir.mkdirs()
            val tempFile = File(cacheDir, "tmp_${System.currentTimeMillis()}_${cacheFile.name}")
            tempFile.writeBytes(bytes)
            if (isValidImageFile(tempFile)) {
                if (cacheFile.exists()) cacheFile.delete()
                if (tempFile.renameTo(cacheFile)) cacheFile else {
                    tempFile.copyTo(cacheFile, overwrite = true)
                    tempFile.delete()
                    cacheFile
                }
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extrae los bytes puros sin downsamplear usando MediaMetadataRetriever -> JAudioTagger -> Sidecar images.
     */
    private fun extractOriginalArtworkBytes(audioFile: File): ByteArray? {
        if (!audioFile.exists() || !audioFile.canRead()) return null
        if (noArtworkCache.contains(audioFile.absolutePath)) return null

        var tagChecked = false

        // 1. JAudioTagger first (pure Java ID3v2 APIC / FLAC PICTURE / MP4 covr / Vorbis without native MMR JNI error logs)
        try {
            val audio = AudioFileIO.read(audioFile)
            val tag = audio.tag
            if (tag != null) {
                tagChecked = true
                val pictures = tag.artworkList
                if (!pictures.isNullOrEmpty()) {
                    val best = pictures
                        .filter { it.binaryData != null && it.binaryData.size > 500 }
                        .maxByOrNull { it.binaryData.size }
                    if (best?.binaryData != null) {
                        val raw = best.binaryData
                        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
                        if (opts.outWidth > 0 && opts.outHeight > 0) {
                            return raw
                        }
                    }
                }

                // Check Vorbis METADATA_BLOCK_PICTURE en FLAC/Ogg
                val picBase64 = tag.getFirst("METADATA_BLOCK_PICTURE")
                if (!picBase64.isNullOrBlank()) {
                    val decoded = decodeVorbisPictureBlock(picBase64)
                    if (decoded != null && decoded.size > 500) {
                        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.size, opts)
                        if (opts.outWidth > 0 && opts.outHeight > 0) {
                            return decoded
                        }
                    }
                }

                // Check COVERART base64
                val coverBase64 = tag.getFirst("COVERART") ?: tag.getFirst("COVER_ART")
                if (!coverBase64.isNullOrBlank()) {
                    try {
                        val bytes = Base64.decode(coverBase64.trim(), Base64.DEFAULT)
                        if (bytes.size > 500) {
                            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                            if (opts.outWidth > 0 && opts.outHeight > 0) {
                                return bytes
                            }
                        }
                    } catch (ignored: Exception) {}
                }
            }
        } catch (e: Exception) {
            // Container not supported by JAudioTagger
        }

        // 2. Sidecar cover files en el mismo directorio
        try {
            val parent = audioFile.parentFile
            if (parent != null && parent.exists() && parent.isDirectory) {
                val candidateNames = listOf(
                    "${audioFile.nameWithoutExtension}.jpg",
                    "${audioFile.nameWithoutExtension}.png",
                    "${audioFile.nameWithoutExtension}.jpeg",
                    "${audioFile.nameWithoutExtension}.webp",
                    "cover.jpg",
                    "cover.png",
                    "folder.jpg",
                    "folder.png"
                )
                for (name in candidateNames) {
                    val sidecar = File(parent, name)
                    if (sidecar.exists() && sidecar.canRead() && sidecar.length() > 500) {
                        val bytes = sidecar.readBytes()
                        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        if (opts.outWidth > 0 && opts.outHeight > 0) {
                            return bytes
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}

        // 3. ONLY call MediaMetadataRetriever if JAudioTagger could NOT parse the audio tags.
        // If JAudioTagger successfully checked the tag and found no artwork, MMR will also find none,
        // and calling it would trigger getEmbeddedPicture failed logcat error!
        if (!tagChecked) {
            try {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(audioFile.absolutePath)
                    val rawBytes = mmr.embeddedPicture
                    if (rawBytes != null && rawBytes.size > 500) {
                        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
                        if (opts.outWidth > 0 && opts.outHeight > 0) {
                            return rawBytes
                        }
                    }
                } finally {
                    try { mmr.release() } catch (ignored: Exception) {}
                }
            } catch (e: Exception) {}
        }

        noArtworkCache.add(audioFile.absolutePath)
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
}
