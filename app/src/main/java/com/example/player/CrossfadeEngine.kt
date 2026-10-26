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
    var crossfadeDurationMs: Long = 200L,
    var fadeOutDurationMs: Long = 200L,
    var fadeInDurationMs: Long = 200L
) {
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun ensureActiveScope(): CoroutineScope {
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }
        return scope
    }

    var isCrossfading = false
        private set

    var isPreloading = false
        private set

    private var crossfadeJob: Job? = null
    private var preloadJob: Job? = null
    private var userVolume = 1f
    private var activePlayer: ExoPlayer? = null
    private var currentOutgoingPlayer: ExoPlayer? = null
    private var currentIncomingPlayer: ExoPlayer? = null

    private var currentTransitionId = 0L

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
     * Executes the gradual, calculated crossfade between outgoing and incoming player.
     * Uses equal-power sinusoidal curves (cos/sin) to eliminate audio dips according to settings.
     */
    suspend fun performCrossfade(
        playerOut: ExoPlayer?,
        playerIn: ExoPlayer,
        durationMs: Long = crossfadeDurationMs,
        transitionId: Long = currentTransitionId
    ) {
        playerOut?.repeatMode = ExoPlayer.REPEAT_MODE_OFF
        playerIn.repeatMode = ExoPlayer.REPEAT_MODE_OFF
        currentOutgoingPlayer = playerOut
        currentIncomingPlayer = playerIn

        val actualDur = durationMs.coerceIn(0L, 5000L)
        if (actualDur <= 40L) {
            performInstantSwitch(playerOut, playerIn)
            return
        }

        val stepDelayMs = 15L
        val steps = (actualDur / stepDelayMs).toInt().coerceIn(5, 50)
        val actualStepDelay = (actualDur / steps).coerceAtLeast(8L)

        try {
            for (i in 0..steps) {
                if (transitionId != currentTransitionId) return

                val progress = (i.toFloat() / steps).coerceIn(0f, 1f)

                // Advanced Frequency-Balanced Equal-Power Crossfade:
                val angle = progress * (Math.PI / 2.0)
                val cosVal = kotlin.math.cos(angle)
                val sinVal = kotlin.math.sin(angle)

                val outFreqFactor = (1.0 - 0.22 * progress.toDouble().pow(1.6))
                val inFreqFactor = (0.86 + 0.14 * progress.toDouble().pow(0.5))

                val outVol = ((cosVal * outFreqFactor).toFloat() * userVolume).coerceIn(0f, 1f)
                val inVol = ((sinVal * inFreqFactor).toFloat() * userVolume).coerceIn(0f, 1f)

                if (playerOut != null && playerOut.playbackState != ExoPlayer.STATE_ENDED) {
                    playerOut.volume = outVol
                } else {
                    playerOut?.volume = 0f
                }
                playerIn.volume = inVol

                delay(actualStepDelay)
            }
        } finally {
            // ONLY perform cleanup if this is STILL the active transition!
            if (transitionId == currentTransitionId) {
                try {
                    playerOut?.volume = 0f
                    playerOut?.stop()
                    playerOut?.clearMediaItems()
                } catch (_: Exception) {}

                playerIn.volume = userVolume
                activePlayer = playerIn
                isCrossfading = false
                currentOutgoingPlayer = null
                currentIncomingPlayer = null
            }
        }
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
        val transitionId = ++currentTransitionId
        crossfadeJob?.cancel()
        preloadJob?.cancel()
        crossfadeJob = null
        preloadJob = null

        val actualDur = durationMs.coerceAtLeast(0L)

        // If instant switch (0ms / gapless), immediately stop outgoing
        if (actualDur <= 40L) {
            performInstantSwitch(outgoingPlayer, incomingPlayer)
            isCrossfading = false
            onComplete()
            return
        }

        currentOutgoingPlayer = outgoingPlayer
        currentIncomingPlayer = incomingPlayer
        userVolume = volume
        activePlayer = incomingPlayer
        isCrossfading = true

        // 1. Activate incoming player and switch UI state immediately
        onBufferReady()

        // 2. Start incoming player smoothly from 0 volume
        try {
            incomingPlayer.seekTo(0)
            incomingPlayer.volume = 0f
            incomingPlayer.playWhenReady = true
            incomingPlayer.play()
        } catch (_: Exception) {}

        crossfadeJob = ensureActiveScope().launch {
            try {
                performCrossfade(outgoingPlayer, incomingPlayer, actualDur, transitionId)
            } finally {
                if (transitionId == currentTransitionId) {
                    incomingPlayer.volume = userVolume
                    isCrossfading = false
                    onComplete()
                }
            }
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
        currentOutgoingPlayer = outgoingPlayer
        currentIncomingPlayer = incomingPlayer
        userVolume = volume

        preloadJob = ensureActiveScope().launch {
            try {
                // 1. Wait silently for incomingPlayer to reach STATE_READY (up to 3000ms max)
                val startWait = SystemClock.elapsedRealtime()
                while (incomingPlayer.playbackState == ExoPlayer.STATE_BUFFERING) {
                    if (SystemClock.elapsedRealtime() - startWait > 3000) break
                    delay(20)
                }

                // 2. Wait actively for the exact moment: (timeRemaining <= durationMs)
                val waitStartActive = SystemClock.elapsedRealtime()
                while (true) {
                    if (!outgoingPlayer.playWhenReady && !outgoingPlayer.isPlaying) {
                        delay(50)
                        if (SystemClock.elapsedRealtime() - waitStartActive > 5000) break
                        continue
                    }
                    if (outgoingPlayer.playbackState == ExoPlayer.STATE_ENDED) {
                        break
                    }
                    val dur = outgoingPlayer.duration
                    val pos = outgoingPlayer.currentPosition
                    if (dur > 0L) {
                        val rem = dur - pos
                        if (rem <= durationMs) {
                            break
                        }
                    } else {
                        break
                    }
                    delay(20)
                }

                // 3. Exact handover moment
                isPreloading = false
                isCrossfading = true

                onSyncMoment()

                incomingPlayer.seekTo(0)
                incomingPlayer.volume = 0f
                incomingPlayer.playWhenReady = true
                incomingPlayer.play()

                // 4. Calculate actual duration based on remaining track duration
                val actualRemaining = if (outgoingPlayer.duration > 0L && outgoingPlayer.playbackState != ExoPlayer.STATE_ENDED) {
                    (outgoingPlayer.duration - outgoingPlayer.currentPosition).coerceAtLeast(100L)
                } else durationMs
                val effectiveDuration = maxOf(durationMs, minOf(durationMs, actualRemaining))

                performCrossfade(outgoingPlayer, incomingPlayer, effectiveDuration)

                onComplete()
            } catch (e: Exception) {
                // Ensure state is never locked
                isPreloading = false
                isCrossfading = false
                outgoingPlayer.volume = userVolume
                incomingPlayer.volume = userVolume
            } finally {
                isPreloading = false
                isCrossfading = false
            }
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
        currentOutgoingPlayer = null
        currentIncomingPlayer = null
    }

    fun startFadeIn(player: ExoPlayer? = activePlayer, customDurationMs: Long = fadeInDurationMs) {
        val p = player ?: return
        cancelCrossfade()
        val duration = customDurationMs.coerceAtLeast(50L)
        val steps = 10
        val stepDelay = (duration / steps).coerceAtLeast(5L)
        p.volume = 0.05f * userVolume

        crossfadeJob = ensureActiveScope().launch {
            try {
                for (i in 0..steps) {
                    val progress = (i.toFloat() / steps).coerceIn(0f, 1f)
                    val inVol = (progress.toDouble().pow(0.5).toFloat() * userVolume).coerceIn(0f, 1f)
                    p.volume = inVol
                    delay(stepDelay)
                }
            } finally {
                p.volume = userVolume
            }
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
        ++currentTransitionId
        crossfadeJob?.cancel()
        preloadJob?.cancel()
        crossfadeJob = null
        preloadJob = null
        isCrossfading = false
        isPreloading = false

        currentOutgoingPlayer = null
        currentIncomingPlayer = null
        activePlayer?.volume = userVolume
    }

    fun release() {
        cancelCrossfade()
        activePlayer = null
    }
}
