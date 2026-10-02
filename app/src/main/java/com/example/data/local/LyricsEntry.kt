package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics")
data class LyricsEntry(
    @PrimaryKey
    val audioPath: String,   // Path o URI del archivo de audio ("liga" directa)
    val lyrics: String,
    val savedAt: Long = System.currentTimeMillis()
)
