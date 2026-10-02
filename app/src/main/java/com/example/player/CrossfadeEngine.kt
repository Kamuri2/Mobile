package com.example.player

import android.content.Context
import android.os.SystemClock
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlin.math.pow

/**
 * Ultra-Fast Gapless Crossfade Engine (100ms) with Media3 / ExoPlayer.
 *
 * Implements the professional gapless crossfade standard:
 * 1. Preload: Prepares the incoming player into STATE_READY before the crossfade starts,
 *    eliminating decoder buffer lag and audio clicks/pops.
 * 2. Exact Synchronization: Synchronizes the incoming player with seekTo(0) and play()
 *    at the precise moment (timeRemaining - crossfadeDurationMs).
 * 3. Exponential / Cubic Power Curve:
 *    - Outgoing: (1 - progress)^3 * userVolume (maintains energy and drops sharply at end to prevent digital cut)
 *    - Incoming: progress^0.5 * userVolume (rises smoothly and immediately)
 * 4. Dual-Player Handover: Alternates between playerA and playerB without gaps or silence.
 */
class CrossfadeEngine(
    private val context: Context,
    var crossfadeDurationMs: Long = 100L,
    var fadeOutDurationMs: Long = 100L,
    var fadeInDurationMs: Long = 100L
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var isCrossfading = false
        private set

    var isPreloading = false
        private set

    private var crossfadeJob: Job? = null
    private var preloadJob: Job? = null
    private var userVolume = 1f
    private var activePlayer: ExoPlayer? = null

    fun attachActivePlayer(player: ExoPlayer, volume: Float) {
        activePlayer = player
        userVolume = volume
        if (!isCrossfading && !isPreloading) {
            player.volume = volume
        }
    }

    fun setVolume(vol: Float) {
        userVolume = vol
        if (!isCrossfading && !isPreloading) {
            activePlayer?.volume = vol
        }
    }

    /**
     * Executes the ultra-fast 100ms crossfade between outgoing and incoming player.
     * 10 steps of 10ms = 100ms using exponential cubic/sqrt curve.
     */
    suspend fun performCrossfade(
        playerOut: ExoPlayer?,
        playerIn: ExoPlayer,
        durationMs: Long = crossfadeDurationMs
    ) {
        val steps = 10
        val stepDelay = (durationMs / steps).coerceAtLeast(5L)

        for (i in 0..steps) {
            val progress = (i.toFloat() / steps).coerceIn(0f, 1f)

            // Curva Exponencial para evitar clics y cortes:
            // Outgoing: (1 - p)^3 mantiene volumen alto y cae en los últimos 20ms
            // Incoming: p^0.5 sube rápido para evitar baches de energía sonora
            val outVol = ((1f - progress).toDouble().pow(3.0).toFloat() * userVolume).coerceIn(0f, 1f)
            val inVol = (progress.toDouble().pow(0.5).toFloat() * userVolume).coerceIn(0f, 1f)

            playerOut?.volume = outVol
            playerIn.volume = inVol

            delay(stepDelay)
        }

        // Limpieza del reproductor saliente
        playerOut?.volume = 0f
        playerOut?.stop()
        playerOut?.clearMediaItems()

        // Reproductor entrante a volumen completo del usuario
        playerIn.volume = userVolume
        activePlayer = playerIn
        isCrossfading = false
    }

    /**
     * Preloads and executes seamless crossfade for manual track changes (next, prev, playlist click).
     */
    fun startManualCrossfade(
        outgoingPlayer: ExoPlayer?,
        incomingPlayer: ExoPlayer,
        durationMs: Long = crossfadeDurationMs,
        volume: Float = userVolume,
        onBufferReady: () -> Unit,
        onComplete: () -> Unit
    ) {
        cancelCrossfade()
        userVolume = volume
        isCrossfading = true

        crossfadeJob = scope.launch {
            // 1. Esperar a que el buffer esté listo y en estado STATE_READY (sin clics)
            val startWait = SystemClock.elapsedRealtime()
            while (incomingPlayer.playbackState == ExoPlayer.STATE_BUFFERING) {
                if (SystemClock.elapsedRealtime() - startWait > 400) break
                delay(5)
            }

            // 2. Notificar que el buffer está listo para que la UI se actualice
            onBufferReady()

            // 3. Sincronización exacta e inicio simultáneo
            incomingPlayer.seekTo(0)
            incomingPlayer.volume = 0f
            incomingPlayer.playWhenReady = true
            incomingPlayer.play()

            // 4. Ejecutar el cruce de volumen en 100ms
            performCrossfade(outgoingPlayer, incomingPlayer, durationMs)

            onComplete()
        }
    }

    /**
     * Preloads next track when approaching end of current song and executes gapless crossfade.
     */
    fun initiateGaplessCrossfade(
        outgoingPlayer: ExoPlayer,
        incomingPlayer: ExoPlayer,
        durationMs: Long = crossfadeDurationMs,
        volume: Float = userVolume,
        onSyncMoment: () -> Unit,
        onComplete: () -> Unit
    ) {
        if (isCrossfading || isPreloading) return
        isPreloading = true
        userVolume = volume

        preloadJob = scope.launch {
            // 1. Esperar a que incomingPlayer alcance STATE_READY (silenciosamente)
            val startWait = SystemClock.elapsedRealtime()
            while (incomingPlayer.playbackState == ExoPlayer.STATE_BUFFERING) {
                if (SystemClock.elapsedRealtime() - startWait > 500) break
                delay(5)
            }

            // 2. Obtener cuánto falta para el final de la canción actual
            val timeRemaining = outgoingPlayer.duration - outgoingPlayer.currentPosition

            if (timeRemaining < 30) {
                // Fallback instantáneo si ya terminó
                performInstantSwitch(outgoingPlayer, incomingPlayer)
                onSyncMoment()
                onComplete()
                isPreloading = false
                return@launch
            }

            // 3. Esperar el momento justo para el inicio del crossfade: (timeRemaining - durationMs)
            val delayBeforeSync = maxOf(0L, timeRemaining - durationMs)
            if (delayBeforeSync > 0L) {
                delay(delayBeforeSync)
            }

            // 4. Momento de la verdad: Sincronización Exacta
            isPreloading = false
            isCrossfading = true

            onSyncMoment()

            incomingPlayer.seekTo(0)
            incomingPlayer.volume = 0f
            incomingPlayer.playWhenReady = true
            incomingPlayer.play()

            // 5. Ejecutar cruce de 100ms
            performCrossfade(outgoingPlayer, incomingPlayer, durationMs)

            onComplete()
        }
    }

    fun performInstantSwitch(playerOut: ExoPlayer?, playerIn: ExoPlayer) {
        playerOut?.volume = 0f
        playerOut?.stop()
        playerOut?.clearMediaItems()
        playerIn.volume = userVolume
        playerIn.playWhenReady = true
        playerIn.play()
        activePlayer = playerIn
        isCrossfading = false
        isPreloading = false
    }

    fun startFadeIn(player: ExoPlayer? = activePlayer, customDurationMs: Long = fadeInDurationMs) {
        val p = player ?: return
        cancelCrossfade()
        val duration = customDurationMs.coerceAtLeast(50L)
        val steps = 10
        val stepDelay = (duration / steps).coerceAtLeast(5L)
        p.volume = 0f

        crossfadeJob = scope.launch {
            for (i in 0..steps) {
                val progress = (i.toFloat() / steps).coerceIn(0f, 1f)
                val inVol = (progress.toDouble().pow(0.5).toFloat() * userVolume).coerceIn(0f, 1f)
                p.volume = inVol
                delay(stepDelay)
            }
            p.volume = userVolume
        }
    }

    fun applyFadeOut(remainingMs: Long, customDurationMs: Long = fadeOutDurationMs, player: ExoPlayer? = activePlayer) {
        val p = player ?: return
        val duration = customDurationMs.coerceAtLeast(50L)
        val progress = (remainingMs.toFloat() / duration).coerceIn(0f, 1f)
        val outVol = (progress.toDouble().pow(3.0).toFloat() * userVolume).coerceIn(0f, 1f)
        p.volume = outVol
    }

    fun cancelCrossfade() {
        crossfadeJob?.cancel()
        preloadJob?.cancel()
        crossfadeJob = null
        preloadJob = null
        isCrossfading = false
        isPreloading = false
        activePlayer?.volume = userVolume
    }

    fun release() {
        cancelCrossfade()
        scope.cancel()
        activePlayer = null
    }
}
