package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM cached_tracks ORDER BY title ASC")
    fun getAllTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM cached_tracks ORDER BY title ASC")
    suspend fun getAllTracks(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTracks(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(track: TrackEntity)

    @Query("UPDATE cached_tracks SET lyrics = :lyrics WHERE id = :id")
    suspend fun updateLyrics(id: Long, lyrics: String)

    @Query("SELECT lyrics FROM cached_tracks WHERE id = :id LIMIT 1")
    suspend fun getLyricsById(id: Long): String?

    @Query("SELECT lyrics FROM cached_tracks WHERE path = :path LIMIT 1")
    suspend fun getLyricsByPath(path: String): String?

    @Query("DELETE FROM cached_tracks WHERE id NOT IN (:validIds)")
    suspend fun deleteMissing(validIds: List<Long>)

    @Query("DELETE FROM cached_tracks")
    suspend fun clear()
}
