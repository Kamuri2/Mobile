package com.example.player

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.ArtistInfoFetcher
import com.example.data.ArtistProfile
import com.example.data.AudioLyricsExtractor
import com.example.data.SampleMusicProvider
import com.example.data.local.AppDatabase
import com.example.model.AppSettings
import com.example.model.AppTheme
import com.example.model.ArtistInfo
import com.example.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class LoopMode {
    OFF, REPEAT_ALL, REPEAT_ONE
}

class AudioPlayerManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val crossfadeEngine: CrossfadeEngine by lazy {
        CrossfadeEngine(
            context = context,
            crossfadeDurationMs = (loadSettings().crossfadeDuration * 1000f).toLong().coerceIn(100L, 12000L)
        )
    }

    // Dual-player architecture for seamless gapless crossfades
    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    private var activePlayerInstance: ExoPlayer? = null

    // State Flows
    private val _appSettings = MutableStateFlow(loadSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _playlist = MutableStateFlow<List<Track>>(emptyList())
    val playlist: StateFlow<List<Track>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
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

    private val _loopMode = MutableStateFlow(LoopMode.OFF)
    val loopMode: StateFlow<LoopMode> = _loopMode.asStateFlow()

    private val _favorites = MutableStateFlow<Set<Long>>(loadFavorites())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _dislikedTracks = MutableStateFlow<Set<Long>>(loadDislikes())
    val dislikedTracks: StateFlow<Set<Long>> = _dislikedTracks.asStateFlow()

    private val _playlistsMap = MutableStateFlow<Map<String, List<Track>>>(emptyMap())
    val playlistsMap: StateFlow<Map<String, List<Track>>> = _playlistsMap.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _currentArtistInfo = MutableStateFlow<ArtistInfo?>(null)
    val currentArtistInfo: StateFlow<ArtistInfo?> = _currentArtistInfo.asStateFlow()

    private val _currentArtistProfiles = MutableStateFlow<List<ArtistProfile>>(emptyList())
    val currentArtistProfiles: StateFlow<List<ArtistProfile>> = _currentArtistProfiles.asStateFlow()

    private val _isArtistInfoLoading = MutableStateFlow(false)
    val isArtistInfoLoading: StateFlow<Boolean> = _isArtistInfoLoading.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private val _selectedEqPreset = MutableStateFlow("Normal")
    val eqPreset: StateFlow<String> = _selectedEqPreset.asStateFlow()

    private var progressJob: Job? = null
    private var pipelineJob: Job? = null
    private var lyricsJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var originalQueue: List<Track> = emptyList()
    private var hasTriggeredAutoCrossfade = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (_isPlaying.value) {
                    togglePlayPause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (_isPlaying.value) {
                    togglePlayPause()
                }
            }
        }
    }

    init {
        // Initialize dual players
        playerA = createExoPlayerInstance()
        playerB = createExoPlayerInstance()
        activePlayerInstance = playerA

        // Load initial tracks from Room database or sample tracks
        scope.launch(Dispatchers.IO) {
            try {
                val dbTracks = AppDatabase.getDatabase(context).trackDao().getAllTracks()
                val initialTracks = if (dbTracks.isNotEmpty()) {
                    dbTracks.map { it.toTrack() }
                } else {
                    SampleMusicProvider.getSampleTracks()
                }
                withContext(Dispatchers.Main) {
                    if (_playlist.value.isEmpty()) {
                        setQueue(initialTracks, 0, autoPlay = false)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (_playlist.value.isEmpty()) {
                        setQueue(SampleMusicProvider.getSampleTracks(), 0, autoPlay = false)
                    }
                }
            }
        }
    }

    private fun createExoPlayerInstance(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000,   // Min buffer ms
                3000,   // Max buffer ms
                500,    // Buffer for playback ms
                800     // Buffer for playback after rebuffer ms
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                addListener(createPlayerListener(this))
            }
    }

    private fun createPlayerListener(player: ExoPlayer) = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (player != activePlayerInstance) return

            when (playbackState) {
                Player.STATE_READY -> {
                    val dur = player.duration
                    if (dur > 0L) {
                        _durationMs.value = dur
                    }
                }
                Player.STATE_ENDED -> {
                    handleTrackCompletion()
                }
                else -> {}
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (player == activePlayerInstance) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }
        }
    }

    private fun getOrCreateActivePlayer(): ExoPlayer {
        return activePlayerInstance ?: playerA ?: createExoPlayerInstance().also {
            playerA = it
            activePlayerInstance = it
        }
    }

    private fun requestAppAudioFocus(): Boolean {
        if (audioManager == null) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun trackToMediaItem(track: Track): MediaItem {
        val uri = if (track.contentUri != Uri.EMPTY) {
            track.contentUri
        } else if (track.path.isNotBlank()) {
            Uri.fromFile(File(track.path))
        } else {
            Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3")
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.albumArtUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Sets the playback queue.
     */
    fun setQueue(tracks: List<Track>, startIndex: Int = 0, autoPlay: Boolean = true) {
        if (tracks.isEmpty()) return
        originalQueue = tracks
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        _playlist.value = tracks
        _currentIndex.value = safeIndex
        val track = tracks[safeIndex]
        _currentTrack.value = track
        _durationMs.value = if (track.durationMs > 0L) track.durationMs else 180000L
        _currentPositionMs.value = 0L

        if (autoPlay) {
            playTrackAtIndex(safeIndex)
        } else {
            val player = getOrCreateActivePlayer()
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(trackToMediaItem(track))
            player.prepare()
            player.playWhenReady = false
            // Start pipeline non-blocking
            startTrackSequentialPipeline(track)
        }
    }

    fun playTrack(track: Track) {
        val list = _playlist.value
        val idx = list.indexOfFirst { it.id == track.id }
        if (idx != -1) {
            playTrackAtIndex(idx)
        } else {
            val newList = list + track
            _playlist.value = newList
            playTrackAtIndex(newList.lastIndex)
        }
    }

    fun playTrackAtIndex(index: Int) {
        val list = _playlist.value
        if (index !in list.indices) return
        val track = list[index]
        crossfadeToTrack(index, track, customDurationMs = 200L)
    }

    /**
     * Crossfade smoothly between tracks with clean 200ms duration.
     */
    private fun crossfadeToTrack(index: Int, track: Track, customDurationMs: Long? = null) {
        val tracks = _playlist.value
        if (index !in tracks.indices) return

        requestAppAudioFocus()
        val durationMs = (customDurationMs ?: 200L).coerceIn(100L, 250L)
        val currentActive = getOrCreateActivePlayer()
        val nextPlayer = if (currentActive == playerA) (playerB ?: createExoPlayerInstance().also { playerB = it }) else (playerA ?: createExoPlayerInstance().also { playerA = it })

        val mediaItem = trackToMediaItem(track)
        nextPlayer.stop()
        nextPlayer.clearMediaItems()
        nextPlayer.setMediaItem(mediaItem)
        nextPlayer.prepare()
        nextPlayer.playWhenReady = false
        nextPlayer.volume = 0f

        hasTriggeredAutoCrossfade = false

        crossfadeEngine.startManualCrossfade(
            outgoingPlayer = currentActive,
            incomingPlayer = nextPlayer,
            durationMs = durationMs,
            volume = _volume.value,
            onBufferReady = {
                activePlayerInstance = nextPlayer
                _currentIndex.value = index
                _currentTrack.value = track
                _durationMs.value = if (track.durationMs > 0L) track.durationMs else 180000L
                _currentPositionMs.value = 0L
                _isPlaying.value = true
                saveLastTrackId(track.id)
                startProgressTracker()

                // Sequential pipeline: Audio & Cover Art render immediately;
                // artist bio and lyrics extraction execute sequentially in background.
                startTrackSequentialPipeline(track)
            },
            onComplete = {}
        )
    }

    /**
     * SEQUENTIAL PIPELINE (Memory-Optimized as requested by user):
     * 1. Music starts playing and cover art is displayed immediately on screen.
     * 2. Yield briefly so UI rendering & audio decoder initialize without memory congestion.
     * 3. Fetch artist biography / profiles in background.
     * 4. Asynchronously attempt lyrics extraction (Opus, Ogg, ID3v2, .lrc) without blocking.
     */
    private fun startTrackSequentialPipeline(track: Track) {
        pipelineJob?.cancel()
        pipelineJob = scope.launch(Dispatchers.IO) {
            // Step 1: Music is playing, cover is visible. Delay to yield CPU & RAM.
            delay(180L)

            // Step 2: Fetch artist info and profiles in background
            try {
                _isArtistInfoLoading.value = true
                val artistName = track.artist.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Artist"
                val info = ArtistInfoFetcher.getArtistInfo(artistName)
                withContext(Dispatchers.Main) {
                    _currentArtistInfo.value = info
                    _currentArtistProfiles.value = listOf(ArtistProfile(name = artistName, isPrimary = true, info = info))
                    _isArtistInfoLoading.value = false
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _isArtistInfoLoading.value = false
                }
            }

            // Step 3: Yield again before extracting lyrics to avoid simultaneous I/O spikes
            delay(220L)

            // Step 4: Extract lyrics in background if missing or unparsed
            if (track.lyrics.isBlank() || AudioLyricsExtractor.isCorruptedLyrics(track.lyrics)) {
                loadLyricsForTrack(track, forceRescan = false)
            }
        }
    }

    suspend fun loadLyricsForTrack(track: Track, forceRescan: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val extracted = AudioLyricsExtractor.extractLyrics(context, track, forceRescan)
            if (!extracted.isNullOrBlank()) {
                val updated = track.copy(lyrics = extracted)
                withContext(Dispatchers.Main) {
                    if (_currentTrack.value?.id == track.id) {
                        _currentTrack.value = updated
                    }
                    _playlist.value = _playlist.value.map { if (it.id == track.id) updated else it }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Failed to load lyrics: ${e.message}")
        }
    }

    fun retryScanTrackExtras(track: Track, onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            _isArtistInfoLoading.value = true
            val extracted = withContext(Dispatchers.IO) {
                AudioLyricsExtractor.extractLyrics(context, track, forceRescan = true)
            }
            val success = !extracted.isNullOrBlank()
            if (success) {
                val updated = track.copy(lyrics = extracted!!)
                if (_currentTrack.value?.id == track.id) {
                    _currentTrack.value = updated
                }
                _playlist.value = _playlist.value.map { if (it.id == track.id) updated else it }
            }
            _isArtistInfoLoading.value = false
            onComplete?.invoke(success)
        }
    }

    fun nextTrack() {
        val next = getNextTrackInfo()
        if (next != null) {
            crossfadeToTrack(next.first, next.second, customDurationMs = 200L)
        } else {
            stop()
        }
    }

    fun previousTrack() {
        if (_currentPositionMs.value > 3000L) {
            seekTo(0L)
            return
        }
        val list = _playlist.value
        if (list.isEmpty()) return
        val currentIdx = _currentIndex.value
        val prevIdx = if (currentIdx <= 0) list.lastIndex else currentIdx - 1
        crossfadeToTrack(prevIdx, list[prevIdx], customDurationMs = 200L)
    }

    fun togglePlayPause() {
        val player = getOrCreateActivePlayer()
        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
            stopProgressTracker()
        } else {
            requestAppAudioFocus()
            player.play()
            _isPlaying.value = true
            startProgressTracker()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = getOrCreateActivePlayer()
        val target = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(1L))
        _currentPositionMs.value = target
        try {
            player.seekTo(target)
        } catch (_: Exception) {}
    }

    fun toggleShuffle() {
        setShuffle(!_isShuffle.value)
    }

    fun setShuffle(enabled: Boolean) {
        _isShuffle.value = enabled
        if (enabled) {
            shuffleQueue()
        } else {
            if (originalQueue.isNotEmpty()) {
                val current = _currentTrack.value
                _playlist.value = originalQueue
                _currentIndex.value = originalQueue.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
            }
        }
    }

    fun shuffleQueue() {
        val list = _playlist.value
        if (list.isEmpty()) return
        val current = _currentTrack.value
        val others = list.filter { it.id != current?.id }.shuffled()
        val reordered = if (current != null) listOf(current) + others else others
        _playlist.value = reordered
        _currentIndex.value = 0
    }

    fun cycleLoopMode() {
        _loopMode.value = when (_loopMode.value) {
            LoopMode.OFF -> LoopMode.REPEAT_ALL
            LoopMode.REPEAT_ALL -> LoopMode.REPEAT_ONE
            LoopMode.REPEAT_ONE -> LoopMode.OFF
        }
    }

    fun toggleFavorite(trackId: Long) {
        val current = _favorites.value.toMutableSet()
        if (current.contains(trackId)) {
            current.remove(trackId)
        } else {
            current.add(trackId)
        }
        _favorites.value = current
        saveFavorites(current)
        if (_currentTrack.value?.id == trackId) {
            _currentTrack.value = _currentTrack.value?.copy(isFavorite = current.contains(trackId))
        }
    }

    fun toggleFavorite(track: Track) {
        toggleFavorite(track.id)
    }

    fun toggleDislike(trackId: Long) {
        val current = _dislikedTracks.value.toMutableSet()
        if (current.contains(trackId)) {
            current.remove(trackId)
        } else {
            current.add(trackId)
        }
        _dislikedTracks.value = current
        saveDislikes(current)
    }

    fun setPlayNext(track: Track) {
        val list = _playlist.value.toMutableList()
        val insertPos = (_currentIndex.value + 1).coerceAtMost(list.size)
        list.add(insertPos, track)
        _playlist.value = list
    }

    fun removeFromQueue(index: Int) {
        val list = _playlist.value.toMutableList()
        if (index in list.indices) {
            val isCurrent = index == _currentIndex.value
            list.removeAt(index)
            _playlist.value = list
            if (isCurrent && list.isNotEmpty()) {
                val nextIdx = index.coerceAtMost(list.lastIndex)
                playTrackAtIndex(nextIdx)
            } else if (list.isEmpty()) {
                stop()
            }
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        val list = _playlist.value.toMutableList()
        if (from in list.indices && to in list.indices) {
            val item = list.removeAt(from)
            list.add(to, item)
            _playlist.value = list
            val current = _currentTrack.value
            _currentIndex.value = list.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
        }
    }

    fun addToPlaylist(playlistName: String, track: Track) {
        val map = _playlistsMap.value.toMutableMap()
        val existing = map[playlistName]?.toMutableList() ?: mutableListOf()
        if (!existing.any { it.id == track.id }) {
            existing.add(track)
            map[playlistName] = existing
            _playlistsMap.value = map
        }
    }

    fun createPlaylist(name: String) {
        val map = _playlistsMap.value.toMutableMap()
        if (!map.containsKey(name)) {
            map[name] = emptyList()
            _playlistsMap.value = map
        }
    }

    fun setSleepTimer(minutes: Int) {
        _sleepTimerMinutes.value = minutes
        sleepTimerJob?.cancel()
        if (minutes > 0) {
            sleepTimerJob = scope.launch {
                delay(minutes * 60 * 1000L)
                if (_isPlaying.value) {
                    togglePlayPause()
                }
                _sleepTimerMinutes.value = 0
            }
        }
    }

    fun setVolume(vol: Float) {
        val v = vol.coerceIn(0f, 1f)
        _volume.value = v
        crossfadeEngine.setVolume(v)
    }

    fun setEqPreset(preset: String) {
        _selectedEqPreset.value = preset
    }

    fun setAppLanguage(lang: String) {
        val updated = _appSettings.value.copy(appLanguage = lang)
        updateSettings(updated)
    }

    fun toggleDarkMode() {
        val updated = _appSettings.value.copy(isDarkMode = !_appSettings.value.isDarkMode)
        updateSettings(updated)
    }

    fun updateSettings(settings: AppSettings) {
        _appSettings.value = settings
        crossfadeEngine.crossfadeDurationMs = (settings.crossfadeDuration * 1000f).toLong().coerceIn(100L, 12000L)
        saveSettings(settings)
    }

    fun clearAllData() {
        scope.launch(Dispatchers.IO) {
            try {
                AppDatabase.getDatabase(context).clearAllTables()
                prefs.edit().clear().apply()
            } catch (_: Exception) {}
        }
    }

    fun refreshCurrentArtistInfo() {
        val track = _currentTrack.value ?: return
        startTrackSequentialPipeline(track)
    }

    private fun getNextTrackInfo(): Pair<Int, Track>? {
        val list = _playlist.value
        if (list.isEmpty()) return null
        val currentIdx = _currentIndex.value

        return when (_loopMode.value) {
            LoopMode.REPEAT_ONE -> Pair(currentIdx, list[currentIdx])
            LoopMode.REPEAT_ALL -> {
                val nextIdx = (currentIdx + 1) % list.size
                Pair(nextIdx, list[nextIdx])
            }
            LoopMode.OFF -> {
                if (currentIdx < list.lastIndex) {
                    Pair(currentIdx + 1, list[currentIdx + 1])
                } else null
            }
        }
    }

    /**
     * Crossfades automatically when approaching song end (same fading effect as manual skipping).
     */
    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (isActive) {
                val player = activePlayerInstance
                if (player != null && player.isPlaying) {
                    val pos = player.currentPosition
                    val dur = player.duration
                    if (pos >= 0L) {
                        _currentPositionMs.value = pos
                    }
                    if (dur > 0L) {
                        _durationMs.value = dur
                    }

                    // Seamless automatic crossfade when reaching song end (200ms):
                    val crossfadeMs = 200L
                    if (dur > 0L && !hasTriggeredAutoCrossfade && !crossfadeEngine.isCrossfading) {
                        val remaining = dur - pos
                        if (remaining in 50L..crossfadeMs) {
                            val next = getNextTrackInfo()
                            if (next != null) {
                                hasTriggeredAutoCrossfade = true
                                crossfadeToTrack(next.first, next.second, crossfadeMs)
                            }
                        }
                    }
                }
                delay(120L)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun handleTrackCompletion() {
        if (crossfadeEngine.isCrossfading) return
        val next = getNextTrackInfo()
        if (next != null) {
            crossfadeToTrack(next.first, next.second, customDurationMs = 200L)
        } else {
            stop()
        }
    }

    fun stop() {
        stopProgressTracker()
        _isPlaying.value = false
        crossfadeEngine.cancelCrossfade()
        playerA?.stop()
        playerB?.stop()
    }

    fun release() {
        stop()
        crossfadeEngine.release()
        playerA?.release()
        playerB?.release()
        playerA = null
        playerB = null
        activePlayerInstance = null
    }

    // SharedPreferences Helpers
    private fun loadSettings(): AppSettings {
        val isDark = prefs.getBoolean("is_dark_mode", true)
        val lang = prefs.getString("app_language", "System") ?: "System"
        val themeName = prefs.getString("selected_theme", AppTheme.SUNSET.name) ?: AppTheme.SUNSET.name
        val theme = try { AppTheme.valueOf(themeName) } catch (_: Exception) { AppTheme.SUNSET }
        val font = prefs.getString("font_family", "System Font (Default)") ?: "System Font (Default)"
        val lyricsFont = prefs.getInt("lyrics_font_size", 110)
        val crossfadeDur = prefs.getFloat("crossfade_duration", 0.2f).coerceIn(0.1f, 0.4f)
        val fadeOutDur = prefs.getFloat("fade_out_duration", 0.2f).coerceIn(0.1f, 0.4f)
        val fadeInDur = prefs.getFloat("fade_in_duration", 0.2f).coerceIn(0.1f, 0.4f)

        return AppSettings(
            isDarkMode = isDark,
            appLanguage = lang,
            selectedTheme = theme,
            fontFamilyName = font,
            lyricsFontSizePercent = lyricsFont,
            crossfadeDuration = crossfadeDur,
            fadeOutDuration = fadeOutDur,
            fadeInDuration = fadeInDur
        )
    }

    private fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putBoolean("is_dark_mode", s.isDarkMode)
            .putString("app_language", s.appLanguage)
            .putString("selected_theme", s.selectedTheme.name)
            .putString("font_family", s.fontFamilyName)
            .putInt("lyrics_font_size", s.lyricsFontSizePercent)
            .putFloat("crossfade_duration", s.crossfadeDuration)
            .putFloat("fade_out_duration", s.fadeOutDuration)
            .putFloat("fade_in_duration", s.fadeInDuration)
            .apply()
    }

    private fun loadFavorites(): Set<Long> {
        val set = prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
        return set.mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun saveFavorites(favs: Set<Long>) {
        prefs.edit().putStringSet("favorite_ids", favs.map { it.toString() }.toSet()).apply()
    }

    private fun loadDislikes(): Set<Long> {
        val set = prefs.getStringSet("disliked_ids", emptySet()) ?: emptySet()
        return set.mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun saveDislikes(dislikes: Set<Long>) {
        prefs.edit().putStringSet("disliked_ids", dislikes.map { it.toString() }.toSet()).apply()
    }

    private fun saveLastTrackId(id: Long) {
        prefs.edit().putLong("last_track_id", id).apply()
    }

    companion object {
        @Volatile
        private var instance: AudioPlayerManager? = null

        fun getInstance(context: Context): AudioPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: AudioPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
