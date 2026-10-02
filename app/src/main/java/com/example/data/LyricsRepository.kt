package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.LyricsDao
import com.example.data.local.LyricsEntry
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Motor central de letras basado en Room Database (SQLite) y JAudioTagger.
 *
 * Flujo:
 * 1. ¿Ya está guardada en Room? (SELECT lyrics FROM lyrics WHERE audioPath = :path)
 *    -> Devolver al instante (< 1ms), sin tocar el archivo de audio.
 * 2. Primera vez -> Leer tag USLT / LYRICS con JAudioTagger (MP3, FLAC, M4A, OGG).
 * 3. Guardar en Room (DAO.save(LyricsEntry(path, lyrics))).
 * 4. Las próximas veces la consulta es instantánea.
 */
class LyricsRepository(private val dao: LyricsDao) {

    companion object {
        private const val TAG = "LyricsRepository"
        private val memCache = ConcurrentHashMap<String, String>()

        @Volatile
        private var instance: LyricsRepository? = null

        fun getInstance(context: Context): LyricsRepository {
            return instance ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val newInstance = LyricsRepository(db.lyricsDao())
                instance = newInstance
                newInstance
            }
        }

        suspend fun getLyricsForTrack(context: Context, track: Track): String? {
            return getInstance(context).getLyrics(context, track)
        }
    }

    /**
     * Consulta por archivo File según especificación directa.
     */
    suspend fun getLyrics(audioFile: File): String? = withContext(Dispatchers.IO) {
        val path = audioFile.absolutePath

        // 1) Memoria / Room: ¿Ya está guardada? -> devolver al instante (< 1ms)
        memCache[path]?.let { return@withContext it }
        try {
            dao.getByPath(path)?.let {
                memCache[path] = it
                return@withContext it
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando Room: ${e.message}")
        }

        // 2) Primera vez -> leer el tag con JAudioTagger
        val lyrics = readLyricsFromTag(audioFile) ?: return@withContext null

        // 3) Guardar para siempre en Room
        try {
            dao.save(LyricsEntry(path, lyrics))
            memCache[path] = lyrics
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando en Room: ${e.message}")
        }

        return@withContext lyrics
    }

    /**
     * Consulta y liga letras directamente al Track (usando path o contentUri).
     */
    suspend fun getLyrics(context: Context, track: Track): String? = withContext(Dispatchers.IO) {
        val path = if (track.path.isNotBlank()) track.path else track.contentUri.toString()
        val uriStr = track.contentUri.toString()

        // 1) Consulta rápida en memoria y Room
        memCache[path]?.let { return@withContext it }
        memCache[uriStr]?.let { return@withContext it }

        try {
            val cached = dao.getByPath(path) ?: (if (uriStr != path) dao.getByPath(uriStr) else null)
            if (!cached.isNullOrBlank()) {
                memCache[path] = cached
                memCache[uriStr] = cached
                return@withContext cached
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando Room: ${e.message}")
        }

        // 2) Si el track ya venía con letras precargadas, guardarlas directamente en Room
        if (track.lyrics.isNotBlank()) {
            save(path, uriStr, track.lyrics)
            return@withContext track.lyrics
        }

        // 3) Primera vez -> leer etiqueta física del archivo de audio con JAudioTagger
        var rawLyrics: String? = null

        // Intento A: Vía File directo
        if (track.path.isNotBlank() && !track.path.startsWith("content://")) {
            val file = File(track.path)
            rawLyrics = readLyricsFromTag(file)
        }

        // Intento B: Vía ContentResolver si el File falló por Scoped Storage
        if (rawLyrics.isNullOrBlank() && track.contentUri != Uri.EMPTY) {
            rawLyrics = readLyricsFromUri(context, track.contentUri, track.path)
        }

        // Intento C: Archivo .lrc adyacente
        if (rawLyrics.isNullOrBlank() && track.path.isNotBlank()) {
            rawLyrics = readAdjacentLrc(track.path)
        }

        if (rawLyrics.isNullOrBlank()) {
            return@withContext null
        }

        val cleaned = cleanExtractedLyrics(rawLyrics)
        save(path, uriStr, cleaned)
        return@withContext cleaned
    }

    private suspend fun save(path: String, uriStr: String?, lyrics: String) {
        try {
            dao.save(LyricsEntry(audioPath = path, lyrics = lyrics))
            memCache[path] = lyrics
            if (!uriStr.isNullOrBlank() && uriStr != path) {
                dao.save(LyricsEntry(audioPath = uriStr, lyrics = lyrics))
                memCache[uriStr] = lyrics
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando letras en Room: ${e.message}")
        }
    }

    private fun readLyricsFromTag(file: File): String? {
        return try {
            if (!file.exists() || file.length() == 0L) return null
            val audio = AudioFileIO.read(file)
            extractTagLyrics(audio.tag)
        } catch (e: Exception) {
            null
        }
    }

    private fun readLyricsFromUri(context: Context, uri: Uri, fallbackPath: String?): String? {
        var tempFile: File? = null
        return try {
            val ext = when {
                !fallbackPath.isNullOrBlank() && fallbackPath.contains(".") -> "." + fallbackPath.substringAfterLast(".").lowercase()
                else -> ".mp3"
            }
            tempFile = File.createTempFile("lyrics_scan_", ext, context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) {
                val audio = AudioFileIO.read(tempFile)
                extractTagLyrics(audio.tag)
            } else null
        } catch (e: Exception) {
            null
        } finally {
            try { tempFile?.delete() } catch (ignored: Exception) {}
        }
    }

    fun getCachedOrNull(track: Track): String? {
        val path = if (track.path.isNotBlank()) track.path else track.contentUri.toString()
        val uriStr = track.contentUri.toString()
        return memCache[path] ?: memCache[uriStr]
    }

    private fun extractTagLyrics(tag: Tag?): String? {
        if (tag == null) return null

        // 1) FieldKey.LYRICS mapea automáticamente USLT (MP3), LYRICS (FLAC/Vorbis), ©lyr (MP4)
        try {
            val standard = tag.getFirst(FieldKey.LYRICS)
            if (!standard.isNullOrBlank()) return standard.trim()
        } catch (ignored: Exception) {}

        // 2) USLT y ULT (ID3v2.2) frame directo para ID3v2 (evita bugs de parser en MP3)
        if (tag is AbstractID3v2Tag) {
            val usltKeys = listOf("USLT", "ULT", "SYLT", "SLT")
            for (key in usltKeys) {
                try {
                    val frameRaw = tag.getFrame(key)
                    val frameList = when (frameRaw) {
                        is List<*> -> frameRaw
                        null -> emptyList()
                        else -> listOf(frameRaw)
                    }
                    for (item in frameList) {
                        val frame = item as? AbstractID3v2Frame ?: continue
                        if (frame.body is FrameBodyUSLT) {
                            val lyric = (frame.body as FrameBodyUSLT).lyric
                            if (!lyric.isNullOrBlank()) return lyric.trim()
                        }
                    }
                } catch (ignored: Exception) {}
            }
        }

        // 3) Vorbis comments y claves alternativas (SYNCEDLYRICS, UNSYNCEDLYRICS)
        val candidateKeys = listOf("LYRICS", "UNSYNCEDLYRICS", "UNSYNCED_LYRICS", "SYNCEDLYRICS", "TEXT", "LYRIC")
        for (k in candidateKeys) {
            try {
                val value = tag.getFirst(k)
                if (!value.isNullOrBlank()) return value.trim()
            } catch (ignored: Exception) {}
        }

        // 4) Iteración genérica de campos si las claves estándar no coincidieron
        try {
            val fields = tag.fields
            while (fields.hasNext()) {
                val field = fields.next()
                val id = field.id.uppercase()
                if (id == "USLT" || id == "ULT" || id == "LYRICS" || id == "UNSYNCEDLYRICS" || id.contains("LYRIC")) {
                    val str = field.toString()
                    val clean = if (str.contains("||")) str.substringAfter("||") else str
                    if (clean.isNotBlank()) return clean.trim()
                }
            }
        } catch (ignored: Exception) {}

        return null
    }

    private fun readAdjacentLrc(filePath: String): String? {
        return try {
            val audioFile = File(filePath)
            val parent = audioFile.parentFile ?: return null
            val baseName = audioFile.nameWithoutExtension
            val lrcFile = File(parent, "$baseName.lrc")
            if (lrcFile.exists() && lrcFile.canRead()) {
                val text = lrcFile.readText(Charsets.UTF_8).trim()
                if (text.isNotBlank()) text else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanExtractedLyrics(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.contains("||")) {
            val parts = cleaned.split("||", limit = 2)
            if (parts.size == 2 && parts[0].length <= 8) {
                cleaned = parts[1]
            }
        }
        val prefixes = listOf("eng|", "spa|", "jpn|", "XXX|", "xxx|", "ger|", "fra|", "ita|")
        for (prefix in prefixes) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length)
            }
        }
        return cleaned.trim { it <= ' ' || it == '\u0000' }
    }
}
