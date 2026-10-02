package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.TrackEntity
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object TrackRepository {
    private const val TAG = "TrackRepository"
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val cached = db.trackDao().getAllTracks().map { it.toTrack() }
                if (cached.isNotEmpty()) {
                    _tracks.value = cached
                    Log.d(TAG, "Loaded ${cached.size} cached tracks from persistent Room database on app start")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing TrackRepository: ${e.message}")
            }
        }
    }

    /**
     * Instantly saves and publishes tracks to UI and Room, then progressively enriches tags in background.
     */
    suspend fun saveScannedTracks(context: Context, newTracks: List<Track>, forceRescan: Boolean = false): List<Track> {
        if (newTracks.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val existing = db.trackDao().getAllTracks().associateBy { it.id }

                // Merge with cached info where available
                val mergedList = newTracks.map { track ->
                    val cached = existing[track.id]
                    if (!forceRescan && cached != null) {
                        cached.toTrack().copy(
                            contentUri = track.contentUri,
                            path = if (track.path.isNotBlank()) track.path else cached.path,
                            albumArtUri = track.albumArtUri ?: cached.toTrack().albumArtUri
                        )
                    } else {
                        track
                    }
                }.distinctBy { it.id }.sortedBy { it.title.lowercase() }

                // Immediately update StateFlow and save to Room
                _tracks.value = mergedList
                val entitiesToSave = mergedList.map { TrackEntity.fromTrack(it) }
                db.trackDao().insertOrUpdateTracks(entitiesToSave)

                val validIds = mergedList.map { it.id }
                if (validIds.isNotEmpty()) {
                    try {
                        db.trackDao().deleteMissing(validIds)
                    } catch (e: Exception) {}
                }

                // Progressive background enrichment for tracks lacking tags/lyrics
                val tracksToEnrich = mergedList.filter { track ->
                    forceRescan || track.genre.isBlank() || track.lyrics.isBlank() || track.bitrate.isBlank()
                }

                if (tracksToEnrich.isNotEmpty()) {
                    scope.launch {
                        for (track in tracksToEnrich) {
                            try {
                                var enriched = MetadataReader.extractFullMetadata(context, track)
                                val thumbUri = ArtworkExtractor.saveArtworkToInternalCache(context, enriched)
                                if (thumbUri != null) {
                                    enriched = enriched.copy(albumArtUri = thumbUri)
                                }
                                db.trackDao().insertOrUpdateTracks(listOf(TrackEntity.fromTrack(enriched)))
                            } catch (e: Exception) {
                                // Ignore single file tag reading error
                            }
                        }
                    }
                }

                mergedList
            } catch (e: Exception) {
                Log.e(TAG, "Error saving scanned tracks to Room: ${e.message}")
                _tracks.value = newTracks
                newTracks
            }
        }
    }

    suspend fun updateTrackLyrics(context: Context, trackId: Long, lyrics: String) {
        if (lyrics.isBlank()) return
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                db.trackDao().updateLyrics(trackId, lyrics)

                val updated = _tracks.value.map {
                    if (it.id == trackId) it.copy(lyrics = lyrics) else it
                }
                _tracks.value = updated
                Log.d(TAG, "Updated persistent lyrics for track ID: $trackId")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating track lyrics: ${e.message}")
            }
        }
    }
}
