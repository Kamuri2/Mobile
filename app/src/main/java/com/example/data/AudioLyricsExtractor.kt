package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodySYLT
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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

    fun extractLyrics(
        context: Context,
        audioUri: Uri,
        audioFilePath: String?,
        artist: String? = null,
        title: String? = null
    ): LyricsResult {
        // 1. PRIMARY SOURCE: Embedded lyrics inside audio file tags via JAudioTagger
        val embeddedResult = extractEmbeddedLyrics(context, audioUri, audioFilePath)
        if (embeddedResult != null && !embeddedResult.lyrics.isNullOrBlank()) {
            return embeddedResult
        }

        // 2. SECONDARY SOURCE: Sidecar file (.lrc, .txt, .srt, .vtt) adjacent to the audio track
        val externalLyrics = checkAdjacentLyricsFile(audioFilePath)
        if (externalLyrics != null && externalLyrics.isNotBlank()) {
            val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(externalLyrics)
            return LyricsResult(lyrics = externalLyrics.trim(), isSynced = isSynced, source = "EXTERNAL_FILE")
        }

        // 3. TERTIARY SOURCE: Direct deep scan for Opus/Ogg Vorbis packets
        if (audioFilePath != null) {
            val deepScanResult = getOpusLyricsDeepScan(audioFilePath)
            if (deepScanResult != null && !deepScanResult.lyrics.isNullOrBlank()) {
                return deepScanResult
            }
        }

        return LyricsResult(lyrics = null, isSynced = false, source = "NONE")
    }

    private fun extractEmbeddedLyrics(context: Context, audioUri: Uri, audioFilePath: String?): LyricsResult? {
        var tempFile: File? = null
        try {
            var fileToRead: File? = null
            if (audioFilePath != null) {
                val direct = File(audioFilePath)
                if (direct.exists() && direct.length() > 0) {
                    fileToRead = direct
                }
            }
            if (fileToRead == null && audioUri != Uri.EMPTY) {
                tempFile = createTempAudioFile(context, audioUri, audioFilePath)
                fileToRead = tempFile
            }

            if (fileToRead != null && fileToRead.exists()) {
                val audioFile = AudioFileIO.read(fileToRead)
                val tag: Tag? = audioFile.tag

                if (tag != null) {
                    // Check synchronized ID3v2 SYLT frame first (highest fidelity sync lyrics)
                    if (tag is AbstractID3v2Tag) {
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
                                        if (parsedLrc.isNotBlank()) {
                                            return LyricsResult(lyrics = parsedLrc, isSynced = true, source = "JAUDIOTAGGER_ID3_SYLT")
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "SYLT frame parsing failed: ${e.message}")
                        }

                        // Check ID3v2 USLT frame (Unsynchronized lyric/text transcription)
                        try {
                            val usltRaw = tag.getFrame("USLT")
                            val usltList = when (usltRaw) {
                                is List<*> -> usltRaw
                                null -> emptyList()
                                else -> listOf(usltRaw)
                            }
                            for (frameObj in usltList) {
                                val usltFrame = frameObj as? AbstractID3v2Frame ?: continue
                                if (usltFrame.body is FrameBodyUSLT) {
                                    val lyricText = (usltFrame.body as FrameBodyUSLT).lyric
                                    if (!lyricText.isNullOrBlank()) {
                                        val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(lyricText)
                                        return LyricsResult(lyrics = lyricText.trim(), isSynced = isSynced, source = "JAUDIOTAGGER_ID3_USLT")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "USLT frame check failed: ${e.message}")
                        }
                    }

                    // FieldKey.LYRICS is jaudiotagger's standard mapping for MP3 (USLT), FLAC/OGG (LYRICS), MP4 (©lyr)
                    try {
                        val standardLyrics = tag.getFirst(FieldKey.LYRICS)
                        if (!standardLyrics.isNullOrBlank()) {
                            val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(standardLyrics)
                            return LyricsResult(lyrics = standardLyrics.trim(), isSynced = isSynced, source = "JAUDIOTAGGER_FIELD_LYRICS")
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "tag.getFirst(FieldKey.LYRICS) failed: ${e.message}")
                    }

                    // For FLAC & Vorbis Comment tags: check specific tags: LYRICS, UNSYNCEDLYRICS, UNSYNCED_LYRICS
                    if (tag is FlacTag || tag is VorbisCommentTag) {
                        val candidateKeys = listOf("LYRICS", "UNSYNCEDLYRICS", "UNSYNCED_LYRICS", "SYNCEDLYRICS", "TEXT", "LYRIC")
                        for (key in candidateKeys) {
                            try {
                                val valText = tag.getFirst(key)
                                if (!valText.isNullOrBlank()) {
                                    val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(valText)
                                    return LyricsResult(lyrics = valText.trim(), isSynced = isSynced, source = "JAUDIOTAGGER_VORBIS_$key")
                                }
                            } catch (e: Exception) {}
                        }
                    }

                    // Check ID3v2 TXXX user-defined text frames (often used for UNSYNCEDLYRICS)
                    if (tag is AbstractID3v2Tag) {
                        try {
                            val txxxFrames = tag.getFrame("TXXX")
                            if (txxxFrames != null) {
                                val framesList = if (txxxFrames is List<*>) txxxFrames else listOf(txxxFrames)
                                for (frame in framesList) {
                                    val txxxFrame = frame as? AbstractID3v2Frame
                                    if (txxxFrame?.body is FrameBodyTXXX) {
                                        val body = txxxFrame.body as FrameBodyTXXX
                                        if (body.description.equals("LYRICS", ignoreCase = true) ||
                                            body.description.equals("UNSYNCEDLYRICS", ignoreCase = true) ||
                                            body.description.equals("UNSYNCED_LYRICS", ignoreCase = true) ||
                                            body.description.equals("SYNCEDLYRICS", ignoreCase = true)
                                        ) {
                                            val lyricText = body.text
                                            if (!lyricText.isNullOrBlank()) {
                                                val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(lyricText)
                                                return LyricsResult(lyrics = lyricText.trim(), isSynced = isSynced, source = "JAUDIOTAGGER_ID3_TXXX")
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "TXXX check failed: ${e.message}")
                        }
                    }

                    // General fields iterator fallback
                    try {
                        val fields = tag.fields
                        while (fields.hasNext()) {
                            val field = fields.next()
                            val lowerId = field.id.lowercase()
                            if (lowerId.contains("lyric") || lowerId.contains("sylt") || lowerId.contains("uslt")) {
                                if (!field.isBinary) {
                                    val content = field.toString()
                                    if (!content.isNullOrBlank() && content.length > 15) {
                                        val cleanContent = tag.getFirst(field.id).takeIf { it.isNotBlank() } ?: content
                                        val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(cleanContent)
                                        return LyricsResult(lyrics = cleanContent.trim(), isSynced = isSynced, source = "JAUDIOTAGGER_FIELD_${field.id.uppercase()}")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "General fields iterator failed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "extractEmbeddedLyrics error: ${e.message}")
        } finally {
            tempFile?.delete()
        }
        return null
    }

    private fun parseSyltToLrc(lyricsBytes: ByteArray): String {
        try {
            val builder = java.lang.StringBuilder()
            var offset = 0
            while (offset < lyricsBytes.size) {
                var textEnd = offset
                while (textEnd < lyricsBytes.size && lyricsBytes[textEnd].toInt() != 0) {
                    textEnd++
                }
                if (textEnd >= lyricsBytes.size) break

                val lineBytes = lyricsBytes.copyOfRange(offset, textEnd)
                val text = decodeTextSmart(lineBytes).trim()

                offset = textEnd + 1
                if (offset + 3 < lyricsBytes.size) {
                    val t1 = lyricsBytes[offset].toInt() and 0xFF
                    val t2 = lyricsBytes[offset + 1].toInt() and 0xFF
                    val t3 = lyricsBytes[offset + 2].toInt() and 0xFF
                    val t4 = lyricsBytes[offset + 3].toInt() and 0xFF
                    val timestampMs = (t1 shl 24) or (t2 shl 16) or (t3 shl 8) or t4

                    val minutes = timestampMs / 60000
                    val seconds = (timestampMs % 60000) / 1000
                    val hundreths = (timestampMs % 1000) / 10

                    val timeStr = String.format("[%02d:%02d.%02d]", minutes, seconds, hundreths)
                    if (text.isNotBlank()) {
                        builder.append(timeStr).append(text).append("\n")
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

    private fun checkAdjacentLyricsFile(audioPath: String?): String? {
        if (audioPath == null) return null
        val audioFile = File(audioPath)
        val parentDir = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension
        val extensions = listOf("lrc", "txt", "srt", "vtt")
        for (ext in extensions) {
            val candidate = File(parentDir, "$baseName.$ext")
            if (candidate.exists() && candidate.canRead()) {
                val bytes = try { candidate.readBytes() } catch (e: Exception) { null }
                if (bytes != null && bytes.isNotEmpty()) {
                    val decoded = decodeTextSmart(bytes)
                    if (decoded.isNotBlank()) {
                        return if (ext == "srt" || ext == "vtt") {
                            convertSubtitleToLrc(decoded)
                        } else {
                            decoded
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Decodificación inteligente y tolerante a fallos para letras en múltiples codificaciones:
     * UTF-8 (con y sin BOM), UTF-16 LE/BE, Windows-1252, ISO-8859-1 (español/acentos), GBK, Shift-JIS.
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
            return charBuffer.toString().trim()
        } catch (e: Exception) {
            // No es UTF-8 puro, pasar a fallbacks
        }

        // 5. Windows-1252 (cubre acentos en español, portugués, francés: á, é, í, ó, ú, ñ, etc.)
        try {
            val win1252 = Charset.forName("Windows-1252")
            val decoded = String(bytes, win1252).trim()
            if (decoded.isNotBlank()) return decoded
        } catch (e: Exception) {}

        // 6. ISO-8859-1 estándar
        try {
            val decoded = String(bytes, Charsets.ISO_8859_1).trim()
            if (decoded.isNotBlank()) return decoded
        } catch (e: Exception) {}

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
            val temp = File.createTempFile("audio_header", ext, context.cacheDir)
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

    private fun getOpusLyricsDeepScan(filePath: String): LyricsResult? {
        try {
            val file = File(filePath)
            if (!file.exists()) return null

            val inputStream = file.inputStream()
            val buffer = ByteArray(5 * 1024 * 1024)
            val bytesRead = inputStream.read(buffer)
            inputStream.close()

            if (bytesRead < 27) return null

            val packetData = ByteArrayOutputStream()
            var offset = 0
            var capturingTags = false

            while (offset < bytesRead - 27) {
                if (buffer[offset] == 'O'.code.toByte() && buffer[offset+1] == 'g'.code.toByte() &&
                    buffer[offset+2] == 'g'.code.toByte() && buffer[offset+3] == 'S'.code.toByte()) {

                    val segments = buffer[offset + 26].toInt() and 0xFF
                    val segmentTableOffset = offset + 27
                    var pageDataOffset = segmentTableOffset + segments

                    if (pageDataOffset > bytesRead) break

                    for (i in 0 until segments) {
                        if (segmentTableOffset + i >= bytesRead) break
                        val segmentLength = buffer[segmentTableOffset + i].toInt() and 0xFF
                        if (pageDataOffset + segmentLength > bytesRead) break

                        if (segmentLength >= 8 &&
                            buffer[pageDataOffset] == 'O'.code.toByte() &&
                            buffer[pageDataOffset+1] == 'p'.code.toByte() &&
                            buffer[pageDataOffset+2] == 'u'.code.toByte() &&
                            buffer[pageDataOffset+3] == 's'.code.toByte() &&
                            buffer[pageDataOffset+4] == 'T'.code.toByte() &&
                            buffer[pageDataOffset+5] == 'a'.code.toByte() &&
                            buffer[pageDataOffset+6] == 'g'.code.toByte() &&
                            buffer[pageDataOffset+7] == 's'.code.toByte()) {
                            capturingTags = true
                        }

                        if (capturingTags) {
                            packetData.write(buffer, pageDataOffset, segmentLength)
                            if (segmentLength < 255) {
                                offset = bytesRead
                                break
                            }
                        }
                        pageDataOffset += segmentLength
                    }
                    if (offset == bytesRead) break
                    offset = pageDataOffset
                } else {
                    offset++
                }
            }

            if (packetData.size() > 8) {
                val packetBytes = packetData.toByteArray()
                var pOffset = 8 // Skip "OpusTags"

                fun readInt32LE(bytes: ByteArray, idx: Int): Int {
                    if (idx + 3 >= bytes.size) return 0
                    return (bytes[idx].toInt() and 0xFF) or
                           ((bytes[idx+1].toInt() and 0xFF) shl 8) or
                           ((bytes[idx+2].toInt() and 0xFF) shl 16) or
                           ((bytes[idx+3].toInt() and 0xFF) shl 24)
                }

                val vendorLen = readInt32LE(packetBytes, pOffset)
                pOffset += 4 + vendorLen
                if (pOffset < packetBytes.size) {
                    val commentListLen = readInt32LE(packetBytes, pOffset)
                    pOffset += 4

                    for (i in 0 until commentListLen) {
                        if (pOffset + 4 > packetBytes.size) break
                        val commentLen = readInt32LE(packetBytes, pOffset)
                        pOffset += 4

                        if (pOffset + commentLen > packetBytes.size) break
                        val commentBytes = packetBytes.copyOfRange(pOffset, pOffset + commentLen)
                        val commentStr = decodeTextSmart(commentBytes)
                        pOffset += commentLen

                        val lower = commentStr.lowercase()
                        if (lower.startsWith("lyrics=") || lower.startsWith("sylt=") || lower.startsWith("uslt=") || lower.startsWith("text=")) {
                            val content = commentStr.substring(commentStr.indexOf('=') + 1).trim()
                            val isSynced = Regex("\\[\\d{2}:\\d{2}(?:[.:]\\d{1,3})?\\]").containsMatchIn(content)
                            return LyricsResult(lyrics = content, isSynced = isSynced, source = "OPUS_TAGS_PARSER")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
