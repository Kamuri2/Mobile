package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.model.Track
import java.io.File

object AudioScanner {

    private const val TAG = "AudioScanner"
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "flac", "aac", "ogg", "opus", "wma", "mka", "mid")

    /**
     * Scans all audio files from device MediaStore and common storage locations.
     */
    fun scanMediaStoreAudio(context: Context): List<Track> {
        val tracksMap = mutableMapOf<Long, Track>()
        val pathMap = mutableSetOf<String>()

        // 1. Scan External MediaStore
        val collections = mutableListOf<Uri>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collections.add(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
        } else {
            collections.add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }
        try {
            collections.add(MediaStore.Audio.Media.INTERNAL_CONTENT_URI)
        } catch (e: Exception) {}

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK
        )

        // Don't restrict by IS_MUSIC because many downloads, ogg, opus have IS_MUSIC = 0
        val selection = "${MediaStore.Audio.Media.DURATION} >= 1000 OR ${MediaStore.Audio.Media.SIZE} >= 50000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        val artworkUriBase = Uri.parse("content://media/external/audio/albumart")

        for (collection in collections) {
            try {
                context.contentResolver.query(
                    collection,
                    projection,
                    selection,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                    val durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                    val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val yearColumn = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                    val trackNumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val rawTitle = if (titleColumn >= 0) cursor.getString(titleColumn) else null
                        val rawArtist = if (artistColumn >= 0) cursor.getString(artistColumn) else null
                        val rawAlbum = if (albumColumn >= 0) cursor.getString(albumColumn) else null
                        val duration = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
                        val albumId = if (albumIdColumn >= 0) cursor.getLong(albumIdColumn) else -1L
                        val sizeBytes = if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0L
                        val path = if (dataColumn >= 0) cursor.getString(dataColumn) ?: "" else ""
                        val year = if (yearColumn >= 0) cursor.getInt(yearColumn) else 2024
                        val rawTrackNum = if (trackNumColumn >= 0) cursor.getInt(trackNumColumn) else 0
                        val trackNum = if (rawTrackNum >= 1000) rawTrackNum % 1000 else if (rawTrackNum > 0) rawTrackNum else 1

                        val contentUri = ContentUris.withAppendedId(collection, id)
                        // No usar album_id de MediaStore para evitar que canciones de distintos artistas con mismo álbum/título compartan portada
                        val artUri: Uri? = null

                        val fallbackTitle = if (path.isNotBlank()) File(path).nameWithoutExtension else "Track $id"
                        val title = if (!rawTitle.isNullOrBlank() && rawTitle != "<unknown>") rawTitle else fallbackTitle
                        val artist = if (!rawArtist.isNullOrBlank() && rawArtist != "<unknown>") rawArtist else "Unknown Artist"
                        val album = if (!rawAlbum.isNullOrBlank() && rawAlbum != "<unknown>") rawAlbum else "Unknown Album"

                        val folderName = if (path.isNotBlank()) {
                            File(path).parentFile?.name ?: "Music"
                        } else "Music"

                        val sizeMb = String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))

                        var lyrics = ""
                        if (path.isNotBlank()) {
                            pathMap.add(path)
                            try {
                                val audioFile = File(path)
                                if (audioFile.exists()) {
                                    val parent = audioFile.parentFile
                                    val baseName = audioFile.nameWithoutExtension
                                    val lrc = File(parent, "$baseName.lrc")
                                    if (lrc.exists() && lrc.canRead()) {
                                        lyrics = lrc.readText(Charsets.UTF_8).trim()
                                    }
                                }
                            } catch (e: Exception) {}
                        }

                        val track = Track(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            contentUri = contentUri,
                            albumArtUri = artUri,
                            trackNumber = trackNum,
                            year = year,
                            fileSizeFormatted = sizeMb,
                            path = path,
                            folderName = folderName,
                            lyrics = lyrics
                        )

                        tracksMap[id] = track
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning MediaStore collection: ${e.message}")
            }
        }

        // 2. Complementary scan of common public directories (Music, Download, etc.) to find unindexed files
        try {
            val commonDirs = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
                File("/storage/emulated/0/Music"),
                File("/storage/emulated/0/Download")
            ).filter { it.exists() && it.isDirectory }.distinctBy { it.absolutePath }

            for (dir in commonDirs) {
                scanLocalDirectory(dir, tracksMap, pathMap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning local directories: ${e.message}")
        }

        return tracksMap.values.sortedBy { it.title.lowercase() }
    }

    private fun scanLocalDirectory(
        dir: File,
        tracksMap: MutableMap<Long, Track>,
        pathMap: MutableSet<String>,
        maxDepth: Int = 3
    ) {
        if (maxDepth <= 0) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                scanLocalDirectory(file, tracksMap, pathMap, maxDepth - 1)
            } else if (file.isFile) {
                val ext = file.extension.lowercase()
                if (AUDIO_EXTENSIONS.contains(ext) && file.length() >= 50 * 1024) {
                    val absPath = file.absolutePath
                    if (!pathMap.contains(absPath)) {
                        pathMap.add(absPath)
                        val id = absPath.hashCode().toLong()
                        val title = file.nameWithoutExtension
                        val parentName = file.parentFile?.name ?: "Music"
                        val sizeMb = String.format("%.1f MB", file.length() / (1024.0 * 1024.0))

                        var lyrics = ""
                        try {
                            val lrc = File(file.parentFile, "$title.lrc")
                            if (lrc.exists() && lrc.canRead()) {
                                lyrics = lrc.readText(Charsets.UTF_8).trim()
                            }
                        } catch (e: Exception) {}

                        val track = Track(
                            id = id,
                            title = title,
                            artist = "Local Artist",
                            album = parentName,
                            durationMs = 0L,
                            contentUri = Uri.fromFile(file),
                            albumArtUri = null,
                            trackNumber = 1,
                            year = 2024,
                            fileSizeFormatted = sizeMb,
                            path = absPath,
                            folderName = parentName,
                            lyrics = lyrics
                        )
                        tracksMap[id] = track
                    }
                }
            }
        }
    }

    /**
     * Fast, lightweight scan of audio files inside a specific folder tree URI selected via Storage Access Framework (SAF)
     */
    fun scanFolderUri(context: Context, treeUri: Uri): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
            val folderName = rootDoc.name ?: "Custom Folder"
            scanDocumentDirectory(context, rootDoc, folderName, tracks)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning folder URI: ${e.message}")
        }
        return tracks
    }

    private fun scanDocumentDirectory(
        context: Context,
        dir: DocumentFile,
        folderName: String,
        outList: MutableList<Track>
    ) {
        val files = dir.listFiles()
        
        val lrcFiles = mutableMapOf<String, DocumentFile>()
        for (file in files) {
            if (file.isFile) {
                val name = file.name ?: ""
                if (name.endsWith(".lrc", true)) {
                    val baseName = name.substringBeforeLast(".")
                    lrcFiles[baseName] = file
                }
            }
        }

        for (file in files) {
            if (file.isDirectory) {
                scanDocumentDirectory(context, file, file.name ?: folderName, outList)
            } else if (file.isFile) {
                val mime = file.type ?: ""
                val name = file.name ?: ""
                val ext = name.substringAfterLast(".", "").lowercase()
                if (mime.startsWith("audio/") || AUDIO_EXTENSIONS.contains(ext)) {
                    val id = file.uri.hashCode().toLong()
                    val title = name.substringBeforeLast(".")
                    
                    var parsedLyrics = ""
                    try {
                        lrcFiles[title]?.let { lrcFile ->
                            context.contentResolver.openInputStream(lrcFile.uri)?.bufferedReader()?.use { reader ->
                                parsedLyrics = reader.readText()
                            }
                        }
                    } catch(e: Exception) {
                        Log.e(TAG, "Error reading LRC: ${e.message}")
                    }

                    val rawTrack = Track(
                        id = id,
                        title = title,
                        artist = "Local Artist",
                        album = folderName,
                        durationMs = 0L,
                        contentUri = file.uri,
                        folderName = folderName,
                        path = name,
                        fileSizeFormatted = String.format("%.1f MB", file.length() / (1024.0 * 1024.0)),
                        lyrics = parsedLyrics
                    )
                    outList.add(rawTrack)
                }
            }
        }
    }
}
