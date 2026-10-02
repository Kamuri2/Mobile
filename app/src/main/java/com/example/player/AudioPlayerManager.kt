package com.example.player

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.SocialRepository
import com.example.model.AppSettings
import com.example.model.AppTheme
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LoopMode {
    OFF, REPEAT_ALL, REPEAT_ONE
}

enum class EqPreset(val displayName: String, val bass: Float, val mid: Float, val treble: Float) {
    FLAT("Normal / Flat", 1.0f, 1.0f, 1.0f),
    BASS_BOOST("Liquid Bass Boost", 1.6f, 0.9f, 0.9f),
    VOCAL("Crystal Vocal", 0.8f, 1.4f, 1.2f),
    ELECTRONIC("Neon Synth / EDM", 1.4f, 1.1f, 1.5f),
    ROCK("Power Rock", 1.3f, 0.8f, 1.4f),
    ACOUSTIC("Acoustic Glass", 1.1f, 1.2f, 1.1f)
}

class AudioPlayerManager private constructor(private val context: Context) {
    private val socialRepository: SocialRepository by lazy { SocialRepository.getInstance(context) }

    companion object {
        private const val TAG = "AudioPlayerManager"
        @Volatile
        private var instance: AudioPlayerManager? = null

        fun getInstance(context: Context): AudioPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: AudioPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val crossfadeEngine by lazy {
        CrossfadeEngine(
            context = context,
            fadeOutDurationMs = 100L,
            fadeInDurationMs = 100L
        )
    }

    // Track Queue State
    private val _playlist = MutableStateFlow<List<Track>>(emptyList())
    val playlist: StateFlow<List<Track>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _loopMode = MutableStateFlow(LoopMode.REPEAT_ALL)
    val loopMode: StateFlow<LoopMode> = _loopMode.asStateFlow()

    private val _eqPreset = MutableStateFlow(EqPreset.FLAT)
    val eqPreset: StateFlow<EqPreset> = _eqPreset.asStateFlow()

    private val _favorites = MutableStateFlow<Set<Long>>(loadFavorites())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _selectedEqPreset = MutableStateFlow(EqPreset.BASS_BOOST)
    val selectedEqPreset: StateFlow<EqPreset> = _selectedEqPreset.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    // App Settings State
    private val _appSettings = MutableStateFlow(loadSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    // Disliked Tracks State
    private val _dislikedTracks = MutableStateFlow<Set<Long>>(loadDislikes())
    val dislikedTracks: StateFlow<Set<Long>> = _dislikedTracks.asStateFlow()

    // Playlists State
    private val _playlistsMap = MutableStateFlow<Map<String, List<Track>>>(
        mapOf("Relax" to emptyList(), "Entrenamiento" to emptyList())
    )
    val playlistsMap: StateFlow<Map<String, List<Track>>> = _playlistsMap.asStateFlow()

    init {
        scope.launch {
            val lastTrackId = prefs.getLong("last_track_id", -1L)
            if (lastTrackId != -1L) {
                try {
                    val tracks = com.example.data.TrackRepository.tracks.first { it.isNotEmpty() }
                    if (_currentTrack.value == null) {
                        val lastTrack = tracks.find { it.id == lastTrackId }
                        if (lastTrack != null) {
                            _playlist.value = listOf(lastTrack)
                            _currentIndex.value = 0
                            _currentTrack.value = lastTrack
                            _durationMs.value = lastTrack.durationMs
                        }
                    }
                } catch (e: Exception) {}
            }
        }
    }

    fun createExoPlayerInstance(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(20_000, true)
            .build()

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)

        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: run {
            createExoPlayerInstance().also { player ->
                exoPlayer = player
                player.volume = _volume.value
                updatePlayerRepeatMode(player)
                player.shuffleModeEnabled = _isShuffle.value
                player.addListener(playerListener)
                crossfadeEngine.attachActivePlayer(player, _volume.value)
            }
        }
    }

    private fun trackToMediaItem(track: Track): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)

        track.albumArtUri?.let { uri ->
            metadataBuilder.setArtworkUri(uri)
        }

        val mediaUri = if (track.contentUri != android.net.Uri.EMPTY) {
            track.contentUri
        } else if (track.path.isNotBlank()) {
            android.net.Uri.fromFile(java.io.File(track.path))
        } else {
            track.contentUri
        }

        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(mediaUri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val player = exoPlayer ?: return
            val tracks = _playlist.value
            if (tracks.isEmpty()) return

            // Match by unique track mediaId to guarantee 100% audio and metadata sync
            val mediaTrackId = mediaItem?.mediaId?.toLongOrNull()
            val matchedIndex = if (mediaTrackId != null) {
                tracks.indexOfFirst { it.id == mediaTrackId }
            } else {
                -1
            }

            val currentIdx = if (matchedIndex != -1) {
                matchedIndex
            } else {
                player.currentMediaItemIndex.coerceIn(0, tracks.lastIndex)
            }

            if (currentIdx in tracks.indices) {
                val track = tracks[currentIdx]
                val prevTrack = _currentTrack.value
                val isDifferentTrack = prevTrack?.id != track.id

                if (isDifferentTrack || _currentIndex.value != currentIdx) {
                    _currentIndex.value = currentIdx
                    _currentTrack.value = track
                    saveLastTrackId(track.id)

                    scope.launch {
                        socialRepository.recordPlayback(track.id)
                    }

                    // S-curve smooth volume fade-in when track auto-transitions
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        val fadeInMs = (_appSettings.value.fadeInDuration * 1000L).toLong()
                        if (fadeInMs > 0L) {
                            crossfadeEngine.startFadeIn(fadeInMs)
                        }
                    }
                }

                // Room SQLite: query saved lyrics (<1ms) or extract from tag and persist
                loadLyricsForTrack(track)

                enrichTrackIfNeeded(track, currentIdx)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val player = exoPlayer
            // If player is transitioning/buffering after a seek or skip, but playWhenReady is true,
            // maintain playing state so the UI and audio do not stutter or falsely indicate pause.
            if (!isPlaying && player != null && player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) {
                _isPlaying.value = true
                return
            }
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startProgressTracker()
            } else {
                stopProgressTracker()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    val dur = exoPlayer?.duration ?: 0L
                    if (dur > 0L) {
                        _durationMs.value = dur
                    }
                }
                Player.STATE_ENDED -> {
                    handleTrackCompletion()
                }
                Player.STATE_IDLE, Player.STATE_BUFFERING -> {}
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer error: ${error.errorCodeName} - ${error.message}", error)
            if (_playlist.value.isNotEmpty()) {
                nextTrack()
            }
        }
    }

    private fun ensureServiceStarted() {
        try {
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PlaybackService: ${e.message}")
        }
    }

    fun setQueue(tracks: List<Track>, startTrackIndex: Int = 0, autoPlay: Boolean = true) {
        crossfadeEngine.cancelCrossfade()
        _isShuffle.value = false
        if (tracks.isEmpty()) {
            _playlist.value = emptyList()
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            _currentIndex.value = -1
            _currentTrack.value = null
            _isPlaying.value = false
            return
        }

        val validIndex = startTrackIndex.coerceIn(0, tracks.lastIndex)
        val track = tracks[validIndex]

        val player = getOrCreatePlayer()

        val isSameList = _playlist.value.size == tracks.size &&
                _playlist.value.indices.all { _playlist.value[it].id == tracks[it].id }

        _playlist.value = tracks
        _currentIndex.value = validIndex
        _currentTrack.value = track
        saveLastTrackId(track.id)
        loadLyricsForTrack(track)

        if (isSameList && player.mediaItemCount == tracks.size) {
            player.seekToDefaultPosition(validIndex)
            if (autoPlay) {
                player.play()
                ensureServiceStarted()
            }
            return
        }

        player.shuffleModeEnabled = false
        val mediaItems = tracks.map { trackToMediaItem(it) }
        player.setMediaItems(mediaItems, validIndex, 0L)
        player.prepare()

        if (autoPlay) {
            player.play()
            ensureServiceStarted()
        } else {
            player.pause()
        }
    }

    fun playTrack(track: Track) {
        val index = _playlist.value.indexOfFirst { it.id == track.id }
        if (index != -1) {
            playTrackAtIndex(index)
        } else {
            val newPlaylist = _playlist.value + track
            _playlist.value = newPlaylist
            val player = getOrCreatePlayer()
            player.addMediaItem(trackToMediaItem(track))
            playTrackAtIndex(newPlaylist.lastIndex)
        }
    }

    fun playTrackAtIndex(index: Int) {
        crossfadeEngine.cancelCrossfade()
        val tracks = _playlist.value
        if (index !in tracks.indices) return

        val track = tracks[index]
        _currentIndex.value = index
        _currentTrack.value = track
        saveLastTrackId(track.id)

        scope.launch {
            socialRepository.recordPlayback(track.id)
        }

        loadLyricsForTrack(track)

        val player = getOrCreatePlayer()
        val needsReload = player.mediaItemCount != tracks.size ||
                try { player.getMediaItemAt(index).mediaId != track.id.toString() } catch (e: Exception) { true }

        if (needsReload) {
            val mediaItems = tracks.map { trackToMediaItem(it) }
            player.setMediaItems(mediaItems, index, 0L)
            player.prepare()
        } else if (player.currentMediaItemIndex != index) {
            player.seekToDefaultPosition(index)
        }

        player.play()
        ensureServiceStarted()
        enrichTrackIfNeeded(track, index)
    }

    fun loadLyricsForTrack(track: Track) {
        scope.launch(Dispatchers.IO) {
            try {
                val lyrics = com.example.data.LyricsRepository.getInstance(context).getLyrics(context, track)
                if (!lyrics.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        val cur = _currentTrack.value
                        if (cur != null && cur.id == track.id) {
                            _currentTrack.value = cur.copy(lyrics = lyrics)
                        }
                        val currentPlaylist = _playlist.value.toMutableList()
                        val idx = currentPlaylist.indexOfFirst { it.id == track.id }
                        if (idx != -1) {
                            currentPlaylist[idx] = currentPlaylist[idx].copy(lyrics = lyrics)
                            _playlist.value = currentPlaylist
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading lyrics for track ${track.title}: ${e.message}")
            }
        }
    }

    private fun enrichTrackIfNeeded(track: Track, index: Int) {
        if (track.bitrate.isBlank() || track.sampleRate.isBlank()) {
            scope.launch(Dispatchers.IO) {
                try {
                    val enriched = com.example.data.MetadataReader.extractFullMetadata(context, track)
                    withContext(Dispatchers.Main) {
                        val cur = _currentTrack.value
                        if (cur != null && cur.id == track.id) {
                            _currentTrack.value = cur.copy(
                                bitrate = if (cur.bitrate.isBlank()) enriched.bitrate else cur.bitrate,
                                sampleRate = if (cur.sampleRate.isBlank()) enriched.sampleRate else cur.sampleRate
                            )
                        }
                        val currentPlaylist = _playlist.value.toMutableList()
                        val idx = currentPlaylist.indexOfFirst { it.id == track.id }
                        if (idx != -1) {
                            currentPlaylist[idx] = currentPlaylist[idx].copy(
                                bitrate = if (currentPlaylist[idx].bitrate.isBlank()) enriched.bitrate else currentPlaylist[idx].bitrate,
                                sampleRate = if (currentPlaylist[idx].sampleRate.isBlank()) enriched.sampleRate else currentPlaylist[idx].sampleRate
                            )
                            _playlist.value = currentPlaylist
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "enrichTrackIfNeeded skipped: ${e.message}")
                }
            }
        }
    }

    fun togglePlayPause() {
        val player = getOrCreatePlayer()
        if (player.mediaItemCount == 0 && _playlist.value.isNotEmpty()) {
            val index = if (_currentIndex.value in _playlist.value.indices) _currentIndex.value else 0
            playTrackAtIndex(index)
            return
        }

        if (player.isPlaying) {
            crossfadeEngine.cancelCrossfade()
            player.pause()
        } else {
            player.play()
            ensureServiceStarted()
        }
    }

    fun nextTrack() {
        crossfadeEngine.cancelCrossfade()
        val tracks = _playlist.value
        if (tracks.isEmpty()) return

        val curIdx = _currentIndex.value
        val nextIdx = when {
            _isShuffle.value -> {
                if (tracks.size > 1) {
                    tracks.indices.filter { it != curIdx }.random()
                } else {
                    0
                }
            }
            curIdx < tracks.lastIndex -> curIdx + 1
            _loopMode.value == LoopMode.REPEAT_ALL -> 0
            else -> return
        }
        playTrackAtIndex(nextIdx)
    }

    fun previousTrack() {
        crossfadeEngine.cancelCrossfade()
        val tracks = _playlist.value
        if (tracks.isEmpty()) return

        val player = getOrCreatePlayer()
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
            _currentPositionMs.value = 0L
            player.play()
            return
        }

        val curIdx = _currentIndex.value
        val prevIdx = when {
            _isShuffle.value -> {
                if (tracks.size > 1) {
                    tracks.indices.filter { it != curIdx }.random()
                } else {
                    0
                }
            }
            curIdx > 0 -> curIdx - 1
            _loopMode.value == LoopMode.REPEAT_ALL -> tracks.lastIndex
            else -> 0
        }
        playTrackAtIndex(prevIdx)
    }

    fun seekTo(positionMs: Long) {
        crossfadeEngine.cancelCrossfade()
        val player = exoPlayer ?: return
        val dur = player.duration.takeIf { it > 0 } ?: _durationMs.value.takeIf { it > 0 } ?: 0L
        val safePos = if (dur > 0L) positionMs.coerceIn(0L, dur) else positionMs.coerceAtLeast(0L)
        val shouldContinuePlaying = player.playWhenReady || _isPlaying.value
        _currentPositionMs.value = safePos
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
        player.seekTo(safePos)
        if (shouldContinuePlaying) {
            player.playWhenReady = true
            player.play()
            _isPlaying.value = true
        }
    }

    fun setVolume(vol: Float) {
        _volume.value = vol
        exoPlayer?.volume = vol
        crossfadeEngine.setVolume(vol)
    }

    fun setShuffle(enabled: Boolean) {
        if (_isShuffle.value != enabled) {
            toggleShuffle()
        }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
        exoPlayer?.shuffleModeEnabled = _isShuffle.value
    }

    fun cycleLoopMode() {
        val newMode = when (_loopMode.value) {
            LoopMode.OFF -> LoopMode.REPEAT_ALL
            LoopMode.REPEAT_ALL -> LoopMode.REPEAT_ONE
            LoopMode.REPEAT_ONE -> LoopMode.OFF
        }
        _loopMode.value = newMode
        exoPlayer?.let { updatePlayerRepeatMode(it) }
    }

    private fun updatePlayerRepeatMode(player: ExoPlayer) {
        player.repeatMode = when (_loopMode.value) {
            LoopMode.OFF -> Player.REPEAT_MODE_OFF
            LoopMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
            LoopMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun toggleFavorite(trackId: Long) {
        val set = _favorites.value.toMutableSet()
        if (set.contains(trackId)) {
            set.remove(trackId)
        } else {
            set.add(trackId)
        }
        _favorites.value = set
        saveFavorites(set)
    }

    fun setEqPreset(preset: EqPreset) {
        _selectedEqPreset.value = preset
    }

    fun setSleepTimer(minutes: Int?) {
        _sleepTimerMinutes.value = minutes
        sleepTimerJob?.cancel()
        if (minutes != null && minutes > 0) {
            sleepTimerJob = scope.launch {
                delay(minutes * 60_000L)
                if (_isPlaying.value) {
                    togglePlayPause()
                }
                _sleepTimerMinutes.value = null
            }
        }
    }

    private fun handleTrackCompletion() {
        val player = exoPlayer ?: return
        if (!player.hasNextMediaItem() && _loopMode.value == LoopMode.OFF) {
            _isPlaying.value = false
            stopProgressTracker()
        }
    }

    private fun getNextTrackInfo(): Pair<Int, Track>? {
        val tracks = _playlist.value
        val curIdx = _currentIndex.value
        if (tracks.isEmpty()) return null

        return when {
            _loopMode.value == LoopMode.REPEAT_ONE -> {
                if (curIdx in tracks.indices) curIdx to tracks[curIdx] else null
            }
            _isShuffle.value -> {
                if (tracks.size > 1) {
                    val nextIdx = tracks.indices.filter { it != curIdx }.random()
                    nextIdx to tracks[nextIdx]
                } else null
            }
            curIdx + 1 < tracks.size -> {
                (curIdx + 1) to tracks[curIdx + 1]
            }
            _loopMode.value == LoopMode.REPEAT_ALL -> {
                0 to tracks[0]
            }
            else -> null
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (true) {
                val player = exoPlayer
                if (player != null && player.isPlaying) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration
                    _currentPositionMs.value = pos
                    if (dur > 0L) {
                        _durationMs.value = dur
                    }

                    // S-Curve Fade-Out check at the end of the song
                    val fadeOutMs = (_appSettings.value.fadeOutDuration * 1000L).toLong()

                    if (fadeOutMs > 0L && dur > fadeOutMs * 2) {
                        val remaining = dur - pos
                        if (remaining in 1..fadeOutMs) {
                            crossfadeEngine.applyFadeOut(remaining, fadeOutMs)
                        }
                    }
                }
                delay(50)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun removeFromQueue(index: Int) {
        val currentList = _playlist.value.toMutableList()
        if (index in currentList.indices) {
            val isCurrentBeingRemoved = index == _currentIndex.value
            currentList.removeAt(index)
            _playlist.value = currentList
            exoPlayer?.removeMediaItem(index)
            if (currentList.isEmpty()) {
                _currentIndex.value = -1
                _currentTrack.value = null
                _isPlaying.value = false
                exoPlayer?.stop()
                exoPlayer?.clearMediaItems()
            } else if (isCurrentBeingRemoved) {
                val nextIdx = index.coerceAtMost(currentList.lastIndex)
                playTrackAtIndex(nextIdx)
            } else if (index < _currentIndex.value) {
                _currentIndex.value = _currentIndex.value - 1
            }
        }
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        val currentList = _playlist.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices && fromIndex != toIndex) {
            val currIdx = _currentIndex.value
            val track = currentList.removeAt(fromIndex)
            currentList.add(toIndex, track)
            _playlist.value = currentList
            try {
                exoPlayer?.moveMediaItem(fromIndex, toIndex)
            } catch (e: Exception) {}

            // Keep tracking of current playing index correctly
            if (currIdx == fromIndex) {
                _currentIndex.value = toIndex
            } else if (fromIndex < currIdx && toIndex >= currIdx) {
                _currentIndex.value = currIdx - 1
            } else if (fromIndex > currIdx && toIndex <= currIdx) {
                _currentIndex.value = currIdx + 1
            }
        }
    }

    fun setPlayNext(track: Track) {
        val currentList = _playlist.value.toMutableList()
        val currIdx = _currentIndex.value
        val existingIdx = currentList.indexOfFirst { it.id == track.id }
        if (existingIdx != -1) {
            currentList.removeAt(existingIdx)
            exoPlayer?.removeMediaItem(existingIdx)
        }
        val insertPos = if (currIdx in currentList.indices) currIdx + 1 else currentList.size
        currentList.add(insertPos, track)
        _playlist.value = currentList
        exoPlayer?.addMediaItem(insertPos, trackToMediaItem(track))
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        val map = _playlistsMap.value.toMutableMap()
        if (!map.containsKey(name)) {
            map[name] = emptyList()
            _playlistsMap.value = map
        }
    }

    fun addToPlaylist(playlistName: String, track: Track) {
        val map = _playlistsMap.value.toMutableMap()
        val list = map[playlistName]?.toMutableList() ?: mutableListOf()
        if (!list.contains(track)) {
            list.add(track)
            map[playlistName] = list
            _playlistsMap.value = map
        }
    }

    private fun saveLastTrackId(trackId: Long?) {
        try {
            if (trackId != null) {
                prefs.edit().putLong("last_track_id", trackId).apply()
            }
        } catch (e: Exception) {}
    }

    private fun loadFavorites(): Set<Long> {
        return try {
            prefs.getStringSet("favorite_ids", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun saveFavorites(favs: Set<Long>) {
        try {
            prefs.edit().putStringSet("favorite_ids", favs.map { it.toString() }.toSet()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving favorites: ${e.message}")
        }
    }

    private fun loadDislikes(): Set<Long> {
        return try {
            prefs.getStringSet("disliked_ids", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun saveDislikes(dislikes: Set<Long>) {
        try {
            prefs.edit().putStringSet("disliked_ids", dislikes.map { it.toString() }.toSet()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving dislikes: ${e.message}")
        }
    }

    private fun loadSettings(): AppSettings {
        return try {
            AppSettings(
                isDarkMode = prefs.getBoolean("isDarkMode", true),
                appLanguage = prefs.getString("appLanguage", "English") ?: "English",
                selectedTheme = try { AppTheme.valueOf(prefs.getString("selectedTheme", "SUNSET") ?: "SUNSET") } catch(e: Exception) { AppTheme.SUNSET },
                fontFamilyName = prefs.getString("fontFamilyName", "System Font (Default)") ?: "System Font (Default)",
                lyricsFontSizePercent = prefs.getInt("lyricsFontSizePercent", 110),
                isLyricsTranslationEnabled = prefs.getBoolean("isLyricsTranslationEnabled", false),
                targetTranslationLanguage = prefs.getString("targetTranslationLanguage", "Spanish") ?: "Spanish",
                crossfadeDuration = prefs.getFloat("crossfadeDuration", 0.1f),
                fadeOutDuration = prefs.getFloat("fadeOutDuration", 0.1f),
                fadeInDuration = prefs.getFloat("fadeInDuration", 0.1f)
            )
        } catch (e: Exception) {
            AppSettings()
        }
    }

    private fun saveSettings(settings: AppSettings) {
        try {
            prefs.edit().apply {
                putBoolean("isDarkMode", settings.isDarkMode)
                putString("appLanguage", settings.appLanguage)
                putString("selectedTheme", settings.selectedTheme.name)
                putString("fontFamilyName", settings.fontFamilyName)
                putInt("lyricsFontSizePercent", settings.lyricsFontSizePercent)
                putBoolean("isLyricsTranslationEnabled", settings.isLyricsTranslationEnabled)
                putString("targetTranslationLanguage", settings.targetTranslationLanguage)
                putFloat("crossfadeDuration", settings.crossfadeDuration)
                putFloat("fadeOutDuration", settings.fadeOutDuration)
                putFloat("fadeInDuration", settings.fadeInDuration)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving settings: ${e.message}")
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        _appSettings.value = newSettings
        saveSettings(newSettings)
    }

    fun updateTheme(theme: AppTheme) {
        updateSettings(_appSettings.value.copy(selectedTheme = theme))
    }

    fun toggleDarkMode() {
        updateSettings(_appSettings.value.copy(isDarkMode = !_appSettings.value.isDarkMode))
    }

    fun setAppLanguage(lang: String) {
        updateSettings(_appSettings.value.copy(appLanguage = lang))
    }

    fun setLyricsFontSize(percent: Int) {
        updateSettings(_appSettings.value.copy(lyricsFontSizePercent = percent))
    }

    fun toggleLyricsTranslation() {
        updateSettings(_appSettings.value.copy(isLyricsTranslationEnabled = !_appSettings.value.isLyricsTranslationEnabled))
    }

    fun toggleDislike(trackId: Long) {
        val set = _dislikedTracks.value.toMutableSet()
        if (set.contains(trackId)) {
            set.remove(trackId)
        } else {
            set.add(trackId)
        }
        _dislikedTracks.value = set
        saveDislikes(set)
    }

    fun release() {
        stopProgressTracker()
        sleepTimerJob?.cancel()
        try {
            exoPlayer?.removeListener(playerListener)
            exoPlayer?.stop()
            exoPlayer?.release()
        } catch (e: Exception) {}
        exoPlayer = null
    }
}
