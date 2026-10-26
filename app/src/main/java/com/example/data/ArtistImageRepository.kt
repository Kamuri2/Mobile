package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val name: String,
    val imageUrl: String?,
    val localImagePath: String? = null,
    val bio: String? = null,
    val followers: String? = null,
    val listeners: String? = null,
    val origin: String? = null,
    val genre: String? = null,
    val website: String? = null,
    val facebook: String? = null,
    val twitter: String? = null,
    val instagram: String? = null,
    val spotify: String? = null,
    val youtube: String? = null,
    val appleMusic: String? = null,
    val deezer: String? = null,
    val lastUpdatedTimestamp: Long = 0L
)

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists WHERE name = :name")
    suspend fun getArtist(name: String): ArtistEntity?

    @Query("SELECT * FROM artists")
    suspend fun getAllArtists(): List<ArtistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artist: ArtistEntity)

    @Query("DELETE FROM artists")
    suspend fun clearAll()
}

object ArtistImageRepository {
    private var dao: ArtistDao? = null
    private val inMemoryCache = ConcurrentHashMap<String, String>()

    fun init(context: Context) {
        try {
            dao = AppDatabase.getDatabase(context).artistDao()
            PersistentArtistStore.init(context)
        } catch (e: Exception) {
            Log.e("ArtistImageRepository", "Error initializing ArtistImageRepository: ${e.message}")
        }
    }

    fun clearCache() {
        inMemoryCache.clear()
    }

    /**
     * Fast synchronous check of in-memory artist image URL or local file (0ms lookup).
     */
    fun getMemoryCachedImageUrl(artistName: String): String? {
        val clean = artistName.trim().lowercase()
        inMemoryCache[clean]?.let { return it }
        if (PersistentArtistStore.hasValidLocalPhoto(artistName)) {
            val path = PersistentArtistStore.getLocalPhotoFile(artistName).absolutePath
            inMemoryCache[clean] = path
            return path
        }
        return null
    }

    suspend fun getArtistImageUrl(
        artistName: String,
        trackTitle: String? = null,
        albumName: String? = null,
        genre: String? = null,
        forceRefresh: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val clean = artistName.trim().lowercase()

        if (!forceRefresh) {
            inMemoryCache[clean]?.let { return@withContext it }

            // 1. Check local persistent storage
            if (PersistentArtistStore.hasValidLocalPhoto(artistName)) {
                val path = PersistentArtistStore.getLocalPhotoFile(artistName).absolutePath
                inMemoryCache[clean] = path
                return@withContext path
            }

            // 2. Check in-memory from ArtistInfoFetcher
            val infoCached = ArtistInfoFetcher.getCachedArtistInfo(artistName)?.imageUrl
            if (!infoCached.isNullOrBlank()) {
                inMemoryCache[clean] = infoCached
                return@withContext infoCached
            }

            // 3. Check Room Database
            val cached = dao?.getArtist(artistName)
            if (cached != null) {
                if (!cached.localImagePath.isNullOrBlank() && java.io.File(cached.localImagePath).exists()) {
                    inMemoryCache[clean] = cached.localImagePath
                    return@withContext cached.localImagePath
                }
                if (!cached.imageUrl.isNullOrBlank()) {
                    inMemoryCache[clean] = cached.imageUrl
                    return@withContext cached.imageUrl
                }
            }
        }

        var imageUrl: String? = null
        val targetNorm = ArtistInfoFetcher.normalizeArtistName(artistName)

        // 3. Priority Track & Album Verification Search (Deezer) with Exact Anchors
        val queries = mutableListOf<String>()
        if (!albumName.isNullOrBlank() && albumName != "<unknown>" && albumName != "Unknown Album") {
            queries.add("artist:\"$artistName\" album:\"$albumName\"")
        }
        if (!trackTitle.isNullOrBlank() && trackTitle != "<unknown>") {
            queries.add("artist:\"$artistName\" track:\"$trackTitle\"")
        }
        if (!albumName.isNullOrBlank() && !trackTitle.isNullOrBlank()) {
            queries.add("$artistName $albumName")
        }
        if (!genre.isNullOrBlank() && genre != "<unknown>" && genre != "Unknown") {
            queries.add("artist:\"$artistName\" \"$genre\"")
        }

        for (q in queries) {
            try {
                val encoded = java.net.URLEncoder.encode(q, "UTF-8").replace("+", "%20")
                val url = URL("https://api.deezer.com/search?q=$encoded&limit=6")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val dataArray = json.optJSONArray("data")
                    if (dataArray != null && dataArray.length() > 0) {
                        for (i in 0 until dataArray.length()) {
                            val trackObj = dataArray.getJSONObject(i)
                            val aObj = trackObj.optJSONObject("artist") ?: continue
                            val candName = aObj.optString("name", "")
                            if (ArtistInfoFetcher.normalizeArtistName(candName) == targetNorm ||
                                candName.equals(artistName, ignoreCase = true) ||
                                candName.contains(artistName, ignoreCase = true)
                            ) {
                                val pic = aObj.optString("picture_xl").takeIf { it.isNotBlank() }
                                    ?: aObj.optString("picture_big").takeIf { it.isNotBlank() }
                                    ?: aObj.optString("picture_medium").takeIf { it.isNotBlank() }
                                if (pic != null) {
                                    imageUrl = pic
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            if (imageUrl != null) break
        }

        // 4. Deezer Artist Search with Disambiguation by Popularity (nb_fan)
        if (imageUrl == null) {
            try {
                val encodedName = java.net.URLEncoder.encode(artistName, "UTF-8").replace("+", "%20")
                val url = URL("https://api.deezer.com/search/artist?q=$encodedName&limit=15")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val dataArray = json.optJSONArray("data")
                    if (dataArray != null && dataArray.length() > 0) {
                        var bestCandidatePic: String? = null
                        var maxFans = -1
                        for (i in 0 until dataArray.length()) {
                            val cand = dataArray.getJSONObject(i)
                            val candName = cand.optString("name", "")
                            val candNorm = ArtistInfoFetcher.normalizeArtistName(candName)
                            val fans = cand.optInt("nb_fan", 0)
                            
                            // Check exact or closest match
                            if (candNorm == targetNorm || candName.equals(artistName, ignoreCase = true)) {
                                val pic = cand.optString("picture_xl").takeIf { it.isNotBlank() }
                                    ?: cand.optString("picture_big").takeIf { it.isNotBlank() }
                                    ?: cand.optString("picture_medium").takeIf { it.isNotBlank() }
                                if (pic != null && fans >= maxFans) {
                                    maxFans = fans
                                    bestCandidatePic = pic
                                }
                            }
                        }
                        if (bestCandidatePic != null) {
                            imageUrl = bestCandidatePic
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ArtistImageRepository", "Deezer artist fetch failed: ${e.message}")
            }
        }

        // 5. TheAudioDB Fallback Search if Deezer failed
        if (imageUrl == null) {
            try {
                val audioDb = ArtistBioRepository.getArtistBiographyResult(
                    artistName = artistName,
                    trackTitle = trackTitle,
                    albumName = albumName,
                    genre = genre
                )
                if (audioDb != null) {
                    imageUrl = audioDb.fanartUrl ?: audioDb.thumbUrl
                }
            } catch (_: Exception) {}
        }

        // 6. Save only valid non-null images permanently to filesDir and Room
        if (imageUrl != null) {
            val permanentPath = PersistentArtistStore.ensurePermanentPhoto(artistName, imageUrl) ?: imageUrl
            inMemoryCache[clean] = permanentPath
            return@withContext permanentPath
        }

        return@withContext null
    }
}
