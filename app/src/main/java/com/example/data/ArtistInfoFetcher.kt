package com.example.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.File

data class ArtistInfo(
    val imageUrl: String?,
    val bio: String?,
    val followers: String,
    val listeners: String,
    val origin: String,
    val website: String? = null,
    val facebook: String? = null,
    val twitter: String? = null,
    val instagram: String? = null,
    val spotify: String? = null,
    val youtube: String? = null,
    val appleMusic: String? = null,
    val deezer: String? = null,
    val genre: String? = null
)

data class ArtistProfile(
    val name: String,
    val isPrimary: Boolean,
    val info: ArtistInfo
)

/**
 * Dedicated Music & Artist API Fetcher with persistent offline storage and intelligent disambiguation.
 * Uses specialized music APIs (Deezer Track & Artist APIs, Apple iTunes Search, MusicBrainz)
 * with album, track title, and genre verification to guarantee matching the exact artist.
 *
 * Prevents locking in empty states: if an artist has no photo, continues searching across all tiers.
 */
object ArtistInfoFetcher {
    private val cache = mutableMapOf<String, ArtistInfo>()
    private var prefs: SharedPreferences? = null

    private val _cacheRevision = MutableStateFlow(0L)
    val cacheRevision: StateFlow<Long> = _cacheRevision.asStateFlow()

    fun init(context: Context) {
        PersistentArtistStore.init(context)
        checkAndSyncWithSystemCache(context)
        if (prefs != null) return
        try {
            val p = context.applicationContext.getSharedPreferences("artist_info_persistent_cache_v2", Context.MODE_PRIVATE)
            prefs = p
            p.all.forEach { (key, value) ->
                if (value is String) {
                    fromJson(value)?.let { info ->
                        if (key.contains("_")) {
                            val artistPart = key.substringBefore("_").trim()
                            if (!info.imageUrl.isNullOrBlank() || (info.origin.isNotBlank() && info.origin != "Unknown")) {
                                if (cache[artistPart] == null || cache[artistPart]?.imageUrl.isNullOrBlank()) {
                                    cache[artistPart] = info
                                    cache[artistPart.lowercase()] = info
                                }
                            }
                            p.edit().remove(key).apply()
                        } else {
                            cache[key] = info
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ArtistInfoFetcher", "Error loading cached artist info: ${e.message}")
        }
    }

    /**
     * Checks if the system cache was cleared (e.g. from Android App Info -> Storage -> Clear Cache).
     * If the sentinel file in cacheDir is missing while app_cache_tracker was initialized,
     * it detects the cache wipe and immediately clears all artist photos and biography records.
     */
    fun checkAndSyncWithSystemCache(context: Context) {
        try {
            val appCtx = context.applicationContext
            val sentinelFile = File(appCtx.cacheDir, ".artist_cache_sentinel")
            val trackerPrefs = appCtx.getSharedPreferences("app_cache_tracker", Context.MODE_PRIVATE)
            val wasInitialized = trackerPrefs.getBoolean("cache_initialized", false)

            if (wasInitialized && !sentinelFile.exists()) {
                Log.d("ArtistInfoFetcher", "System cache wipe detected! Deleting artist photos and biography records...")
                clearCache()
                CoroutineScope(Dispatchers.IO).launch {
                    PersistentArtistStore.clearAllPhotosAndDatabase(appCtx)
                }
                sentinelFile.createNewFile()
            } else if (!sentinelFile.exists()) {
                sentinelFile.createNewFile()
                trackerPrefs.edit().putBoolean("cache_initialized", true).apply()
            }
        } catch (e: Exception) {
            Log.e("ArtistInfoFetcher", "Error checking system cache sentinel: ${e.message}")
        }
    }

    /**
     * Clears in-memory artist caches and persistent SharedPreferences records.
     */
    fun clearCache() {
        synchronized(cache) {
            cache.clear()
        }
        try {
            prefs?.edit()?.clear()?.apply()
        } catch (e: Exception) {
            Log.e("ArtistInfoFetcher", "Error clearing prefs: ${e.message}")
        }
        ArtistImageRepository.clearCache()
        _cacheRevision.value = System.currentTimeMillis()
    }

    /**
     * Full cache wipe: deletes all artist photos, biography records from Room,
     * clears SharedPreferences and in-memory caches, and notifies UI listeners.
     */
    suspend fun clearAllData(context: Context) = withContext(Dispatchers.IO) {
        clearCache()
        PersistentArtistStore.clearAllPhotosAndDatabase(context.applicationContext)
        try {
            val sentinelFile = File(context.applicationContext.cacheDir, ".artist_cache_sentinel")
            if (sentinelFile.exists()) {
                sentinelFile.delete()
            }
            sentinelFile.createNewFile()
        } catch (e: Exception) {
            Log.e("ArtistInfoFetcher", "Error refreshing sentinel: ${e.message}")
        }
    }

    /**
     * Normalizes artist name by stripping punctuation, dots, quotes, hyphens, and standardizing symbols.
     * Example: "S.O.A.P." -> "soap", "Soap" -> "soap", "KoЯn" -> "korn".
     */
    fun normalizeArtistName(s: String): String {
        return s.lowercase()
            .replace(".", "")
            .replace("'", "")
            .replace("\"", "")
            .replace("’", "")
            .replace("-", " ")
            .replace("_", " ")
            .replace("&", "and")
            .replace("+", "and")
            .replace("я", "r")
            .replace("ø", "o")
            .replace("æ", "ae")
            .replace("$", "s")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun getCachedArtistInfo(rawArtistName: String?): ArtistInfo? {
        if (rawArtistName.isNullOrBlank()) return null
        val clean = rawArtistName.trim()
        val norm = clean.lowercase()
        val cached = cache[clean] ?: cache[norm] ?: prefs?.getString(clean, null)?.let { fromJson(it) } ?: prefs?.getString(norm, null)?.let { fromJson(it) }
        val hasLocalPhoto = PersistentArtistStore.hasValidLocalPhoto(clean)
        val localPhotoPath = if (hasLocalPhoto) PersistentArtistStore.getLocalPhotoFile(clean).absolutePath else null

        if (cached != null) {
            val safeBio = if (cached.bio != null && isNonMusicDefinition(cached.bio)) null else cached.bio
            val resolvedImage = localPhotoPath ?: cached.imageUrl
            val sanitized = cached.copy(bio = safeBio, imageUrl = resolvedImage)
            cache[clean] = sanitized
            cache[norm] = sanitized
            return sanitized
        } else if (hasLocalPhoto) {
            val info = ArtistInfo(
                imageUrl = localPhotoPath,
                bio = null,
                followers = "",
                listeners = "",
                origin = "Unknown"
            )
            cache[clean] = info
            cache[norm] = info
            return info
        }
        return null
    }

    fun saveArtistInfo(rawArtistName: String, info: ArtistInfo) {
        val clean = rawArtistName.trim()
        val norm = clean.lowercase()
        cache[clean] = info
        cache[norm] = info
        try {
            val json = toJson(info)
            prefs?.edit()
                ?.putString(clean, json)
                ?.putString(norm, json)
                ?.apply()

            // Save to Room database for permanent storage
            val localPhoto = if (PersistentArtistStore.hasValidLocalPhoto(clean)) {
                PersistentArtistStore.getLocalPhotoFile(clean).absolutePath
            } else null

            val entity = ArtistEntity(
                name = clean,
                imageUrl = info.imageUrl,
                localImagePath = localPhoto,
                bio = info.bio,
                followers = info.followers,
                listeners = info.listeners,
                origin = info.origin,
                website = info.website,
                facebook = info.facebook,
                twitter = info.twitter,
                instagram = info.instagram,
                spotify = info.spotify,
                youtube = info.youtube,
                appleMusic = info.appleMusic,
                deezer = info.deezer,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                PersistentArtistStore.saveArtistToRoom(entity)
            }
        } catch (e: Exception) {
            Log.e("ArtistInfoFetcher", "Error saving artist info: ${e.message}")
        }
    }

    private fun toJson(info: ArtistInfo): String {
        val obj = JSONObject()
        obj.put("imageUrl", info.imageUrl ?: "")
        obj.put("bio", info.bio ?: "")
        obj.put("followers", info.followers)
        obj.put("listeners", info.listeners)
        obj.put("origin", info.origin)
        obj.put("website", info.website ?: "")
        obj.put("facebook", info.facebook ?: "")
        obj.put("twitter", info.twitter ?: "")
        obj.put("instagram", info.instagram ?: "")
        obj.put("spotify", info.spotify ?: "")
        obj.put("youtube", info.youtube ?: "")
        obj.put("appleMusic", info.appleMusic ?: "")
        obj.put("deezer", info.deezer ?: "")
        obj.put("genre", info.genre ?: "")
        return obj.toString()
    }

    private fun fromJson(jsonStr: String): ArtistInfo? {
        return try {
            val obj = JSONObject(jsonStr)
            ArtistInfo(
                imageUrl = obj.optString("imageUrl").takeIf { it.isNotBlank() },
                bio = obj.optString("bio").takeIf { it.isNotBlank() },
                followers = obj.optString("followers", ""),
                listeners = obj.optString("listeners", ""),
                origin = obj.optString("origin", "Unknown"),
                website = obj.optString("website").takeIf { it.isNotBlank() },
                facebook = obj.optString("facebook").takeIf { it.isNotBlank() },
                twitter = obj.optString("twitter").takeIf { it.isNotBlank() },
                instagram = obj.optString("instagram").takeIf { it.isNotBlank() },
                spotify = obj.optString("spotify").takeIf { it.isNotBlank() },
                youtube = obj.optString("youtube").takeIf { it.isNotBlank() },
                appleMusic = obj.optString("appleMusic").takeIf { it.isNotBlank() },
                deezer = obj.optString("deezer").takeIf { it.isNotBlank() },
                genre = obj.optString("genre").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Primary entry point for fetching enriched artist information and photography.
     * Incorporates album, track title, and genre metadata for strict disambiguation.
     * Does NOT stop early if the cached artist has no photo (keeps looking until resolved).
     */
    suspend fun fetchArtistInfo(
        rawArtistName: String,
        trackTitle: String? = null,
        albumName: String? = null,
        genre: String? = null
    ): ArtistInfo = withContext(Dispatchers.IO) {
        val cleanArtist = rawArtistName.trim()
        val cached = getCachedArtistInfo(cleanArtist)
        
        // RULE: Only return early from cache if we ALREADY HAVE A VALID PHOTO!
        // If imageUrl is null or blank, do not treat cache as complete — continue to search!
        if (cached != null && !cached.imageUrl.isNullOrBlank()) {
            return@withContext cached
        }

        var artistName = rawArtistName.trim()
        var imageUrl: String? = cached?.imageUrl
        var bio: String? = cached?.bio
        var website: String? = cached?.website
        var facebook: String? = cached?.facebook
        var twitter: String? = cached?.twitter
        var instagram: String? = cached?.instagram
        var spotify: String? = cached?.spotify
        var youtube: String? = cached?.youtube
        var appleMusic: String? = cached?.appleMusic
        var deezer: String? = cached?.deezer
        var followersStr = cached?.followers ?: ""
        var origin = if (cached?.origin.isNullOrBlank() || cached?.origin == "Unknown") "Unknown" else cached!!.origin
        var listeners = cached?.listeners ?: ""

        val isGenericArtist = artistName.isBlank() ||
                artistName.equals("Unknown Artist", ignoreCase = true) ||
                artistName.equals("<unknown>", ignoreCase = true) ||
                artistName.equals("Desconocido", ignoreCase = true) ||
                artistName.equals("Unknown", ignoreCase = true)

        if (isGenericArtist) {
            val emptyInfo = ArtistInfo(
                imageUrl = null,
                bio = null,
                followers = "",
                listeners = "",
                origin = ""
            )
            saveArtistInfo(artistName, emptyInfo)
            return@withContext emptyInfo
        }

        var canonicalArtistName = artistName
        val targetNorm = normalizeArtistName(artistName)

        // ==================== TIER 1: VERIFIED ALBUM & TRACK CROSS-REFERENCE SEARCH (Deezer) ====================
        // Directly searches Deezer with album, track title, and genre to find the exact artist entity!
        if (imageUrl.isNullOrBlank()) {
            val verifiedResult = searchDeezerVerifiedTrack(artistName, trackTitle, albumName, genre)
            if (verifiedResult != null) {
                val (pic, name, fans, link) = verifiedResult
                if (!pic.isNullOrBlank()) {
                    imageUrl = pic
                }
                if (name.isNotBlank()) {
                    canonicalArtistName = name
                }
                if (fans > 0) {
                    followersStr = "%,d".format(fans)
                    listeners = "%,d".format((fans * 1.8).toLong())
                }
                if (!link.isNullOrBlank()) {
                    deezer = link
                }
            }
        }

        // ==================== TIER 2: SMART DEEZER ARTIST SEARCH WITH DISAMBIGUATION ====================
        // If not found yet, query artist catalog with strict candidate scoring
        if (imageUrl.isNullOrBlank()) {
            try {
                val encodedName = URLEncoder.encode(artistName, "UTF-8").replace("+", "%20")
                val url = URL("https://api.deezer.com/search/artist?q=$encodedName&limit=15")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    connectTimeout = 3500
                    readTimeout = 3500
                }

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val dataArray = json.optJSONArray("data")
                    if (dataArray != null && dataArray.length() > 0) {
                        val candidates = mutableListOf<JSONObject>()
                        for (i in 0 until dataArray.length()) {
                            candidates.add(dataArray.getJSONObject(i))
                        }

                        // Score each candidate against target artistName, album, and trackTitle
                        val bestMatch = candidates.maxByOrNull { cand ->
                            var score = 0
                            val candName = cand.optString("name", "")
                            val candNorm = normalizeArtistName(candName)

                            // 1. Exact or normalized name match
                            if (candNorm == targetNorm || candName.equals(artistName, ignoreCase = true)) {
                                score += 500
                            } else if (candNorm.startsWith("$targetNorm ") || candNorm.endsWith(" $targetNorm")) {
                                score += 50
                            } else if (candNorm.contains("&") && !targetNorm.contains("and") && !targetNorm.contains("&")) {
                                // Penalize compound names like "Soap&Skin" when target is "Soap"
                                score -= 300
                            }

                            // 2. Has photo
                            val pic = cand.optString("picture_xl").takeIf { it.isNotBlank() }
                                ?: cand.optString("picture_big").takeIf { it.isNotBlank() }
                            if (pic != null) score += 40

                            // 3. Fan tiebreaker (log-scaled)
                            val fans = cand.optInt("nb_fan", 0)
                            if (fans > 0) score += (kotlin.math.ln(fans.toDouble()).toInt()).coerceIn(0, 30)

                            score
                        }

                        if (bestMatch != null) {
                            val matchedNorm = normalizeArtistName(bestMatch.optString("name", ""))
                            // Only accept if score indicates a plausible match (prevents totally unrelated artists)
                            if (matchedNorm == targetNorm || matchedNorm.contains(targetNorm) || targetNorm.contains(matchedNorm)) {
                                val dName = bestMatch.optString("name", "")
                                if (dName.isNotBlank()) canonicalArtistName = dName

                                val pic = bestMatch.optString("picture_xl").takeIf { it.isNotBlank() }
                                    ?: bestMatch.optString("picture_big").takeIf { it.isNotBlank() }
                                    ?: bestMatch.optString("picture_medium").takeIf { it.isNotBlank() }
                                if (pic != null) imageUrl = pic

                                val link = bestMatch.optString("link").takeIf { it.isNotBlank() }
                                if (link != null) deezer = link

                                val fans = bestMatch.optInt("nb_fan", 0)
                                if (fans > 0 && followersStr.isEmpty()) {
                                    followersStr = "%,d".format(fans)
                                    listeners = "%,d".format((fans * 1.8).toLong())
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ArtistInfoFetcher", "Error in Deezer Artist API: ${e.message}")
            }
        }

        // ==================== TIER 3: APPLE ITUNES CROSS-REFERENCE SEARCH ====================
        // Highly reliable for international and regional music catalog
        if (imageUrl.isNullOrBlank()) {
            val itunesPic = searchItunesVerifiedArtist(artistName, trackTitle, albumName, genre)
            if (itunesPic != null) {
                imageUrl = itunesPic
            }
        }

        // ==================== TIER 4: THEAUDIODB & WIKIPEDIA DUAL-TIER ====================
        try {
            val bioResult = ArtistBioRepository.getArtistBiographyResult(canonicalArtistName, trackTitle, albumName, genre)
                ?: ArtistBioRepository.getArtistBiographyResult(artistName, trackTitle, albumName, genre)
            if (bioResult != null) {
                if (!bioResult.bio.isNullOrBlank()) {
                    bio = bioResult.bio
                }
                if (origin.isBlank() || origin == "Unknown") {
                    bioResult.origin?.takeIf { it.isNotBlank() }?.let { origin = it }
                }
                if (imageUrl.isNullOrBlank()) {
                    bioResult.fanartUrl?.takeIf { it.isNotBlank() }?.let { imageUrl = it }
                        ?: bioResult.thumbUrl?.takeIf { it.isNotBlank() }?.let { imageUrl = it }
                }
            }
        } catch (e: Exception) {
            Log.e("ArtistInfoFetcher", "Error in ArtistBioRepository: ${e.message}")
        }

        // ==================== TIER 5: MUSICBRAINZ API (Entity Resolution & Origin) ====================
        try {
            val mbResult = fetchMusicBrainzArtistData(canonicalArtistName, albumName)
            if (mbResult != null) {
                if (mbResult.origin.isNotBlank() && mbResult.origin != "Unknown") {
                    origin = mbResult.origin
                }
                if (bio.isNullOrBlank() && !mbResult.bio.isNullOrBlank() && !isNonMusicDefinition(mbResult.bio)) {
                    bio = mbResult.bio
                }
            }
        } catch (e: Exception) {
            Log.e("ArtistInfoFetcher", "Error in MusicBrainz API: ${e.message}")
        }

        // ==================== TIER 6: LOCAL REPOSITORY CHECK ====================
        if (imageUrl.isNullOrBlank()) {
            try {
                imageUrl = ArtistImageRepository.getArtistImageUrl(canonicalArtistName, trackTitle, albumName, genre)
                    ?: ArtistImageRepository.getArtistImageUrl(artistName, trackTitle, albumName, genre)
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Bio fallback with contextual tags (Genre, Album, Song) to prevent generic terms
        if (bio.isNullOrBlank() || isNonMusicDefinition(bio)) {
            val tagsList = mutableListOf<String>()
            if (!genre.isNullOrBlank() && genre != "Unknown" && genre != "<unknown>") {
                tagsList.add("en el género $genre")
            }
            if (!albumName.isNullOrBlank() && albumName != "Unknown" && albumName != "<unknown>" && albumName != "Unknown Album") {
                tagsList.add("con el álbum '$albumName'")
            }
            if (!trackTitle.isNullOrBlank() && trackTitle != "Unknown" && trackTitle != "<unknown>") {
                tagsList.add("y canciones como '$trackTitle'")
            }
            val details = if (tagsList.isNotEmpty()) " destacando " + tagsList.joinToString(", ") else ""
            bio = if (followersStr.isNotEmpty()) {
                "$canonicalArtistName es un artista musical con más de $followersStr oyentes$details."
            } else {
                "$canonicalArtistName es un proyecto artístico y musical$details."
            }
        }

        val encodedForLinks = URLEncoder.encode(canonicalArtistName, "UTF-8").replace("+", "%20")
        if (spotify == null || !spotify.contains(encodedForLinks, ignoreCase = true)) {
            spotify = "https://open.spotify.com/search/${encodedForLinks}"
        }
        if (youtube == null) youtube = "https://www.youtube.com/results?search_query=${encodedForLinks}+artist"
        if (appleMusic == null) appleMusic = "https://music.apple.com/search?term=${encodedForLinks}"

        var resolvedImage = imageUrl
        if (!imageUrl.isNullOrBlank()) {
            try {
                val permanent = PersistentArtistStore.ensurePermanentPhoto(canonicalArtistName, imageUrl)
                if (!permanent.isNullOrBlank()) {
                    resolvedImage = permanent
                }
            } catch (_: Exception) {}
        }

        val info = ArtistInfo(
            imageUrl = resolvedImage,
            bio = bio,
            followers = followersStr,
            listeners = listeners,
            origin = origin,
            website = website,
            facebook = facebook,
            twitter = twitter,
            instagram = instagram,
            spotify = spotify,
            youtube = youtube,
            appleMusic = appleMusic,
            deezer = deezer,
            genre = genre
        )

        saveArtistInfo(artistName, info)
        saveArtistInfo(canonicalArtistName, info)
        saveArtistInfo(rawArtistName, info)
        info
    }

    private data class VerifiedDeezerMatch(
        val pictureUrl: String?,
        val artistName: String,
        val nbFans: Int,
        val link: String?
    )

    /**
     * Cross-references Deezer track and album search with track title and album metadata.
     * Matches the exact recording and extracts the verified artist ID and high-res picture.
     */
    private fun searchDeezerVerifiedTrack(
        artistName: String,
        trackTitle: String?,
        albumName: String?,
        genre: String? = null
    ): VerifiedDeezerMatch? {
        val targetNorm = normalizeArtistName(artistName)
        val queries = mutableListOf<String>()

        if (!albumName.isNullOrBlank() && albumName != "<unknown>" && albumName != "Unknown Album") {
            queries.add("artist:\"$artistName\" album:\"$albumName\"")
            queries.add("\"$artistName\" \"$albumName\"")
        }
        if (!trackTitle.isNullOrBlank() && trackTitle != "<unknown>") {
            queries.add("artist:\"$artistName\" track:\"$trackTitle\"")
            queries.add("\"$artistName\" \"$trackTitle\"")
        }
        if (!genre.isNullOrBlank() && genre != "<unknown>" && genre != "Unknown") {
            queries.add("artist:\"$artistName\" \"$genre\"")
        }

        for (query in queries) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
                val url = URL("https://api.deezer.com/search?q=$encoded&limit=6")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(resp)
                    val data = json.optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        for (i in 0 until data.length()) {
                            val trackObj = data.getJSONObject(i)
                            val artistObj = trackObj.optJSONObject("artist") ?: continue
                            val candArtistName = artistObj.optString("name", "")
                            val candNorm = normalizeArtistName(candArtistName)

                            // Check artist name match (exact or normalized)
                            val matchesArtist = candNorm == targetNorm ||
                                    candNorm.replace(" ", "") == targetNorm.replace(" ", "")

                            if (matchesArtist) {
                                val artistId = artistObj.optLong("id", 0L)
                                var pic = artistObj.optString("picture_xl").takeIf { it.isNotBlank() }
                                    ?: artistObj.optString("picture_big").takeIf { it.isNotBlank() }
                                    ?: artistObj.optString("picture_medium").takeIf { it.isNotBlank() }

                                var fans = 0
                                var link = artistObj.optString("link").takeIf { it.isNotBlank() }

                                // If artistId exists, query artist endpoint for verified picture_xl and fans
                                if (artistId > 0L) {
                                    try {
                                        val artistUrl = URL("https://api.deezer.com/artist/$artistId")
                                        val aConn = (artistUrl.openConnection() as HttpURLConnection).apply {
                                            requestMethod = "GET"
                                            setRequestProperty("User-Agent", "Mozilla/5.0")
                                            connectTimeout = 2500
                                            readTimeout = 2500
                                        }
                                        if (aConn.responseCode == 200) {
                                            val aResp = aConn.inputStream.bufferedReader().use { it.readText() }
                                            val aJson = JSONObject(aResp)
                                            val fullPic = aJson.optString("picture_xl").takeIf { it.isNotBlank() }
                                                ?: aJson.optString("picture_big").takeIf { it.isNotBlank() }
                                            if (fullPic != null) pic = fullPic
                                            fans = aJson.optInt("nb_fan", 0)
                                            val fLink = aJson.optString("link").takeIf { it.isNotBlank() }
                                            if (fLink != null) link = fLink
                                        }
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }

                                if (!pic.isNullOrBlank()) {
                                    return VerifiedDeezerMatch(
                                        pictureUrl = pic,
                                        artistName = candArtistName,
                                        nbFans = fans,
                                        link = link
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore and try next query
            }
        }
        return null
    }

    /**
     * Cross-references Apple iTunes API using artist name and album/track.
     */
    private fun searchItunesVerifiedArtist(
        artistName: String,
        trackTitle: String?,
        albumName: String?,
        genre: String? = null
    ): String? {
        val targetNorm = normalizeArtistName(artistName)
        val queries = mutableListOf<String>()
        if (!albumName.isNullOrBlank()) queries.add("$artistName $albumName")
        if (!trackTitle.isNullOrBlank()) queries.add("$artistName $trackTitle")
        if (!genre.isNullOrBlank() && genre != "<unknown>" && genre != "Unknown") queries.add("$artistName $genre")
        queries.add(artistName)

        for (q in queries) {
            try {
                val encoded = URLEncoder.encode(q, "UTF-8").replace("+", "%20")
                val url = URL("https://itunes.apple.com/search?term=$encoded&entity=song&limit=5")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(resp)
                    val results = json.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        for (i in 0 until results.length()) {
                            val item = results.getJSONObject(i)
                            val candArtist = item.optString("artistName", "")
                            if (normalizeArtistName(candArtist) == targetNorm) {
                                val artwork = item.optString("artworkUrl100", "")
                                if (artwork.isNotBlank()) {
                                    // Upgrade 100x100 thumbnail to 1000x1000 high-res artwork
                                    return artwork.replace("100x100bb", "1000x1000bb")
                                        .replace("100x100", "1000x1000")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return null
    }

    private data class MusicBrainzData(
        val mbid: String,
        val origin: String,
        val bio: String?
    )

    private fun fetchMusicBrainzArtistData(artistName: String, albumName: String? = null): MusicBrainzData? {
        return try {
            val encodedName = URLEncoder.encode(artistName, "UTF-8")
            val url = URL("https://musicbrainz.org/ws/2/artist/?query=artist:$encodedName&fmt=json&limit=5")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "LiquidMusicPlayer/1.0 ( music@example.com )")
                connectTimeout = 3500
                readTimeout = 3500
            }

            if (connection.responseCode != 200) return null
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val artists = json.optJSONArray("artists") ?: return null
            if (artists.length() == 0) return null

            val targetNorm = normalizeArtistName(artistName)
            var bestArtist: JSONObject? = null
            for (i in 0 until artists.length()) {
                val candidate = artists.getJSONObject(i)
                val candName = candidate.optString("name", "")
                if (normalizeArtistName(candName) == targetNorm) {
                    bestArtist = candidate
                    break
                }
            }
            if (bestArtist == null) bestArtist = artists.getJSONObject(0)

            val mbid = bestArtist.optString("id")
            val areaObj = bestArtist.optJSONObject("area")
            val origin = areaObj?.optString("name")
                ?: bestArtist.optString("country").takeIf { it.isNotBlank() }
                ?: "Unknown"

            var artistBio: String? = null

            if (mbid.isNotBlank()) {
                val relUrl = URL("https://musicbrainz.org/ws/2/artist/$mbid?inc=url-rels&fmt=json")
                val relConn = (relUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "LiquidMusicPlayer/1.0 ( music@example.com )")
                    connectTimeout = 3500
                    readTimeout = 3500
                }

                if (relConn.responseCode == 200) {
                    val relResponse = relConn.inputStream.bufferedReader().use { it.readText() }
                    val relJson = JSONObject(relResponse)
                    val relations = relJson.optJSONArray("relations")
                    var wikidataId: String? = null

                    if (relations != null) {
                        for (i in 0 until relations.length()) {
                            val rel = relations.getJSONObject(i)
                            if (rel.optString("type") == "wikidata") {
                                val resUrl = rel.optJSONObject("url")?.optString("resource") ?: ""
                                val qid = resUrl.trimEnd('/').substringAfterLast('/')
                                if (qid.startsWith("Q")) {
                                    wikidataId = qid
                                    break
                                }
                            }
                        }
                    }

                    if (wikidataId != null) {
                        artistBio = resolveBioFromWikidata(wikidataId)
                    }
                }
            }

            MusicBrainzData(mbid = mbid, origin = origin, bio = artistBio)
        } catch (e: Exception) {
            Log.e("ArtistInfoFetcher", "Error fetching from MusicBrainz: ${e.message}")
            null
        }
    }

    private fun resolveBioFromWikidata(wikidataId: String): String? {
        return try {
            val url = URL("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$wikidataId&props=sitelinks|descriptions&format=json")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "LiquidMusicPlayer/1.0")
                connectTimeout = 3500
                readTimeout = 3500
            }

            if (connection.responseCode != 200) return null
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val entity = json.optJSONObject("entities")?.optJSONObject(wikidataId) ?: return null
            val sitelinks = entity.optJSONObject("sitelinks") ?: return null

            val esTitle = sitelinks.optJSONObject("eswiki")?.optString("title")
            val enTitle = sitelinks.optJSONObject("enwiki")?.optString("title")

            val title = esTitle ?: enTitle ?: return null
            val lang = if (!esTitle.isNullOrBlank()) "es" else "en"

            val encodedTitle = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
            val sumUrl = URL("https://$lang.wikipedia.org/api/rest_v1/page/summary/$encodedTitle")
            val sumConn = (sumUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "LiquidMusicPlayer/1.0 (Android; music-app)")
                connectTimeout = 3500
                readTimeout = 3500
            }

            if (sumConn.responseCode == 200) {
                val sumResponse = sumConn.inputStream.bufferedReader().use { it.readText() }
                val sumJson = JSONObject(sumResponse)
                val extract = sumJson.optString("extract", "")
                if (extract.length > 30 && !isNonMusicDefinition(extract)) {
                    extract
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isNonMusicDefinition(text: String): Boolean {
        return ArtistBioRepository.isNonMusicDefinition(text)
    }

    suspend fun fetchArtistPhoto(
        artistName: String,
        trackTitle: String? = null,
        albumName: String? = null,
        genre: String? = null
    ): String? {
        val info = fetchArtistInfo(
            rawArtistName = artistName,
            trackTitle = trackTitle,
            albumName = albumName,
            genre = genre
        )
        return info.imageUrl
    }

    /**
     * Resolves cached artist profiles for all individual artists associated with a track.
     */
    fun getCachedArtistsForTrack(track: com.example.model.Track?): List<ArtistProfile> {
        if (track == null) return emptyList()
        val names = com.example.util.ArtistParser.extractAllArtists(track.artist, track.title)
        if (names.isEmpty()) return emptyList()
        return names.mapIndexed { index, name ->
            val info = getCachedArtistInfo(name) ?: ArtistInfo(
                imageUrl = null,
                bio = null,
                followers = "",
                listeners = "",
                origin = ""
            )
            ArtistProfile(name = name, isPrimary = index == 0, info = info)
        }
    }

    /**
     * Fetches and enriches profiles for each individual artist found in the track tags/title,
     * passing album, title, and genre for precise disambiguation.
     */
    suspend fun fetchArtistsForTrack(track: com.example.model.Track?): List<ArtistProfile> = withContext(Dispatchers.IO) {
        if (track == null) return@withContext emptyList()
        val names = com.example.util.ArtistParser.extractAllArtists(track.artist, track.title)
        if (names.isEmpty()) return@withContext emptyList()
        names.mapIndexed { index, name ->
            val info = fetchArtistInfo(
                rawArtistName = name,
                trackTitle = track.title,
                albumName = track.album,
                genre = track.genre
            )
            ArtistProfile(name = name, isPrimary = index == 0, info = info)
        }
    }

    suspend fun translateArtistInfoIfNeeded(info: ArtistInfo, appLanguage: String): ArtistInfo {
        val bioText = info.bio
        if (bioText.isNullOrBlank()) return info

        val targetCode = TranslationHelper.mapToLanguageCode(appLanguage)
        if (TranslationHelper.isAlreadyInTargetLanguage(bioText, targetCode)) {
            return info
        }

        val translatedBio = TranslationHelper.translateText(bioText, appLanguage)
        return if (!translatedBio.isNullOrBlank()) {
            info.copy(bio = translatedBio)
        } else {
            info
        }
    }
}
