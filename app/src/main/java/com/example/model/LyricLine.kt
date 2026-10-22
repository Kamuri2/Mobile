package com.example.model

data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val isHighlighted: Boolean = false
)

data class ArtistInfo(
    val name: String = "",
    val bio: String = "",
    val genres: List<String> = emptyList(),
    val imageUrl: String? = null,
    val listeners: String = "",
    val followers: String = "",
    val origin: String = "",
    val spotify: String? = null,
    val instagram: String? = null,
    val youtube: String? = null,
    val appleMusic: String? = null,
    val twitter: String? = null,
    val facebook: String? = null,
    val website: String? = null,
    val deezer: String? = null
)
