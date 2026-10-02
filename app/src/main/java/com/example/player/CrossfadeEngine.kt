package com.example.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/**
 * Crossfade Engine for seamless S-curve volume transitions on the active player.
 * Eliminates audio cuts, clicks, and abrupt transitions without desynchronizing the playback queue.
 */
class CrossfadeEngine(
    private val context: Context,
    var fadeOutDurationMs: Long = 100L,
    var fadeInDurationMs: Long = 100L,
    var crossfadeDurationMs: Long = 100L,
    private val playerFactory: () -> ExoPlayer = { error("Unused") },
    private val onHandover: (newPlayer: ExoPlayer) -> Unit = {}
) {
    private var activePlayer: ExoPlayer? = null
    var isCrossfading = false
        private set
    private var crossfadeStartTime = 0L
    private var currentFadeInDuration = 100L
    private var userVolume = 1f

    private val handler = Handler(Looper.getMainLooper())

    private val fadeInTicker = object : Runnable {
        override fun run() {
            val player = activePlayer ?: return
            val elapsed = SystemClock.elapsedRealtime() - crossfadeStartTime
            val duration = currentFadeInDuration.coerceAtLeast(50L)
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

            // S-Curve (smoothstep: t * t * (3 - 2t))
            val smooth = progress * progress * (3f - 2f * progress)
            player.volume = smooth * userVolume

            if (progress < 1f) {
                handler.postDelayed(this, 15)
            } else {
                player.volume = userVolume
                isCrossfading = false
            }
        }
    }

    fun attachActivePlayer(player: ExoPlayer, volume: Float) {
        activePlayer = player
        userVolume = volume
        if (!isCrossfading) {
            player.volume = volume
        }
    }

    fun setVolume(vol: Float) {
        userVolume = vol
        if (!isCrossfading) {
            activePlayer?.volume = vol
        }
    }

    /** Preload stub (handled natively by ExoPlayer) */
    fun preloadNext(nextItem: MediaItem) {
        // Handled natively by ExoPlayer buffer
    }

    /** Smooth S-curve volume fade out during the last N ms of a track */
    fun applyFadeOut(remainingMs: Long, customDurationMs: Long = fadeOutDurationMs) {
        val player = activePlayer ?: return
        val duration = customDurationMs.coerceAtLeast(50L)
        val progress = (remainingMs.toFloat() / duration).coerceIn(0f, 1f)
        val smooth = progress * progress * (3f - 2f * progress)
        player.volume = smooth * userVolume
    }

    /** Smooth S-curve volume fade in at the beginning of the next track */
    fun startFadeIn(customDurationMs: Long = fadeInDurationMs) {
        val player = activePlayer ?: return
        currentFadeInDuration = customDurationMs.coerceAtLeast(50L)
        isCrossfading = true
        crossfadeStartTime = SystemClock.elapsedRealtime()
        player.volume = 0f
        handler.removeCallbacks(fadeInTicker)
        handler.post(fadeInTicker)
    }

    fun cancelCrossfade() {
        handler.removeCallbacks(fadeInTicker)
        isCrossfading = false
        activePlayer?.volume = userVolume
    }

    fun release() {
        handler.removeCallbacks(fadeInTicker)
        isCrossfading = false
        activePlayer = null
    }
}
