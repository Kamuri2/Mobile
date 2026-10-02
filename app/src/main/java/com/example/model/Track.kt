package com.example.model

import android.net.Uri

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: Uri,
    val albumArtUri: Uri? = null,
    
    val trackNumber: Int = 0,
    val year: Int = 0,
    val genre: String = "Unknown Genre",
    val bitrate: String = "320 kbps",
    val sampleRate: String = "44.1 kHz",
    val fileSizeFormatted: String = "6.2 MB",
    val path: String = "",
    val folderName: String = "Music",
    val lyrics: String = "",
    val isFavorite: Boolean = false,
    val isSample: Boolean = false
)
