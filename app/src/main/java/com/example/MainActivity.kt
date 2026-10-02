package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.example.ui.components.TrackImage
import com.example.data.AudioScanner

import com.example.data.UserRepository
import com.example.data.SocialRepository
import com.example.ui.screens.SetupScreen
import com.example.ui.screens.UserProfileScreen
import androidx.compose.runtime.collectAsState
import com.example.model.Track
import com.example.player.AudioPlayerManager
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.QueueBottomSheet
import com.example.ui.screens.AlbumsScreen
import com.example.ui.screens.ArtistsScreen
import com.example.ui.screens.FoldersScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.GlassTextMuted
import com.example.ui.theme.GlassTextPrimary
import com.example.ui.theme.GlassTextSecondary
import com.example.ui.theme.LiquidMusicTheme

enum class NavigationScreen {
    HOME, FOLDERS, PLAYLISTS, ALBUMS, ARTISTS, PLAYER, SETTINGS, PROFILE
}

class MainActivity : ComponentActivity() {

    private lateinit var playerManager: AudioPlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        com.example.data.ArtistImageRepository.init(this)
        com.example.data.ArtistInfoFetcher.init(this)
        com.example.data.TrackRepository.init(this)
        enableEdgeToEdge()

        playerManager = AudioPlayerManager.getInstance(applicationContext)

        setContent {
            LiquidMusicApp(playerManager = playerManager)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do not release playerManager here, so music can continue playing in the background
    }
}

@Composable
fun LiquidMusicApp(playerManager: AudioPlayerManager) {
    val context = LocalContext.current
    val appSettings by playerManager.appSettings.collectAsState()

    LiquidMusicTheme(appSettings = appSettings) {
        val coroutineScope = rememberCoroutineScope()
        var currentScreen by remember { mutableStateOf(NavigationScreen.HOME) }
        var initialPlaylist by remember { mutableStateOf<String?>(null) }
        var initialAlbum by remember { mutableStateOf<String?>(null) }
        var initialArtist by remember { mutableStateOf<String?>(null) }
        var isPlayerExpanded by remember { mutableStateOf(false) }
    var isForceRescan by remember { mutableStateOf(false) }
        
    val userRepository = remember { UserRepository.getInstance(context) }
    val socialRepository = remember { SocialRepository.getInstance(context) }
    val userProfile by userRepository.getUserProfile().collectAsState(initial = null)
    
    var isCheckingUser by remember { mutableStateOf(true) }
    var needsSetup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val user = userRepository.getUserProfileSync()
        if (user == null || user.name.isBlank()) {
            needsSetup = true
        }
        isCheckingUser = false
    }

    LaunchedEffect(currentScreen) {
        initialPlaylist = null
        initialAlbum = null
        initialArtist = null
    }

    // Clear old history on start
    LaunchedEffect(Unit) {
        socialRepository.clearOldHistory()
    }

    if (isCheckingUser) {
        return@LiquidMusicTheme
    }

    if (needsSetup) {
        SetupScreen(
            currentLanguage = appSettings.appLanguage,
            onLanguageChange = { playerManager.setAppLanguage(it) },
            onComplete = { needsSetup = false }
        )
        return@LiquidMusicTheme
    }

        val cachedTracks by com.example.data.TrackRepository.tracks.collectAsState()
        var loadedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
        var showQueueSheet by remember { mutableStateOf(false) }

        // Sync with cached tracks immediately on start
        LaunchedEffect(cachedTracks) {
            if (cachedTracks.isNotEmpty()) {
                loadedTracks = cachedTracks
                com.example.data.ArtPreExtractor.startPreExtraction(context, cachedTracks)
                if (playerManager.playlist.value.isEmpty()) {
                    playerManager.setQueue(cachedTracks, 0, false)
                }
            } else {
                com.example.data.ArtPreExtractor.startPreExtraction(context)
            }
        }

    // Player State Collection
    val playlist by playerManager.playlist.collectAsState()
    val currentIndex by playerManager.currentIndex.collectAsState()
    val currentTrack by playerManager.currentTrack.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val currentPositionMs by playerManager.currentPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val isShuffle by playerManager.isShuffle.collectAsState()
    val loopMode by playerManager.loopMode.collectAsState()
    val favorites by playerManager.favorites.collectAsState()
    val dislikedTracks by playerManager.dislikedTracks.collectAsState()
    val playlistsMap by playerManager.playlistsMap.collectAsState()
    val dbPlaylists by socialRepository.getAllPlaylists().collectAsState(initial = emptyList())
    val appSettings by playerManager.appSettings.collectAsState()

    // SAF Folder Picker Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore persist errors
            }
            val folderTracks = AudioScanner.scanFolderUri(context, uri)
            if (folderTracks.isNotEmpty()) {
                coroutineScope.launch(Dispatchers.IO) {
                    val merged = com.example.data.TrackRepository.saveScannedTracks(context, folderTracks)
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        loadedTracks = merged
                        playerManager.setQueue(merged, 0, false)
                        Toast.makeText(context, "Se cargaron ${merged.size} canciones", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "No se encontraron archivos de audio en la carpeta", Toast.LENGTH_LONG).show()
            }
        }
    }

    // MediaStore Permission Launcher
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val performScan: (Boolean) -> Unit = { force ->
        coroutineScope.launch(Dispatchers.IO) {
            val deviceTracks = AudioScanner.scanMediaStoreAudio(context)
            if (deviceTracks.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    loadedTracks = deviceTracks
                    if (playerManager.playlist.value.isEmpty()) {
                        playerManager.setQueue(deviceTracks, 0, false)
                    }
                }
                val saved = com.example.data.TrackRepository.saveScannedTracks(context, deviceTracks, force)
                withContext(Dispatchers.Main) {
                    loadedTracks = saved
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        if (isGranted) {
            performScan(isForceRescan)
            isForceRescan = false
        }
    }

    // Initial Scanner Load
    LaunchedEffect(Unit) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        if (hasPermission) {
            performScan(false)
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
    BackHandler(enabled = isPlayerExpanded || currentScreen != NavigationScreen.HOME) {
        if (isPlayerExpanded) {
            isPlayerExpanded = false
        } else {
            currentScreen = NavigationScreen.HOME
        }
    }
        Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (!isPlayerExpanded && currentScreen != NavigationScreen.SETTINGS) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        // Floating Mini Player
                        AnimatedVisibility(
                            visible = currentTrack != null,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut()
                        ) {
                            MiniPlayerBar(
                                track = currentTrack,
                                isPlaying = isPlaying,
                                isDarkMode = appSettings.isDarkMode,
                                onPlayPauseToggle = { playerManager.togglePlayPause() },
                                onNext = { playerManager.nextTrack() },
                                onPrevious = { playerManager.previousTrack() },
                                onClick = { isPlayerExpanded = true }
                            )
                        }

                        // Bottom Navigation Bar with 5 Tabs
                        FiveTabBottomNavBar(
                            appSettings = appSettings,
                            currentScreen = currentScreen,
                            onScreenSelect = { currentScreen = it }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    NavigationScreen.HOME -> HomeScreen(
                        tracks = loadedTracks,
                        currentTrack = currentTrack,
                        currentIndex = currentIndex,
                        isPlaying = isPlaying,
                        userProfile = userProfile,
                        socialRepository = socialRepository,
                        onTrackSelect = { tracks, index ->
                            playerManager.setQueue(tracks, index)
                        },
                        onShuffleAll = {
                            if (loadedTracks.isNotEmpty()) {
                                playerManager.setQueue(loadedTracks.shuffled(), 0)
                            }
                        },
                        onOpenProfile = { currentScreen = NavigationScreen.PROFILE },
                        onOpenSettings = { currentScreen = NavigationScreen.SETTINGS },
                        onPickFolder = { folderPickerLauncher.launch(null) },
                        onRequestPermissions = { permissionLauncher.launch(requiredPermissions) },
                        onNavigateToPlaylist = { name -> 
                            initialPlaylist = name
                            // Do not change currentScreen
                        },
                        onNavigateToAlbum = { name -> 
                            initialAlbum = name
                            // Do not change currentScreen
                        },
                        onNavigateToArtist = { name -> 
                            initialArtist = name
                            // Do not change currentScreen
                        },
                        settings = appSettings
                    )

                    NavigationScreen.FOLDERS -> FoldersScreen(
                        settings = appSettings,
                        tracks = loadedTracks,
                        currentTrack = currentTrack,
                        onPlayFolder = { folderTracks, idx ->
                            playerManager.setQueue(folderTracks, idx)
                        },
                        onPickFolderUri = { folderPickerLauncher.launch(null) }
                    )

                    NavigationScreen.PLAYLISTS -> PlaylistsScreen(
                        settings = appSettings,
                        socialRepository = socialRepository,
                        favorites = favorites,
                        allTracks = loadedTracks,
                        initialPlaylistName = initialPlaylist,
                        onCreatePlaylist = { name -> 
                            // Now created in SocialRepository inside screen
                        },
                        onPlayPlaylist = { tracks, idx -> playerManager.setQueue(tracks, idx) }
                    )

                    NavigationScreen.ALBUMS -> AlbumsScreen(
                        settings = appSettings,
                        tracks = loadedTracks,
                        initialAlbumName = initialAlbum,
                        onPlayAlbum = { albumTracks, idx -> 
                            playerManager.setShuffle(false)
                            playerManager.setQueue(albumTracks, idx)
                        },
                        onSetPlayNext = { track -> playerManager.setPlayNext(track) },
                        onAddToPlaylist = { playlistId, track ->
                            coroutineScope.launch {
                                socialRepository.addTrackToPlaylist(playlistId, track.id)
                                val p = dbPlaylists.find { it.playlistId == playlistId }
                                if (p != null) {
                                    playerManager.addToPlaylist(p.name, track)
                                }
                            }
                        },
                        onCreatePlaylistAndAdd = { name, track ->
                            coroutineScope.launch {
                                val newId = socialRepository.createPlaylist(name)
                                socialRepository.addTrackToPlaylist(newId, track.id)
                                playerManager.addToPlaylist(name, track)
                            }
                        },
                        playlists = dbPlaylists
                    )

                    NavigationScreen.ARTISTS -> ArtistsScreen(
                        settings = appSettings,
                        tracks = loadedTracks,
                        initialArtistName = initialArtist,
                        onPlayArtist = { artistTracks, idx -> playerManager.setQueue(artistTracks, idx) },
                        onNavigateToAlbum = { albumName ->
                            initialAlbum = albumName
                            currentScreen = NavigationScreen.ALBUMS
                        }
                    )



                    NavigationScreen.SETTINGS -> SettingsScreen(
                        settings = appSettings,
                        onUpdateSettings = { playerManager.updateSettings(it) },
                        onBack = { currentScreen = NavigationScreen.HOME },
                        onRescanAudio = { isForceRescan = true; permissionLauncher.launch(requiredPermissions) }
                    )
                    
                    NavigationScreen.PROFILE -> com.example.ui.screens.UserProfileScreen(
                        userProfile = userProfile,
                        socialRepository = socialRepository,
                        onBack = { currentScreen = NavigationScreen.HOME },
                        onPlaylistClick = { playlistName -> 
                            initialPlaylist = playlistName
                            // Do not change currentScreen
                        }
                    )
    
                    else -> {}
                }
                
                if (initialPlaylist != null && currentScreen != NavigationScreen.PLAYLISTS) {
                    PlaylistsScreen(
                        settings = appSettings,
                        socialRepository = socialRepository,
                        favorites = favorites,
                        allTracks = loadedTracks,
                        initialPlaylistName = initialPlaylist,
                        onCreatePlaylist = {},
                        onPlayPlaylist = { tracks, idx -> playerManager.setQueue(tracks, idx) },
                        onDismissOverlay = { initialPlaylist = null },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                if (initialAlbum != null && currentScreen != NavigationScreen.ALBUMS) {
                    AlbumsScreen(
                        settings = appSettings,
                        tracks = loadedTracks,
                        initialAlbumName = initialAlbum,
                        onPlayAlbum = { albumTracks, idx -> 
                            playerManager.setShuffle(false)
                            playerManager.setQueue(albumTracks, idx)
                        },
                        onDismissOverlay = { initialAlbum = null },
                        onSetPlayNext = { track -> playerManager.setPlayNext(track) },
                        onAddToPlaylist = { playlistId, track ->
                            coroutineScope.launch {
                                socialRepository.addTrackToPlaylist(playlistId, track.id)
                                val p = dbPlaylists.find { it.playlistId == playlistId }
                                if (p != null) {
                                    playerManager.addToPlaylist(p.name, track)
                                }
                            }
                        },
                        onCreatePlaylistAndAdd = { name, track ->
                            coroutineScope.launch {
                                val newId = socialRepository.createPlaylist(name)
                                socialRepository.addTrackToPlaylist(newId, track.id)
                                playerManager.addToPlaylist(name, track)
                            }
                        },
                        playlists = dbPlaylists,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (initialArtist != null && currentScreen != NavigationScreen.ARTISTS) {
                    ArtistsScreen(
                        settings = appSettings,
                        tracks = loadedTracks,
                        initialArtistName = initialArtist,
                        onPlayArtist = { artistTracks, idx -> playerManager.setQueue(artistTracks, idx) },
                        onDismissOverlay = { initialArtist = null },
                        onNavigateToAlbum = { albumName ->
                            initialArtist = null
                            initialAlbum = albumName
                            currentScreen = NavigationScreen.ALBUMS
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Queue Bottom Sheet Drawer matching Screenshot #3

        androidx.compose.animation.AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(240, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerScreen(
                settings = appSettings,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                isShuffle = isShuffle,
                loopMode = loopMode,
                isFavorite = currentTrack?.let { favorites.contains(it.id) } ?: false,
                isDisliked = currentTrack?.let { dislikedTracks.contains(it.id) } ?: false,
                playlists = dbPlaylists,
                onPlayPauseToggle = { playerManager.togglePlayPause() },
                onNext = { playerManager.nextTrack() },
                onPrevious = { playerManager.previousTrack() },
                onSeek = { playerManager.seekTo(it) },
                onShuffleToggle = { playerManager.toggleShuffle() },
                onLoopCycle = { playerManager.cycleLoopMode() },
                onFavoriteToggle = { currentTrack?.let { playerManager.toggleFavorite(it.id) } },
                onDislikeToggle = { currentTrack?.let { playerManager.toggleDislike(it.id) } },
                onOpenQueue = { showQueueSheet = true },
                onAddToPlaylist = { playlistId ->
                    currentTrack?.let { track ->
                        coroutineScope.launch {
                            socialRepository.addTrackToPlaylist(playlistId, track.id)
                            Toast.makeText(context, "Canción agregada a la playlist", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onCreatePlaylistAndAdd = { name ->
                    currentTrack?.let { track ->
                        coroutineScope.launch {
                            val newId = socialRepository.createPlaylist(name)
                            socialRepository.addTrackToPlaylist(newId, track.id)
                            Toast.makeText(context, "Playlist '$name' creada", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onBack = { isPlayerExpanded = false }
            )
        }
        } // End of Box wrapping Scaffold

        if (showQueueSheet) {
            QueueBottomSheet(
                queue = playlist,
                currentIndex = currentIndex,
                onDismissRequest = { showQueueSheet = false },
                onTrackSelect = { idx ->
                    playerManager.playTrackAtIndex(idx)
                    showQueueSheet = false
                },
                onRemoveFromQueue = { idx -> playerManager.removeFromQueue(idx) },
                onSetPlayNext = { track -> playerManager.setPlayNext(track) },
                onMoveInQueue = { from, to -> playerManager.moveInQueue(from, to) },
                language = appSettings.appLanguage
            )
        }
    }
}

@Composable
fun MiniPlayerBar(
    track: Track?,
    isPlaying: Boolean,
    isDarkMode: Boolean = true,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClick: () -> Unit
) {
    if (track == null) return

    val titleColor = if (isDarkMode) Color.White else Color(0xFF1E1E1E)
    val artistColor = if (isDarkMode) GlassTextSecondary else Color(0xFF666666)
    val iconTint = if (isDarkMode) Color.White else Color(0xFF1E1E1E)
    val cardBg = if (isDarkMode) Color(0x26FFFFFF) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkMode) Color(0x33FFFFFF) else Color(0x1F000000)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(8.dp)
            .testTag("mini_player_bar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDarkMode) Color(0x2AFFFFFF) else Color(0x0F000000)),
                contentAlignment = Alignment.Center
            ) {
                TrackImage(track = track, modifier = Modifier.fillMaxSize())
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    fontSize = 12.sp,
                    color = artistColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onPrevious) {
                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous", tint = iconTint, modifier = Modifier.size(22.dp))
            }

            // Play/Pause Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onPlayPauseToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = onNext) {
                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = iconTint, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun FiveTabBottomNavBar(
    appSettings: com.example.model.AppSettings,
    currentScreen: NavigationScreen,
    onScreenSelect: (NavigationScreen) -> Unit
) {
    val isDark = appSettings.isDarkMode
    val navBg = if (isDark) Color(0xFF0F0F0F) else Color(0xFFFFFFFF)
    val navBorder = if (isDark) Color(0x1AFFFFFF) else Color(0x1F000000)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(navBg)
            .border(width = 0.5.dp, color = navBorder)
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem5(
                label = com.example.ui.Translations.get(appSettings.appLanguage, "home"),
                icon = Icons.Default.Home,
                isSelected = currentScreen == NavigationScreen.HOME,
                isDarkMode = isDark,
                primaryColor = appSettings.selectedTheme.primaryColor,
                onClick = { onScreenSelect(NavigationScreen.HOME) },
                testTag = "nav_home"
            )
            NavItem5(
                label = com.example.ui.Translations.get(appSettings.appLanguage, "folders"),
                icon = Icons.Default.Folder,
                isSelected = currentScreen == NavigationScreen.FOLDERS,
                isDarkMode = isDark,
                primaryColor = appSettings.selectedTheme.primaryColor,
                onClick = { onScreenSelect(NavigationScreen.FOLDERS) },
                testTag = "nav_folders"
            )
            NavItem5(
                label = com.example.ui.Translations.get(appSettings.appLanguage, "playlists"),
                icon = Icons.Default.PlaylistPlay,
                isSelected = currentScreen == NavigationScreen.PLAYLISTS,
                isDarkMode = isDark,
                primaryColor = appSettings.selectedTheme.primaryColor,
                onClick = { onScreenSelect(NavigationScreen.PLAYLISTS) },
                testTag = "nav_playlists"
            )
            NavItem5(
                label = com.example.ui.Translations.get(appSettings.appLanguage, "albums"),
                icon = Icons.Default.Album,
                isSelected = currentScreen == NavigationScreen.ALBUMS,
                isDarkMode = isDark,
                primaryColor = appSettings.selectedTheme.primaryColor,
                onClick = { onScreenSelect(NavigationScreen.ALBUMS) },
                testTag = "nav_albums"
            )
            NavItem5(
                label = com.example.ui.Translations.get(appSettings.appLanguage, "artists"),
                icon = Icons.Default.Person,
                isSelected = currentScreen == NavigationScreen.ARTISTS,
                isDarkMode = isDark,
                primaryColor = appSettings.selectedTheme.primaryColor,
                onClick = { onScreenSelect(NavigationScreen.ARTISTS) },
                testTag = "nav_artists"
            )
        }
    }
}

@Composable
private fun NavItem5(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    isDarkMode: Boolean,
    primaryColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    val selectedColor = primaryColor
    val unselectedColor = if (isDarkMode) Color.Gray else Color(0xFF757575)
    val selectedBg = if (isDarkMode) Color(0xFF262626) else primaryColor.copy(alpha = 0.14f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (isSelected) selectedBg else Color.Transparent)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) selectedColor else unselectedColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) selectedColor else unselectedColor
        )
    }
}
