package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.model.Track
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object AudioMetadataHelper {

    private val badgeCache = ConcurrentHashMap<Long, String>()

    /**
     * Extracts technical audio information (Format • Bitrate / Bit-depth • Sample Rate)
     * e.g.: "FLAC • 24-bit • 48 kHz" or "MP3 • 320 kbps • 44.1 kHz"
     */
    fun getAudioQualityBadge(context: Context, track: Track?): String {
        if (track == null) return "AUDIO"
        badgeCache[track.id]?.let { return it }

        val badge = extractTechnicalInfo(context, track)
        badgeCache[track.id] = badge
        return badge
    }

    private fun extractTechnicalInfo(context: Context, track: Track): String {
        val retriever = MediaMetadataRetriever()
        try {
            if (track.path.isNotBlank() && File(track.path).exists()) {
                retriever.setDataSource(track.path)
            } else if (track.contentUri != Uri.EMPTY) {
                retriever.setDataSource(context, track.contentUri)
            } else {
                return formatFallback(track)
            }

            // 1. Format / MIME Type
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val format = when {
                mimeType?.contains("flac", ignoreCase = true) == true || track.path.endsWith(".flac", true) -> "FLAC"
                mimeType?.contains("mpeg", ignoreCase = true) == true || mimeType?.contains("mp3", ignoreCase = true) == true || track.path.endsWith(".mp3", true) -> "MP3"
                mimeType?.contains("aac", ignoreCase = true) == true || mimeType?.contains("mp4", ignoreCase = true) == true || track.path.endsWith(".m4a", true) || track.path.endsWith(".aac", true) -> "AAC"
                mimeType?.contains("wav", ignoreCase = true) == true || track.path.endsWith(".wav", true) -> "WAV"
                mimeType?.contains("ogg", ignoreCase = true) == true || track.path.endsWith(".ogg", true) -> "OGG"
                mimeType?.contains("opus", ignoreCase = true) == true || track.path.endsWith(".opus", true) -> "OPUS"
                else -> track.path.substringAfterLast(".", "AUDIO").uppercase()
            }

            // 2. Sample Rate (e.g. 44100 -> 44.1 kHz, 48000 -> 48 kHz)
            val sampleRateRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            val sampleRateStr = if (sampleRateRaw != null && sampleRateRaw > 0) {
                if (sampleRateRaw % 1000 == 0) {
                    "${sampleRateRaw / 1000} kHz"
                } else {
                    String.format("%.1f kHz", sampleRateRaw / 1000.0)
                }
            } else {
                "44.1 kHz"
            }

            // 3. Bitrate or Bit-Depth
            val bitrateRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            val bitrateOrDepth = when {
                format == "FLAC" || format == "WAV" -> {
                    // Lossless formats: estimate bit depth from bitrate and sample rate
                    if (bitrateRaw != null && sampleRateRaw != null && sampleRateRaw > 0) {
                        val bitsPerSampleEstimate = (bitrateRaw.toDouble() / (sampleRateRaw * 2.0))
                        if (bitsPerSampleEstimate > 18.0) "24-bit" else "16-bit"
                    } else {
                        "24-bit"
                    }
                }
                bitrateRaw != null && bitrateRaw > 0 -> {
                    val kbps = bitrateRaw / 1000
                    "$kbps kbps"
                }
                else -> "Lossless"
            }

            return "$format • $bitrateOrDepth • $sampleRateStr"
        } catch (_: Exception) {
            return formatFallback(track)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun formatFallback(track: Track): String {
        val ext = track.path.substringAfterLast(".", "AUDIO").uppercase()
        val format = when (ext) {
            "FLAC" -> "FLAC • 24-bit • 48 kHz"
            "MP3" -> "MP3 • 320 kbps • 44.1 kHz"
            "M4A", "AAC" -> "AAC • 256 kbps • 44.1 kHz"
            "WAV" -> "WAV • 16-bit • 44.1 kHz"
            "OGG" -> "OGG • 320 kbps • 44.1 kHz"
            "OPUS" -> "OPUS • 160 kbps • 48 kHz"
            else -> "AUDIO • Lossless • 44.1 kHz"
        }
        return format
    }
}
