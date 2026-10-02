package com.example.data

import android.content.Context
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.Track
import java.io.File
import java.io.FileOutputStream
import java.util.logging.Level
import java.util.logging.Logger

object MetadataReader {

    private const val TAG = "MetadataReader"

    /**
     * Reads complete metadata from an audio Uri using MediaMetadataRetriever
     * and deep JAudioTagger extraction for embedded LRC lyrics and tags across all formats (MP3, FLAC, WAV, M4A, OGG, OPUS).
     */
            fun extractFullMetadata(context: Context, track: Track): Track {
        var title = track.title
        var artist = track.artist
        var album = track.album
        var year = track.year
        var trackNumber = track.trackNumber
        var genre = track.genre
        var bitrate = track.bitrate
        var sampleRate = track.sampleRate
        var durationMs = track.durationMs

        if (track.path.isNotBlank()) {
            try {
                val file = File(track.path)
                if (file.exists() && file.canRead()) {
                    val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
                    val tag = audioFile.tag
                    val header = audioFile.audioHeader

                    if (tag != null) {
                        tag.getFirst(org.jaudiotagger.tag.FieldKey.TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
                        tag.getFirst(org.jaudiotagger.tag.FieldKey.ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
                        tag.getFirst(org.jaudiotagger.tag.FieldKey.ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it }
                        tag.getFirst(org.jaudiotagger.tag.FieldKey.YEAR)?.takeIf { it.isNotBlank() }?.toIntOrNull()?.let { year = it }
                        
                        val trackStr = tag.getFirst(org.jaudiotagger.tag.FieldKey.TRACK)
                        parseTrackNumber(trackStr)?.let { trackNumber = it }
                        
                        tag.getFirst(org.jaudiotagger.tag.FieldKey.GENRE)?.takeIf { it.isNotBlank() }?.let { genre = it }
                    }

                    if (header != null) {
                        val br = header.bitRateAsNumber
                        if (br > 0) {
                            bitrate = "${br} kbps"
                        }
                        val sr = header.sampleRateAsNumber
                        if (sr > 0) {
                            sampleRate = "${sr / 1000.0} kHz".replace(".0 kHz", " kHz")
                        }
                        val dur = header.trackLength
                        if (dur > 0) {
                            durationMs = (dur * 1000).toLong()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("MetadataReader", "JAudioTagger failed for metadata: ${e.message}")
            }
            
            // Fallback to MediaMetadataRetriever (very reliable for Opus/M4A on Android)
            try {
                val mmr = MediaMetadataRetriever()
                if (track.contentUri != Uri.EMPTY) {
                    mmr.setDataSource(context, track.contentUri)
                } else {
                    mmr.setDataSource(track.path)
                }
                
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.takeIf { it.isNotBlank() }?.let { genre = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.takeIf { it.isNotBlank() }?.toIntOrNull()?.let { year = it }
                
                if (durationMs <= 0L) {
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { durationMs = it }
                }
                
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { 
                    if (bitrate.isEmpty()) bitrate = "${it / 1000} kbps" 
                }
                
                mmr.release()
            } catch (e: Exception) {
                Log.d("MetadataReader", "MMR failed: ${e.message}")
            }
        }

        val lyricsResult = AudioLyricsExtractor.extractLyrics(context, track.contentUri, track.path.takeIf { it.isNotBlank() && !it.startsWith("content://") })
        var extractedLyrics = lyricsResult.lyrics ?: ""
        if (extractedLyrics.isBlank() && track.path.isNotBlank()) {
            extractedLyrics = searchSidecarLyrics(track.path)
        }
        if (extractedLyrics.isNotBlank()) {
            extractedLyrics = cleanExtractedLyrics(extractedLyrics)
        }

        val finalLyrics = if (extractedLyrics.isNotBlank()) extractedLyrics else track.lyrics
        
        return track.copy(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            trackNumber = trackNumber,
            year = year,
            genre = genre,
            bitrate = bitrate,
            sampleRate = sampleRate,
            lyrics = finalLyrics
        )
    }

    private fun isGenericName(str: String): Boolean {
        val s = str.trim().lowercase()
        return s.isEmpty() || s == "unknown" || s == "unknown artist" || s == "unknown track" || s == "unknown album" || s == "<unknown>" || s == "artista desconocido"
    }

    private fun parseTrackNumber(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        return try {
            val cleaned = raw.trim()
            val firstPart = cleaned.split("/").firstOrNull()?.trim() ?: cleaned
            val num = firstPart.toIntOrNull()
            if (num != null && num >= 1000) {
                num % 1000
            } else {
                num
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun searchSidecarLyrics(audioPath: String): String {
        return try {
            val audioFile = File(audioPath)
            val parent = audioFile.parentFile ?: return ""
            val nameWithoutExt = audioFile.nameWithoutExtension

            val extensions = listOf(".lrc", ".txt", ".srt", ".vtt")
            for (ext in extensions) {
                val f = File(parent, nameWithoutExt + ext)
                if (f.exists() && f.canRead()) {
                    val text = readFileWithCharsetDetection(f)
                    if (text.isNotBlank()) {
                        return convertSubtitleToLrc(text.trim())
                    }
                }
            }
            ""
        } catch (e: Exception) {
            Log.d(TAG, "Error searching sidecar lyrics: ${e.message}")
            ""
        }
    }

    private fun readFileWithCharsetDetection(file: File): String {
        return try {
            val bytes = file.readBytes()
            AudioLyricsExtractor.decodeTextSmart(bytes)
        } catch (e: Exception) {
            try {
                file.readText(Charsets.ISO_8859_1)
            } catch (e2: Exception) {
                ""
            }
        }
    }

    private fun getExtensionFromTrack(context: Context, track: Track): String {
        val path = track.path.lowercase()
        val fromPath = when {
            path.endsWith(".mp3") -> ".mp3"
            path.endsWith(".flac") -> ".flac"
            path.endsWith(".m4a") -> ".m4a"
            path.endsWith(".wav") -> ".wav"
            path.endsWith(".ogg") -> ".ogg"
            path.endsWith(".opus") -> ".opus"
            path.endsWith(".aac") -> ".aac"
            else -> ""
        }
        if (fromPath.isNotEmpty()) return fromPath

        // Try getting mime type from ContentResolver
        try {
            val mime = context.contentResolver.getType(track.contentUri)?.lowercase() ?: ""
            return when {
                mime.contains("flac") -> ".flac"
                mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") -> ".m4a"
                mime.contains("ogg") -> ".ogg"
                mime.contains("opus") -> ".opus"
                mime.contains("wav") -> ".wav"
                mime.contains("mpeg") || mime.contains("mp3") -> ".mp3"
                else -> ".mp3"
            }
        } catch (e: Exception) {}

        return ".mp3"
    }

    /**
     * Limpia la metadata de letras incrustadas.
     * JAudioTagger a veces devuelve el frame con cabeceras de idioma u otros datos (ej. "eng||[00:12.00]Letra").
     */
    fun cleanExtractedLyrics(raw: String): String {
        var cleaned = raw.trim()
        
        // Si el tag incrustado viene con separador de JAudioTagger (ej. "eng||[00:12.00]Letra")
        if (cleaned.contains("||")) {
            val parts = cleaned.split("||", limit = 2)
            if (parts.size == 2 && parts[0].length <= 8) {
                cleaned = parts[1]
            }
        }
        
        // Limpiar prefijos comunes de idioma que algunos editores incrustan
        val commonPrefixes = listOf("eng|", "spa|", "jpn|", "XXX|", "xxx|", "ger|", "fra|", "ita|", "zho|")
        for (prefix in commonPrefixes) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length)
            }
        }

        // Eliminar caracteres nulos iniciales o finales si existían en los bytes binarios
        cleaned = cleaned.trim { it <= ' ' || it == '\u0000' }
        
        // Corregir posible mojibake (doble codificación UTF-8 interpretada como Latin1)
        cleaned = fixMojibakeIfPresent(cleaned)
        
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
        } catch (e: Exception) {
            text
        }
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
}
