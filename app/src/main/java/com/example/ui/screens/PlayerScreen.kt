package com.example.ui.screens

import androidx.compose.ui.platform.LocalContext
import com.example.player.AudioPlayerManager
import androidx.compose.material3.MaterialTheme
import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbDownOffAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.LrcLine
import com.example.data.LrcParser
import com.example.model.Track
import com.example.player.LoopMode
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.ArtistInfoCard
import com.example.ui.components.ArtistInfoTab
import com.example.ui.components.WaveformSeekBar
import androidx.compose.ui.zIndex
import com.example.ui.theme.GlassTextMuted
import com.example.ui.theme.GlassTextMutedDark
import com.example.ui.theme.GlassTextSecondary
import com.example.ui.theme.GlassTextSecondaryDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%d:%02d", mins, secs)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    settings: com.example.model.AppSettings,
    currentTrack: Track?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    isShuffle: Boolean,
    loopMode: LoopMode,
    isFavorite: Boolean,
    isDisliked: Boolean,
    playlists: List<com.example.data.local.PlaylistEntity> = emptyList(),
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffleToggle: () -> Unit,
    onLoopCycle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDislikeToggle: () -> Unit,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: (Long) -> Unit = {},
    onCreatePlaylistAndAdd: (String) -> Unit = {},
    onNavigateToArtist: ((String) -> Unit)? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showLyricsMode by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val isDarkMode = settings.isDarkMode
    val primaryTextColor = if (isDarkMode) Color.White else Color(0xFF111111)
    val secondaryTextColor = if (isDarkMode) GlassTextSecondaryDark else Color(0xFF4A4A4A)
    val mutedTextColor = if (isDarkMode) GlassTextMutedDark else Color(0xFF757575)
    val iconTintColor = if (isDarkMode) Color.White else Color.Black

    val swipeOffsetY = remember { androidx.compose.animation.core.Animatable(0f) }
    var isClosingByGesture by remember { mutableStateOf(false) }

    fun triggerDismissDown() {
        if (isClosingByGesture) return
        isClosingByGesture = true
        coroutineScope.launch {
            try {
                swipeOffsetY.animateTo(
                    targetValue = screenHeightPx,
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                )
            } catch (_: Exception) {}
            onBack()
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (showLyricsMode) {
            showLyricsMode = false
        } else {
            triggerDismissDown()
        }
    }

    LaunchedEffect(Unit) {
        swipeOffsetY.snapTo(0f)
        isClosingByGesture = false
    }

    val context = LocalContext.current
    val playerManager = remember { AudioPlayerManager.getInstance(context) }

    val trackId = currentTrack?.id ?: -1L
    var oldTrackId by remember { mutableStateOf(-1L) }
    var slideDirection by remember { mutableStateOf(1) }
    var skipTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(trackId) {
        if (oldTrackId != -1L && oldTrackId != trackId) {
            slideDirection = 1
            skipTrigger++
        }
        oldTrackId = trackId
        currentTrack?.let { track ->
            if (track.lyrics.isBlank() || com.example.data.AudioLyricsExtractor.isCorruptedLyrics(track.lyrics)) {
                playerManager.loadLyricsForTrack(track, forceRescan = false)
            }
        }
    }

    var showAddToPlaylistModal by remember { mutableStateOf(false) }
    
    val pipelineArtistInfo by playerManager.currentArtistInfo.collectAsState()
    val pipelineArtistProfiles by playerManager.currentArtistProfiles.collectAsState()
    val isArtistLoading by playerManager.isArtistInfoLoading.collectAsState()

    val artistInfo = pipelineArtistInfo ?: remember(currentTrack?.artist) {
        currentTrack?.artist?.let { com.example.data.ArtistInfoFetcher.getCachedArtistInfo(it) }
    }

    val parsedLyrics = remember(currentTrack?.lyrics) {
        val raw = currentTrack?.lyrics ?: ""
        val cleaned = com.example.data.AudioLyricsExtractor.cleanExtractedLyrics(raw)
        if (cleaned.isNotBlank() && !com.example.data.AudioLyricsExtractor.isCorruptedLyrics(cleaned)) {
            com.example.model.LyricsParser.parseLrc(cleaned)
        } else {
            emptyList()
        }
    }

    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableStateOf(0f) }

    val effectiveDuration = if (durationMs > 0L) durationMs else (currentTrack?.durationMs ?: 0L)
    val displayPositionMs = if (isSeeking) (seekFraction * effectiveDuration).toLong() else currentPositionMs
    val currentProgress = if (isSeeking) seekFraction else (if (effectiveDuration > 0L) (currentPositionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f) else 0f)

    // artworkBitmap removed in favor of TrackImage

    val formatTag = remember(currentTrack) {
        if (currentTrack == null) "FLAC  988 KBPS  44.1 KHZ"
        else {
            val extension = when {
                currentTrack.path.endsWith(".flac", true) -> "FLAC"
                currentTrack.path.endsWith(".mp3", true) -> "MP3"
                currentTrack.path.endsWith(".m4a", true) || currentTrack.path.endsWith(".aac", true) -> "AAC"
                currentTrack.path.endsWith(".wav", true) -> "WAV"
                currentTrack.path.endsWith(".opus", true) -> "OPUS"
                currentTrack.path.endsWith(".ogg", true) -> "OGG"
                else -> "FLAC"
            }
            // Use fallback strings if bitrate/samplerate properties don't exist
            "$extension  |  ${currentTrack.fileSizeFormatted}  |  44.1 KHZ"
        }
    }

    val dominantColor = com.example.data.ArtworkExtractor.rememberTrackDominantColor(currentTrack, MaterialTheme.colorScheme.primary)
    val currentOffsetY = swipeOffsetY.value
    val progress = (currentOffsetY / screenHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val maxScrim = if (isDarkMode) 0.65f else 0.35f
    val scrimAlpha = (maxScrim * (1f - progress)).coerceIn(0f, maxScrim)
    val topCornerRadius = (progress * 28f).dp
    val playerAlpha = if (progress <= 0.001f) 1f else (1f - (progress * 0.4f)).coerceIn(0.6f, 1f)
    val solidBaseColor = if (isDarkMode) com.example.ui.theme.LiquidDarkBg else Color(0xFFF7F8FA)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (progress > 0f) Color.Black.copy(alpha = scrimAlpha) else solidBaseColor)
    ) {
        LiquidGlassBackground(
            isPlaying = isPlaying,
            currentTrack = currentTrack,
            primaryGlowColor = dominantColor,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, currentOffsetY.roundToInt()) }
                .graphicsLayer {
                    alpha = playerAlpha
                }
                .clip(RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            if (isLandscape) {
            // ==================== LANDSCAPE MODE ====================
            Box(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { triggerDismissDown() },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).testTag("close_player_landscape")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = com.example.ui.Translations.get(settings.appLanguage, "swipe_down_to_close"),
                        tint = iconTintColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // LEFT SIDE: Cover, Title, Artist - Album, and Actions
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Artwork Cover
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.91f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1AFFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentTrack != null) {
                            com.example.ui.components.PlayerAlbumArt(track = currentTrack, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(96.dp))
                        }
                        
                        // Action Icons Row overlaid at the bottom of the album art
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.70f))
                                    )
                                )
                                .padding(vertical = 8.dp, horizontal = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onFavoriteToggle, modifier = Modifier.testTag("player_favorite_btn")) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(onClick = onDislikeToggle) {
                                Icon(
                                    imageVector = if (isDisliked) Icons.Default.ThumbDown else Icons.Default.ThumbDownOffAlt,
                                    contentDescription = "Dislike",
                                    tint = if (isDisliked) Color(0xFFEF4444) else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(onClick = { showAddToPlaylistModal = true }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add to playlist",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            // Lyrics Mic Icon Toggle
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (showLyricsMode) Color.White.copy(alpha = 0.35f) else Color.Transparent)
                                    .clickable { showLyricsMode = !showLyricsMode },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Lyrics",
                                    tint = if (showLyricsMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(onClick = onOpenQueue, modifier = Modifier.testTag("player_queue_btn")) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = "Queue",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Song Title & Artist - Album
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    ) {
                        val noTrackText = com.example.ui.Translations.get(settings.appLanguage, "no_track_playing")
                        val unknownArtistText = com.example.ui.Translations.get(settings.appLanguage, "unknown_artist")
                        val unknownAlbumText = com.example.ui.Translations.get(settings.appLanguage, "unknown_album")
                        Text(
                            text = currentTrack?.title ?: noTrackText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${currentTrack?.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: unknownArtistText} - ${currentTrack?.album?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: unknownAlbumText}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                }
                // RIGHT SIDE: Playback Controls OR Lyrics Display
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!showLyricsMode) {
                        // Playback Controls View in Landscape
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Main Playback Controls Row (Rewind 10s, Previous, Big Play/Pause, Next, Forward 10s)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { onSeek((currentPositionMs - 10000L).coerceAtLeast(0L)) }) {
                                    Icon(
                                        imageVector = Icons.Default.FastRewind,
                                        contentDescription = "Rewind 10s",
                                        tint = iconTintColor.copy(alpha = 0.75f),
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            Spacer(modifier = Modifier.height(24.dp))
                                IconButton(onClick = { slideDirection = -1; onPrevious() }) {
                                    Icon(
                                        imageVector = Icons.Default.FastRewind,
                                        contentDescription = "Previous",
                                        tint = iconTintColor,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                                // Play/Pause Button (White in dark mode, Black in light mode, NO circle)
                                IconButton(
                                    onClick = onPlayPauseToggle,
                                    modifier = Modifier
                                        .size(68.dp)
                                        .testTag("player_main_play_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = iconTintColor,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                                IconButton(onClick = { slideDirection = 1; onNext() }) {
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = "Next",
                                        tint = iconTintColor,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                                IconButton(onClick = { onSeek(currentPositionMs + 10000L) }) {
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = "Forward 10s",
                                        tint = iconTintColor.copy(alpha = 0.75f),
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }

                            // Waveform Progress Bar with Audio Soundwaves
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                WaveformSeekBar(
                                    progress = currentProgress,
                                    onProgressChange = { fraction ->
                                        isSeeking = true
                                        seekFraction = fraction
                                    },
                                    onProgressChangeFinished = {
                                        isSeeking = false
                                        val target = (seekFraction * effectiveDuration).toLong().coerceIn(0L, effectiveDuration)
                                        onSeek(target)
                                    },
                                    isPlaying = isPlaying,
                                    trackId = currentTrack?.id ?: 0L,
                                    activeColor = MaterialTheme.colorScheme.primary,
                                    height = 42.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatDuration(displayPositionMs),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = GlassTextMuted
                                    )
                                    Text(
                                        text = formatDuration(durationMs),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = GlassTextMuted
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))


                            // Secondary Control Icons (Mic/Lyrics, Repeat, Shuffle)
                            Row(
                                modifier = Modifier.fillMaxWidth(0.85f),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onShuffleToggle) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = if (isShuffle) MaterialTheme.colorScheme.primary else (if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Lyrics Toggle Button (Mic)
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(if (showLyricsMode) (if (isDarkMode) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f)) else Color.Transparent)
                                        .clickable { showLyricsMode = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Lyrics",
                                        tint = if (showLyricsMode) MaterialTheme.colorScheme.primary else (if (isDarkMode) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f)),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                IconButton(onClick = onLoopCycle) {
                                    Icon(
                                        imageVector = if (loopMode == LoopMode.REPEAT_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                        contentDescription = "Repeat",
                                        tint = if (loopMode != LoopMode.OFF) MaterialTheme.colorScheme.primary else (if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Lyrics View replacing Controls on Right Side in Landscape
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0x1A000000))
                                .padding(12.dp)
                        ) {
                            // Header of Lyrics View with Close Button to return to controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = com.example.ui.Translations.get(settings.appLanguage, "lyrics"),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryTextColor
                                    )
                                }

                                IconButton(
                                    onClick = { showLyricsMode = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = com.example.ui.Translations.get(settings.appLanguage, "close_lyrics"),
                                        tint = iconTintColor.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Lyrics Content Display
                            LyricsContent(
                                parsedLyrics = parsedLyrics,
                                rawLyrics = currentTrack?.lyrics ?: "",
                                currentPositionMs = currentPositionMs,
                                language = settings.appLanguage,
                                onSeek = onSeek,
                                onRetry = {
                                    currentTrack?.let { AudioPlayerManager.getInstance(context).retryScanTrackExtras(it) }
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                    }
                }
            }
            }
        } else {
            // ==================== PORTRAIT MODE ====================
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val totalScreenHeight = maxHeight
                val totalScreenWidth = maxWidth
                val portraitScrollState = rememberScrollState()
                val density = LocalDensity.current
                var lastTrackId by remember { mutableStateOf<Long?>(null) }
                LaunchedEffect(currentTrack?.id) {
                    if (currentTrack?.id != null && lastTrackId != null && currentTrack.id != lastTrackId) {
                        portraitScrollState.scrollTo(0)
                    }
                    lastTrackId = currentTrack?.id
                }

                // Cover size increased by an additional 1 percent (0.928f -> 0.938f)
                val cardWidthFraction = 0.938f
                val albumArtWidth = totalScreenWidth * cardWidthFraction
                // Standard 1:1 aspect ratio square cover
                val albumArtHeight = albumArtWidth
                val lyricsCardHeight = (totalScreenHeight * 0.58f).coerceAtLeast(albumArtWidth + 40.dp)
                // Breathing space above cover
                val coverTopSpacing = (totalScreenHeight * 0.040f).coerceIn(24.dp, 36.dp)

                // Spring physics for natural bounce in and bounce out
                val bounceSpring = spring<Dp>(
                    dampingRatio = 0.68f, // Smooth, natural bounce
                    stiffness = 320f     // Responsive physics
                )

                val animatedCardHeight by animateDpAsState(
                    targetValue = if (showLyricsMode) lyricsCardHeight else albumArtHeight,
                    animationSpec = bounceSpring,
                    label = "CardBounceHeight"
                )

                // Enhanced breathing space above seekbar for visual acuity
                val seekbarTopSpace by animateDpAsState(
                    targetValue = if (showLyricsMode) 12.dp else 24.dp,
                    animationSpec = bounceSpring,
                    label = "SeekbarTopSpaceBounce"
                )

                val screenHeightPx = with(density) { totalScreenHeight.toPx() }
                val dismissThresholdPx = with(density) { 95.dp.toPx() }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(portraitScrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(screenHeightPx, dismissThresholdPx) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (swipeOffsetY.value > dismissThresholdPx) {
                                            triggerDismissDown()
                                        } else {
                                            coroutineScope.launch {
                                                swipeOffsetY.animateTo(0f, animationSpec = tween(180))
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        if (swipeOffsetY.value > dismissThresholdPx) {
                                            triggerDismissDown()
                                        } else {
                                            coroutineScope.launch {
                                                swipeOffsetY.animateTo(0f, animationSpec = tween(180))
                                            }
                                        }
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        if (dragAmount > 0 || swipeOffsetY.value > 0f) {
                                            change.consume()
                                            coroutineScope.launch {
                                                val newOffset = (swipeOffsetY.value + dragAmount).coerceAtLeast(0f)
                                                swipeOffsetY.snapTo(newOffset)
                                            }
                                        }
                                    }
                                )
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Header with Swipe Down Handle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(if (isDarkMode) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.35f))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null
                                        ) { triggerDismissDown() }
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = com.example.ui.Translations.get(settings.appLanguage, "close"),
                                        tint = mutedTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = com.example.ui.Translations.get(settings.appLanguage, "playing_now"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = mutedTextColor,
                                        letterSpacing = 1.5.sp
                                    )
                                }
                            }
                        }

                        // Breathing space down to cover lowered on screen
                        Spacer(modifier = Modifier.height(coverTopSpacing))

                AnimatedContent(
                    targetState = currentTrack,
                    transitionSpec = {
                        val duration = 220
                        if (slideDirection >= 0) {
                            (slideInHorizontally(androidx.compose.animation.core.tween(duration, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { width -> width / 3 } + fadeIn(androidx.compose.animation.core.tween(duration)))
                                .togetherWith(slideOutHorizontally(androidx.compose.animation.core.tween(duration, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { width -> -width / 3 } + fadeOut(androidx.compose.animation.core.tween(duration)))
                        } else {
                            (slideInHorizontally(androidx.compose.animation.core.tween(duration, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { width -> -width / 3 } + fadeIn(androidx.compose.animation.core.tween(duration)))
                                .togetherWith(slideOutHorizontally(androidx.compose.animation.core.tween(duration, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { width -> width / 3 } + fadeOut(androidx.compose.animation.core.tween(duration)))
                        }
                    },
                    label = "Track Transition",
                    modifier = Modifier.fillMaxWidth()
                ) { targetTrack ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .width(albumArtWidth)
                                .height(animatedCardHeight)
                                .clip(RoundedCornerShape(22.dp))
                                .background(if (isDarkMode) Color(0x18FFFFFF) else Color(0x0C000000))
                        ) {
                            // ==================== ALBUM ART MODE ====================
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !showLyricsMode,
                                enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)),
                                exit = fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .align(Alignment.Center),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (targetTrack != null) {
                                            com.example.ui.components.PlayerAlbumArt(
                                                track = targetTrack,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(110.dp)
                                            )
                                        }
                                    }

                                    // Action Icons Row overlaid at the bottom corners of the album art (with circular frosted glass pills so no artwork is obscured or cropped)
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp, horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.38f))
                                                .clickable { showAddToPlaylistModal = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Add to playlist",
                                                tint = Color.White.copy(alpha = 0.95f),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        // Lyrics Mic Icon Toggle (Click to open lyrics with bounce in)
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.38f))
                                                .clickable { showLyricsMode = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "Lyrics",
                                                tint = Color.White.copy(alpha = 0.95f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // ==================== EXPANDED LYRICS VIEW MODE ====================
                            // Fills the green box marked by the user
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showLyricsMode,
                                enter = fadeIn(animationSpec = tween(220, delayMillis = 40, easing = FastOutSlowInEasing)),
                                exit = fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Full-height scrolling lyrics
                                    LyricsContent(
                                        parsedLyrics = parsedLyrics,
                                        rawLyrics = targetTrack?.lyrics ?: "",
                                        currentPositionMs = currentPositionMs,
                                        language = settings.appLanguage,
                                        onSeek = onSeek,
                                        onRetry = {
                                            targetTrack?.let { AudioPlayerManager.getInstance(context).retryScanTrackExtras(it) }
                                        },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 82.dp)
                                    )

                                    // Action Icons and Song Info Row overlaid at the bottom of the expanded lyrics card
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(
                                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.18f),
                                                        Color.Black.copy(alpha = 0.35f)
                                                    )
                                                )
                                            )
                                            .padding(top = 10.dp, bottom = 8.dp, start = 12.dp, end = 12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = targetTrack?.title ?: "",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = primaryTextColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = targetTrack?.artist ?: "",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = secondaryTextColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(onClick = { showAddToPlaylistModal = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add to playlist",
                                                    tint = iconTintColor.copy(alpha = 0.9f),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }

                                            // Active Lyrics Mic Icon (Click to close lyrics with bounce out)
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                                    .clickable { showLyricsMode = false },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Mic,
                                                    contentDescription = "Lyrics",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // When in album art mode, show centered title/artist and live lyric snippet below
                androidx.compose.animation.AnimatedVisibility(
                    visible = !showLyricsMode,
                    enter = fadeIn(animationSpec = tween(220)) + expandVertically(
                        animationSpec = spring(dampingRatio = 0.68f, stiffness = 320f)
                    ),
                    exit = fadeOut(animationSpec = tween(160)) + shrinkVertically(
                        animationSpec = spring(dampingRatio = 0.68f, stiffness = 320f)
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Breathing space between cover and current playing lyric
                        Spacer(modifier = Modifier.height(14.dp))

                        // Currently playing lyric placed in the middle (between cover and title) matching user reference
                        val activeLyricLine = remember(parsedLyrics, currentPositionMs, currentTrack?.lyrics, durationMs) {
                            if (parsedLyrics.isNotEmpty()) {
                                val idx = parsedLyrics.indexOfLast { it.timestampMs <= currentPositionMs }
                                if (idx != -1) parsedLyrics[idx].text.trim() else parsedLyrics.firstOrNull()?.text?.trim()
                            } else {
                                val raw = currentTrack?.lyrics ?: ""
                                if (raw.isNotBlank() && !com.example.data.AudioLyricsExtractor.isCorruptedLyrics(raw)) {
                                    val cleaned = com.example.data.AudioLyricsExtractor.cleanExtractedLyrics(raw)
                                    val lines = cleaned.lines().map { it.trim() }.filter { line ->
                                        line.isNotBlank() &&
                                        !line.startsWith("[") &&
                                        !line.matches(Regex("""(?i)^\s*(?:ti|ar|al|by|offset|re|ve|length|id|artist|album|title)\s*:.*"""))
                                    }
                                    if (lines.isNotEmpty()) {
                                        val fraction = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 0.99f) else 0f
                                        val idx = (fraction * lines.size).toInt().coerceIn(0, lines.lastIndex)
                                        lines[idx]
                                    } else null
                                } else null
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 28.dp)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) { showLyricsMode = true },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = activeLyricLine,
                                transitionSpec = {
                                    (slideInVertically { h -> h / 2 } + fadeIn())
                                        .togetherWith(slideOutVertically { h -> -h / 2 } + fadeOut())
                                },
                                label = "MiddleLiveLyricSnippet"
                            ) { lyricText ->
                                if (!lyricText.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    ) {
                                        Text(
                                            text = lyricText,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryTextColor.copy(alpha = 0.92f),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val noTrackText = com.example.ui.Translations.get(settings.appLanguage, "no_track_playing")
                        val unknownArtistText = com.example.ui.Translations.get(settings.appLanguage, "unknown_artist")

                        Text(
                            text = currentTrack?.title ?: noTrackText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = currentTrack?.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: unknownArtistText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(seekbarTopSpace))

                // Capsule Progress Bar matching Image 1
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    var isDragging by remember { mutableStateOf(false) }
                    var dragFraction by remember { mutableFloatStateOf(0f) }
                    val effectiveFraction = if (isDragging) dragFraction else currentProgress.coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(13.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (isDarkMode) Color(0x33000000) else Color(0x14000000))
                            .border(1.5.dp, if (isDarkMode) Color(0x66FFFFFF) else Color(0x33000000), RoundedCornerShape(percent = 50))
                            .pointerInput(effectiveDuration) {
                                detectTapGestures { offset ->
                                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    val target = (fraction * effectiveDuration).toLong()
                                    onSeek(target)
                                }
                            }
                            .pointerInput(effectiveDuration) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        isDragging = true
                                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        val target = (dragFraction * effectiveDuration).toLong()
                                        onSeek(target)
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                    }
                                )
                            }
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(effectiveFraction.coerceAtLeast(0.01f))
                                .clip(RoundedCornerShape(percent = 50))
                                .background(if (isDarkMode) Color.White else Color(0xFF1E1E1E))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentMs = if (isDragging) (dragFraction * effectiveDuration).toLong() else displayPositionMs
                        Text(
                            text = formatDuration(currentMs),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor
                        )
                        Text(
                            text = formatDuration(durationMs),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor
                        )
                    }
                }

                // Generous breathing space between seekbar and playback controls
                Spacer(modifier = Modifier.height(26.dp))

                // Playback Controls Row matching reference image:
                // [Heart] [FastRewind <<] [Pause || / Play >] [FastForward >>] [Menu =]
                val controlsBtnColor = if (isDarkMode) Color.White else Color.Black
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Favorite / Heart (Left)
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("player_favorite_btn")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else controlsBtnColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // 2. Previous Track (FastRewind << double-triangle left)
                    IconButton(
                        onClick = { slideDirection = -1; skipTrigger++; onPrevious() },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("previous_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Previous",
                            tint = controlsBtnColor,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // 3. Play / Pause Button (Clean white pause bars || or play triangle > with NO circle or border)
                    IconButton(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("player_main_play_btn")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = controlsBtnColor,
                            modifier = Modifier.size(54.dp)
                        )
                    }

                    // 4. Next Track (FastForward >> double-triangle right)
                    IconButton(
                        onClick = { slideDirection = 1; skipTrigger++; onNext() },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Next",
                            tint = controlsBtnColor,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // 5. Menu / Queue (Right - three horizontal bars)
                    IconButton(
                        onClick = onOpenQueue,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("player_queue_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Queue / Menu",
                            tint = controlsBtnColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } // End of Column with player content

                // Artist Info Card placed at bottom separated generously from controls
                Spacer(modifier = Modifier.height(64.dp))

                if (currentTrack != null) {
                    ArtistInfoCard(
                        track = currentTrack,
                        artistInfo = artistInfo,
                        language = settings.appLanguage,
                        scrollState = portraitScrollState,
                        onNavigateToArtist = onNavigateToArtist,
                        externalProfiles = pipelineArtistProfiles,
                        isLoading = isArtistLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

        // Modal to add song to custom playlists
        if (showAddToPlaylistModal) {
            var showNewPlaylistInput by remember { mutableStateOf(false) }
            var newPlaylistNameInput by remember { mutableStateOf("") }

            ModalBottomSheet(
                onDismissRequest = { showAddToPlaylistModal = false },
                containerColor = if (isDarkMode) Color(0xFF181818) else Color(0xFFFFFFFF)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            com.example.ui.Translations.get(settings.appLanguage, "add_to_playlist"),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                        IconButton(onClick = { showNewPlaylistInput = !showNewPlaylistInput }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create playlist",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (showNewPlaylistInput) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newPlaylistNameInput,
                                onValueChange = { newPlaylistNameInput = it },
                                placeholder = { Text(com.example.ui.Translations.get(settings.appLanguage, "list_name"), color = Color.Gray) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = primaryTextColor,
                                    unfocusedTextColor = primaryTextColor,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newPlaylistNameInput.isNotBlank()) {
                                        onCreatePlaylistAndAdd(newPlaylistNameInput.trim())
                                        newPlaylistNameInput = ""
                                        showNewPlaylistInput = false
                                        showAddToPlaylistModal = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(com.example.ui.Translations.get(settings.appLanguage, "create"), color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val userPlaylists = remember(playlists) {
                        playlists.filter { it.name != "All Songs" }
                    }

                    if (userPlaylists.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = com.example.ui.Translations.get(settings.appLanguage, "no_playlists_yet"),
                                color = GlassTextSecondary,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(userPlaylists.size) { index ->
                                val pl = userPlaylists[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x1FFFFFFF))
                                        .clickable {
                                            onAddToPlaylist(pl.playlistId)
                                            showAddToPlaylistModal = false
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF2A2A2A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (pl.imageUri != null) {
                                            AsyncImage(
                                                model = android.net.Uri.parse(pl.imageUri),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pl.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!pl.description.isNullOrBlank()) {
                                            Text(
                                                text = pl.description,
                                                fontSize = 12.sp,
                                                color = GlassTextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
    }
}

@Composable
fun LiveSyncedLyricSnippet(
    parsedLyrics: List<com.example.model.LyricsLine>,
    rawLyrics: String,
    currentPositionMs: Long,
    durationMs: Long = 0L,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val currentLineText = remember(currentPositionMs, parsedLyrics, rawLyrics, durationMs) {
        if (parsedLyrics.isNotEmpty()) {
            val idx = parsedLyrics.indexOfLast { it.timestampMs <= currentPositionMs }
            val activeIndex = if (idx != -1) idx else 0
            if (activeIndex in parsedLyrics.indices) {
                val candidate = parsedLyrics[activeIndex].text.trim()
                if (!com.example.data.AudioLyricsExtractor.isCorruptedLyrics(candidate)) candidate else ""
            } else ""
        } else if (rawLyrics.isNotBlank() && !com.example.data.AudioLyricsExtractor.isCorruptedLyrics(rawLyrics)) {
            val cleaned = com.example.data.AudioLyricsExtractor.cleanExtractedLyrics(rawLyrics)
            val lines = cleaned.lines().map { it.trim() }.filter { line ->
                line.isNotBlank() &&
                !line.startsWith("[") &&
                !line.matches(Regex("""(?i)^\s*(?:ti|ar|al|by|offset|re|ve|length|id|artist|album|title|composer|lyricist)\s*:.*""")) &&
                !line.matches(Regex("""(?i)^\s*(?:Exact\s*Audio\s*Copy|LAME|Lavf|Frame:|eng\|\||synced\s*by|lyrics\s*by|[0-9a-f]{8}(?:\s+[0-9a-f]{8}){3,}).*"""))
            }
            if (lines.size >= 2) {
                val fraction = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 0.99f) else 0f
                val idx = (fraction * lines.size).toInt().coerceIn(0, lines.lastIndex)
                val lineCandidate = lines[idx]
                if (!com.example.data.AudioLyricsExtractor.isCorruptedLyrics(lineCandidate)) lineCandidate else ""
            } else ""
        } else {
            ""
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (currentLineText.isNotBlank()) {
            AnimatedContent(
                targetState = currentLineText,
                transitionSpec = {
                    (slideInVertically { height -> height / 2 } + fadeIn()).togetherWith(
                        slideOutVertically { height -> -height / 2 } + fadeOut()
                    )
                },
                label = "LiveLyricLine"
            ) { targetLine ->
                Text(
                    text = targetLine,
                    fontSize = 14.5.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isLightMode) Color(0xFF111111) else Color.White.copy(alpha = 0.95f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun NowPlayingLyricsCard(
    track: Track,
    parsedLyrics: List<com.example.model.LyricsLine>,
    currentPositionMs: Long,
    language: String,
    onExpandLyrics: () -> Unit,
    onRetryLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardBg = if (isLightMode) Color(0x0C000000) else Color(0x18FFFFFF)
    val cardBorder = if (isLightMode) Color(0x14000000) else Color(0x22FFFFFF)
    val primaryText = if (isLightMode) Color(0xFF111111) else Color.White
    val secondaryText = if (isLightMode) Color(0xFF555555) else Color.White.copy(alpha = 0.65f)

    val raw = track.lyrics
    val hasLyrics = parsedLyrics.isNotEmpty() || (raw.isNotBlank() && !com.example.data.AudioLyricsExtractor.isCorruptedLyrics(raw))

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable { onExpandLyrics() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: "Lyrics" title and Actions (Retry + Expand)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = com.example.ui.Translations.get(language, "lyrics"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Retry / re-scan button
                    IconButton(
                        onClick = onRetryLyrics,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry lyrics scan",
                            tint = secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Expand button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                            .clickable { onExpandLyrics() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = com.example.ui.Translations.get(language, "song_lyrics_title"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (hasLyrics) {
                if (parsedLyrics.isNotEmpty()) {
                    val activeIndex = parsedLyrics.indexOfLast { it.timestampMs <= currentPositionMs }.coerceAtLeast(0)
                    val startIdx = (activeIndex - 1).coerceAtLeast(0)
                    val endIdx = (activeIndex + 2).coerceAtMost(parsedLyrics.size)
                    val displayLines = parsedLyrics.subList(startIdx, endIdx)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for ((offset, line) in displayLines.withIndex()) {
                            val actualIndex = startIdx + offset
                            val isActive = actualIndex == activeIndex
                            Text(
                                text = line.text,
                                fontSize = if (isActive) 16.sp else 14.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) MaterialTheme.colorScheme.primary else secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }.take(3)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (line in lines) {
                            Text(
                                text = line,
                                fontSize = 14.sp,
                                color = secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = com.example.ui.Translations.get(language, "no_embedded_lyrics"),
                        fontSize = 13.sp,
                        color = secondaryText,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRetryLyrics) {
                        Text(
                            text = com.example.ui.Translations.get(language, "rescan_audio"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsContent(
    parsedLyrics: List<com.example.model.LyricsLine>,
    rawLyrics: String,
    currentPositionMs: Long,
    language: String,
    onSeek: (Long) -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cleanPlainText = remember(rawLyrics) {
        if (rawLyrics.isBlank() || com.example.data.AudioLyricsExtractor.isCorruptedLyrics(rawLyrics)) ""
        else {
            val cleaned = com.example.data.AudioLyricsExtractor.cleanExtractedLyrics(rawLyrics)
            if (cleaned.isNotBlank() && !com.example.data.AudioLyricsExtractor.isCorruptedLyrics(cleaned)) {
                cleaned.lines()
                    .map { it.trim() }
                    .filter { line ->
                        line.isNotBlank() &&
                        !line.matches(Regex("""(?i)^\[\s*[a-zA-Z_ ]{1,24}\s*:[^\]]*\]$""")) &&
                        !line.matches(Regex("""(?i)^\s*(?:ti|ar|al|by|offset|re|ve|length|id|artist|album|title|composer|lyricist|tool)\s*:.*""")) &&
                        !line.matches(Regex("""(?i)^\s*(?:Exact\s*Audio\s*Copy|LAME|Lavf|Frame:|eng\|\||synced\s*by|lyrics\s*by|[0-9a-f]{8}(?:\s+[0-9a-f]{8}){3,}).*"""))
                    }
                    .joinToString("\n")
            } else ""
        }
    }
    if (parsedLyrics.isEmpty()) {
        if (cleanPlainText.isNotBlank()) {
            val scrollState = rememberScrollState()
            Column(
                modifier = modifier
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = cleanPlainText,
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isLightMode) Color(0xFF111111) else Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (isLightMode) Color(0x66000000) else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = com.example.ui.Translations.get(language, "lyrics_not_available"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLightMode) Color(0xFF555555) else Color.White.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center
                    )
                    if (onRetry != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = onRetry,
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isLightMode) MaterialTheme.colorScheme.primary else Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isLightMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (language == "es") "Reintentar extracción" else "Retry extraction",
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    } else {
        val activeIndex = remember(currentPositionMs, parsedLyrics) {
            (if (parsedLyrics.isEmpty()) -1 else { val idx = parsedLyrics.indexOfLast { it.timestampMs <= currentPositionMs }; if (idx != -1) idx else 0 })
        }
        val listState = rememberLazyListState()

        LaunchedEffect(activeIndex) {
            if (activeIndex >= 0 && !listState.isScrollInProgress) {
                val targetIndex = (activeIndex - 2).coerceAtLeast(0)
                listState.animateScrollToItem(targetIndex)
            }
        }

        val inactiveColor = if (isLightMode) Color(0xFF757575) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f)

        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(items = parsedLyrics) { idx, line ->
                val isActive = idx == activeIndex
                val animatedColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isActive) androidx.compose.material3.MaterialTheme.colorScheme.primary else inactiveColor,
                    animationSpec = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    label = "lyrics_color"
                )
                val animatedScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isActive) 1.15f else 0.95f,
                    animationSpec = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    label = "lyrics_scale"
                )
                Text(
                    text = line.text,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = animatedColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSeek(line.timestampMs) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Modern frosted-glass capsule pill button matching liquid glass player aesthetics.
 */
@Composable
private fun GlassCapsuleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 54.dp,
    height: Dp = 38.dp,
    isActive: Boolean = false,
    content: @Composable () -> Unit
) {
    val bgBrush = if (isActive) {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            )
        )
    } else {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(
                Color(0x38FFFFFF),
                Color(0x14FFFFFF)
            )
        )
    }
    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    } else {
        Color(0x40FFFFFF)
    }

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(percent = 50),
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = Color.Black.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(percent = 50))
            .background(bgBrush)
            .border(1.dp, borderColor, RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}