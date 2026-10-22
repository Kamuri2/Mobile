package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.model.Track
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyCOMM
import org.jaudiotagger.tag.id3.framebody.FrameBodySYLT
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object AudioLyricsExtractor {
    private const val TAG = "AudioLyricsExtractor"

    data class LyricsResult(
        val lyrics: String?,
        val isSynced: Boolean,
        val source: String
    )

    /**
     * Motor completo de extracción de letras:
     * 1. Extractor directo binario/stream (ultra rápido, sin copiar archivos temporales, funciona en Android 10/11/12/13/14 con Scoped Storage).
     * 2. Extractor JAudioTagger exhaustivo (MP3 USLT/SYLT/TXXX/COMM, FLAC VorbisComment, MP4 ©lyr).
     * 3. Archivos adyacentes .lrc, .txt, .srt en el directorio del audio o subcarpeta Lyrics/.
     * 4. Escáner profundo de paquetes Vorbis/Opus en contenedores OGG/Opus.
     */
    fun extractLyrics(
        context: Context,
        track: Track,
        forceRescan: Boolean = false
    ): String? {
        val result = extractLyrics(
            context = context,
            audioUri = track.contentUri,
            audioFilePath = track.path,
            artist = track.artist,
            title = track.title
        )
        return result.lyrics
    }

    fun extractLyrics(
        context: Context,
        audioUri: Uri,
        audioFilePath: String?,
        artist: String? = null,
        title: String? = null
    ): LyricsResult {
        // 1. Archivos .lrc o .txt adyacentes (ultrarrápido, mínimo consumo de memoria, soporta song.opus.lrc)
        val externalLyrics = checkAdjacentLyricsFile(audioFilePath)
        if (externalLyrics != null && externalLyrics.isNotBlank() && !isCorruptedLyrics(externalLyrics)) {
            val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(externalLyrics)
            Log.d(TAG, "Lyrics found via external file")
            return LyricsResult(lyrics = cleanExtractedLyrics(externalLyrics), isSynced = isSynced, source = "EXTERNAL_FILE")
        }

        val isOpusOrOgg = (!audioFilePath.isNullOrBlank() && (audioFilePath.endsWith(".opus", true) || audioFilePath.endsWith(".ogg", true))) ||
            (try { context.contentResolver.getType(audioUri)?.lowercase()?.let { it.contains("opus") || it.contains("ogg") } } catch (_: Exception) { null } == true)

        // 2. Si es archivo Opus u Ogg, usar el motor especializado directo (JAudioTagger no soporta Opus)
        if (isOpusOrOgg) {
            val opusResult = extractOpusOrOggLyrics(context, audioUri, audioFilePath)
            if (opusResult != null && !opusResult.lyrics.isNullOrBlank() && !isCorruptedLyrics(opusResult.lyrics)) {
                Log.d(TAG, "Lyrics found via Opus/Ogg specialized engine: ${opusResult.source}")
                return opusResult
            }
        }

        // 3. Escaneo directo de cabeceras estructuradas (ID3v2, FLAC nativo, MP4, OGG/OPUS) sin copiar archivo
        val streamResult = scanStreamForLyrics(context, audioUri, audioFilePath)
        if (streamResult != null && !streamResult.lyrics.isNullOrBlank() && !isCorruptedLyrics(streamResult.lyrics)) {
            Log.d(TAG, "Lyrics found via direct stream parser: ${streamResult.source}")
            return streamResult
        }

        // 4. Si es formato no-Opus y existe ruta directa en disco, leer con JAudioTagger
        if (!isOpusOrOgg && !audioFilePath.isNullOrBlank() && File(audioFilePath).canRead()) {
            val embeddedResult = extractEmbeddedLyrics(context, audioUri, audioFilePath)
            if (embeddedResult != null && !embeddedResult.lyrics.isNullOrBlank() && !isCorruptedLyrics(embeddedResult.lyrics)) {
                Log.d(TAG, "Lyrics found via JAudioTagger (direct file): ${embeddedResult.source}")
                return embeddedResult
            }
        }

        // 5. Escaneo profundo de contingencia para paquetes Opus/Ogg Vorbis (verificando campos de letras explícitos)
        val deepScanResult = extractOpusOrOggLyrics(context, audioUri, audioFilePath)
        if (deepScanResult != null && !deepScanResult.lyrics.isNullOrBlank() && !isCorruptedLyrics(deepScanResult.lyrics)) {
            Log.d(TAG, "Lyrics found via Opus deep scan: ${deepScanResult.source}")
            return deepScanResult
        }

        // 6. Fallback JAudioTagger para URIs virtuales si todo lo anterior falló (solo para formatos no-Opus)
        if (!isOpusOrOgg && audioFilePath.isNullOrBlank() && audioUri != Uri.EMPTY) {
            val embeddedFallback = extractEmbeddedLyrics(context, audioUri, null)
            if (embeddedFallback != null && !embeddedFallback.lyrics.isNullOrBlank() && !isCorruptedLyrics(embeddedFallback.lyrics)) {
                return embeddedFallback
            }
        }

        return LyricsResult(lyrics = null, isSynced = false, source = "NONE")
    }

    // =========================================================================
    // 1. ESCÁNER DIRECTO DE CABECERAS Y MARCOS BINARIOS (ZERO FILE COPY)
    // =========================================================================

    private fun scanStreamForLyrics(context: Context, audioUri: Uri, audioFilePath: String?): LyricsResult? {
        var inputStream: InputStream? = null
        try {
            if (audioUri != Uri.EMPTY) {
                try {
                    inputStream = context.contentResolver.openInputStream(audioUri)
                } catch (e: Exception) {
                    Log.d(TAG, "ContentResolver stream open failed: ${e.message}")
                }
            }
            if (inputStream == null && !audioFilePath.isNullOrBlank()) {
                try {
                    val f = File(audioFilePath)
                    if (f.exists() && f.canRead()) {
                        inputStream = FileInputStream(f)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "FileInputStream failed: ${e.message}")
                }
            }
            if (inputStream == null) return null

            // Leemos hasta 4MB iniciales de la pista para metadatos ID3/FLAC/MP4/OGG/OPUS
            val maxHeaderBytes = 4 * 1024 * 1024
            val buffer = ByteArray(maxHeaderBytes)
            var totalRead = 0
            inputStream.use { input ->
                while (totalRead < maxHeaderBytes) {
                    val r = input.read(buffer, totalRead, maxHeaderBytes - totalRead)
                    if (r <= 0) break
                    totalRead += r
                }
            }

            if (totalRead < 16) return null
            val headerBytes = if (totalRead == maxHeaderBytes) buffer else buffer.copyOfRange(0, totalRead)

            // A. Cabecera ID3v2 (MP3, WAV, FLAC o paquetes Opus empaquetados con ID3)
            val id3Result = parseId3v2Bytes(headerBytes)
            if (id3Result != null && !id3Result.lyrics.isNullOrBlank() && !isCorruptedLyrics(id3Result.lyrics)) {
                return id3Result
            }

            // B. Contenedor OGG / OPUS ("OggS" con paquetes OpusTags o \x03vorbis)
            val oggResult = parseOggOpusBytes(headerBytes)
            if (oggResult != null && !oggResult.lyrics.isNullOrBlank() && !isCorruptedLyrics(oggResult.lyrics)) {
                return oggResult
            }

            // C. Cabecera nativa FLAC ("fLaC" con bloques VORBIS_COMMENT)
            val flacLyrics = parseFlacBytes(headerBytes)
            if (!flacLyrics.isNullOrBlank() && !isCorruptedLyrics(flacLyrics)) {
                val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(flacLyrics)
                return LyricsResult(lyrics = flacLyrics, isSynced = isSynced, source = "DIRECT_FLAC_VORBIS")
            }

            // D. Átomo MP4/M4A ("©lyr" / "----")
            val mp4Lyrics = parseMp4Bytes(headerBytes)
            if (!mp4Lyrics.isNullOrBlank() && !isCorruptedLyrics(mp4Lyrics)) {
                val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(mp4Lyrics)
                return LyricsResult(lyrics = mp4Lyrics, isSynced = isSynced, source = "DIRECT_MP4_LYR")
            }
        } catch (e: Exception) {
            Log.d(TAG, "scanStreamForLyrics error: ${e.message}")
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Parsea etiquetas ID3v2 directamente desde bytes (versiones 2.2, 2.3 y 2.4).
     * Extrae: USLT (Unsynced lyrics), ULT (ID3v2.2), SYLT (Synced lyrics), TXXX (con descripciones tipo LYRICS/LRC/SIMPLE), COMM.
     */
    fun parseId3v2Bytes(bytes: ByteArray): LyricsResult? {
        if (bytes.size < 10) return null
        if (bytes[0] != 0x49.toByte() || bytes[1] != 0x44.toByte() || bytes[2] != 0x33.toByte()) {
            return null
        }
        val major = bytes[3].toInt() and 0xFF // 2, 3 o 4
        val flags = bytes[5].toInt() and 0xFF
        val hasExtended = (flags and 0x40) != 0

        // Tamaño synchsafe (7 bits por byte)
        val tagSize = ((bytes[6].toInt() and 0x7F) shl 21) or
                      ((bytes[7].toInt() and 0x7F) shl 14) or
                      ((bytes[8].toInt() and 0x7F) shl 7) or
                      (bytes[9].toInt() and 0x7F)

        val totalLimit = (10 + tagSize).coerceAtMost(bytes.size)
        var offset = 10

        // Saltar extended header si existe
        if (hasExtended && major >= 3 && offset + 4 <= totalLimit) {
            val extSize = if (major == 4) {
                ((bytes[offset].toInt() and 0x7F) shl 21) or
                ((bytes[offset+1].toInt() and 0x7F) shl 14) or
                ((bytes[offset+2].toInt() and 0x7F) shl 7) or
                (bytes[offset+3].toInt() and 0x7F)
            } else {
                ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset+1].toInt() and 0xFF) shl 16) or
                ((bytes[offset+2].toInt() and 0xFF) shl 8) or
                (bytes[offset+3].toInt() and 0xFF)
            }
            offset += (if (major == 4) extSize else extSize + 4).coerceAtMost(totalLimit - offset)
        }

        var candidateLyrics: String? = null

        while (offset < totalLimit - 6) {
            if (major == 2) {
                // ID3v2.2: ID de 3 caracteres + tamaño de 3 bytes
                if (offset + 6 > totalLimit) break
                val id = String(bytes, offset, 3, Charsets.US_ASCII)
                if (id.isBlank() || id[0] == '\u0000') break
                val frameSize = ((bytes[offset+3].toInt() and 0xFF) shl 16) or
                                ((bytes[offset+4].toInt() and 0xFF) shl 8) or
                                (bytes[offset+5].toInt() and 0xFF)
                offset += 6
                if (frameSize <= 0 || offset + frameSize > totalLimit) break

                if (id == "ULT") {
                    val lyr = parseUsltFrame(bytes, offset, frameSize)
                    if (!lyr.isNullOrBlank()) return LyricsResult(lyr, Regex("\\[\\d{2}:\\d{2}").containsMatchIn(lyr), "DIRECT_ID3v2.2_ULT")
                } else if (id == "SLT") {
                    val lyr = parseSyltFrame(bytes, offset, frameSize)
                    if (!lyr.isNullOrBlank()) return LyricsResult(lyr, true, "DIRECT_ID3v2.2_SLT")
                } else if (id == "TXX" || id == "COM") {
                    val lyr = parseTxxxOrCommFrame(bytes, offset, frameSize, id == "COM")
                    if (!lyr.isNullOrBlank() && candidateLyrics == null) candidateLyrics = lyr
                }
                offset += frameSize
            } else {
                // ID3v2.3 / ID3v2.4: ID de 4 caracteres + tamaño de 4 bytes + 2 bytes de flags
                if (offset + 10 > totalLimit) break
                val id = String(bytes, offset, 4, Charsets.US_ASCII)
                if (id.isBlank() || id[0] == '\u0000') break
                val frameSize = if (major == 4) {
                    ((bytes[offset+4].toInt() and 0x7F) shl 21) or
                    ((bytes[offset+5].toInt() and 0x7F) shl 14) or
                    ((bytes[offset+6].toInt() and 0x7F) shl 7) or
                    (bytes[offset+7].toInt() and 0x7F)
                } else {
                    ((bytes[offset+4].toInt() and 0xFF) shl 24) or
                    ((bytes[offset+5].toInt() and 0xFF) shl 16) or
                    ((bytes[offset+6].toInt() and 0xFF) shl 8) or
                    (bytes[offset+7].toInt() and 0xFF)
                }
                offset += 10
                if (frameSize <= 0 || offset + frameSize > totalLimit) break

                if (id == "USLT") {
                    val lyr = parseUsltFrame(bytes, offset, frameSize)
                    if (!lyr.isNullOrBlank()) return LyricsResult(lyr, Regex("\\[\\d{2}:\\d{2}").containsMatchIn(lyr), "DIRECT_ID3v2_USLT")
                } else if (id == "SYLT") {
                    val lyr = parseSyltFrame(bytes, offset, frameSize)
                    if (!lyr.isNullOrBlank()) return LyricsResult(lyr, true, "DIRECT_ID3v2_SYLT")
                } else if (id == "TXXX" || id == "COMM") {
                    val lyr = parseTxxxOrCommFrame(bytes, offset, frameSize, id == "COMM")
                    if (!lyr.isNullOrBlank() && candidateLyrics == null) candidateLyrics = lyr
                }
                offset += frameSize
            }
        }

        if (!candidateLyrics.isNullOrBlank()) {
            val isSynced = Regex("\\[\\d{2}:\\d{2}").containsMatchIn(candidateLyrics)
            return LyricsResult(candidateLyrics, isSynced, "DIRECT_ID3v2_TEXT_FRAME")
        }

        return null
    }

    private fun parseUsltFrame(bytes: ByteArray, offset: Int, size: Int): String? {
        if (size < 5) return null
        val encoding = bytes[offset].toInt() and 0xFF
        // Saltar encoding (1 byte) + código de idioma ISO-639-2 (3 bytes)
        var descEnd = offset + 4
        if (encoding == 1 || encoding == 2) {
            // UTF-16: delimitador de 2 bytes nulos alineados
            while (descEnd + 1 < offset + size) {
                if (bytes[descEnd] == 0.toByte() && bytes[descEnd + 1] == 0.toByte()) {
                    descEnd += 2
                    break
                }
                descEnd += 2
            }
        } else {
            // ISO-8859-1 o UTF-8: delimitador de 1 byte nulo
            while (descEnd < offset + size) {
                if (bytes[descEnd] == 0.toByte()) {
                    descEnd += 1
                    break
                }
                descEnd += 1
            }
        }

        val lyricsLen = (offset + size) - descEnd
        if (lyricsLen <= 0) {
            val fallbackLen = (offset + size) - (offset + 4)
            if (fallbackLen > 0) {
                val text = decodeWithEncoding(bytes, offset + 4, fallbackLen, encoding)
                if (text.isNotBlank()) return cleanExtractedLyrics(text)
            }
            return null
        }
        val text = decodeWithEncoding(bytes, descEnd, lyricsLen, encoding)
        if (text.isNotBlank()) return cleanExtractedLyrics(text)
        return null
    }

    private fun parseTxxxOrCommFrame(bytes: ByteArray, offset: Int, size: Int, isComm: Boolean): String? {
        if (size < 5) return null
        val encoding = bytes[offset].toInt() and 0xFF
        val startDesc = if (isComm) offset + 4 else offset + 1 // COMM tiene 3 bytes de idioma
        if (startDesc >= offset + size) return null

        var descEnd = startDesc
        if (encoding == 1 || encoding == 2) {
            while (descEnd + 1 < offset + size) {
                if (bytes[descEnd] == 0.toByte() && bytes[descEnd + 1] == 0.toByte()) {
                    descEnd += 2
                    break
                }
                descEnd += 2
            }
        } else {
            while (descEnd < offset + size) {
                if (bytes[descEnd] == 0.toByte()) {
                    descEnd += 1
                    break
                }
                descEnd += 1
            }
        }

        val descStr = decodeWithEncoding(bytes, startDesc, descEnd - startDesc, encoding).uppercase()
        val textLen = (offset + size) - descEnd
        if (textLen <= 0) return null

        val text = decodeWithEncoding(bytes, descEnd, textLen, encoding)
        if (text.isBlank()) return null

        val isLyricsDesc = if (isComm) {
            descStr == "LYRICS" || descStr == "LRC" || descStr == "SYNCEDLYRICS" || descStr == "UNSYNCEDLYRICS"
        } else {
            val isBlacklisted = descStr.contains("LYRICIST") || descStr.contains("AUTHOR") ||
                descStr.contains("URL") || descStr.contains("SITE") || descStr.contains("ID") ||
                descStr.contains("LANG") || descStr.contains("COPYRIGHT") || descStr.contains("SORT")
            !isBlacklisted && (
                descStr == "LYRICS" || descStr == "UNSYNCEDLYRICS" || descStr == "SYNCEDLYRICS" ||
                descStr == "LRC" || descStr == "LYRICS_SYNCED" || descStr == "UNSYNCED LYRICS" || descStr == "SYNCED LYRICS" ||
                (descStr.startsWith("LYRIC") && !descStr.contains("IST"))
            )
        }
        val containsTimestamps = Regex("""\[\d{2}:\d{2}""").containsMatchIn(text)

        if (isLyricsDesc || (containsTimestamps && !isCorruptedLyrics(text))) {
            val cleaned = cleanExtractedLyrics(text)
            if (cleaned.isNotBlank() && !isCorruptedLyrics(cleaned)) {
                return cleaned
            }
        }
        return null
    }

    private fun parseSyltFrame(bytes: ByteArray, offset: Int, size: Int): String? {
        if (size < 6) return null
        val syltSlice = bytes.copyOfRange(offset, offset + size)
        val lrc = parseSyltToLrc(syltSlice)
        return if (lrc.isNotBlank()) cleanExtractedLyrics(lrc) else null
    }

    /**
     * Parsea bloques nativos FLAC (fLaC) y comentarios Vorbis.
     */
    fun parseFlacBytes(bytes: ByteArray): String? {
        var pos = 0
        // Omitir cabecera ID3v2 previa si existe al inicio del archivo FLAC
        if (bytes.size >= 10 && bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()) {
            val tagSize = ((bytes[6].toInt() and 0x7F) shl 21) or
                          ((bytes[7].toInt() and 0x7F) shl 14) or
                          ((bytes[8].toInt() and 0x7F) shl 7) or
                          (bytes[9].toInt() and 0x7F)
            pos = 10 + tagSize
        }
        if (pos + 4 > bytes.size) return null
        if (bytes[pos] != 'f'.code.toByte() || bytes[pos+1] != 'L'.code.toByte() ||
            bytes[pos+2] != 'a'.code.toByte() || bytes[pos+3] != 'C'.code.toByte()) {
            return null
        }
        pos += 4

        while (pos + 4 <= bytes.size) {
            val b0 = bytes[pos].toInt() and 0xFF
            val isLast = (b0 and 0x80) != 0
            val blockType = b0 and 0x7F
            val blockLen = ((bytes[pos+1].toInt() and 0xFF) shl 16) or
                           ((bytes[pos+2].toInt() and 0xFF) shl 8) or
                           (bytes[pos+3].toInt() and 0xFF)
            pos += 4
            if (blockLen < 0 || pos + blockLen > bytes.size) break

            if (blockType == 4) { // VORBIS_COMMENT block
                val commentSlice = bytes.copyOfRange(pos, pos + blockLen)
                val lyrics = parseVorbisCommentBlock(commentSlice)
                if (!lyrics.isNullOrBlank()) return lyrics
            }
            pos += blockLen
            if (isLast) break
        }
        return null
    }

    fun parseVorbisCommentBlock(block: ByteArray): String? {
        val res = parseVorbisCommentBlockToResult(block, "VORBIS_COMMENT")
        return res?.lyrics
    }

    fun parseVorbisCommentBlockToResult(block: ByteArray, sourceLabel: String = "VORBIS_COMMENT"): LyricsResult? {
        if (block.size < 8) return null
        fun readLE32(idx: Int): Int {
            if (idx + 3 >= block.size) return 0
            return (block[idx].toInt() and 0xFF) or
                   ((block[idx+1].toInt() and 0xFF) shl 8) or
                   ((block[idx+2].toInt() and 0xFF) shl 16) or
                   ((block[idx+3].toInt() and 0xFF) shl 24)
        }

        var offset = 0
        // Omitir cabecera mágica si está presente:
        // "OpusTags" (8 bytes) o "\x03vorbis" (7 bytes)
        if (block.size >= 8 &&
            block[0] == 'O'.code.toByte() && block[1] == 'p'.code.toByte() &&
            block[2] == 'u'.code.toByte() && block[3] == 's'.code.toByte() &&
            block[4] == 'T'.code.toByte() && block[5] == 'a'.code.toByte() &&
            block[6] == 'g'.code.toByte() && block[7] == 's'.code.toByte()) {
            offset = 8
        } else if (block.size >= 7 &&
            block[0] == 3.toByte() && block[1] == 'v'.code.toByte() &&
            block[2] == 'o'.code.toByte() && block[3] == 'r'.code.toByte() &&
            block[4] == 'b'.code.toByte() && block[5] == 'i'.code.toByte() &&
            block[6] == 's'.code.toByte()) {
            offset = 7
        }

        if (offset + 4 > block.size) return null
        val vendorLen = readLE32(offset)
        if (vendorLen < 0 || offset + 4 + vendorLen > block.size) return null
        offset += 4 + vendorLen
        if (offset + 4 > block.size) return null

        val userCommentCount = readLE32(offset)
        offset += 4
        if (userCommentCount < 0) return null

        var candidateLyrics: String? = null

        for (i in 0 until userCommentCount) {
            if (offset + 4 > block.size) break
            val commentLen = readLE32(offset)
            offset += 4
            if (commentLen < 0 || offset + commentLen > block.size) break

            // Si es imagen de carátula en Base64 (METADATA_BLOCK_PICTURE), omitir para ahorrar memoria
            if (commentLen > 24 &&
                block[offset] == 'M'.code.toByte() &&
                block[offset+1] == 'E'.code.toByte() &&
                block[offset+2] == 'T'.code.toByte() &&
                block[offset+3] == 'A'.code.toByte()) {
                val headerCheck = String(block, offset, 23.coerceAtMost(commentLen), Charsets.US_ASCII)
                if (headerCheck.equals("METADATA_BLOCK_PICTURE=", ignoreCase = true)) {
                    offset += commentLen
                    continue
                }
            }

            val commentSlice = block.copyOfRange(offset, offset + commentLen)
            offset += commentLen

            val commentStr = decodeTextSmart(commentSlice)
            if (commentStr.contains("=")) {
                val key = commentStr.substringBefore("=").trim().uppercase()
                val value = commentStr.substringAfter("=").trim()
                if (value.isNotBlank()) {
                    val isBlacklistedKey = key.contains("LYRICIST") || key.contains("AUTHOR") ||
                        key.contains("URL") || key.contains("SITE") || key.contains("ID") ||
                        key.contains("LANG") || key.contains("COPYRIGHT") || key.contains("SORT")
                    val isLyricsKey = !isBlacklistedKey && (
                        key == "LYRICS" || key == "UNSYNCEDLYRICS" || key == "UNSYNCED LYRICS" ||
                        key == "UNSYNCED_LYRICS" || key == "SYNCEDLYRICS" || key == "SYNCED LYRICS" ||
                        key == "SYNCED_LYRICS" || key == "LYRICS_SYNCED" ||
                        key == "LRC" || key == "TEXT" || key == "USLT" || key == "SYLT" || key == "LYRIC" ||
                        (key.startsWith("LYRIC") && !key.contains("IST"))
                    )
                    val hasTimestamps = Regex("""\[\d{2}:\d{2}""").containsMatchIn(value)

                    if (isLyricsKey || (hasTimestamps && !isCorruptedLyrics(value))) {
                        val cleaned = cleanExtractedLyrics(value)
                        if (cleaned.isNotBlank() && !isCorruptedLyrics(cleaned)) {
                            // Prioridad a letras sincronizadas/LRC con marcas de tiempo
                            if (hasTimestamps) {
                                return LyricsResult(lyrics = cleaned, isSynced = true, source = "${sourceLabel}_$key")
                            }
                            if (candidateLyrics == null) candidateLyrics = cleaned
                        }
                    } else if ((key == "COMMENT" || key == "DESCRIPTION" || key == "NOTES" || key == "SUBTITLE") && !isCorruptedLyrics(value)) {
                        // En algunos ripeos o descargas, las letras se guardan en el comentario
                        if (value.lines().count { it.isNotBlank() } >= 3 || hasTimestamps) {
                            val cleaned = cleanExtractedLyrics(value)
                            if (cleaned.isNotBlank() && !isCorruptedLyrics(cleaned)) {
                                if (hasTimestamps) {
                                    return LyricsResult(lyrics = cleaned, isSynced = true, source = "${sourceLabel}_$key")
                                }
                                if (candidateLyrics == null) candidateLyrics = cleaned
                            }
                        }
                    }
                }
            }
        }

        return if (!candidateLyrics.isNullOrBlank()) {
            val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(candidateLyrics)
            LyricsResult(lyrics = candidateLyrics, isSynced = isSynced, source = sourceLabel)
        } else null
    }

    /**
     * Parsea átomos MP4/M4A ("©lyr" / "data").
     */
    fun parseMp4Bytes(bytes: ByteArray): String? {
        if (bytes.size < 16) return null
        val lyrMarker = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        var idx = 0
        while (idx < bytes.size - 8) {
            if (bytes[idx] == lyrMarker[0] && bytes[idx+1] == lyrMarker[1] &&
                bytes[idx+2] == lyrMarker[2] && bytes[idx+3] == lyrMarker[3]) {
                val atomStart = (idx - 4).coerceAtLeast(0)
                val atomSize = ((bytes[atomStart].toInt() and 0xFF) shl 24) or
                               ((bytes[atomStart+1].toInt() and 0xFF) shl 16) or
                               ((bytes[atomStart+2].toInt() and 0xFF) shl 8) or
                               (bytes[atomStart+3].toInt() and 0xFF)
                val end = (atomStart + atomSize).coerceAtMost(bytes.size)
                val dataMarker = "data".toByteArray(Charsets.US_ASCII)
                var dIdx = idx + 4
                while (dIdx < end - 12) {
                    if (bytes[dIdx] == dataMarker[0] && bytes[dIdx+1] == dataMarker[1] &&
                        bytes[dIdx+2] == dataMarker[2] && bytes[dIdx+3] == dataMarker[3]) {
                        val payloadStart = dIdx + 12
                        val payloadLen = end - payloadStart
                        if (payloadLen > 0) {
                            val text = decodeTextSmart(bytes.copyOfRange(payloadStart, end))
                            if (text.isNotBlank()) return cleanExtractedLyrics(text)
                        }
                    }
                    dIdx++
                }
            }
            idx++
        }
        return null
    }

    /**
     * Escaneo profundo por patrón de marcas de tiempo LRC [mm:ss.xx] en el búfer de metadatos.
     */
    fun scanBufferForLrc(bytes: ByteArray, limit: Int = bytes.size): String? {
        if (limit < 30) return null
        val maxSearch = limit.coerceAtMost(bytes.size)
        val sampleText = decodeTextSmart(bytes.copyOfRange(0, maxSearch))
        val matches = Regex("""\[\d{2}:\d{2}(?:[.:]\d{1,3})?\]""").findAll(sampleText).toList()
        if (matches.size >= 3) {
            val firstIdx = matches.first().range.first
            val lines = sampleText.substring(firstIdx).lines()
            val lrcLines = mutableListOf<String>()
            for (line in lines) {
                val t = line.trim()
                if (t.matches(Regex("""^\[\d{2}:\d{2}.*""")) || (lrcLines.isNotEmpty() && t.isNotBlank())) {
                    lrcLines.add(t)
                } else if (lrcLines.size >= 4 && t.isBlank()) {
                    break
                }
            }
            if (lrcLines.size >= 3) {
                val res = lrcLines.joinToString("\n").trim()
                if (res.isNotBlank()) return cleanExtractedLyrics(res)
            }
        }
        return null
    }

    private fun decodeWithEncoding(bytes: ByteArray, offset: Int, length: Int, encoding: Int): String {
        if (length <= 0 || offset + length > bytes.size) return ""
        val slice = bytes.copyOfRange(offset, offset + length)
        return when (encoding) {
            0 -> decodeTextSmart(slice) // ISO-8859-1 o Windows-1252
            1 -> {
                // UTF-16 con BOM (o fallback inteligente)
                if (slice.size >= 2 && slice[0] == 0xFE.toByte() && slice[1] == 0xFF.toByte()) {
                    String(slice, 2, slice.size - 2, Charsets.UTF_16BE)
                } else if (slice.size >= 2 && slice[0] == 0xFF.toByte() && slice[1] == 0xFE.toByte()) {
                    String(slice, 2, slice.size - 2, Charsets.UTF_16LE)
                } else {
                    decodeTextSmart(slice)
                }
            }
            2 -> {
                try { String(slice, Charsets.UTF_16BE) } catch (_: Exception) { decodeTextSmart(slice) }
            }
            3 -> {
                try { String(slice, Charsets.UTF_8) } catch (_: Exception) { decodeTextSmart(slice) }
            }
            else -> decodeTextSmart(slice)
        }.trim { it <= ' ' || it == '\u0000' }
    }

    // =========================================================================
    // 2. ESCÁNER VÍA JAUDIOTAGGER (COMPLEMENTARIO)
    // =========================================================================

    private fun extractEmbeddedLyrics(context: Context, audioUri: Uri, audioFilePath: String?): LyricsResult? {
        var tempFile: File? = null
        try {
            var fileToRead: File? = null
            if (audioFilePath != null) {
                val direct = File(audioFilePath)
                if (direct.exists() && direct.length() > 0 && direct.canRead()) {
                    fileToRead = direct
                }
            }
            if (fileToRead == null && audioUri != Uri.EMPTY) {
                tempFile = createTempAudioFile(context, audioUri, audioFilePath)
                fileToRead = tempFile
            }

            if (fileToRead != null && fileToRead.exists() && fileToRead.length() > 0) {
                val audioFile = AudioFileIO.read(fileToRead)
                val tag: Tag? = audioFile.tag

                if (tag != null) {
                    // ID3v2 Tags
                    if (tag is AbstractID3v2Tag) {
                        // 1. SYLT (Sincronizadas directas en ID3v2)
                        try {
                            val syltRaw = tag.getFrame("SYLT")
                            val syltList = when (syltRaw) {
                                is List<*> -> syltRaw
                                null -> emptyList()
                                else -> listOf(syltRaw)
                            }
                            for (frameObj in syltList) {
                                val syltFrame = frameObj as? AbstractID3v2Frame ?: continue
                                if (syltFrame.body is FrameBodySYLT) {
                                    val syltBody = syltFrame.body as FrameBodySYLT
                                    val lyricsBytes = syltBody.lyrics
                                    if (lyricsBytes != null && lyricsBytes.isNotEmpty()) {
                                        val parsedLrc = parseSyltToLrc(lyricsBytes)
                                        if (parsedLrc.isNotBlank() && !isCorruptedLyrics(parsedLrc)) {
                                            return LyricsResult(lyrics = cleanExtractedLyrics(parsedLrc), isSynced = true, source = "JAUDIOTAGGER_ID3_SYLT")
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}

                        // 2. USLT / ULT (No sincronizadas o LRC incrustado en USLT como hace Musicolet)
                        val usltKeys = listOf("USLT", "ULT")
                        for (key in usltKeys) {
                            try {
                                val usltRaw = tag.getFrame(key)
                                val usltList = when (usltRaw) {
                                    is List<*> -> usltRaw
                                    null -> emptyList()
                                    else -> listOf(usltRaw)
                                }
                                for (frameObj in usltList) {
                                    val usltFrame = frameObj as? AbstractID3v2Frame ?: continue
                                    if (usltFrame.body is FrameBodyUSLT) {
                                        val body = usltFrame.body as FrameBodyUSLT
                                        val lyricText = body.lyric.takeIf { !it.isNullOrBlank() }
                                            ?: (if (body.description != null && (body.description.contains("\n") || Regex("\\[\\d{2}:\\d{2}").containsMatchIn(body.description))) body.description else null)
                                        if (!lyricText.isNullOrBlank()) {
                                            val clean = cleanExtractedLyrics(lyricText)
                                            if (clean.isNotBlank() && !isCorruptedLyrics(clean)) {
                                                val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(clean)
                                                return LyricsResult(lyrics = clean, isSynced = isSynced, source = "JAUDIOTAGGER_ID3_$key")
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        // 3. FieldKey.LYRICS mapeo estándar JAudioTagger
                        try {
                            val standardLyrics = tag.getFirst(FieldKey.LYRICS)
                            if (!standardLyrics.isNullOrBlank()) {
                                val clean = cleanExtractedLyrics(standardLyrics)
                                if (clean.isNotBlank() && !isCorruptedLyrics(clean)) {
                                    val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(clean)
                                    return LyricsResult(lyrics = clean, isSynced = isSynced, source = "JAUDIOTAGGER_FIELD_LYRICS")
                                }
                            }
                        } catch (_: Exception) {}

                        // 4. TXXX (User frames: LYRICS, UNSYNCEDLYRICS, SYNCEDLYRICS, LRC)
                        try {
                            val txxxFrames = tag.getFrame("TXXX")
                            if (txxxFrames != null) {
                                val framesList = if (txxxFrames is List<*>) txxxFrames else listOf(txxxFrames)
                                for (frame in framesList) {
                                    val txxxFrame = frame as? AbstractID3v2Frame
                                    if (txxxFrame?.body is FrameBodyTXXX) {
                                        val body = txxxFrame.body as FrameBodyTXXX
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
                                            val lyricText = body.text
                                            if (!lyricText.isNullOrBlank()) {
                                                val clean = cleanExtractedLyrics(lyricText)
                                                if (clean.isNotBlank() && !isCorruptedLyrics(clean)) {
                                                    val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(clean)
                                                    return LyricsResult(lyrics = clean, isSynced = isSynced, source = "JAUDIOTAGGER_ID3_TXXX")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}

                        // 5. COMM (Comentarios SOLO si contienen marcas de tiempo o descripción explícita de letra)
                        try {
                            val commFrames = tag.getFrame("COMM")
                            if (commFrames != null) {
                                val framesList = if (commFrames is List<*>) commFrames else listOf(commFrames)
                                for (frame in framesList) {
                                    val commFrame = frame as? AbstractID3v2Frame
                                    if (commFrame?.body is FrameBodyCOMM) {
                                        val body = commFrame.body as FrameBodyCOMM
                                        val desc = body.description.uppercase()
                                        val text = body.text
                                        if (!text.isNullOrBlank() && (desc == "LYRICS" || Regex("\\[\\d{2}:\\d{2}").containsMatchIn(text))) {
                                            val clean = cleanExtractedLyrics(text)
                                            if (clean.isNotBlank() && !isCorruptedLyrics(clean)) {
                                                val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(clean)
                                                return LyricsResult(lyrics = clean, isSynced = isSynced, source = "JAUDIOTAGGER_ID3_COMM")
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    } else {
                        // FieldKey.LYRICS para FLAC, MP4 ©lyr, OGG Vorbis
                        try {
                            val standardLyrics = tag.getFirst(FieldKey.LYRICS)
                            if (!standardLyrics.isNullOrBlank()) {
                                val clean = cleanExtractedLyrics(standardLyrics)
                                if (clean.isNotBlank() && !isCorruptedLyrics(clean)) {
                                    val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(clean)
                                    return LyricsResult(lyrics = clean, isSynced = isSynced, source = "JAUDIOTAGGER_FIELD_LYRICS")
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // Vorbis Comments (FLAC y OGG) - Filtrando claves residuales como COMMENT o DESCRIPTION
                    if (tag is FlacTag || tag is VorbisCommentTag) {
                        val candidateKeys = listOf(
                            "LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "SYNCEDLYRICS",
                            "SYNCED LYRICS", "LYRICS_SYNCED", "LRC"
                        )
                        for (key in candidateKeys) {
                            try {
                                val valText = tag.getFirst(key)
                                if (!valText.isNullOrBlank()) {
                                    val clean = cleanExtractedLyrics(valText)
                                    if (clean.isNotBlank() && !isCorruptedLyrics(clean)) {
                                        val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(clean)
                                        return LyricsResult(lyrics = clean, isSynced = isSynced, source = "JAUDIOTAGGER_VORBIS_$key")
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    // Iteración estructurada de campos de la etiqueta (evitando volcados de depuración field.toString())
                    try {
                        val fields = tag.fields
                        while (fields.hasNext()) {
                            val field = fields.next()
                            val idUpper = field.id.uppercase()
                            val isBlacklistedField = idUpper.contains("LYRICIST") || idUpper.contains("AUTHOR") ||
                                idUpper.contains("URL") || idUpper.contains("SITE") || idUpper.contains("ID") ||
                                idUpper.contains("LANG") || idUpper.contains("COPYRIGHT") || idUpper.contains("SORT")
                            val isAcceptedField = !isBlacklistedField && (
                                idUpper == "USLT" || idUpper == "ULT" || idUpper == "SYLT" || idUpper == "LRC" ||
                                idUpper == "LYRICS" || idUpper == "UNSYNCEDLYRICS" || idUpper == "UNSYNCED LYRICS" ||
                                idUpper == "SYNCEDLYRICS" || idUpper == "SYNCED LYRICS" || idUpper == "LYRICS_SYNCED" ||
                                (idUpper.startsWith("LYRIC") && !idUpper.contains("IST"))
                            )
                            if (isAcceptedField) {
                                val content = when {
                                    field is org.jaudiotagger.tag.TagTextField -> field.content
                                    field is AbstractID3v2Frame && field.body is FrameBodyUSLT -> (field.body as FrameBodyUSLT).lyric
                                    field is AbstractID3v2Frame && field.body is FrameBodySYLT -> parseSyltToLrc((field.body as FrameBodySYLT).lyrics ?: ByteArray(0))
                                    else -> null
                                }
                                if (!content.isNullOrBlank() && content.length > 10) {
                                    val clean = cleanExtractedLyrics(content)
                                    if (clean.isNotBlank() && !isCorruptedLyrics(clean)) {
                                        val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(clean)
                                        return LyricsResult(lyrics = clean, isSynced = isSynced, source = "JAUDIOTAGGER_FIELD_${field.id.uppercase()}")
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "extractEmbeddedLyrics error: ${e.message}")
        } finally {
            try { tempFile?.delete() } catch (_: Exception) {}
        }
        return null
    }

    fun parseSyltToLrc(lyricsBytes: ByteArray): String {
        try {
            if (lyricsBytes.size < 6) return ""
            var offset = 0
            val encoding = lyricsBytes[0].toInt() and 0xFF
            val isUtf16 = encoding == 1 || encoding == 2

            // Si la cabecera SYLT está presente (códigos de idioma en bytes 1..3 son letras ASCII como 'eng'):
            val hasHeader = lyricsBytes.size >= 6 &&
                    lyricsBytes[1].toInt() in 65..122 &&
                    lyricsBytes[2].toInt() in 65..122 &&
                    lyricsBytes[3].toInt() in 65..122

            if (hasHeader) {
                // Saltar encoding (1) + language (3) + format (1) + content type (1) = 6 bytes
                offset = 6
                // Saltar descriptor de contenido (terminado en null)
                if (isUtf16) {
                    while (offset + 1 < lyricsBytes.size) {
                        if (lyricsBytes[offset] == 0.toByte() && lyricsBytes[offset + 1] == 0.toByte()) {
                            offset += 2
                            break
                        }
                        offset += 2
                    }
                } else {
                    while (offset < lyricsBytes.size) {
                        if (lyricsBytes[offset] == 0.toByte()) {
                            offset += 1
                            break
                        }
                        offset += 1
                    }
                }
            }

            val builder = java.lang.StringBuilder()
            while (offset < lyricsBytes.size) {
                var textEnd = offset
                if (isUtf16) {
                    while (textEnd + 1 < lyricsBytes.size) {
                        if (lyricsBytes[textEnd] == 0.toByte() && lyricsBytes[textEnd + 1] == 0.toByte()) {
                            break
                        }
                        textEnd += 2
                    }
                } else {
                    while (textEnd < lyricsBytes.size && lyricsBytes[textEnd] != 0.toByte()) {
                        textEnd++
                    }
                }
                if (textEnd >= lyricsBytes.size) break

                val lineBytes = lyricsBytes.copyOfRange(offset, textEnd)
                val text = decodeTextSmart(lineBytes).trim()

                offset = if (isUtf16) textEnd + 2 else textEnd + 1
                if (offset + 3 < lyricsBytes.size) {
                    val t1 = lyricsBytes[offset].toInt() and 0xFF
                    val t2 = lyricsBytes[offset + 1].toInt() and 0xFF
                    val t3 = lyricsBytes[offset + 2].toInt() and 0xFF
                    val t4 = lyricsBytes[offset + 3].toInt() and 0xFF
                    val timestampMs = ((t1.toLong() shl 24) or (t2.toLong() shl 16) or (t3.toLong() shl 8) or t4.toLong())

                    if (timestampMs in 0..86_400_000L) {
                        val minutes = timestampMs / 60000
                        val seconds = (timestampMs % 60000) / 1000
                        val hundredths = (timestampMs % 1000) / 10

                        val timeStr = String.format("[%02d:%02d.%02d]", minutes, seconds, hundredths)
                        if (text.isNotBlank() && !isCorruptedLyrics(text)) {
                            builder.append(timeStr).append(text).append("\n")
                        }
                    }
                    offset += 4
                } else {
                    break
                }
            }
            return builder.toString().trim()
        } catch (e: Exception) {
            return ""
        }
    }

    fun checkAdjacentLyricsFile(audioPath: String?): String? {
        if (audioPath.isNullOrBlank()) return null
        val audioFile = File(audioPath)
        val parentDir = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension
        val fullName = audioFile.name
        val extensions = listOf("lrc", "txt", "srt", "vtt")

        // Buscar en la misma carpeta y en subcarpetas comunes
        val candidateDirs = listOf(
            parentDir,
            File(parentDir, "Lyrics"),
            File(parentDir, "lyrics"),
            File(parentDir, "LRC"),
            File(parentDir, "lrc")
        )

        // Candidatos de nombre: baseName ("song") y fullName ("song.opus" -> "song.opus.lrc" típico de yt-dlp)
        val candidateNames = if (baseName != fullName) listOf(baseName, fullName) else listOf(baseName)

        for (dir in candidateDirs) {
            if (!dir.exists() || !dir.isDirectory) continue

            // 1. Coincidencia directa rápida
            for (name in candidateNames) {
                for (ext in extensions) {
                    val candidate = File(dir, "$name.$ext")
                    if (candidate.exists() && candidate.canRead()) {
                        val bytes = try { candidate.readBytes() } catch (_: Exception) { null }
                        if (bytes != null && bytes.isNotEmpty()) {
                            val decoded = decodeTextSmart(bytes)
                            if (decoded.isNotBlank()) {
                                return if (ext == "srt" || ext == "vtt") convertSubtitleToLrc(decoded) else decoded
                            }
                        }
                    }
                }
            }

            // 2. Coincidencia insensible a mayúsculas/minúsculas en el directorio
            val filesInDir = try { dir.listFiles() } catch (_: Exception) { null }
            if (filesInDir != null) {
                for (name in candidateNames) {
                    for (ext in extensions) {
                        val targetLower = "$name.$ext".lowercase()
                        val match = filesInDir.firstOrNull { it.name.lowercase() == targetLower && it.canRead() }
                        if (match != null) {
                            val bytes = try { match.readBytes() } catch (_: Exception) { null }
                            if (bytes != null && bytes.isNotEmpty()) {
                                val decoded = decodeTextSmart(bytes)
                                if (decoded.isNotBlank()) {
                                    return if (ext == "srt" || ext == "vtt") convertSubtitleToLrc(decoded) else decoded
                                }
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Decodificación inteligente y tolerante a fallos para letras en múltiples codificaciones:
     * UTF-8 (con y sin BOM), UTF-16 LE/BE, Windows-1252, ISO-8859-1 (acentos en español: á, é, í, ó, ú, ñ).
     */
    fun decodeTextSmart(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        // 1. UTF-8 con BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8).trim()
        }
        // 2. UTF-16 LE con BOM
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE).trim()
        }
        // 3. UTF-16 BE con BOM
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE).trim()
        }

        // 4. Intento estricto de UTF-8
        try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val charBuffer = decoder.decode(ByteBuffer.wrap(bytes))
            val res = charBuffer.toString().trim()
            if (res.isNotBlank()) return res
        } catch (_: Exception) {}

        // 5. Windows-1252 (acentos latinos y signos especiales)
        try {
            val win1252 = Charset.forName("Windows-1252")
            val decoded = String(bytes, win1252).trim()
            if (decoded.isNotBlank() && !decoded.contains("\uFFFD")) return decoded
        } catch (_: Exception) {}

        // 6. ISO-8859-1 estándar
        try {
            val decoded = String(bytes, Charsets.ISO_8859_1).trim()
            if (decoded.isNotBlank()) return decoded
        } catch (_: Exception) {}

        // 7. Fallback general a UTF-8 reemplazando caracteres inválidos
        return String(bytes, Charsets.UTF_8).trim()
    }

    private fun convertSubtitleToLrc(content: String): String {
        if (content.contains(Regex("(\\[|<)\\d{1,3}:\\d{1,2}"))) {
            return content
        }
        val builder = StringBuilder()
        val lines = content.lines()
        val timeRegex = Regex("(?:\\d{2}:)?(\\d{2}):(\\d{2})[,.](\\d{2,3})\\s*-->.*")
        var currentTimestamp = ""
        for (line in lines) {
            val match = timeRegex.find(line)
            if (match != null) {
                val min = match.groupValues[1]
                val sec = match.groupValues[2]
                var milli = match.groupValues[3]
                if (milli.length == 3) milli = milli.substring(0, 2)
                currentTimestamp = "[$min:$sec.$milli]"
            } else if (line.isNotBlank() && !line.matches(Regex("^\\d+$")) && !line.startsWith("WEBVTT")) {
                if (currentTimestamp.isNotBlank()) {
                    builder.append(currentTimestamp).append(line).append("\n")
                    currentTimestamp = ""
                }
            }
        }
        if (builder.isNotEmpty()) {
            return builder.toString().trim()
        }
        return content
    }

    private fun createTempAudioFile(context: Context, uri: Uri, fallbackPath: String?): File? {
        return try {
            val ext = when {
                !fallbackPath.isNullOrBlank() && fallbackPath.contains(".") -> {
                    "." + fallbackPath.substringAfterLast(".").lowercase()
                }
                else -> {
                    val mime = context.contentResolver.getType(uri)?.lowercase() ?: ""
                    when {
                        mime.contains("flac") -> ".flac"
                        mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") -> ".m4a"
                        mime.contains("ogg") -> ".ogg"
                        mime.contains("opus") -> ".opus"
                        mime.contains("wav") -> ".wav"
                        else -> ".mp3"
                    }
                }
            }
            val temp = File.createTempFile("audio_scan_", ext, context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output)
                }
            }
            temp
        } catch (e: Exception) {
            null
        }
    }

    fun extractOpusOrOggLyrics(context: Context, audioUri: Uri, audioFilePath: String?): LyricsResult? {
        var inputStream: InputStream? = null
        try {
            if (!audioFilePath.isNullOrBlank()) {
                val f = File(audioFilePath)
                if (f.exists() && f.canRead()) {
                    inputStream = FileInputStream(f)
                }
            }
            if (inputStream == null && audioUri != Uri.EMPTY) {
                try {
                    inputStream = context.contentResolver.openInputStream(audioUri)
                } catch (_: Exception) {}
            }
            if (inputStream == null) return null

            // Leemos hasta 4MB para capturar cabeceras de Vorbis / Opus completas
            val maxHeaderBytes = 4 * 1024 * 1024
            val buffer = ByteArray(maxHeaderBytes)
            var totalRead = 0
            inputStream.use { input ->
                while (totalRead < maxHeaderBytes) {
                    val r = input.read(buffer, totalRead, maxHeaderBytes - totalRead)
                    if (r <= 0) break
                    totalRead += r
                }
            }

            if (totalRead < 16) return null
            val headerBytes = if (totalRead == maxHeaderBytes) buffer else buffer.copyOfRange(0, totalRead)

            return parseOggOpusBytes(headerBytes)
        } catch (e: Exception) {
            Log.d(TAG, "extractOpusOrOggLyrics error: ${e.message}")
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
        return null
    }

    fun parseOggOpusBytes(bytes: ByteArray): LyricsResult? {
        if (bytes.size < 16) return null

        // 1. Si el archivo Opus tiene una cabecera ID3v2 antepuesta (común en yt-dlp / ffmpeg)
        if (bytes.size >= 10 && bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()) {
            val id3Result = parseId3v2Bytes(bytes)
            if (id3Result != null && !id3Result.lyrics.isNullOrBlank() && !isCorruptedLyrics(id3Result.lyrics)) {
                return id3Result
            }
        }

        // 2. Extraer y reensamblar paquetes Ogg (OpusTags / VorbisComment) a través de las páginas OggS
        val pageResult = parseOggPackets(bytes)
        if (pageResult != null && !pageResult.lyrics.isNullOrBlank() && !isCorruptedLyrics(pageResult.lyrics)) {
            return pageResult
        }

        // 3. Fallback: Escaneo heurístico en búfer para marcadores OpusTags / VorbisComment
        val bufferScanResult = scanBufferForVorbisOrOpusLyrics(bytes)
        if (bufferScanResult != null && !bufferScanResult.lyrics.isNullOrBlank() && !isCorruptedLyrics(bufferScanResult.lyrics)) {
            return bufferScanResult
        }

        return null
    }

    private fun parseOggPackets(bytes: ByteArray): LyricsResult? {
        var offset = 0
        // Saltar cabecera ID3v2 si está presente para llegar al primer OggS
        if (bytes.size >= 10 && bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()) {
            val tagSize = ((bytes[6].toInt() and 0x7F) shl 21) or
                          ((bytes[7].toInt() and 0x7F) shl 14) or
                          ((bytes[8].toInt() and 0x7F) shl 7) or
                          (bytes[9].toInt() and 0x7F)
            val id3End = 10 + tagSize
            if (id3End < bytes.size - 27) {
                offset = id3End
            }
        }

        val packetStream = ByteArrayOutputStream()
        var targetPacketFound = false

        while (offset <= bytes.size - 27) {
            // Buscar "OggS"
            if (bytes[offset] != 'O'.code.toByte() ||
                bytes[offset + 1] != 'g'.code.toByte() ||
                bytes[offset + 2] != 'g'.code.toByte() ||
                bytes[offset + 3] != 'S'.code.toByte()) {
                offset++
                continue
            }

            val segmentsCount = bytes[offset + 26].toInt() and 0xFF
            val segTableOffset = offset + 27

            if (segTableOffset + segmentsCount > bytes.size) break

            var pageDataOffset = segTableOffset + segmentsCount
            var segTableIdx = 0

            while (segTableIdx < segmentsCount) {
                val segLen = bytes[segTableOffset + segTableIdx].toInt() and 0xFF
                if (pageDataOffset + segLen > bytes.size) break

                if (!targetPacketFound) {
                    if (segLen >= 8 &&
                        bytes[pageDataOffset] == 'O'.code.toByte() &&
                        bytes[pageDataOffset + 1] == 'p'.code.toByte() &&
                        bytes[pageDataOffset + 2] == 'u'.code.toByte() &&
                        bytes[pageDataOffset + 3] == 's'.code.toByte() &&
                        bytes[pageDataOffset + 4] == 'T'.code.toByte() &&
                        bytes[pageDataOffset + 5] == 'a'.code.toByte() &&
                        bytes[pageDataOffset + 6] == 'g'.code.toByte() &&
                        bytes[pageDataOffset + 7] == 's'.code.toByte()) {
                        targetPacketFound = true
                        packetStream.reset()
                    } else if (segLen >= 7 &&
                        bytes[pageDataOffset] == 3.toByte() &&
                        bytes[pageDataOffset + 1] == 'v'.code.toByte() &&
                        bytes[pageDataOffset + 2] == 'o'.code.toByte() &&
                        bytes[pageDataOffset + 3] == 'r'.code.toByte() &&
                        bytes[pageDataOffset + 4] == 'b'.code.toByte() &&
                        bytes[pageDataOffset + 5] == 'i'.code.toByte() &&
                        bytes[pageDataOffset + 6] == 's'.code.toByte()) {
                        targetPacketFound = true
                        packetStream.reset()
                    }
                }

                if (targetPacketFound) {
                    packetStream.write(bytes, pageDataOffset, segLen)

                    if (segLen < 255) {
                        val packetBytes = packetStream.toByteArray()
                        val result = parseVorbisCommentBlockToResult(packetBytes, "OGG_OPUS_TAGS")
                        if (result != null && !result.lyrics.isNullOrBlank() && !isCorruptedLyrics(result.lyrics)) {
                            return result
                        }
                        targetPacketFound = false
                        packetStream.reset()
                    }
                }

                pageDataOffset += segLen
                segTableIdx++
            }

            offset = pageDataOffset
        }

        if (targetPacketFound && packetStream.size() > 8) {
            val packetBytes = packetStream.toByteArray()
            val result = parseVorbisCommentBlockToResult(packetBytes, "OGG_OPUS_TAGS")
            if (result != null && !result.lyrics.isNullOrBlank() && !isCorruptedLyrics(result.lyrics)) {
                return result
            }
        }

        return null
    }

    private fun scanBufferForVorbisOrOpusLyrics(bytes: ByteArray): LyricsResult? {
        val opusMagic = "OpusTags".toByteArray(Charsets.US_ASCII)
        var idx = 0
        while (idx < bytes.size - opusMagic.size - 8) {
            if (bytes[idx] == opusMagic[0] && bytes[idx+1] == opusMagic[1] &&
                bytes[idx+2] == opusMagic[2] && bytes[idx+3] == opusMagic[3] &&
                bytes[idx+4] == opusMagic[4] && bytes[idx+5] == opusMagic[5] &&
                bytes[idx+6] == opusMagic[6] && bytes[idx+7] == opusMagic[7]) {
                val slice = bytes.copyOfRange(idx, bytes.size)
                val res = parseVorbisCommentBlockToResult(slice, "OPUS_BUFFER_MAGIC")
                if (res != null && !res.lyrics.isNullOrBlank() && !isCorruptedLyrics(res.lyrics)) {
                    return res
                }
            }
            idx++
        }

        // Búsqueda de tags explícitos en texto bruto en el búfer
        val rawLrc = scanBufferForLrc(bytes)
        if (!rawLrc.isNullOrBlank() && !isCorruptedLyrics(rawLrc)) {
            val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(rawLrc)
            return LyricsResult(lyrics = rawLrc, isSynced = isSynced, source = "OPUS_BUFFER_LRC_SCAN")
        }

        return null
    }

    /**
     * Valida si un texto recuperado como letra es en realidad datos residuales, metadatos de ripeo,
     * volcados de depuración de frames o ruido binario SIN contenido real de canciones.
     */
    fun isCorruptedLyrics(lyrics: String?): Boolean {
        if (lyrics.isNullOrBlank()) return true
        val trimmed = lyrics.trim()
        val len = trimmed.length

        // 1. Longitud mínima insuficiente para cualquier letra
        if (len < 3) return true

        // 2. Contiene demasiados caracteres de reemplazo Unicode (\uFFFD) producto de mala decodificación
        if (trimmed.count { it == '\uFFFD' } >= 4) return true

        // 3. Descriptores / palabras clave únicas o etiquetas de metadatos prohibidas
        val singleBadTokens = setOf(
            "eng", "spa", "und", "xxx", "lyrics", "lyric", "text", "none", "unknown",
            "simple", "synced", "unsynced", "no lyrics", "null", "description",
            "comment", "comments", "notes", "information", "soundcheck", "itunnorm",
            "itunsmpb", "itunpgap", "accuraterip", "musicbrainz", "freedb", "cddb",
            "track", "default", "untitled", "stereo", "mono", "audio", "flac", "mp3",
            "uslt", "sylt", "comm", "txxx", "cover", "front", "back", "album", "artist",
            "title", "genre", "year", "composer", "lyricist", "performer", "producer",
            "encoder", "encoding", "settings", "media", "format", "source", "author"
        )
        if (singleBadTokens.contains(trimmed.lowercase())) return true

        // 4. Volcado de inspección de clases o frames de JAudioTagger
        if (trimmed.startsWith("Frame:") || trimmed.startsWith("org.jaudiotagger") ||
            trimmed.startsWith("AbstractID3v2Frame") || trimmed.contains("FrameBodyUSLT") ||
            trimmed.contains("FrameBodySYLT") || trimmed.contains("FrameBodyCOMM") ||
            trimmed.contains("FrameBodyTXXX")) {
            return true
        }

        // 5. SoundCheck / Normalización / ReplayGain
        val soundCheckPattern = Regex("""(?i)^[0-9a-f]{8}(?:\s+[0-9a-f]{8}){2,}""")
        if (soundCheckPattern.containsMatchIn(trimmed)) return true
        if (trimmed.matches(Regex("""(?i)^00000000\s+[0-9a-f]{8}.*"""))) return true
        if (trimmed.matches(Regex("""(?i)^[+-]?\d+(?:\.\d+)?\s*dB(?:\s+[+-]?\d+(?:\.\d+)?)*$"""))) return true

        // 6. UUIDs, Hashes y Códigos de Identificación (ISRC, AcoustID, Barcode, etc.)
        if (trimmed.matches(Regex("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"""))) return true
        if (trimmed.matches(Regex("""(?i)^[a-z]{2}-?[a-z0-9]{3}-?\d{2}-?\d{5}$"""))) return true
        if (trimmed.matches(Regex("""^[0-9a-fA-F]{32,64}$"""))) return true

        // 7. URLs o dominios solos sin letra real
        if (trimmed.matches(Regex("""(?i)^https?://[^\s]+$""")) || trimmed.matches(Regex("""(?i)^www\.[a-z0-9.-]+\.[a-z]{2,}(?:/[^\s]*)?$"""))) return true

        // 8. Ripeadores, Encoders y Software sin contenido lírico
        val encoderPattern = Regex("""(?i)^\s*(?:Exact\s*Audio\s*Copy|LAME|Lavf|Audiograbber|dBpoweramp|Ripped\s*by|Encoded\s*by|Track\s*\d+|CD\s*Track|EAC\s*v?\d+|CDex|FLAC\s*\d+).*""")
        if (encoderPattern.matches(trimmed) && !trimmed.contains("\n")) return true

        // 9. Copyright / Licencia sola de una línea
        if (trimmed.matches(Regex("""(?i)^\s*(?:\(c\)|copyright|\(p\)|under\s*license|all\s*rights\s*reserved).{0,100}$"""))) return true

        // 10. Si contiene marcas de tiempo LRC válidas ([mm:ss)
        val hasLrcTimestamps = Regex("""\[\d{2}:\d{2}""").containsMatchIn(trimmed)
        if (hasLrcTimestamps) {
            // Verificar que no sea ÚNICAMENTE cabeceras [ti:...], [ar:...] o publicidad
            val nonHeaderLines = trimmed.lines().map { it.trim() }.filter { line ->
                line.isNotBlank() &&
                !line.matches(Regex("""(?i)^\[\s*[a-zA-Z_ ]{1,24}\s*:[^\]]*\]$""")) &&
                !line.matches(Regex("""(?i)^\[\d{2}:\d{2}(?:[.:]\d{1,3})?\]\s*(?:synced\s*by|lyrics\s*(?:provided\s*)?by|created\s*by|editor|qq\s*music|musixmatch|minilyrics|lrc\s*maker|www\.|https?://).*"""))
            }
            if (nonHeaderLines.isEmpty()) return true
            return false
        }

        // 11. Para texto plano SIN marcas de tiempo (letras estáticas no sincronizadas):
        val meaningfulLines = trimmed.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                !line.matches(Regex("""(?i)^\[\s*[a-zA-Z_ ]{1,24}\s*:[^\]]*\]$""")) &&
                !line.matches(Regex("""(?i)^(?:ti|ar|al|by|offset|re|ve|length|artist|album|title|composer|lyricist|genre|year|comment|soundcheck|itunnorm)\s*:.*"""))
            }

        // Si todas las líneas eran cabeceras
        if (meaningfulLines.isEmpty()) return true

        // Si es solo 1 sola línea corta (menor a 60 caracteres o menos de 8 palabras):
        // A menos que sea explícitamente "Instrumental", un texto tan corto es casi con certeza
        // el nombre de un compositor ("Ludwig Göransson"), un comentario residual ("Great song"),
        // una categoría ("Pop") o residuo de etiqueta.
        if (meaningfulLines.size == 1) {
            val single = meaningfulLines.first()
            if (single.equals("Instrumental", ignoreCase = true) || single.equals("[Instrumental]", ignoreCase = true)) {
                return false // Instrumental legítimo
            }
            val wordCount = single.split(Regex("""\s+""")).count { it.isNotBlank() }
            if (single.length < 60 || wordCount < 8) {
                return true
            }
        }

        return false
    }

    /**
     * Limpia profundamente las letras extraídas:
     * - Extrae el contenido real de volcados de depuración ("Frame: USLT: Description=...; Lyrics=...", "Description=; Lyrics=...").
     * - Remueve prefijos de idioma ("eng||", "spa||", "eng|", etc.).
     * - Remueve campos residuales ("Description: ", "Language: ").
     * - Limpia caracteres nulos \u0000, marcas BOM y caracteres de control binarios.
     * - Repara posibles errores de codificación Mojibake.
     * - Descarta volcados hexadecimales residuales.
     */
    fun cleanExtractedLyrics(raw: String): String {
        if (raw.isBlank()) return ""
        var cleaned = raw.trim()

        // 1. Extraer letra de volcados de depuración de JAudioTagger / ID3v2 si existen
        val jaudioMatch = Regex("""(?i)(?:Frame:\s*[A-Z0-9]{3,4}:)?\s*(?:Description\s*=\s*[^;]*;)?\s*(?:Lyrics?|Text)\s*[:=]\s*["']?([\s\S]+?)(?:["']?\s*(?:;|\z))""").find(cleaned)
        if (jaudioMatch != null) {
            val candidate = jaudioMatch.groupValues[1].trim()
            if (candidate.isNotBlank()) {
                cleaned = candidate
            }
        }

        // Si aún contiene prefijo "Lyrics=" o "Lyrics:", extraer lo que sigue
        if (cleaned.contains("Lyrics=", ignoreCase = true) || cleaned.contains("Lyrics:", ignoreCase = true)) {
            val after = cleaned.substringAfter("Lyrics=", "").ifBlank {
                cleaned.substringAfter("lyrics=", "").ifBlank {
                    cleaned.substringAfter("Lyrics:", "").ifBlank {
                        cleaned.substringAfter("lyrics:", "")
                    }
                }
            }.trim().trim('"', '\'', ';')
            if (after.isNotBlank()) {
                cleaned = after
            }
        }

        // 2. Manejo de delimitador || de JAudioTagger / ID3v2 (ej: "eng||[00:12.00]Letra" o "eng||Description||Letra")
        if (cleaned.contains("||")) {
            val parts = cleaned.split("||").map { it.trim() }.filter { it.isNotBlank() }
            if (parts.isNotEmpty()) {
                val filteredParts = parts.filterNot { part ->
                    val lower = part.lowercase()
                    lower == "eng" || lower == "spa" || lower == "xxx" || lower == "und" ||
                    lower == "jpn" || lower == "ger" || lower == "fra" || lower == "ita" ||
                    lower == "chi" || lower == "zho" || lower == "rus" ||
                    lower == "description" || lower == "comment" || lower == "comments" ||
                    lower == "lyrics" || lower == "unsynced" || lower == "synced" ||
                    lower == "soundcheck" || lower == "itunnorm" || lower == "itunsmpb" ||
                    lower == "null" || lower.matches(Regex("""(?i)^[0-9a-f]{8}(?:\s+[0-9a-f]{8})*"""))
                }
                val bestPart = filteredParts.maxByOrNull { part ->
                    var score = part.length
                    if (part.contains("\n")) score += 300
                    if (Regex("""\[\d{2}:\d{2}""").containsMatchIn(part)) score += 500
                    score
                }
                if (bestPart != null && bestPart.length > 3) {
                    cleaned = bestPart
                } else {
                    cleaned = ""
                }
            }
        }

        // 3. Limpiar prefijos de idioma y descripción comunes
        val prefixes = listOf(
            "eng||", "spa||", "xxx||", "und||", "jpn||", "ger||", "fra||", "ita||", "chi||", "rus||", "||",
            "eng|", "spa|", "jpn|", "xxx|", "XXX|", "ger|", "fra|", "ita|", "und|", "chi|", "zho|", "rus|",
            "Simple|", "Synced|", "Unsynced|", "lyrics|", "Lyrics|",
            "Description: ", "Description=", "Description: ;", "Description=;", "Description:",
            "Comment: ", "Comment=", "Comments: ", "Comments=",
            "Language: eng", "Language: spa", "Language: xxx", "Language: und",
            "UNSYNCED LYRICS=", "UNSYNCED LYRICS: ", "UNSYNCED LYRICS:",
            "SYNCED LYRICS=", "SYNCED LYRICS: ", "SYNCED LYRICS:",
            "UNSYNCEDLYRICS=", "UNSYNCEDLYRICS: ", "UNSYNCEDLYRICS:",
            "SYNCEDLYRICS=", "SYNCEDLYRICS: ", "SYNCEDLYRICS:",
            "LYRICS_SYNCED=", "LYRICS_SYNCED: ", "LYRICS_SYNCED:",
            "LRC=", "LRC: ", "LRC:",
            "USLT=", "USLT: ", "USLT:",
            "SYLT=", "SYLT: ", "SYLT:",
            "Lyrics: ", "Lyric: ", "Lyrics=", "Lyric=", "Text: ", "Text="
        )
        var changed = true
        while (changed) {
            changed = false
            for (prefix in prefixes) {
                if (cleaned.startsWith(prefix, ignoreCase = true)) {
                    cleaned = cleaned.substring(prefix.length).trim()
                    changed = true
                }
            }
        }

        // 4. Si el texto resultante está envuelto en comillas residuales o termina en punto y coma residual, removerlos
        cleaned = cleaned.trim().trim(';', ',')
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length >= 2) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }
        if (cleaned.startsWith("'") && cleaned.endsWith("'") && cleaned.length >= 2) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }

        // 5. Eliminar caracteres nulos \u0000, BOM (\uFEFF, \uFFFE) y caracteres de control binarios
        cleaned = cleaned
            .replace("\u0000", "")
            .replace("\uFEFF", "")
            .replace("\uFFFE", "")
            .filter { c -> c == '\n' || c == '\r' || c == '\t' || c >= ' ' }
            .trim()

        // 6. Reparar Mojibake si está presente (ej. "canciÃ³n" -> "canción")
        if (cleaned.contains("Ã")) {
            cleaned = fixMojibakeIfPresent(cleaned)
        }

        // 7. Filtrar líneas de cabeceras o publicidad residual
        if (cleaned.lines().any { line ->
            val t = line.trim()
            t.startsWith("Frame:") || t.startsWith("Description=") || t.startsWith("iTunNORM") ||
            t.startsWith("iTunSMPB") || t.matches(Regex("""(?i)^\[\d{2}:\d{2}(?:[.:]\d{1,3})?\]\s*(?:synced\s*by|lyrics\s*(?:provided\s*)?by|created\s*by|editor|qq\s*music|musixmatch|minilyrics|lrc\s*maker|www\.|https?://).*"""))
        }) {
            cleaned = cleaned.lines()
                .filterNot { line ->
                    val t = line.trim()
                    t.startsWith("Frame:") || t.startsWith("Description=") || t.startsWith("iTunNORM") ||
                    t.startsWith("iTunSMPB") || t.matches(Regex("""(?i)^\[\d{2}:\d{2}(?:[.:]\d{1,3})?\]\s*(?:synced\s*by|lyrics\s*(?:provided\s*)?by|created\s*by|editor|qq\s*music|musixmatch|minilyrics|lrc\s*maker|www\.|https?://).*"""))
                }
                .joinToString("\n")
                .trim()
        }

        // 8. Si después de la limpieza el resultado es residual o corrupto, retornar vacío
        if (isCorruptedLyrics(cleaned)) {
            return ""
        }

        return cleaned
    }

    private fun fixMojibakeIfPresent(text: String): String {
        if (!text.contains("Ã")) return text
        return try {
            val bytes = text.toByteArray(Charsets.ISO_8859_1)
            val candidate = String(bytes, Charsets.UTF_8)
            if (!candidate.contains("\uFFFD") && candidate.length < text.length) {
                candidate
            } else {
                text
            }
        } catch (_: Exception) {
            text
        }
    }
}
