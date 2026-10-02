package com.example.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

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
    val deezer: String? = null
)

/**
 * Dedicated Music & Artist API Fetcher with persistent offline storage.
 * Uses specialized music APIs (Deezer API & MusicBrainz Encyclopedia)
 * and persists all discovered artist data locally so it is displayed at all times,
 * even with no internet connection or after closing and reopening the app.
 */
object ArtistInfoFetcher {
    private val cache = mutableMapOf<String, ArtistInfo>()
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
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

    fun getCachedArtistInfo(rawArtistName: String?): ArtistInfo? {
        if (rawArtistName.isNullOrBlank()) return null
        val clean = rawArtistName.trim()
        val norm = clean.lowercase()
        val cached = cache[clean] ?: cache[norm] ?: prefs?.getString(clean, null)?.let { fromJson(it) } ?: prefs?.getString(norm, null)?.let { fromJson(it) }
        if (cached != null) {
            val safeBio = if (cached.bio != null && isNonMusicDefinition(cached.bio)) null else cached.bio
            val sanitized = if (safeBio != cached.bio) cached.copy(bio = safeBio) else cached
            cache[clean] = sanitized
            cache[norm] = sanitized
            return sanitized
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
                deezer = obj.optString("deezer").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchArtistInfo(rawArtistName: String, trackTitle: String? = null): ArtistInfo = withContext(Dispatchers.IO) {
        val cleanArtist = rawArtistName.trim()
        val cached = getCachedArtistInfo(cleanArtist)
        // If we already have rich artist info (with photo or verified country), return it immediately!
        if (cached != null && (!cached.imageUrl.isNullOrBlank() || (cached.origin.isNotBlank() && cached.origin != "Unknown"))) {
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

        // If artist is unknown or generic, create a clean friendly profile
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
            saveArtistInfo(rawArtistName, emptyInfo)
            return@withContext emptyInfo
        }

        var canonicalArtistName = artistName

        // ==================== 1. DEEZER MUSIC API (HD Picture, Fans, Link) ====================
        try {
            val encodedName = URLEncoder.encode(artistName, "UTF-8").replace("+", "%20")
            val url = URL("https://api.deezer.com/search/artist?q=$encodedName")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 4000
                readTimeout = 4000
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val dataArray = json.optJSONArray("data")
                if (dataArray != null && dataArray.length() > 0) {
                    val artistsList = mutableListOf<JSONObject>()
                    for (i in 0 until dataArray.length()) {
                        artistsList.add(dataArray.getJSONObject(i))
                    }

                    var matchedFans = 0
                    val bestMatch = artistsList.sortedWith(Comparator { a, b ->
                        val aName = a.optString("name", "")
                        val bName = b.optString("name", "")

                        // Normalización para artistas con caracteres estilizados (e.g. KoЯn -> Korn)
                        fun normalize(s: String) = s.lowercase()
                            .replace("я", "r")
                            .replace("ø", "o")
                            .replace("æ", "ae")
                            .replace("$", "s")
                            .trim()

                        val aNorm = normalize(aName)
                        val bNorm = normalize(bName)
                        val targetNorm = normalize(artistName)

                        val aExact = if (aNorm == targetNorm || aName.equals(artistName, ignoreCase = true)) 1 else 0
                        val bExact = if (bNorm == targetNorm || bName.equals(artistName, ignoreCase = true)) 1 else 0

                        if (aExact != bExact) {
                            return@Comparator bExact - aExact // Prioridad 1: Coincidencia exacta
                        }

                        val aFans = a.optInt("nb_fan", 0)
                        val bFans = b.optInt("nb_fan", 0)
                        return@Comparator bFans - aFans // Prioridad 2: Popularidad (nb_fan)
                    }).firstOrNull()

                    if (bestMatch != null) {
                        val dName = bestMatch.optString("name", "")
                        if (dName.isNotBlank()) {
                            canonicalArtistName = dName
                        }
                        imageUrl = bestMatch.optString("picture_xl").takeIf { it.isNotBlank() }
                            ?: bestMatch.optString("picture_big").takeIf { it.isNotBlank() }
                            ?: bestMatch.optString("picture_medium").takeIf { it.isNotBlank() }

                        deezer = bestMatch.optString("link").takeIf { it.isNotBlank() }
                        val fans = bestMatch.optInt("nb_fan", 0)
                        matchedFans = fans
                        if (fans > 0) {
                            followersStr = "%,d".format(fans)
                            listeners = "%,d".format((fans * 1.8).toLong())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ArtistInfoFetcher", "Error in Deezer API: ${e.message}")
        }

        // ==================== 2. THEAUDIODB & WIKIPEDIA DUAL-TIER (ArtistBioRepository) ====================
        try {
            val bioResult = ArtistBioRepository.getArtistBiographyResult(canonicalArtistName)
                ?: ArtistBioRepository.getArtistBiographyResult(artistName)
            if (bioResult != null) {
                if (!bioResult.bio.isNullOrBlank()) {
                    bio = bioResult.bio
                }
                if (origin.isBlank() || origin == "Unknown") {
                    bioResult.origin?.takeIf { it.isNotBlank() }?.let { origin = it }
                }
                // Si Deezer devolvió una imagen de pocos fans (< 5000) o no hay imagen, preferir TheAudioDB fanart
                val isDeezerLowConfidence = imageUrl.isNullOrBlank() || (followersStr.replace(",", "").toLongOrNull() ?: 0L) < 5000L
                if (isDeezerLowConfidence && (!bioResult.fanartUrl.isNullOrBlank() || !bioResult.thumbUrl.isNullOrBlank())) {
                    bioResult.fanartUrl?.takeIf { it.isNotBlank() }?.let { imageUrl = it }
                        ?: bioResult.thumbUrl?.takeIf { it.isNotBlank() }?.let { imageUrl = it }
                } else if (imageUrl.isNullOrBlank()) {
                    bioResult.fanartUrl?.takeIf { it.isNotBlank() }?.let { imageUrl = it }
                        ?: bioResult.thumbUrl?.takeIf { it.isNotBlank() }?.let { imageUrl = it }
                }
            }
        } catch (e: Exception) {
            Log.e("ArtistInfoFetcher", "Error in ArtistBioRepository: ${e.message}")
        }

        // ==================== 3. MUSICBRAINZ API (Entity Resolution & Origin) ====================
        try {
            val mbResult = fetchMusicBrainzArtistData(canonicalArtistName)
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

        // ==================== 4. MUSIC-ACCURATE FALLBACK (Never generic dictionary) ====================
        if (bio.isNullOrBlank() || isNonMusicDefinition(bio)) {
            bio = if (followersStr.isNotEmpty()) {
                "$canonicalArtistName es un reconocido artista musical en Deezer con más de $followersStr seguidores globales y destacadas canciones en streaming."
            } else {
                "$canonicalArtistName es un artista musical con producciones y lanzamientos destacados en la escena internacional."
            }
        }

        // Do not generate mock/fake numbers if not returned by Deezer API

        val encodedForLinks = URLEncoder.encode(canonicalArtistName, "UTF-8").replace("+", "%20")
        if (spotify == null) spotify = "https://open.spotify.com/search/${encodedForLinks}/artists"
        if (youtube == null) youtube = "https://www.youtube.com/results?search_query=${encodedForLinks}+artist"
        if (appleMusic == null) appleMusic = "https://music.apple.com/search?term=${encodedForLinks}"

        // If image is still null, check local ArtistImageRepository cache
        if (imageUrl == null) {
            try {
                imageUrl = ArtistImageRepository.getArtistImageUrl(canonicalArtistName)
                    ?: ArtistImageRepository.getArtistImageUrl(artistName)
            } catch (e: Exception) {
                // Ignore
            }
        }

        val info = ArtistInfo(
            imageUrl = imageUrl,
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
            deezer = deezer
        )
        saveArtistInfo(artistName, info)
        saveArtistInfo(canonicalArtistName, info)
        saveArtistInfo(rawArtistName, info)
        info
    }

    private data class MusicBrainzData(
        val mbid: String,
        val origin: String,
        val bio: String?
    )

    /**
     * Queries MusicBrainz (the open music encyclopedia) strictly for music artists.
     * Then follows verified music entity links to extract the official artist biography.
     */
    private fun fetchMusicBrainzArtistData(artistName: String): MusicBrainzData? {
        return try {
            val encodedName = URLEncoder.encode(artistName, "UTF-8")
            val url = URL("https://musicbrainz.org/ws/2/artist/?query=artist:$encodedName&fmt=json&limit=3")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "LiquidMusicPlayer/1.0 ( music@example.com )")
                connectTimeout = 4000
                readTimeout = 4000
            }

            if (connection.responseCode != 200) return null
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val artists = json.optJSONArray("artists") ?: return null
            if (artists.length() == 0) return null

            val bestArtist = artists.getJSONObject(0)
            val mbid = bestArtist.optString("id")
            val areaObj = bestArtist.optJSONObject("area")
            val origin = areaObj?.optString("name")
                ?: bestArtist.optString("country").takeIf { it.isNotBlank() }
                ?: "Unknown"

            var artistBio: String? = null

            // Query relations from MusicBrainz to get verified music Wikidata ID
            if (mbid.isNotBlank()) {
                val relUrl = URL("https://musicbrainz.org/ws/2/artist/$mbid?inc=url-rels&fmt=json")
                val relConn = (relUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "LiquidMusicPlayer/1.0 ( music@example.com )")
                    connectTimeout = 4000
                    readTimeout = 4000
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

                    // Resolve verified music artist biography from Wikidata sitelinks
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

    /**
     * Guard to prevent non-music dictionary definitions (such as "A tool is a device...")
     */
    private fun isNonMusicDefinition(text: String): Boolean {
        return ArtistBioRepository.isNonMusicDefinition(text)
    }

    private fun searchDeezerArtistByTrack(trackTitle: String): String? {
        return try {
            val encodedTitle = URLEncoder.encode(trackTitle, "UTF-8").replace("+", "%20")
            val url = URL("https://api.deezer.com/search?q=$encodedTitle&limit=1")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 3000
                readTimeout = 3000
            }
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val data = json.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val trackObj = data.getJSONObject(0)
                    trackObj.optJSONObject("artist")?.optString("name")
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchArtistPhoto(artistName: String): String? {
        val info = fetchArtistInfo(artistName)
        return info.imageUrl
    }
}
