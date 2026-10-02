package com.example.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.ArtworkExtractor
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.R

class PlaybackService : Service() {

    private lateinit var mediaSession: MediaSession
    private lateinit var playerManager: AudioPlayerManager
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val CHANNEL_ID = "fuzion_playback_channel"

    override fun onCreate() {
        super.onCreate()
        playerManager = AudioPlayerManager.getInstance(applicationContext)

        mediaSession = MediaSession(this, "FuzionPlaybackService").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { playerManager.togglePlayPause() }
                override fun onPause() { playerManager.togglePlayPause() }
                override fun onSkipToNext() { playerManager.nextTrack() }
                override fun onSkipToPrevious() { playerManager.previousTrack() }
                override fun onSeekTo(pos: Long) { playerManager.seekTo(pos) }
            })
            isActive = true
        }

        createNotificationChannel()

        scope.launch {
            playerManager.currentTrack.collect { track ->
                updateNotificationAndSession()
            }
        }
        scope.launch {
            playerManager.isPlaying.collect { isPlaying ->
                updateNotificationAndSession()
            }
        }
    }

    private var lastTrackForImage: Long? = null
    private var lastLoadedBitmap: android.graphics.Bitmap? = null

    private fun updateNotificationAndSession() {
        val track = playerManager.currentTrack.value ?: return
        val isPlaying = playerManager.isPlaying.value

        // Update MediaSession
        val stateBuilder = PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO)
            .setState(if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, playerManager.currentPositionMs.value, 1.0f)
        mediaSession.setPlaybackState(stateBuilder.build())

        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationMs)

        if (lastTrackForImage != track.id) {
            lastTrackForImage = track.id
            // Check cache synchronously first
            val cached = ArtworkExtractor.getCachedBitmap(track)
            lastLoadedBitmap = cached

            if (cached == null) {
                scope.launch(Dispatchers.IO) {
                    val albumArtBitmap = ArtworkExtractor.loadArtworkBitmap(applicationContext, track, targetDim = 512)
                    if (albumArtBitmap != null) {
                        lastLoadedBitmap = albumArtBitmap
                        scope.launch(Dispatchers.Main) {
                            if (lastTrackForImage == track.id) {
                                updateNotificationAndSession() // trigger rebuild with image
                            }
                        }
                    }
                }
            }
        }
        
        if (lastLoadedBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, lastLoadedBitmap)
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, lastLoadedBitmap)
        }
        mediaSession.setMetadata(metadataBuilder.build())

        // Build Notification
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }

        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText(track.album)
            .setContentIntent(pendingIntent)
            .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            .setStyle(android.app.Notification.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSession.sessionToken))

        // Actions
        val prevIntent = Intent(this, PlaybackService::class.java).setAction("PREV")
        builder.addAction(android.app.Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_IMMUTABLE)).build())

        if (isPlaying) {
            val pauseIntent = Intent(this, PlaybackService::class.java).setAction("PAUSE")
            builder.addAction(android.app.Notification.Action.Builder(android.R.drawable.ic_media_pause, "Pause", PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE)).build())
        } else {
            val playIntent = Intent(this, PlaybackService::class.java).setAction("PLAY")
            builder.addAction(android.app.Notification.Action.Builder(android.R.drawable.ic_media_play, "Play", PendingIntent.getService(this, 3, playIntent, PendingIntent.FLAG_IMMUTABLE)).build())
        }

        val nextIntent = Intent(this, PlaybackService::class.java).setAction("NEXT")
        builder.addAction(android.app.Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", PendingIntent.getService(this, 4, nextIntent, PendingIntent.FLAG_IMMUTABLE)).build())

        if (lastLoadedBitmap != null) {
            builder.setLargeIcon(lastLoadedBitmap)
        }

        if (isPlaying) {
            startForeground(1, builder.build())
        } else {
            val notification = builder.build()
            startForeground(1, notification)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                stopForeground(false)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY", "PAUSE" -> playerManager.togglePlayPause()
            "NEXT" -> playerManager.nextTrack()
            "PREV" -> playerManager.previousTrack()
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
        playerManager.release()
    }
}