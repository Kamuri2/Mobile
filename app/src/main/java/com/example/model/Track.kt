package com.example.model

import android.net.Uri

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String = "Unknown Album",
    val durationMs: Long = 0L,
    val contentUri: Uri = Uri.EMPTY,
    val albumArtUri: Uri? = null,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val fileSizeFormatted: String = "",
    val path: String = "",
    val folderName: String = "",
    val genre: String = "Unknown Genre",
    val bitrate: String = "320 kbps",
    val sampleRate: String = "44.1 kHz",
    val lyrics: String = "",
    val isFavorite: Boolean = false,
    val isSample: Boolean = false
) {
    val displayArtist: String
        get() = if (artist.isBlank() || artist.equals("<unknown>", ignoreCase = true)) "Unknown Artist" else artist

    val displayTitle: String
        get() = if (title.isBlank()) "Unknown Title" else title

    val isSyncedLyrics: Boolean
        get() = lyrics.isNotBlank() && lyrics.contains("[") && lyrics.contains("]")
}
