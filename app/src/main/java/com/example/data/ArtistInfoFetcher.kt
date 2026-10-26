package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.ArtistInfo
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import com.example.util.ArtistParser

typealias ArtistInfo = com.example.model.ArtistInfo

data class ArtistProfile(
    val name: String,
    val isPrimary: Boolean = true,
    val info: ArtistInfo = ArtistInfo(),
    val followers: String = "",
    val listeners: String = "",
    val origin: String = ""
)

object ArtistInfoFetcher {
    private const val TAG = "ArtistInfoFetcher"
    private val memoryCache = ConcurrentHashMap<String, ArtistInfo>()
    private val _cacheRevision = MutableStateFlow(0)
    val cacheRevision: StateFlow<Int> = _cacheRevision.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun normalizeArtistName(name: String): String {
        return name.trim().lowercase()
    }

    fun isFallbackBio(bio: String?): Boolean {
        if (bio.isNullOrBlank()) return true
        if (bio.contains("es un artista musical en tu biblioteca")) return true
        if (bio.contains("is a musical artist in your library")) return true
        return false
    }

    fun getCachedArtistInfo(artistName: String): ArtistInfo? {
        val key = normalizeArtistName(artistName)
        if (key.isBlank()) return null
        
        // 1. Check in-memory cache
        val inMem = memoryCache[key]
        if (inMem != null && !isFallbackBio(inMem.bio)) {
            return inMem
        }

        // 2. Check local photo if available
        if (PersistentArtistStore.hasValidLocalPhoto(artistName)) {
            val localPath = PersistentArtistStore.getLocalPhotoFile(artistName).absolutePath
            val info = inMem?.copy(imageUrl = localPath) ?: ArtistInfo(
                name = artistName,
                imageUrl = localPath
            )
            return info
        }
        return inMem
    }

    fun getCachedArtistsForTrack(track: Track?): List<ArtistProfile> {
        if (track == null) return emptyList()
        val artistNames = ArtistParser.extractAllArtists(track.artist, track.title)
        val namesToUse = if (artistNames.isNotEmpty()) artistNames else listOf(
            track.artist.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Artist"
        )
        return namesToUse.mapIndexed { index, name ->
            val info = getCachedArtistInfo(name) ?: ArtistInfo(name = name)
            ArtistProfile(
                name = name,
                isPrimary = index == 0,
                info = info,
                followers = info.followers ?: "",
                listeners = info.listeners ?: "",
                origin = info.origin ?: ""
            )
        }
    }

    suspend fun getArtistInfo(
        artistName: String,
        trackTitle: String? = null,
        albumName: String? = null,
        genre: String? = null,
        forceRefresh: Boolean = false
    ): ArtistInfo = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        val key = normalizeArtistName(cleanName)
        if (key.isBlank() || key == "<unknown>" || key == "unknown artist") {
            return@withContext ArtistInfo(name = cleanName.ifBlank { "Unknown Artist" })
        }

        // Contextual Album & Track Discovery from user's library if not provided
        var effectiveAlbum = albumName
        var effectiveTrack = trackTitle
        var effectiveGenre = genre

        if (effectiveAlbum.isNullOrBlank() || effectiveAlbum == "<unknown>" || effectiveAlbum == "Unknown Album") {
            val libraryTracks = TrackRepository.tracks.value
            val matching = libraryTracks.filter { t ->
                ArtistParser.extractAllArtists(t.artist, t.title).any { it.equals(cleanName, ignoreCase = true) } ||
                t.artist.contains(cleanName, ignoreCase = true)
            }
            if (matching.isNotEmpty()) {
                effectiveAlbum = matching.map { it.album }.firstOrNull { it.isNotBlank() && it != "<unknown>" && it != "Unknown Album" }
                if (effectiveTrack.isNullOrBlank()) {
                    effectiveTrack = matching.firstOrNull()?.title
                }
                if (effectiveGenre.isNullOrBlank()) {
                    effectiveGenre = matching.mapNotNull { it.genre }.firstOrNull { it.isNotBlank() && it != "Unknown" }
                }
            }
        }

        if (!forceRefresh) {
            val cached = memoryCache[key]
            if (cached != null && !cached.imageUrl.isNullOrBlank() && !isFallbackBio(cached.bio)) {
                return@withContext cached
            }

            val fromRoom = PersistentArtistStore.getArtistFromRoom(cleanName)
            if (fromRoom != null && !isFallbackBio(fromRoom.bio)) {
                val info = ArtistInfo(
                    name = fromRoom.name,
                    bio = fromRoom.bio ?: "",
                    genres = if (!fromRoom.genre.isNullOrBlank()) listOf(fromRoom.genre) else emptyList(),
                    imageUrl = fromRoom.localImagePath ?: fromRoom.imageUrl,
                    listeners = fromRoom.listeners ?: "",
                    followers = fromRoom.followers ?: "",
                    origin = fromRoom.origin ?: ""
                )
                memoryCache[key] = info
                return@withContext info
            }
        }

        var imageUrl: String? = if (!forceRefresh) memoryCache[key]?.imageUrl else null
        var bioText: String? = null
        var originCountry: String? = null
        var genresList: List<String> = emptyList()
        var listenersCount: String? = null
        var followersCount: String? = null

        // 1. Fetch Real Artist Image via ArtistImageRepository with album & track disambiguation
        if (imageUrl.isNullOrBlank() || forceRefresh) {
            try {
                imageUrl = ArtistImageRepository.getArtistImageUrl(
                    artistName = cleanName,
                    trackTitle = effectiveTrack,
                    albumName = effectiveAlbum,
                    genre = effectiveGenre,
                    forceRefresh = forceRefresh
                )
            } catch (e: Exception) {
                Log.d(TAG, "Error fetching image from ArtistImageRepository: ${e.message}")
            }
        }

        // 2. Fetch Real Biography via TheAudioDB + Wikipedia (ES/EN) + Last.fm
        try {
            val bioResult = ArtistBioRepository.getArtistBiographyResult(
                artistName = cleanName,
                trackTitle = effectiveTrack,
                albumName = effectiveAlbum,
                genre = effectiveGenre
            )
            if (bioResult != null) {
                bioText = bioResult.bio
                if (!bioResult.origin.isNullOrBlank()) originCountry = bioResult.origin
                if (imageUrl.isNullOrBlank()) {
                    imageUrl = bioResult.fanartUrl ?: bioResult.thumbUrl
                }
                if (bioResult.genre != null && genresList.isEmpty()) {
                    genresList = listOf(bioResult.genre)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error fetching bio from ArtistBioRepository: ${e.message}")
        }

        // 3. Fallback clean biography if online sources found nothing
        val finalBio = bioText?.takeIf { it.isNotBlank() } ?: "$cleanName es un artista musical en tu biblioteca de Fuzion Player."
        val finalGenres = if (genresList.isNotEmpty()) genresList else listOf("Música")

        val info = ArtistInfo(
            name = cleanName,
            bio = finalBio,
            genres = finalGenres,
            imageUrl = imageUrl,
            listeners = listenersCount ?: "",
            followers = followersCount ?: "",
            origin = originCountry ?: ""
        )

        memoryCache[key] = info

        // Persist to Room so info is preserved permanently
        try {
            val localPath = if (PersistentArtistStore.hasValidLocalPhoto(cleanName)) {
                PersistentArtistStore.getLocalPhotoFile(cleanName).absolutePath
            } else imageUrl
            val entity = ArtistEntity(
                name = cleanName,
                imageUrl = imageUrl,
                localImagePath = localPath,
                bio = finalBio,
                origin = originCountry,
                listeners = listenersCount,
                followers = followersCount,
                genre = finalGenres.firstOrNull(),
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
            PersistentArtistStore.saveArtistToRoom(entity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist artist entity to Room: ${e.message}")
        }

        _cacheRevision.value += 1
        info
    }

    suspend fun fetchArtistsForTrack(track: Track?, forceRefresh: Boolean = false): List<ArtistProfile> = withContext(Dispatchers.IO) {
        if (track == null) return@withContext emptyList()
        val artistNames = ArtistParser.extractAllArtists(track.artist, track.title)
        val targetNames = if (artistNames.isNotEmpty()) artistNames else listOf(
            track.artist.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Artist"
        )

        targetNames.mapIndexed { index, name ->
            val info = getArtistInfo(
                artistName = name,
                trackTitle = track.title,
                albumName = track.album,
                genre = track.genre,
                forceRefresh = forceRefresh
            )
            ArtistProfile(
                name = name,
                isPrimary = index == 0,
                info = info,
                followers = info.followers ?: "",
                listeners = info.listeners ?: "",
                origin = info.origin ?: ""
            )
        }
    }

    suspend fun fetchArtistPhoto(
        artistName: String,
        trackTitle: String? = null,
        albumName: String? = null,
        genre: String? = null,
        forceRefresh: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val info = getArtistInfo(artistName, trackTitle, albumName, genre, forceRefresh)
        info.imageUrl
    }

    suspend fun fetchArtistInfo(
        rawArtistName: String,
        trackTitle: String? = null,
        albumName: String? = null,
        genre: String? = null,
        forceRefresh: Boolean = false
    ): ArtistInfo? = withContext(Dispatchers.IO) {
        getArtistInfo(rawArtistName, trackTitle, albumName, genre, forceRefresh)
    }

    /**
     * Searches and downloads photos and info for artists with disambiguation.
     */
    suspend fun searchAndFetchAllArtistPhotos(
        artistNames: List<String>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        var foundCount = 0
        val total = artistNames.size
        for ((index, name) in artistNames.withIndex()) {
            try {
                val info = getArtistInfo(artistName = name, forceRefresh = true)
                if (!info.imageUrl.isNullOrBlank() || !isFallbackBio(info.bio)) {
                    foundCount++
                }
            } catch (_: Exception) {}
            onProgress?.invoke(index + 1, total)
        }
        _cacheRevision.value += 1
        foundCount
    }

    fun clearCache() {
        memoryCache.clear()
        ArtistImageRepository.clearCache()
        _cacheRevision.value += 1
    }

    fun clearAllData(context: Context? = null) {
        clearCache()
    }

    fun checkAndSyncWithSystemCache(context: Context) {}
}
