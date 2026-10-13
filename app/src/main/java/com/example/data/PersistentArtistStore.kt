package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object PersistentArtistStore {
    private const val TAG = "PersistentArtistStore"
    private const val PHOTOS_DIR = "artist_photos"

    @Volatile
    private var appContext: Context? = null
    private var dao: ArtistDao? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            try {
                dao = AppDatabase.getDatabase(context.applicationContext).artistDao()
                val dir = getPhotosDir()
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize PersistentArtistStore: ${e.message}")
            }
        }
    }

    fun getPhotosDir(): File {
        val ctx = appContext ?: throw IllegalStateException("PersistentArtistStore must be initialized with Context")
        val dir = File(ctx.cacheDir, PHOTOS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Clears all artist photos from cache/internal storage and removes all artist records from Room database.
     */
    suspend fun clearAllPhotosAndDatabase(context: Context) = withContext(Dispatchers.IO) {
        try {
            dao?.clearAll()
            // Delete cache photos directory
            val cachePhotos = File(context.cacheDir, PHOTOS_DIR)
            if (cachePhotos.exists()) {
                cachePhotos.deleteRecursively()
            }
            // Also clean legacy filesDir photos if any exist
            val filesPhotos = File(context.filesDir, PHOTOS_DIR)
            if (filesPhotos.exists()) {
                filesPhotos.deleteRecursively()
            }
            Log.d(TAG, "All artist photos and Room database records cleared successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing artist photos and database: ${e.message}")
        }
    }

    fun getLocalPhotoFile(artistName: String): File {
        val safeName = sanitizeArtistName(artistName)
        return File(getPhotosDir(), "${safeName}.jpg")
    }

    fun sanitizeArtistName(artistName: String): String {
        return artistName.trim().lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "unknown" }
    }

    fun hasValidLocalPhoto(artistName: String): Boolean {
        return try {
            val file = getLocalPhotoFile(artistName)
            file.exists() && file.length() > 512L
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getArtistFromRoom(artistName: String): ArtistEntity? = withContext(Dispatchers.IO) {
        try {
            dao?.getArtist(artistName.trim())
                ?: dao?.getArtist(artistName.trim().lowercase())
        } catch (e: Exception) {
            Log.e(TAG, "Error getting artist from Room: ${e.message}")
            null
        }
    }

    suspend fun saveArtistToRoom(artist: ArtistEntity) = withContext(Dispatchers.IO) {
        try {
            dao?.insert(artist)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving artist to Room: ${e.message}")
        }
    }

    /**
     * Downloads and saves artist photo to cacheDir (cleared when user clears app cache).
     */
    suspend fun downloadAndSavePhoto(artistName: String, remoteUrl: String): String? = withContext(Dispatchers.IO) {
        if (remoteUrl.isBlank() || remoteUrl.startsWith("file://") || remoteUrl.startsWith("/")) {
            return@withContext remoteUrl
        }
        try {
            val targetFile = getLocalPhotoFile(artistName)
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.${System.currentTimeMillis()}.tmp")

            val url = URL(remoteUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 512L) {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    tempFile.renameTo(targetFile)
                    Log.d(TAG, "Saved permanent photo for '$artistName' (${targetFile.length()} bytes) at ${targetFile.absolutePath}")
                    return@withContext targetFile.absolutePath
                } else {
                    tempFile.delete()
                }
            } else {
                Log.w(TAG, "HTTP error ${conn.responseCode} downloading photo for '$artistName'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception downloading photo for '$artistName': ${e.message}")
        }
        null
    }

    /**
     * Ensures artist photo is permanently stored and only updated if remote URL changed.
     */
    suspend fun ensurePermanentPhoto(artistName: String, remoteUrl: String?): String? = withContext(Dispatchers.IO) {
        val clean = artistName.trim()
        val localFile = getLocalPhotoFile(clean)

        // 1. Check existing Room record
        val existing = getArtistFromRoom(clean)
        val hasLocal = localFile.exists() && localFile.length() > 512L

        if (hasLocal && !remoteUrl.isNullOrBlank() && existing?.imageUrl == remoteUrl) {
            // Already saved and remote URL hasn't changed! Keep untouched.
            return@withContext localFile.absolutePath
        }

        if (hasLocal && remoteUrl.isNullOrBlank()) {
            // No new URL, existing local file is valid.
            return@withContext localFile.absolutePath
        }

        if (!remoteUrl.isNullOrBlank()) {
            // Either no local file exists or remote URL changed!
            val downloadedPath = downloadAndSavePhoto(clean, remoteUrl)
            if (downloadedPath != null) {
                val updatedEntity = existing?.copy(
                    imageUrl = remoteUrl,
                    localImagePath = downloadedPath,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                ) ?: ArtistEntity(
                    name = clean,
                    imageUrl = remoteUrl,
                    localImagePath = downloadedPath,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
                saveArtistToRoom(updatedEntity)
                return@withContext downloadedPath
            } else if (hasLocal) {
                return@withContext localFile.absolutePath
            }
        }

        if (hasLocal) localFile.absolutePath else remoteUrl
    }
}
