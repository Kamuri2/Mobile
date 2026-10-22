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

        suspend fun purgeCorruptedLyrics(context: Context) {
            getInstance(context).purgeCorruptedLyricsFromDb()
        }
    }

    suspend fun purgeCorruptedLyricsFromDb() = withContext(Dispatchers.IO) {
        try {
            memCache.clear()
            val allEntries = dao.getAll()
            for (entry in allEntries) {
                if (AudioLyricsExtractor.isCorruptedLyrics(entry.lyrics)) {
                    dao.deleteByPath(entry.audioPath)
                }
            }
            Log.d(TAG, "Purgadas ${allEntries.size} entradas de letras evaluadas en Room")
        } catch (e: Exception) {
            Log.e(TAG, "Error purgando letras corruptas: ${e.message}")
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
     * Soporta forceRescan para forzar extracción desde etiquetas del archivo de audio.
     */
    suspend fun getLyrics(context: Context, track: Track, forceRescan: Boolean = false): String? = withContext(Dispatchers.IO) {
        val path = if (track.path.isNotBlank()) track.path else track.contentUri.toString()
        val uriStr = track.contentUri.toString()

        if (!forceRescan) {
            // 1) Consulta rápida en memoria y Room (verificando que no sean datos residuales o corruptos)
            memCache[path]?.let { 
                if (it.isNotBlank() && !AudioLyricsExtractor.isCorruptedLyrics(it)) return@withContext it 
                else memCache.remove(path)
            }
            memCache[uriStr]?.let { 
                if (it.isNotBlank() && !AudioLyricsExtractor.isCorruptedLyrics(it)) return@withContext it 
                else memCache.remove(uriStr)
            }

            try {
                val cached = dao.getByPath(path) ?: (if (uriStr != path) dao.getByPath(uriStr) else null)
                if (!cached.isNullOrBlank()) {
                    if (!AudioLyricsExtractor.isCorruptedLyrics(cached)) {
                        memCache[path] = cached
                        memCache[uriStr] = cached
                        return@withContext cached
                    } else {
                        // El dato almacenado previamente era residual/corrupto -> limpiar para forzar extracción fresca
                        try {
                            dao.deleteByPath(path)
                            if (uriStr != path) dao.deleteByPath(uriStr)
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error consultando Room: ${e.message}")
            }

            // 2) Si el track ya venía con letras precargadas y son válidas, guardarlas directamente en Room
            if (track.lyrics.isNotBlank() && !AudioLyricsExtractor.isCorruptedLyrics(track.lyrics)) {
                val cleanPreloaded = AudioLyricsExtractor.cleanExtractedLyrics(track.lyrics)
                if (cleanPreloaded.isNotBlank()) {
                    save(path, uriStr, cleanPreloaded)
                    return@withContext cleanPreloaded
                }
            }
        } else {
            // Limpieza forzada de cachés para reintento de lectura sin datos residuales
            memCache.remove(path)
            memCache.remove(uriStr)
            try {
                dao.deleteByPath(path)
                if (uriStr != path) dao.deleteByPath(uriStr)
            } catch (_: Exception) {}
        }

        // 3) Motor unificado multietapa vía AudioLyricsExtractor
        val extractedResult = AudioLyricsExtractor.extractLyrics(
            context = context,
            audioUri = track.contentUri,
            audioFilePath = track.path.takeIf { it.isNotBlank() && !it.startsWith("content://") },
            artist = track.artist,
            title = track.title
        )

        var rawLyrics = extractedResult.lyrics

        // 4) Fallbacks complementarios si la extracción directa no arrojó resultados
        val isOpus = track.path.endsWith(".opus", true) ||
            (track.contentUri != Uri.EMPTY && try { context.contentResolver.getType(track.contentUri)?.contains("opus", true) == true } catch (_: Exception) { false })

        if ((rawLyrics.isNullOrBlank() || AudioLyricsExtractor.isCorruptedLyrics(rawLyrics)) && isOpus) {
            val opusRes = AudioLyricsExtractor.extractOpusOrOggLyrics(context, track.contentUri, track.path.takeIf { it.isNotBlank() && !it.startsWith("content://") })
            if (opusRes != null && !opusRes.lyrics.isNullOrBlank()) {
                rawLyrics = opusRes.lyrics
            }
        }

        if ((rawLyrics.isNullOrBlank() || AudioLyricsExtractor.isCorruptedLyrics(rawLyrics)) && track.path.isNotBlank() && !track.path.startsWith("content://")) {
            val file = File(track.path)
            rawLyrics = readLyricsFromTag(file)
        }

        if ((rawLyrics.isNullOrBlank() || AudioLyricsExtractor.isCorruptedLyrics(rawLyrics)) && track.contentUri != Uri.EMPTY) {
            rawLyrics = readLyricsFromUri(context, track.contentUri, track.path)
        }

        if ((rawLyrics.isNullOrBlank() || AudioLyricsExtractor.isCorruptedLyrics(rawLyrics)) && track.path.isNotBlank()) {
            rawLyrics = readAdjacentLrc(track.path)
        }

        if (rawLyrics.isNullOrBlank() || AudioLyricsExtractor.isCorruptedLyrics(rawLyrics)) {
            return@withContext null
        }

        val cleaned = AudioLyricsExtractor.cleanExtractedLyrics(rawLyrics)
        if (cleaned.isNotBlank()) {
            save(path, uriStr, cleaned)
            return@withContext cleaned
        }
        return@withContext null
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
            if (file.name.endsWith(".opus", true) || file.name.endsWith(".ogg", true)) {
                val res = AudioLyricsExtractor.parseOggOpusBytes(file.readBytes())
                return res?.lyrics
            }
            val audio = AudioFileIO.read(file)
            extractTagLyrics(audio.tag)
        } catch (e: Exception) {
            null
        }
    }

    private fun readLyricsFromUri(context: Context, uri: Uri, fallbackPath: String?): String? {
        val isOpusUri = fallbackPath?.endsWith(".opus", true) == true ||
            try { context.contentResolver.getType(uri)?.contains("opus", true) == true } catch (_: Exception) { false }
        if (isOpusUri) {
            val res = AudioLyricsExtractor.extractOpusOrOggLyrics(context, uri, fallbackPath)
            if (res != null && !res.lyrics.isNullOrBlank()) return res.lyrics
        }
        var tempFile: File? = null
        return try {
            val ext = when {
                !fallbackPath.isNullOrBlank() && fallbackPath.contains(".") -> "." + fallbackPath.substringAfterLast(".").lowercase()
                else -> ".mp3"
            }
            if (ext == ".opus") {
                val res = AudioLyricsExtractor.extractOpusOrOggLyrics(context, uri, fallbackPath)
                return res?.lyrics
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
        val candidate = memCache[path] ?: memCache[uriStr]
        return if (candidate != null && !AudioLyricsExtractor.isCorruptedLyrics(candidate)) candidate else null
    }

    suspend fun getCachedOrDisk(track: Track): String? = withContext(Dispatchers.IO) {
        if (track.lyrics.isNotBlank() && !AudioLyricsExtractor.isCorruptedLyrics(track.lyrics)) return@withContext track.lyrics
        val path = if (track.path.isNotBlank()) track.path else track.contentUri.toString()
        val uriStr = track.contentUri.toString()
        memCache[path]?.let { if (it.isNotBlank() && !AudioLyricsExtractor.isCorruptedLyrics(it)) return@withContext it }
        memCache[uriStr]?.let { if (it.isNotBlank() && !AudioLyricsExtractor.isCorruptedLyrics(it)) return@withContext it }
        try {
            val cached = dao.getByPath(path) ?: (if (uriStr != path) dao.getByPath(uriStr) else null)
            if (!cached.isNullOrBlank()) {
                if (!AudioLyricsExtractor.isCorruptedLyrics(cached)) {
                    memCache[path] = cached
                    memCache[uriStr] = cached
                    return@withContext cached
                } else {
                    // Limpiar dato residual guardado en Room
                    try {
                        dao.deleteByPath(path)
                        if (uriStr != path) dao.deleteByPath(uriStr)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        null
    }

    private fun extractTagLyrics(tag: Tag?): String? {
        if (tag == null) return null

        // 1) FieldKey.LYRICS mapea automáticamente USLT (MP3), LYRICS (FLAC/Vorbis), ©lyr (MP4)
        try {
            val standard = tag.getFirst(FieldKey.LYRICS)
            if (!standard.isNullOrBlank()) {
                val clean = AudioLyricsExtractor.cleanExtractedLyrics(standard)
                if (clean.isNotBlank() && !AudioLyricsExtractor.isCorruptedLyrics(clean)) return clean
            }
        } catch (ignored: Exception) {}

        // 2) USLT, SYLT y TXXX frames directos para ID3v2
        if (tag is AbstractID3v2Tag) {
            // Check synchronized lyrics (SYLT) first
            try {
                val syltRaw = tag.getFrame("SYLT")
                val syltList = when (syltRaw) {
                    is List<*> -> syltRaw
                    null -> emptyList()
                    else -> listOf(syltRaw)
                }
                for (item in syltList) {
                    val frame = item as? AbstractID3v2Frame ?: continue
                    if (frame.body is org.jaudiotagger.tag.id3.framebody.FrameBodySYLT) {
                        val syltBody = frame.body as org.jaudiotagger.tag.id3.framebody.FrameBodySYLT
                        val bytes = syltBody.lyrics
                        if (bytes != null && bytes.isNotEmpty()) {
                            val parsed = AudioLyricsExtractor.parseSyltToLrc(bytes)
                            if (parsed.isNotBlank() && !AudioLyricsExtractor.isCorruptedLyrics(parsed)) return parsed.trim()
                        }
                    }
                }
            } catch (ignored: Exception) {}

            // Check unsynchronized lyrics (USLT / ULT)
            val usltKeys = listOf("USLT", "ULT")
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
                            if (!lyric.isNullOrBlank()) {
                                val clean = AudioLyricsExtractor.cleanExtractedLyrics(lyric)
                                if (clean.isNotBlank() && !AudioLyricsExtractor.isCorruptedLyrics(clean)) return clean
                            }
                        }
                    }
                } catch (ignored: Exception) {}
            }

            // Check TXXX user frames (e.g. TXXX:LYRICS, TXXX:UNSYNCEDLYRICS, TXXX:SYNCEDLYRICS)
            try {
                val txxxRaw = tag.getFrame("TXXX")
                val txxxList = when (txxxRaw) {
                    is List<*> -> txxxRaw
                    null -> emptyList()
                    else -> listOf(txxxRaw)
                }
                for (item in txxxList) {
                    val frame = item as? AbstractID3v2Frame ?: continue
                    if (frame.body is org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX) {
                        val body = frame.body as org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
                        val desc = body.description.uppercase()
                        val isBlacklistedDesc = desc.contains("LYRICIST") || desc.contains("AUTHOR") ||
                            desc.contains("URL") || desc.contains("SITE") || desc.contains("ID") ||
                            desc.contains("LANG") || desc.contains("COPYRIGHT") || desc.contains("SORT")
                        val isLyricsDesc = !isBlacklistedDesc && (
                            desc == "LYRICS" || desc == "UNSYNCEDLYRICS" || desc == "SYNCEDLYRICS" ||
                            desc == "LRC" || desc == "LYRICS_SYNCED" || desc == "UNSYNCED LYRICS" || desc == "SYNCED LYRICS" ||
                            (desc.startsWith("LYRIC") && !desc.contains("IST"))
                        )
                        if (isLyricsDesc) {
                            if (!body.text.isNullOrBlank()) {
                                val clean = AudioLyricsExtractor.cleanExtractedLyrics(body.text)
                                if (clean.isNotBlank() && !AudioLyricsExtractor.isCorruptedLyrics(clean)) return clean
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }

        // 3) Vorbis comments y claves alternativas (SYNCEDLYRICS, UNSYNCEDLYRICS)
        val candidateKeys = listOf("LYRICS", "UNSYNCEDLYRICS", "UNSYNCED_LYRICS", "SYNCEDLYRICS", "LYRICS_SYNCED", "LRC")
        for (k in candidateKeys) {
            try {
                val value = tag.getFirst(k)
                if (!value.isNullOrBlank()) {
                    val clean = AudioLyricsExtractor.cleanExtractedLyrics(value)
                    if (clean.isNotBlank() && !AudioLyricsExtractor.isCorruptedLyrics(clean)) return clean
                }
            } catch (ignored: Exception) {}
        }

        // 4) Iteración estructurada de campos si las claves estándar no coincidieron
        try {
            val fields = tag.fields
            while (fields.hasNext()) {
                val field = fields.next()
                val id = field.id.uppercase()
                val isBlacklistedField = id.contains("LYRICIST") || id.contains("AUTHOR") ||
                    id.contains("URL") || id.contains("SITE") || id.contains("ID") ||
                    id.contains("LANG") || id.contains("COPYRIGHT") || id.contains("SORT")
                val isAcceptedField = !isBlacklistedField && (
                    id == "USLT" || id == "ULT" || id == "SYLT" || id == "LRC" ||
                    id == "LYRICS" || id == "UNSYNCEDLYRICS" || id == "UNSYNCED LYRICS" ||
                    id == "SYNCEDLYRICS" || id == "SYNCED LYRICS" || id == "LYRICS_SYNCED" ||
                    (id.startsWith("LYRIC") && !id.contains("IST"))
                )
                if (isAcceptedField) {
                    val rawContent = when {
                        field is org.jaudiotagger.tag.TagTextField -> field.content
                        field is AbstractID3v2Frame && field.body is FrameBodyUSLT -> (field.body as FrameBodyUSLT).lyric
                        field is AbstractID3v2Frame && field.body is org.jaudiotagger.tag.id3.framebody.FrameBodySYLT -> {
                            AudioLyricsExtractor.parseSyltToLrc((field.body as org.jaudiotagger.tag.id3.framebody.FrameBodySYLT).lyrics ?: ByteArray(0))
                        }
                        else -> null
                    }
                    if (!rawContent.isNullOrBlank()) {
                        val clean = AudioLyricsExtractor.cleanExtractedLyrics(rawContent)
                        if (clean.isNotBlank() && !AudioLyricsExtractor.isCorruptedLyrics(clean)) return clean
                    }
                }
            }
        } catch (ignored: Exception) {}

        return null
    }

    private fun readAdjacentLrc(filePath: String): String? {
        val adjacent = AudioLyricsExtractor.checkAdjacentLyricsFile(filePath)
        if (!adjacent.isNullOrBlank() && !AudioLyricsExtractor.isCorruptedLyrics(adjacent)) {
            return AudioLyricsExtractor.cleanExtractedLyrics(adjacent)
        }
        return null
    }

    private fun cleanExtractedLyrics(raw: String): String {
        return AudioLyricsExtractor.cleanExtractedLyrics(raw)
    }
}
