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

    fun getCachedArtistInfo(artistName: String): ArtistInfo? {
        val key = normalizeArtistName(artistName)
        if (key.isBlank()) return null
        memoryCache[key]?.let { return it }

        // Fast check from persistent photo store
        if (PersistentArtistStore.hasValidLocalPhoto(artistName)) {
            val localPath = PersistentArtistStore.getLocalPhotoFile(artistName).absolutePath
            val info = ArtistInfo(
                name = artistName,
                imageUrl = localPath
            )
            memoryCache[key] = info
            return info
        }
        return null
    }

    fun getCachedArtistsForTrack(track: Track?): List<ArtistProfile> {
        if (track == null) return emptyList()
        val artistName = track.artist.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Artist"
        val info = getCachedArtistInfo(artistName) ?: ArtistInfo(name = artistName)
        return listOf(
            ArtistProfile(
                name = artistName,
                isPrimary = true,
                info = info,
                followers = info.followers ?: "",
                listeners = info.listeners ?: "",
                origin = info.origin ?: ""
            )
        )
    }

    suspend fun getArtistInfo(
        artistName: String,
        trackTitle: String? = null,
        albumName: String? = null,
        genre: String? = null
    ): ArtistInfo = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        val key = normalizeArtistName(cleanName)
        if (key.isBlank() || key == "<unknown>" || key == "unknown artist") {
            return@withContext ArtistInfo(name = cleanName.ifBlank { "Unknown Artist" })
        }

        val cached = memoryCache[key]
        if (cached != null && !cached.imageUrl.isNullOrBlank() && !cached.bio.isNullOrBlank()) {
            return@withContext cached
        }

        var imageUrl: String? = cached?.imageUrl
        var bioText: String? = cached?.bio
        var originCountry: String? = cached?.origin
        var genresList: List<String> = cached?.genres ?: emptyList()
        var listenersCount: String? = cached?.listeners
        var followersCount: String? = cached?.followers

        // 1. Fetch Real Artist Image via Deezer API (via ArtistImageRepository)
        if (imageUrl.isNullOrBlank()) {
            try {
                imageUrl = ArtistImageRepository.getArtistImageUrl(
                    artistName = cleanName,
                    trackTitle = trackTitle,
                    albumName = albumName,
                    genre = genre
                )
            } catch (e: Exception) {
                Log.d(TAG, "Error fetching image from ArtistImageRepository: ${e.message}")
            }
        }

        // 2. Fetch Real Biography via TheAudioDB + Wikipedia (ES/EN) + Last.fm
        if (bioText.isNullOrBlank()) {
            try {
                val bioResult = ArtistBioRepository.getArtistBiographyResult(
                    artistName = cleanName,
                    trackTitle = trackTitle,
                    albumName = albumName,
                    genre = genre
                )
                if (bioResult != null) {
                    bioText = bioResult.bio
                    if (originCountry.isNullOrBlank()) originCountry = bioResult.origin
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
        _cacheRevision.value += 1
        info
    }

    suspend fun fetchArtistsForTrack(track: Track?): List<ArtistProfile> = withContext(Dispatchers.IO) {
        if (track == null) return@withContext emptyList()
        val artistName = track.artist.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Artist"
        val info = getArtistInfo(
            artistName = artistName,
            trackTitle = track.title,
            albumName = track.album,
            genre = track.genre
        )
        listOf(
            ArtistProfile(
                name = artistName,
                isPrimary = true,
                info = info,
                followers = info.followers ?: "",
                listeners = info.listeners ?: "",
                origin = info.origin ?: ""
            )
        )
    }

    suspend fun fetchArtistPhoto(
        artistName: String,
        trackTitle: String? = null,
        albumName: String? = null,
        genre: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val info = getArtistInfo(artistName, trackTitle, albumName, genre)
        info.imageUrl
    }

    suspend fun fetchArtistInfo(
        rawArtistName: String,
        trackTitle: String? = null,
        albumName: String? = null,
        genre: String? = null
    ): ArtistInfo? = withContext(Dispatchers.IO) {
        getArtistInfo(rawArtistName, trackTitle, albumName, genre)
    }

    /**
     * Searches and downloads photos for a list of artists (e.g. from the Artists screen).
     */
    suspend fun searchAndFetchAllArtistPhotos(
        artistNames: List<String>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        var foundCount = 0
        val total = artistNames.size
        for ((index, name) in artistNames.withIndex()) {
            try {
                val photo = ArtistImageRepository.getArtistImageUrl(name)
                if (!photo.isNullOrBlank()) {
                    foundCount++
                    val key = normalizeArtistName(name)
                    val existing = memoryCache[key] ?: ArtistInfo(name = name)
                    memoryCache[key] = existing.copy(imageUrl = photo)
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
