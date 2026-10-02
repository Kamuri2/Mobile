package com.example.data.local

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Track

@Entity(tableName = "cached_tracks")
data class TrackEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
    val albumArtUri: String?,
    val trackNumber: Int,
    val year: Int,
    val fileSizeFormatted: String,
    val path: String,
    val folderName: String,
    val genre: String,
    val bitrate: String,
    val sampleRate: String,
    val lyrics: String
) {
    fun toTrack(): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            contentUri = Uri.parse(contentUri),
            albumArtUri = albumArtUri?.let { Uri.parse(it) },
            
            trackNumber = trackNumber,
            year = year,
            fileSizeFormatted = fileSizeFormatted,
            path = path,
            folderName = folderName,
            genre = genre,
            bitrate = bitrate,
            sampleRate = sampleRate,
            lyrics = lyrics
        )
    }

    companion object {
        fun fromTrack(track: Track): TrackEntity {
            return TrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                contentUri = track.contentUri.toString(),
                albumArtUri = track.albumArtUri?.toString(),
                trackNumber = track.trackNumber,
                year = track.year,
                fileSizeFormatted = track.fileSizeFormatted,
                path = track.path,
                folderName = track.folderName,
                genre = track.genre,
                bitrate = track.bitrate,
                sampleRate = track.sampleRate,
                lyrics = track.lyrics
            )
        }
    }
}
