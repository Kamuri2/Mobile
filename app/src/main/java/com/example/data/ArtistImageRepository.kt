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

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val name: String,
    val imageUrl: String?
)

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists WHERE name = :name")
    suspend fun getArtist(name: String): ArtistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artist: ArtistEntity)
}

object ArtistImageRepository {
    private var dao: ArtistDao? = null

    fun init(context: Context) {
        try {
            dao = AppDatabase.getDatabase(context).artistDao()
        } catch (e: Exception) {
            Log.e("ArtistImageRepository", "Error initializing ArtistImageRepository: ${e.message}")
        }
    }

    suspend fun getArtistImageUrl(artistName: String): String? = withContext(Dispatchers.IO) {
        if (dao == null) return@withContext null
        
        // 1. Check Room
        val cached = dao?.getArtist(artistName)
        if (cached != null) {
            return@withContext cached.imageUrl
        }

        // 2. Fetch from Deezer API
        var imageUrl: String? = null
        try {
            val encodedName = java.net.URLEncoder.encode(artistName, "UTF-8").replace("+", "%20")
            val url = URL("https://api.deezer.com/search/artist?q=$encodedName")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val dataArray = json.optJSONArray("data")
                if (dataArray != null && dataArray.length() > 0) {
                    val firstArtist = dataArray.getJSONObject(0)
                    imageUrl = firstArtist.optString("picture_xl")
                }
            }
        } catch (e: Exception) {
            Log.e("ArtistImageRepository", "Deezer fetch failed: ${e.message}")
        }
        
        // 4. Save to Room
        dao?.insert(ArtistEntity(name = artistName, imageUrl = imageUrl))

        return@withContext imageUrl
    }
}
