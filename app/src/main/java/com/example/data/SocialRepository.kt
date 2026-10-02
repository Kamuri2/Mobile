package com.example.data

import android.content.Context
import android.net.Uri
import com.example.data.local.*
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SocialRepository(
    private val playlistDao: PlaylistDao,
    private val socialDao: SocialDao
) {
    // Likes
    suspend fun toggleLike(trackId: Long, isLiked: Boolean) {
        if (isLiked) {
            socialDao.likeTrack(LikedTrackEntity(trackId))
        } else {
            socialDao.unlikeTrack(trackId)
        }
    }

    fun isTrackLiked(trackId: Long): Flow<Boolean> = socialDao.isTrackLiked(trackId)

    fun getLikedTracks(): Flow<List<Track>> = socialDao.getLikedTracks().map { list -> list.map { it.toTrack() } }

    // Playlists
    suspend fun updatePlaylist(playlist: PlaylistEntity) {
        playlistDao.updatePlaylist(playlist)
    }

    suspend fun createPlaylist(name: String, description: String? = null, imageUri: String? = null): Long {
        return playlistDao.insertPlaylist(PlaylistEntity(name = name, description = description, imageUri = imageUri))
    }

    suspend fun savePlaylistImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, "playlist_cover_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun deletePlaylist(playlist: PlaylistEntity) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        playlistDao.removeTrackFromPlaylist(PlaylistTrackCrossRef(playlistId, trackId))
    }

    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    fun getPlaylistWithTracks(playlistId: Long): Flow<PlaylistWithTracks?> = playlistDao.getPlaylistWithTracks(playlistId)

    // History
    suspend fun recordPlayback(trackId: Long) {
        socialDao.insertPlaybackHistory(PlaybackHistoryEntity(trackId = trackId))
    }

    fun getRecentTracks(since: Long = System.currentTimeMillis() - 3600_000L, limit: Int = 10): Flow<List<Track>> {
        return socialDao.getRecentTracks(since, limit).map { list -> list.map { it.toTrack() } }
    }

    fun getTopGenreLastHour(): Flow<String?> {
        val since = System.currentTimeMillis() - 3600_000L
        return socialDao.getTopGenreSince(since).map { it?.genre }
    }

    fun getTopArtistsToday(): Flow<List<ArtistCount>> {
        val todayStart = getStartOfDay()
        return socialDao.getTopArtistsSince(todayStart)
    }

    suspend fun clearOldHistory() {
        val today3AM = getStartOfDay() + (3 * 3600_000L)
        val limit = if (System.currentTimeMillis() < today3AM) {
            today3AM - 24 * 3600_000L
        } else {
            today3AM
        }
        socialDao.clearHistoryBefore(limit)
    }

    private fun getStartOfDay(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        @Volatile
        private var INSTANCE: SocialRepository? = null

        fun getInstance(context: Context): SocialRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val instance = SocialRepository(db.playlistDao(), db.socialDao())
                INSTANCE = instance
                instance
            }
        }
    }
}
