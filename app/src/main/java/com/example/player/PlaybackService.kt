package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.MainActivity
import com.example.R
import com.example.data.ArtworkExtractor
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackService : Service() {

    private lateinit var mediaSession: MediaSession
    private lateinit var playerManager: AudioPlayerManager
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val CHANNEL_ID = "fuzion_playback_channel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        playerManager = AudioPlayerManager.getInstance(applicationContext)

        mediaSession = MediaSession(this, "FuzionPlaybackService").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (!playerManager.isPlaying.value) {
                        playerManager.togglePlayPause()
                    }
                }
                override fun onPause() {
                    if (playerManager.isPlaying.value) {
                        playerManager.togglePlayPause()
                    }
                }
                override fun onSkipToNext() { playerManager.nextTrack() }
                override fun onSkipToPrevious() { playerManager.previousTrack() }
                override fun onSeekTo(pos: Long) { playerManager.seekTo(pos) }
                override fun onStop() {
                    if (playerManager.isPlaying.value) {
                        playerManager.togglePlayPause()
                    }
                }
            })
            isActive = true
        }

        createNotificationChannel()

        // Immediate startForeground call to satisfy Android 8+ requirement
        updateNotificationAndSession()

        scope.launch {
            playerManager.currentTrack.collect {
                updateNotificationAndSession()
            }
        }
        scope.launch {
            playerManager.isPlaying.collect {
                updateNotificationAndSession()
            }
        }
    }

    private var lastTrackForImage: Long? = null
    private var lastLoadedBitmap: Bitmap? = null

    private fun updateNotificationAndSession() {
        val track = playerManager.currentTrack.value
        val isPlaying = playerManager.isPlaying.value

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        if (track == null) {
            builder.setSmallIcon(R.drawable.ic_stat_music)
                .setContentTitle("Fuzion Player")
                .setContentText("Listo para reproducir")
                .setContentIntent(pendingIntent)
                .setVisibility(Notification.VISIBILITY_PUBLIC)

            val notification = builder.build()
            startForegroundNotification(notification)
            return
        }

        // Update MediaSession
        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                playerManager.currentPositionMs.value,
                if (isPlaying) 1.0f else 0.0f
            )
        mediaSession.setPlaybackState(stateBuilder.build())

        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationMs)

        track.albumArtUri?.let { uri ->
            metadataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, uri.toString())
            metadataBuilder.putString(MediaMetadata.METADATA_KEY_ART_URI, uri.toString())
            metadataBuilder.putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, uri.toString())
        }

        if (lastTrackForImage != track.id || lastLoadedBitmap == null) {
            lastTrackForImage = track.id
            val cached = ArtworkExtractor.getCachedBitmap(track)
            if (cached != null) {
                lastLoadedBitmap = cached
            } else {
                scope.launch(Dispatchers.IO) {
                    val albumArtBitmap = ArtworkExtractor.loadArtworkBitmap(applicationContext, track, targetDim = 320)
                    if (albumArtBitmap != null) {
                        lastLoadedBitmap = albumArtBitmap
                        withContext(Dispatchers.Main) {
                            if (playerManager.currentTrack.value?.id == track.id) {
                                updateNotificationAndSession()
                            }
                        }
                    }
                }
            }
        }

        if (lastLoadedBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, lastLoadedBitmap)
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, lastLoadedBitmap)
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, lastLoadedBitmap)
        }
        mediaSession.setMetadata(metadataBuilder.build())

        builder.setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText(track.album)
            .setContentIntent(pendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setStyle(
                Notification.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession.sessionToken)
            )

        // Actions: Previous, Play/Pause, Next
        val prevIntent = Intent(this, PlaybackService::class.java).setAction("PREV")
        val prevAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_skip_prev_rounded),
                "Previous",
                PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_IMMUTABLE)
            ).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Action.Builder(
                R.drawable.ic_skip_prev_rounded,
                "Previous",
                PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_IMMUTABLE)
            ).build()
        }
        builder.addAction(prevAction)

        val playPauseAction = if (isPlaying) {
            val pauseIntent = Intent(this, PlaybackService::class.java).setAction("PAUSE")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_pause_rounded),
                    "Pause",
                    PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
                ).build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Action.Builder(
                    R.drawable.ic_pause_rounded,
                    "Pause",
                    PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
                ).build()
            }
        } else {
            val playIntent = Intent(this, PlaybackService::class.java).setAction("PLAY")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_play_rounded),
                    "Play",
                    PendingIntent.getService(this, 3, playIntent, PendingIntent.FLAG_IMMUTABLE)
                ).build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Action.Builder(
                    R.drawable.ic_play_rounded,
                    "Play",
                    PendingIntent.getService(this, 3, playIntent, PendingIntent.FLAG_IMMUTABLE)
                ).build()
            }
        }
        builder.addAction(playPauseAction)

        val nextIntent = Intent(this, PlaybackService::class.java).setAction("NEXT")
        val nextAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_skip_next_rounded),
                "Next",
                PendingIntent.getService(this, 4, nextIntent, PendingIntent.FLAG_IMMUTABLE)
            ).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Action.Builder(
                R.drawable.ic_skip_next_rounded,
                "Next",
                PendingIntent.getService(this, 4, nextIntent, PendingIntent.FLAG_IMMUTABLE)
            ).build()
        }
        builder.addAction(nextAction)

        if (lastLoadedBitmap != null) {
            builder.setLargeIcon(lastLoadedBitmap)
        }

        startForegroundNotification(builder.build())
    }

    private fun startForegroundNotification(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("PlaybackService", "Failed to start foreground notification: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY" -> {
                if (!playerManager.isPlaying.value) {
                    playerManager.togglePlayPause()
                }
            }
            "PAUSE" -> {
                if (playerManager.isPlaying.value) {
                    playerManager.togglePlayPause()
                }
            }
            "TOGGLE" -> playerManager.togglePlayPause()
            "NEXT" -> playerManager.nextTrack()
            "PREV" -> playerManager.previousTrack()
            "STOP" -> {
                if (playerManager.isPlaying.value) {
                    playerManager.togglePlayPause()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        updateNotificationAndSession()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // If music is playing, keep the foreground service and playback alive
        if (playerManager.isPlaying.value) {
            updateNotificationAndSession()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reproducción multimedia",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Controles del reproductor multimedia en segundo plano"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaSession.release()
        } catch (_: Exception) {}
    }
}