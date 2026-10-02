package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "liked_tracks", primaryKeys = ["trackId"])
data class LikedTrackEntity(
    val trackId: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackId"), Index("timestamp")]
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class GenreCount(
    val genre: String,
    val count: Int
)

data class ArtistCount(
    val artist: String,
    val count: Int,
    val imageUri: String? = null
)

@Dao
interface SocialDao {
    // LIKES
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun likeTrack(likedTrack: LikedTrackEntity)

    @Query("DELETE FROM liked_tracks WHERE trackId = :trackId")
    suspend fun unlikeTrack(trackId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM liked_tracks WHERE trackId = :trackId)")
    fun isTrackLiked(trackId: Long): Flow<Boolean>

    @Transaction
    @Query("SELECT t.* FROM cached_tracks t INNER JOIN liked_tracks l ON t.id = l.trackId ORDER BY l.timestamp DESC")
    fun getLikedTracks(): Flow<List<TrackEntity>>

    // HISTORY
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaybackHistory(history: PlaybackHistoryEntity)

    @Query("SELECT t.* FROM cached_tracks t INNER JOIN playback_history h ON t.id = h.trackId WHERE h.timestamp >= :since ORDER BY h.timestamp DESC LIMIT :limit")
    fun getRecentTracks(since: Long, limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT t.genre AS genre, COUNT(h.id) AS count FROM playback_history h INNER JOIN cached_tracks t ON h.trackId = t.id WHERE h.timestamp >= :since AND t.genre != '' GROUP BY t.genre ORDER BY count DESC LIMIT 1")
    fun getTopGenreSince(since: Long): Flow<GenreCount?>

    @Query("SELECT t.artist AS artist, COUNT(h.id) AS count, MAX(t.albumArtUri) AS imageUri FROM playback_history h INNER JOIN cached_tracks t ON h.trackId = t.id WHERE h.timestamp >= :since AND t.artist != '' GROUP BY t.artist ORDER BY count DESC LIMIT 5")
    fun getTopArtistsSince(since: Long): Flow<List<ArtistCount>>
    
    @Query("DELETE FROM playback_history WHERE timestamp < :before")
    suspend fun clearHistoryBefore(before: Long)
}
