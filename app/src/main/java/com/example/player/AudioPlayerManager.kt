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
import kotlinx.coroutines.isActive
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

    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    private var activePlayerInstance: ExoPlayer? = null

    val exoPlayer: ExoPlayer?
        get() = activePlayerInstance

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var lyricsJob: Job? = null
    private var enrichJob: Job? = null
    private var pipelineJob: Job? = null

    // Flujos de información de artista cargados secuencialmente en la Etapa 3
    private val _currentArtistInfo = MutableStateFlow<com.example.data.ArtistInfo?>(null)
    val currentArtistInfo: StateFlow<com.example.data.ArtistInfo?> = _currentArtistInfo.asStateFlow()

    private val _currentArtistProfiles = MutableStateFlow<List<com.example.data.ArtistProfile>>(emptyList())
    val currentArtistProfiles: StateFlow<List<com.example.data.ArtistProfile>> = _currentArtistProfiles.asStateFlow()

    private val _isArtistInfoLoading = MutableStateFlow<Boolean>(false)
    val isArtistInfoLoading: StateFlow<Boolean> = _isArtistInfoLoading.asStateFlow()

    private val crossfadeEngine by lazy {
        CrossfadeEngine(
            context = context,
            fadeOutDurationMs = 100L,
            fadeInDurationMs = 100L,
            crossfadeDurationMs = 100L
        )
    }

    // Track Queue State
    private val _playlist = MutableStateFlow<List<Track>>(emptyList())
    val playlist: StateFlow<List<Track>> = _playlist.asStateFlow()
    private var originalQueue: List<Track>? = null

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
    private val _playlistsMap = MutableStateFlow<Map<String, List<Track>>>(emptyMap())
    val playlistsMap: StateFlow<Map<String, List<Track>>> = _playlistsMap.asStateFlow()

    init {
        try {
            val initialLangCode = com.example.ui.LanguageManager.getLanguageCode(_appSettings.value.appLanguage)
            com.example.ui.LanguageManager.cambiarIdioma(initialLangCode)
        } catch (e: Exception) {
            Log.w(TAG, "Error applying initial language: ${e.message}")
        }
        scope.launch {
            val lastTrackId = prefs.getLong("last_track_id", -1L)
            if (lastTrackId != -1L) {
                try {
                    val tracks = com.example.data.TrackRepository.tracks.first { it.isNotEmpty() }
                    if (_currentTrack.value == null) {
                        val lastTrack = tracks.find { it.id == lastTrackId } ?: tracks.firstOrNull()
                        if (lastTrack != null) {
                            _playlist.value = tracks
                            _currentIndex.value = tracks.indexOf(lastTrack).coerceAtLeast(0)
                            _currentTrack.value = lastTrack
                            _durationMs.value = lastTrack.durationMs
                        }
                    }
                } catch (e: Exception) {}
            }
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
    private val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_LOSS,
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_isPlaying.value) {
                    togglePlayPause()
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                activePlayerInstance?.volume = _volume.value * 0.2f
            }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                activePlayerInstance?.volume = _volume.value
            }
        }
    }

    private fun requestAppAudioFocus(): Boolean {
        val am = audioManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .build()
            val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attr)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            am.requestAudioFocus(request) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(audioFocusChangeListener, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun createExoPlayerInstance(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 100,
                /* bufferForPlaybackAfterRebufferMs = */ 200
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, false)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setLoadControl(loadControl)
            .build()
    }

    private fun getOrCreateActivePlayer(): ExoPlayer {
        if (playerA == null) {
            val pA = createExoPlayerInstance()
            val pB = createExoPlayerInstance()
            pA.volume = _volume.value
            pB.volume = 0f
            updatePlayerRepeatMode(pA)
            updatePlayerRepeatMode(pB)
            pA.shuffleModeEnabled = _isShuffle.value
            pB.shuffleModeEnabled = _isShuffle.value
            pA.addListener(createPlayerListener(pA))
            pB.addListener(createPlayerListener(pB))
            playerA = pA
            playerB = pB
            activePlayerInstance = pA
            crossfadeEngine.attachActivePlayer(pA, _volume.value)
        }
        return activePlayerInstance ?: playerA!!
    }

    private fun getOrCreatePlayer(): ExoPlayer = getOrCreateActivePlayer()

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

    private fun createPlayerListener(player: ExoPlayer) = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (player != activePlayerInstance) return
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
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !crossfadeEngine.isCrossfading) {
                        val fadeInMs = (_appSettings.value.fadeInDuration * 1000L).toLong()
                        if (fadeInMs > 0L) {
                            crossfadeEngine.startFadeIn(player, fadeInMs)
                        }
                    }
                }

                // Iniciar pipeline secuencial (1. Portada y canción, 2. Letras, 3. Tarjeta de artista)
                startTrackSequentialPipeline(track, currentIdx)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (player != activePlayerInstance) return
            // If player is transitioning/buffering after a seek or skip, but playWhenReady is true,
            // maintain playing state so the UI and audio do not stutter or falsely indicate pause.
            if (!isPlaying && player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) {
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
                Player.STATE_IDLE, Player.STATE_BUFFERING -> {}
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (player != activePlayerInstance) return
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
        originalQueue = tracks
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

        _playlist.value = tracks
        _currentIndex.value = validIndex
        _currentTrack.value = track
        saveLastTrackId(track.id)
        loadLyricsForTrack(track)

        val mediaItem = trackToMediaItem(track)
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(mediaItem)
        player.prepare()

        if (autoPlay) {
            player.volume = _volume.value
            player.playWhenReady = true
            requestAppAudioFocus()
            player.play()
            _isPlaying.value = true
            ensureServiceStarted()
        } else {
            player.pause()
            _isPlaying.value = false
        }
        enrichTrackIfNeeded(track, validIndex)
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

    private fun crossfadeToTrack(index: Int, track: Track, customDurationMs: Long? = null) {
        val tracks = _playlist.value
        if (index !in tracks.indices) return

        requestAppAudioFocus()
        val durationMs = customDurationMs ?: 100L
        val currentActive = getOrCreateActivePlayer()
        val nextPlayer = if (currentActive == playerA) playerB!! else playerA!!

        // Preparar silenciosamente el reproductor entrante
        val mediaItem = trackToMediaItem(track)
        nextPlayer.stop()
        nextPlayer.clearMediaItems()
        nextPlayer.setMediaItem(mediaItem)
        nextPlayer.prepare()
        nextPlayer.playWhenReady = false
        nextPlayer.volume = 0f

        crossfadeEngine.startManualCrossfade(
            outgoingPlayer = currentActive,
            incomingPlayer = nextPlayer,
            durationMs = durationMs,
            volume = _volume.value,
            onBufferReady = {
                activePlayerInstance = nextPlayer

                _currentIndex.value = index
                _currentTrack.value = track
                _currentPositionMs.value = 0L
                _durationMs.value = track.durationMs
                _isPlaying.value = true
                saveLastTrackId(track.id)

                scope.launch {
                    socialRepository.recordPlayback(track.id)
                }
                loadLyricsForTrack(track)
                enrichTrackIfNeeded(track, index)
                ensureServiceStarted()
            },
            onComplete = {}
        )
    }

    private var lastTrackChangeTimeMs: Long = 0L

    fun playTrackAtIndex(index: Int) {
        val tracks = _playlist.value
        if (index !in tracks.indices) return
        val track = tracks[index]

        // 1. Cancel any active crossfade or gapless preload
        crossfadeEngine.cancelCrossfade()

        // 2. Stop and clear the inactive player to prevent any conflicting or old playback
        val player = getOrCreateActivePlayer()
        val otherPlayer = if (player == playerA) playerB else playerA
        try {
            otherPlayer?.volume = 0f
            otherPlayer?.stop()
            otherPlayer?.clearMediaItems()
        } catch (_: Exception) {}

        // Ensure activePlayerInstance and crossfade engine are synced to this player
        activePlayerInstance = player
        crossfadeEngine.attachActivePlayer(player, _volume.value)

        // 3. Immediately set and load the new audio on the player in proper order:
        // setMediaItem -> prepare -> play
        val mediaItem = trackToMediaItem(track)
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.volume = _volume.value
        player.playWhenReady = true
        requestAppAudioFocus()
        player.play()

        // 4. Update the current index and track state (UI and metadata sync)
        _currentIndex.value = index
        _currentTrack.value = track
        _currentPositionMs.value = 0L
        _durationMs.value = track.durationMs
        _isPlaying.value = true
        saveLastTrackId(track.id)

        scope.launch {
            socialRepository.recordPlayback(track.id)
        }
        ensureServiceStarted()
        startTrackSequentialPipeline(track, index)

        val fadeInMs = (_appSettings.value.fadeInDuration * 1000L).toLong()
        if (fadeInMs > 80L) {
            crossfadeEngine.startFadeIn(player, fadeInMs)
        } else {
            player.volume = _volume.value
        }
    }

    /**
     * Pipeline secuencial de carga de recursos para evitar saturación de RAM:
     * 1. Lo esencial: la portada y la canción (audio arrancado + carátula lista en disco + bitrate ligero).
     *    Se liberan los buffers temporales de la etapa 1.
     * 2. Las letras: lectura de etiquetas/LRC de forma aislada, sin competir con carátula ni red.
     *    Se liberan los recursos de parsing de la etapa 2.
     * 3. La tarjeta de los artistas: info, bio y fotos de perfiles cargados paso a paso para el tarjetero.
     */
    private fun startTrackSequentialPipeline(track: Track, index: Int) {
        pipelineJob?.cancel()
        lyricsJob?.cancel()
        enrichJob?.cancel()

        // Sembrar caché inmediato en memoria si ya existía de antes (0ms, 0 RAM adicional)
        val initialCachedInfo = com.example.data.ArtistInfoFetcher.getCachedArtistInfo(track.artist)
        _currentArtistInfo.value = initialCachedInfo
        val initialCachedProfiles = com.example.data.ArtistInfoFetcher.getCachedArtistsForTrack(track)
        _currentArtistProfiles.value = if (initialCachedProfiles.isNotEmpty()) initialCachedProfiles else {
            if (initialCachedInfo != null) listOf(com.example.data.ArtistProfile(name = track.artist, isPrimary = true, info = initialCachedInfo))
            else emptyList()
        }

        // Si los perfiles y fotos ya están completos en disco, no mostramos skeleton
        val isFullyCached = initialCachedProfiles.isNotEmpty() && initialCachedProfiles.all {
            !it.info.imageUrl.isNullOrBlank() && !it.info.bio.isNullOrBlank()
        }
        _isArtistInfoLoading.value = !isFullyCached

        pipelineJob = scope.launch(Dispatchers.IO) {
            // =========================================================================
            // ETAPA 1: LO ESENCIAL (LA CANCIÓN Y LA PORTADA)
            // =========================================================================
            // La canción ya arrancó en el reproductor. Aseguramos la carátula en disco para
            // que TrackImage/Coil la pinte de inmediato con máxima prioridad sin contención de I/O.
            try {
                val artExtractor = com.example.data.AlbumArtExtractor(context)
                if (artExtractor.getExistingArtFile(track) == null) {
                    artExtractor.getHighResArt(track)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.d(TAG, "Etapa 1 carátula: ${e.message}")
            }

            // Enriquecer bitrate/sampleRate de forma ligera sin abrir árboles pesados ni extraer letras
            if (track.bitrate.isBlank() || track.sampleRate.isBlank()) {
                try {
                    val (br, sr) = com.example.data.MetadataReader.extractAudioSpecsLightweight(context, track)
                    if (isActive && (br.isNotBlank() || sr.isNotBlank())) {
                        withContext(Dispatchers.Main) {
                            val cur = _currentTrack.value
                            if (cur != null && cur.id == track.id) {
                                _currentTrack.value = cur.copy(
                                    bitrate = if (cur.bitrate.isBlank()) br else cur.bitrate,
                                    sampleRate = if (cur.sampleRate.isBlank()) sr else cur.sampleRate
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                }
            }

            // Liberar recursos temporales de la etapa 1 antes de dar paso a las letras
            if (!isActive) return@launch
            delay(120)
            if (!isActive) return@launch

            // =========================================================================
            // ETAPA 2: LAS LETRAS
            // =========================================================================
            // Se leen las letras sin interferencia de la carátula ni de descargas del artista.
            try {
                val repo = com.example.data.LyricsRepository.getInstance(context)
                val immediate = repo.getCachedOrDisk(track)
                val finalLyrics = if (!immediate.isNullOrBlank() && !com.example.data.AudioLyricsExtractor.isCorruptedLyrics(immediate)) {
                    immediate
                } else {
                    repo.getLyrics(context, track, forceRescan = false)
                }

                if (!finalLyrics.isNullOrBlank() && !com.example.data.AudioLyricsExtractor.isCorruptedLyrics(finalLyrics) && isActive) {
                    val cleanLyrics = com.example.data.AudioLyricsExtractor.cleanExtractedLyrics(finalLyrics)
                    if (cleanLyrics.isNotBlank() && !com.example.data.AudioLyricsExtractor.isCorruptedLyrics(cleanLyrics)) {
                        withContext(Dispatchers.Main) {
                            val cur = _currentTrack.value
                            if (cur != null && cur.id == track.id) {
                                _currentTrack.value = cur.copy(lyrics = cleanLyrics)
                            }
                            val currentPlaylist = _playlist.value.toMutableList()
                            val idx = currentPlaylist.indexOfFirst { it.id == track.id }
                            if (idx != -1) {
                                currentPlaylist[idx] = currentPlaylist[idx].copy(lyrics = cleanLyrics)
                                _playlist.value = currentPlaylist
                            }
                        }
                        try {
                            com.example.data.TrackRepository.updateTrackLyrics(context, track.id, cleanLyrics)
                        } catch (_: Exception) {}
                    }
                } else if (isActive) {
                    withContext(Dispatchers.Main) {
                        val cur = _currentTrack.value
                        if (cur != null && cur.id == track.id && (cur.lyrics.isBlank() || com.example.data.AudioLyricsExtractor.isCorruptedLyrics(cur.lyrics))) {
                            _currentTrack.value = cur.copy(lyrics = "")
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.d(TAG, "Etapa 2 letras: ${e.message}")
            }

            // Liberar memoria intermedia y descriptores de etiquetas de la etapa 2
            if (!isActive) return@launch
            delay(180)
            if (!isActive) return@launch

            // =========================================================================
            // ETAPA 3: LA TARJETA DE LOS ARTISTAS
            // =========================================================================
            // Ahora que la canción suena, la portada está en pantalla y las letras se leyeron,
            // procedemos a cargar la tarjeta de los artistas paso a paso sin ahogar la memoria.
            try {
                val cached = com.example.data.ArtistInfoFetcher.getCachedArtistInfo(track.artist)
                val needFetchInfo = cached == null || cached.imageUrl.isNullOrBlank() || cached.bio.isNullOrBlank()
                val fetchedInfo = if (needFetchInfo) {
                    com.example.data.ArtistInfoFetcher.fetchArtistInfo(
                        rawArtistName = track.artist,
                        trackTitle = track.title,
                        albumName = track.album,
                        genre = track.genre
                    )
                } else {
                    cached
                }

                if (isActive && fetchedInfo != null) {
                    _currentArtistInfo.value = fetchedInfo
                }

                // Cargar perfiles enriquecidos para el tarjetero
                if (isActive) {
                    val fetchedProfiles = com.example.data.ArtistInfoFetcher.fetchArtistsForTrack(track)
                    if (isActive && fetchedProfiles.isNotEmpty()) {
                        _currentArtistProfiles.value = fetchedProfiles
                    } else if (fetchedInfo != null) {
                        _currentArtistProfiles.value = listOf(
                            com.example.data.ArtistProfile(name = track.artist, isPrimary = true, info = fetchedInfo)
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.d(TAG, "Etapa 3 tarjeta de artista: ${e.message}")
            } finally {
                _isArtistInfoLoading.value = false
            }
        }
    }

    fun refreshCurrentArtistInfo() {
        val track = _currentTrack.value ?: return
        _currentArtistInfo.value = null
        _currentArtistProfiles.value = emptyList()
        _isArtistInfoLoading.value = true
        enrichJob?.cancel()
        enrichJob = scope.launch(Dispatchers.IO) {
            try {
                val fetchedInfo = com.example.data.ArtistInfoFetcher.fetchArtistInfo(
                    rawArtistName = track.artist,
                    trackTitle = track.title,
                    albumName = track.album,
                    genre = track.genre
                )
                if (isActive && fetchedInfo != null) {
                    _currentArtistInfo.value = fetchedInfo
                }
                val fetchedProfiles = com.example.data.ArtistInfoFetcher.fetchArtistsForTrack(track)
                if (isActive && fetchedProfiles.isNotEmpty()) {
                    _currentArtistProfiles.value = fetchedProfiles
                } else if (isActive && fetchedInfo != null) {
                    _currentArtistProfiles.value = listOf(
                        com.example.data.ArtistProfile(name = track.artist, isPrimary = true, info = fetchedInfo)
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.d(TAG, "Error refreshing artist info: ${e.message}")
            } finally {
                _isArtistInfoLoading.value = false
            }
        }
    }

    fun loadLyricsForTrack(track: Track, forceRescan: Boolean = false) {
        lyricsJob?.cancel()
        lyricsJob = scope.launch(Dispatchers.IO) {
            try {
                val repo = com.example.data.LyricsRepository.getInstance(context)
                val immediate = if (!forceRescan) repo.getCachedOrDisk(track) else null
                if (!immediate.isNullOrBlank() && !com.example.data.AudioLyricsExtractor.isCorruptedLyrics(immediate)) {
                    val cleanImmediate = com.example.data.AudioLyricsExtractor.cleanExtractedLyrics(immediate)
                    if (cleanImmediate.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            val cur = _currentTrack.value
                            if (cur != null && cur.id == track.id) {
                                _currentTrack.value = cur.copy(lyrics = cleanImmediate)
                            }
                        }
                        return@launch
                    }
                }

                val lyrics = repo.getLyrics(context, track, forceRescan)
                if (!lyrics.isNullOrBlank() && !com.example.data.AudioLyricsExtractor.isCorruptedLyrics(lyrics) && isActive) {
                    val cleanLyrics = com.example.data.AudioLyricsExtractor.cleanExtractedLyrics(lyrics)
                    if (cleanLyrics.isNotBlank() && !com.example.data.AudioLyricsExtractor.isCorruptedLyrics(cleanLyrics)) {
                        withContext(Dispatchers.Main) {
                            val cur = _currentTrack.value
                            if (cur != null && cur.id == track.id) {
                                _currentTrack.value = cur.copy(lyrics = cleanLyrics)
                            }
                            val currentPlaylist = _playlist.value.toMutableList()
                            val idx = currentPlaylist.indexOfFirst { it.id == track.id }
                            if (idx != -1) {
                                currentPlaylist[idx] = currentPlaylist[idx].copy(lyrics = cleanLyrics)
                                _playlist.value = currentPlaylist
                            }
                        }
                        try {
                            com.example.data.TrackRepository.updateTrackLyrics(context, track.id, cleanLyrics)
                        } catch (_: Exception) {}
                    }
                } else if (isActive) {
                    withContext(Dispatchers.Main) {
                        val cur = _currentTrack.value
                        if (cur != null && cur.id == track.id && (cur.lyrics.isBlank() || com.example.data.AudioLyricsExtractor.isCorruptedLyrics(cur.lyrics))) {
                            _currentTrack.value = cur.copy(lyrics = "")
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Error loading lyrics for track ${track.title}: ${e.message}")
                }
            }
        }
    }

    private fun enrichTrackIfNeeded(track: Track, index: Int) {
        startTrackSequentialPipeline(track, index)
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
            requestAppAudioFocus()
            player.play()
            ensureServiceStarted()
        }
    }

    fun nextTrack() {
        val tracks = _playlist.value
        if (tracks.isEmpty()) return

        val curIdx = _currentIndex.value
        val nextIdx = when {
            _loopMode.value == LoopMode.REPEAT_ONE -> curIdx
            curIdx < tracks.lastIndex -> curIdx + 1
            _loopMode.value == LoopMode.REPEAT_ALL -> 0
            else -> 0
        }
        playTrackAtIndex(nextIdx)
    }

    fun previousTrack() {
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
        playerA?.volume = vol
        playerB?.volume = vol
        crossfadeEngine.setVolume(vol)
    }

    fun setShuffle(enabled: Boolean) {
        if (_isShuffle.value == enabled) return
        _isShuffle.value = enabled
        playerA?.shuffleModeEnabled = enabled
        playerB?.shuffleModeEnabled = enabled

        val cur = _currentTrack.value
        val tracks = _playlist.value
        if (enabled) {
            if (originalQueue == null || originalQueue!!.isEmpty()) {
                originalQueue = tracks
            }
            if (tracks.size > 1) {
                val otherTracks = tracks.filter { it.id != cur?.id }.shuffled()
                val newQueue = if (cur != null) listOf(cur) + otherTracks else tracks.shuffled()
                _playlist.value = newQueue
                _currentIndex.value = 0
            }
        } else {
            val orig = originalQueue
            if (orig != null && orig.isNotEmpty()) {
                _playlist.value = orig
                _currentIndex.value = orig.indexOfFirst { it.id == cur?.id }.coerceAtLeast(0)
            }
            originalQueue = null
        }
    }

    fun toggleShuffle() {
        setShuffle(!_isShuffle.value)
    }

    fun shuffleQueue() {
        val cur = _currentTrack.value
        val tracks = _playlist.value
        if (tracks.size <= 1) return
        if (originalQueue == null || originalQueue!!.isEmpty()) {
            originalQueue = tracks
        }
        val otherTracks = tracks.filter { it.id != cur?.id }.shuffled()
        val newQueue = if (cur != null) listOf(cur) + otherTracks else tracks.shuffled()
        _playlist.value = newQueue
        _currentIndex.value = 0
        _isShuffle.value = true
    }

    fun cycleLoopMode() {
        val newMode = when (_loopMode.value) {
            LoopMode.OFF -> LoopMode.REPEAT_ALL
            LoopMode.REPEAT_ALL -> LoopMode.REPEAT_ONE
            LoopMode.REPEAT_ONE -> LoopMode.OFF
        }
        _loopMode.value = newMode
        playerA?.let { updatePlayerRepeatMode(it) }
        playerB?.let { updatePlayerRepeatMode(it) }
    }

    private fun updatePlayerRepeatMode(player: ExoPlayer) {
        // Individual player instances must NEVER repeat their single track automatically!
        // AudioPlayerManager manages playlist looping in getNextTrackInfo() and nextTrack().
        // If an ExoPlayer instance had REPEAT_MODE_ALL, it looped its 1-song playlist back to 0
        // right before crossfade finished, causing the 1-second replay pop reported by the user!
        player.repeatMode = Player.REPEAT_MODE_OFF
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
        crossfadeEngine.cancelCrossfade()
        val next = getNextTrackInfo()
        if (next != null) {
            playTrackAtIndex(next.first)
        } else {
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
                val player = activePlayerInstance
                var isNearEnd = false
                if (player != null && player.isPlaying) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration
                    _currentPositionMs.value = pos
                    if (dur > 0L) {
                        _durationMs.value = dur
                    }

                    // Combined Gapless Crossfade at end of song:
                    // Preload next track silently, wait for STATE_READY, then crossfade smoothly
                    val autoCrossfadeMs = maxOf(crossfadeEngine.crossfadeDurationMs, 600L)
                    val preloadThreshold = autoCrossfadeMs + 3400L

                    if (dur > 0L && dur > 1200L) {
                        val remaining = dur - pos
                        if (remaining in 0..(preloadThreshold + 600L)) {
                            isNearEnd = true
                        }

                        if (remaining in 1..preloadThreshold && !crossfadeEngine.isCrossfading && !crossfadeEngine.isPreloading) {
                            val next = getNextTrackInfo()
                            if (next != null) {
                                val nextPlayer = if (player == playerA) playerB!! else playerA!!
                                val mediaItem = trackToMediaItem(next.second)
                                nextPlayer.stop()
                                nextPlayer.clearMediaItems()
                                nextPlayer.setMediaItem(mediaItem)
                                nextPlayer.prepare()
                                nextPlayer.playWhenReady = false
                                nextPlayer.volume = 0f

                                crossfadeEngine.initiateGaplessCrossfade(
                                    outgoingPlayer = player,
                                    incomingPlayer = nextPlayer,
                                    durationMs = autoCrossfadeMs,
                                    volume = _volume.value,
                                    onSyncMoment = {
                                        activePlayerInstance = nextPlayer
                                        _currentIndex.value = next.first
                                        _currentTrack.value = next.second
                                        _currentPositionMs.value = 0L
                                        _durationMs.value = next.second.durationMs
                                        _isPlaying.value = true
                                        saveLastTrackId(next.second.id)

                                        scope.launch(Dispatchers.IO) {
                                            socialRepository.recordPlayback(next.second.id)
                                        }
                                        loadLyricsForTrack(next.second)
                                        enrichTrackIfNeeded(next.second, next.first)
                                        ensureServiceStarted()
                                    },
                                    onComplete = {}
                                )
                            } else {
                                crossfadeEngine.applyFadeOut(remaining, autoCrossfadeMs, player)
                            }
                        }
                    }
                }
                delay(if (isNearEnd) 30L else 200L)
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
            crossfadeEngine.cancelCrossfade()
            val inactivePlayer = if (activePlayerInstance == playerA) playerB else playerA
            inactivePlayer?.volume = 0f
            try {
                inactivePlayer?.stop()
                inactivePlayer?.clearMediaItems()
            } catch (_: Exception) {}

            val removedTrack = currentList[index]
            val isCurrentBeingRemoved = index == _currentIndex.value
            currentList.removeAt(index)
            _playlist.value = currentList
            originalQueue = originalQueue?.filter { it.id != removedTrack.id }

            if (currentList.isEmpty()) {
                _currentIndex.value = -1
                _currentTrack.value = null
                _isPlaying.value = false
                playerA?.stop()
                playerA?.clearMediaItems()
                playerB?.stop()
                playerB?.clearMediaItems()
            } else if (isCurrentBeingRemoved) {
                val nextIdx = index.coerceAtMost(currentList.lastIndex)
                playTrackAtIndex(nextIdx)
            } else {
                val currentTrackId = _currentTrack.value?.id
                val actualCurrIdx = if (currentTrackId != null) {
                    currentList.indexOfFirst { it.id == currentTrackId }.takeIf { it != -1 } ?: 0
                } else {
                    _currentIndex.value.coerceIn(0, currentList.lastIndex)
                }
                _currentIndex.value = actualCurrIdx
            }
        }
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        val currentList = _playlist.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices && fromIndex != toIndex) {
            crossfadeEngine.cancelCrossfade()
            val inactivePlayer = if (activePlayerInstance == playerA) playerB else playerA
            inactivePlayer?.volume = 0f
            try {
                inactivePlayer?.stop()
                inactivePlayer?.clearMediaItems()
            } catch (_: Exception) {}

            val currentTrackId = _currentTrack.value?.id
            val track = currentList.removeAt(fromIndex)
            currentList.add(toIndex, track)
            _playlist.value = currentList

            val actualCurrIdx = if (currentTrackId != null) {
                currentList.indexOfFirst { it.id == currentTrackId }.takeIf { it != -1 } ?: _currentIndex.value
            } else {
                _currentIndex.value
            }
            _currentIndex.value = actualCurrIdx
        }
    }

    fun setPlayNext(track: Track) {
        // Cancel any active crossfade or preload immediately
        crossfadeEngine.cancelCrossfade()

        // Stop and clear inactive player to purge any stale preloaded track
        val inactivePlayer = if (activePlayerInstance == playerA) playerB else playerA
        inactivePlayer?.volume = 0f
        try {
            inactivePlayer?.stop()
            inactivePlayer?.clearMediaItems()
        } catch (_: Exception) {}

        val currentList = _playlist.value.toMutableList()
        val currIdx = _currentIndex.value
        val currentTrackId = _currentTrack.value?.id

        val existingIdx = currentList.indexOfFirst { it.id == track.id }
        if (existingIdx != -1) {
            // If already the currently playing track, do nothing
            if (existingIdx == currIdx) return
            currentList.removeAt(existingIdx)
        }

        // Accurately find current playing track's index in the modified list
        val actualCurrIdx = if (currentTrackId != null) {
            currentList.indexOfFirst { it.id == currentTrackId }.takeIf { it != -1 } ?: currIdx
        } else {
            currIdx
        }

        // Insert immediately after the current playing track (front of the upcoming queue)
        val insertPos = if (actualCurrIdx in currentList.indices) actualCurrIdx + 1 else 0
        currentList.add(insertPos, track)

        _playlist.value = currentList
        _currentIndex.value = actualCurrIdx

        // Maintain originalQueue if shuffle was active
        originalQueue = originalQueue?.let { orig ->
            val origList = orig.filter { it.id != track.id }.toMutableList()
            val origCurPos = origList.indexOfFirst { it.id == currentTrackId }
            val origInsert = if (origCurPos != -1) origCurPos + 1 else origList.size
            origList.add(origInsert, track)
            origList
        }
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
            val savedLang = prefs.getString("appLanguage", null)
            val resolvedLang = if (savedLang != null && savedLang.isNotBlank()) {
                savedLang
            } else {
                com.example.ui.LanguageManager.getDeviceDefaultLanguage(context)
            }
            AppSettings(
                isDarkMode = prefs.getBoolean("isDarkMode", true),
                appLanguage = resolvedLang,
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
        val oldLang = _appSettings.value.appLanguage
        _appSettings.value = newSettings
        saveSettings(newSettings)
        crossfadeEngine.crossfadeDurationMs = 100L
        crossfadeEngine.fadeOutDurationMs = 100L
        crossfadeEngine.fadeInDurationMs = 100L
        if (newSettings.appLanguage != oldLang) {
            val code = com.example.ui.LanguageManager.getLanguageCode(newSettings.appLanguage)
            com.example.ui.LanguageManager.cambiarIdioma(code)
        }
    }

    fun updateTheme(theme: AppTheme) {
        updateSettings(_appSettings.value.copy(selectedTheme = theme))
    }

    fun toggleDarkMode() {
        updateSettings(_appSettings.value.copy(isDarkMode = !_appSettings.value.isDarkMode))
    }

    fun setAppLanguage(lang: String) {
        val code = com.example.ui.LanguageManager.getLanguageCode(lang)
        com.example.ui.LanguageManager.cambiarIdioma(code)
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
        crossfadeEngine.release()
        try {
            playerA?.stop()
            playerA?.release()
        } catch (_: Exception) {}
        try {
            playerB?.stop()
            playerB?.release()
        } catch (_: Exception) {}
        playerA = null
        playerB = null
        activePlayerInstance = null
    }
}
