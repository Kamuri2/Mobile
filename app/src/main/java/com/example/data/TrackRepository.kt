package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.TrackEntity
import com.example.model.Track
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object TrackRepository {
    private const val TAG = "TrackRepository"
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _isRescanning = MutableStateFlow(false)
    val isRescanning: StateFlow<Boolean> = _isRescanning.asStateFlow()

    private val _isRepairingCovers = MutableStateFlow(false)
    val isRepairingCovers: StateFlow<Boolean> = _isRepairingCovers.asStateFlow()

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
            _isRescanning.value = true
            try {
                if (forceRescan) {
                    // Limpiar caches de portadas en memoria y archivos temporales/corruptos
                    AlbumArtExtractor(context).resetArtworkCaches(purgeAllArtFiles = false)
                    ArtworkExtractor.resetArtworkCaches()
                }

                val db = AppDatabase.getDatabase(context)
                val existing = db.trackDao().getAllTracks().associateBy { it.id }

                // Merge with cached info where available
                val mergedList = newTracks.map { track ->
                    val cached = existing[track.id]
                    if (!forceRescan && cached != null) {
                        val cachedTrack = cached.toTrack()
                        cachedTrack.copy(
                            contentUri = track.contentUri,
                            path = if (track.path.isNotBlank()) track.path else cached.path,
                            albumArtUri = track.albumArtUri ?: cachedTrack.albumArtUri,
                            lyrics = if (com.example.data.AudioLyricsExtractor.isCorruptedLyrics(cachedTrack.lyrics)) "" else cachedTrack.lyrics
                        )
                    } else {
                        track.copy(
                            lyrics = if (com.example.data.AudioLyricsExtractor.isCorruptedLyrics(track.lyrics)) "" else track.lyrics
                        )
                    }
                }.distinctBy { it.id }.sortedBy { it.title.lowercase() }

                // Purgar letras corruptas residuales de Room
                try {
                    LyricsRepository.purgeCorruptedLyrics(context)
                } catch (_: Exception) {}

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

                // Sección 2: Enriquecimiento ligero y escalonado en segundo plano (sin saturar memoria)
                // Se procesan únicamente portadas faltantes de forma espaciada (delay 150ms)
                val tracksNeedingArt = mergedList.filter { track ->
                    isArtworkMissingOrCorrupt(context, track)
                }.take(20)

                if (tracksNeedingArt.isNotEmpty()) {
                    scope.launch {
                        kotlinx.coroutines.delay(1000L) // Dar prioridad a que la UI cargue la música primero
                        val albumUriMap = mutableMapOf<String, android.net.Uri?>()
                        val enrichedBatch = mutableListOf<TrackEntity>()
                        for (track in tracksNeedingArt) {
                            if (!isActive) break
                            try {
                                val albumKey = AlbumArtExtractor.getAlbumKey(track)
                                val thumbUri = if (albumKey != null && albumUriMap.containsKey(albumKey)) {
                                    albumUriMap[albumKey]
                                } else {
                                    val uri = ArtworkExtractor.saveArtworkToInternalCache(context, track)
                                    if (albumKey != null) albumUriMap[albumKey] = uri
                                    uri
                                }
                                if (thumbUri != null) {
                                    val enriched = track.copy(albumArtUri = thumbUri)
                                    enrichedBatch.add(TrackEntity.fromTrack(enriched))
                                }
                                kotlinx.coroutines.delay(150L) // Ceder CPU y memoria para fluidez total
                            } catch (_: Exception) {}
                        }
                        if (enrichedBatch.isNotEmpty()) {
                            try {
                                db.trackDao().insertOrUpdateTracks(enrichedBatch)
                            } catch (_: Exception) {}
                        }
                    }
                }

                mergedList
            } catch (e: Exception) {
                Log.e(TAG, "Error saving scanned tracks to Room: ${e.message}")
                _tracks.value = newTracks
                newTracks
            } finally {
                _isRescanning.value = false
            }
        }
    }

    /**
     * Determina si una portada no cargó bien, no existe, está corrupta o quedó a medio escanear.
     */
    fun isArtworkMissingOrCorrupt(context: Context, track: Track): Boolean {
        // 1. Si no tiene URI de carátula y tampoco tiene archivo HD pre-extraído válido
        val existingArt = AlbumArtExtractor(context).getExistingArtFile(track)
        if (existingArt != null) {
            // Verificar si el archivo es válido y mide más de 500 bytes
            return !AlbumArtExtractor.isValidImageFile(existingArt)
        }

        val artUri = track.albumArtUri
        if (artUri == null || artUri.toString().isBlank()) {
            return true
        }

        // 2. Si tiene URI de tipo file, validar que el archivo exista en disco y tenga tamaño > 500 bytes
        if (artUri.scheme == "file" || artUri.path?.startsWith("/") == true) {
            val file = File(artUri.path ?: "")
            if (!file.exists() || file.length() < 500L) {
                return true
            }
        }
        return false
    }

    /**
     * Repara y re-extrae rápidamente únicamente las carátulas que fallaron, quedaron cortadas
     * o no se cargaron bien, sin tener que reescanear toda la biblioteca de cero.
     */
    suspend fun repairMissingOrCorruptArtwork(context: Context): Int {
        return withContext(Dispatchers.IO) {
            _isRepairingCovers.value = true
            var repairedCount = 0
            try {
                // Limpiar cachés negativas para permitir reintentos limpios
                AlbumArtExtractor(context).resetArtworkCaches(purgeAllArtFiles = false)
                ArtworkExtractor.resetArtworkCaches()

                val db = AppDatabase.getDatabase(context)
                val currentTracks = _tracks.value.ifEmpty {
                    db.trackDao().getAllTracks().map { it.toTrack() }
                }

                val tracksNeedingRepair = currentTracks.filter { track ->
                    isArtworkMissingOrCorrupt(context, track)
                }

                if (tracksNeedingRepair.isEmpty()) {
                    return@withContext 0
                }

                val albumExtractor = AlbumArtExtractor(context)
                val updatedEntities = mutableListOf<TrackEntity>()
                val updatedList = currentTracks.toMutableList()

                for (track in tracksNeedingRepair) {
                    if (!isActive) break
                    try {
                        // 1. Extraer a través de AlbumArtExtractor (alta resolución directa)
                        val highResFile = albumExtractor.getHighResArt(track)
                        val newArtUri = if (highResFile != null && AlbumArtExtractor.isValidImageFile(highResFile)) {
                            android.net.Uri.fromFile(highResFile)
                        } else {
                            ArtworkExtractor.saveArtworkToInternalCache(context, track)
                        }

                        if (newArtUri != null) {
                            val updatedTrack = track.copy(albumArtUri = newArtUri)
                            val idx = updatedList.indexOfFirst { it.id == track.id }
                            if (idx != -1) {
                                updatedList[idx] = updatedTrack
                            }
                            updatedEntities.add(TrackEntity.fromTrack(updatedTrack))
                            repairedCount++
                        }
                    } catch (_: Exception) {}
                }

                if (updatedEntities.isNotEmpty()) {
                    db.trackDao().insertOrUpdateTracks(updatedEntities)
                    _tracks.value = updatedList
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reparando carátulas: ${e.message}")
            } finally {
                _isRepairingCovers.value = false
            }
            repairedCount
        }
    }

    suspend fun updateTrackLyrics(context: Context, trackId: Long, lyrics: String) {
        val cleanLyrics = if (AudioLyricsExtractor.isCorruptedLyrics(lyrics)) "" else AudioLyricsExtractor.cleanExtractedLyrics(lyrics)
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                db.trackDao().updateLyrics(trackId, cleanLyrics)

                val updated = _tracks.value.map {
                    if (it.id == trackId) it.copy(lyrics = cleanLyrics) else it
                }
                _tracks.value = updated
                Log.d(TAG, "Updated persistent lyrics for track ID: $trackId")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating track lyrics: ${e.message}")
            }
        }
    }
}
