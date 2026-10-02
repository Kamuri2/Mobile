package com.example.ui.screens


import androidx.compose.material3.MaterialTheme
import android.graphics.BitmapFactory

import androidx.compose.foundation.Image

import androidx.compose.foundation.background

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.grid.GridCells

import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

import androidx.compose.foundation.lazy.grid.items

import androidx.compose.foundation.lazy.itemsIndexed

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle

import androidx.compose.material3.*

import androidx.compose.foundation.gestures.detectVerticalDragGestures

import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.layout.onSizeChanged

import kotlinx.coroutines.launch

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.delay
import com.example.ui.components.TrackImage

import com.example.model.Track

import com.example.ui.theme.GlassTextMuted

import com.example.ui.theme.GlassTextSecondary
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.border
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.data.local.PlaylistEntity

@Composable
fun AlbumsScreen(
    settings: com.example.model.AppSettings,
    tracks: List<Track>,
    initialAlbumName: String? = null,
    onPlayAlbum: (List<Track>, Int) -> Unit,
    onDismissOverlay: (() -> Unit)? = null,
    onSetPlayNext: ((Track) -> Unit)? = null,
    onAddToPlaylist: ((Long, Track) -> Unit)? = null,
    onCreatePlaylistAndAdd: ((String, Track) -> Unit)? = null,
    playlists: List<PlaylistEntity> = emptyList(),
    modifier: Modifier = Modifier
) {
    val albumGroups = remember(tracks) {
        tracks.groupBy { it.album }.toSortedMap(compareBy { it.lowercase() })
    }
    
    var selectedAlbum by remember { mutableStateOf<String?>(initialAlbumName) }
    var searchQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(initialAlbumName) { if(initialAlbumName != null) selectedAlbum = initialAlbumName }
    BackHandler(enabled = selectedAlbum != null || onDismissOverlay != null) {
        if (selectedAlbum != null) {
            if (onDismissOverlay != null) onDismissOverlay()
            else selectedAlbum = null
        } else {
            onDismissOverlay?.invoke()
        }
    }

    if (selectedAlbum != null) {
        val albumTracks = (albumGroups[selectedAlbum] ?: emptyList()).sortedWith(
            compareBy(
                { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE },
                { it.title.lowercase() }
            )
        )
        AlbumDetailScreen(
            albumName = selectedAlbum!!,
            tracks = albumTracks,
            settings = settings,
            onBack = { 
                if (onDismissOverlay != null) onDismissOverlay()
                else selectedAlbum = null 
            },
            onPlayTrack = { index -> onPlayAlbum(albumTracks, index) },
            onShuffleAll = { onPlayAlbum(albumTracks.shuffled(), 0) },
            onSetPlayNext = onSetPlayNext,
            onAddToPlaylist = onAddToPlaylist,
            onCreatePlaylistAndAdd = onCreatePlaylistAndAdd,
            playlists = playlists
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDismissOverlay != null) {
                    IconButton(onClick = onDismissOverlay) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = com.example.ui.Translations.get(settings.appLanguage, "albums"),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar álbum...", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            val filteredAlbums = remember(albumGroups, searchQuery) {
                if (searchQuery.isBlank()) albumGroups.keys.toList()
                else albumGroups.keys.filter { it.contains(searchQuery, ignoreCase = true) }
            }

            val coroutineScope = rememberCoroutineScope()
            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            val alphabet = remember { listOf("#") + ('A'..'Z').map { it.toString() } }

            val letterIndices = remember(filteredAlbums) {
                val map = mutableMapOf<String, Int>()
                filteredAlbums.forEachIndexed { index, albumName ->
                    val trimmed = albumName.trim()
                    val firstChar = trimmed.firstOrNull()?.uppercaseChar()
                    val letterKey = if (firstChar != null && firstChar in 'A'..'Z') firstChar.toString() else "#"
                    if (!map.containsKey(letterKey)) {
                        map[letterKey] = index
                    }
                }
                map
            }

            var activeLetterBubble by remember { mutableStateOf<String?>(null) }

            fun jumpToLetter(letter: String) {
                activeLetterBubble = letter
                val targetIndex = if (letter == "#") {
                    letterIndices["#"] ?: 0
                } else {
                    letterIndices[letter] ?: run {
                        val targetChar = letter.first()
                        letterIndices.entries
                            .filter { it.key != "#" && it.key.first() >= targetChar }
                            .minByOrNull { it.key.first() }?.value ?: 0
                    }
                }
                coroutineScope.launch {
                    gridState.scrollToItem(targetIndex)
                    delay(900)
                    if (activeLetterBubble == letter) {
                        activeLetterBubble = null
                    }
                }
            }

            // Horizontal Alphabet Quick-Jump Strip
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                items(alphabet) { letter ->
                    val hasAlbums = letterIndices.containsKey(letter)
                    val isSelected = activeLetterBubble == letter
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    hasAlbums -> if (settings.isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
                                    else -> if (settings.isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
                                }
                            )
                            .clickable { jumpToLetter(letter) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter,
                            fontSize = 13.sp,
                            fontWeight = if (hasAlbums) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isSelected -> Color.Black
                                hasAlbums -> if (settings.isDarkMode) Color.White else Color(0xFF111111)
                                else -> if (settings.isDarkMode) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.30f)
                            }
                        )
                    }
                }
            }

            if (filteredAlbums.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (albumGroups.isEmpty()) com.example.ui.Translations.get(settings.appLanguage, "no_albums") else "No hay resultados", color = GlassTextMuted)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredAlbums) { albumName ->
                            val albumTracks = albumGroups[albumName] ?: emptyList()
                            val firstTrack = albumTracks.firstOrNull()

                            val artworkBitmap: androidx.compose.ui.graphics.ImageBitmap? = null
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedAlbum = albumName },
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                            ) {
                                Column(modifier = Modifier.padding(4.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFF2B2B2B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when {
                                            artworkBitmap != null -> Image(bitmap = artworkBitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            firstTrack != null -> TrackImage(track = firstTrack, modifier = Modifier.fillMaxSize())
                                            else -> Icon(imageVector = Icons.Default.Album, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(text = albumName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = "${firstTrack?.artist ?: "Various"} • ${albumTracks.size} tracks", fontSize = 12.sp, color = GlassTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(100.dp)) }
                        item { Spacer(modifier = Modifier.height(100.dp)) }
                    }

                    // Floating Letter Bubble on Jump
                    if (activeLetterBubble != null) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
                                .align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = activeLetterBubble!!,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumDetailScreen(
    albumName: String,
    tracks: List<Track>,
    settings: com.example.model.AppSettings = com.example.model.AppSettings(),
    initialAlbumName: String? = null,
    onBack: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onShuffleAll: () -> Unit,
    onArtistClick: ((String) -> Unit)? = null,
    onSetPlayNext: ((Track) -> Unit)? = null,
    onAddToPlaylist: ((Long, Track) -> Unit)? = null,
    onCreatePlaylistAndAdd: ((String, Track) -> Unit)? = null,
    playlists: List<PlaylistEntity> = emptyList()
) {
    val context = LocalContext.current
    val isSpanish = settings.appLanguage.startsWith("Esp", ignoreCase = true)
    var selectedTrackForPlaylist by remember { mutableStateOf<Track?>(null) }
    val firstTrack = tracks.firstOrNull()
    val sortedTracks = tracks
    val primaryColor = settings.selectedTheme.primaryColor
    val isDarkMode = settings.isDarkMode
    val surfaceBg = if (isDarkMode) Color(0xFF121212) else Color(0xFFF7F7F9)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF111111)
    val textSecondary = if (isDarkMode) Color(0xB3FFFFFF) else Color(0xFF555555)
    val iconTint = if (isDarkMode) Color.White else Color(0xFF1E1E1E)
    val isLightPrimary = (0.299f * primaryColor.red + 0.587f * primaryColor.green + 0.114f * primaryColor.blue) > 0.5f
    val playIconTint = if (isLightPrimary) Color.Black else Color.White

    val dominantColor = com.example.data.ArtworkExtractor.rememberTrackDominantColor(firstTrack, primaryColor)
    val topColor = dominantColor

    // Dynamic top gradient fading to surface background matching album's most dominant color
    val backgroundBrush = remember(topColor, isDarkMode, surfaceBg) {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                topColor.copy(alpha = if (isDarkMode) 0.55f else 0.35f),
                topColor.copy(alpha = if (isDarkMode) 0.22f else 0.14f),
                surfaceBg.copy(alpha = 0.95f),
                surfaceBg
            ),
            startY = 0f,
            endY = 1200f
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceBg)
            .background(backgroundBrush)
    ) {
        // 1. Top Bar with Back Button
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = iconTint,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // 2. Centered Album Cover
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF242424)),
                    contentAlignment = Alignment.Center
                ) {
                    if (firstTrack != null) {
                        TrackImage(
                            track = firstTrack,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // 3. Album Metadata Section (Left aligned, Spotify style)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Album Title
                Text(
                    text = albumName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    lineHeight = 30.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Artist Thumbnail and Name Row
                val artistName = firstTrack?.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Various Artists"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onArtistClick?.invoke(artistName) }
                        .padding(vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isDarkMode) Color(0x33FFFFFF) else Color(0x1A000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (firstTrack != null) {
                            TrackImage(
                                track = firstTrack,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = artistName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Album • Year • Tracks metadata line
                val yearStr = firstTrack?.year?.takeIf { it > 0 }?.toString() ?: ""
                val metadataText = buildString {
                    append("Álbum")
                    if (yearStr.isNotBlank()) {
                        append(" • ").append(yearStr)
                    }
                    if (tracks.isNotEmpty()) {
                        append(" • ").append(tracks.size).append(if (tracks.size == 1) " canción" else " canciones")
                    }
                }

                Text(
                    text = metadataText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = textSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Action Bar Row (Aligned to Left side with Selected Theme Colors)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Big Play Button with Theme Color
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                            .clickable { onPlayTrack(0) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Album",
                            tint = playIconTint,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Shuffle Button with Theme Color
                    IconButton(
                        onClick = onShuffleAll,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = primaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // 4. Track List
        itemsIndexed(sortedTracks) { index, track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlayTrack(index) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        color = textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                var showMenu by remember { mutableStateOf(false) }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = if (isSpanish) "Opciones de canción" else "Song options",
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(if (isDarkMode) Color(0xFF282828) else Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, if (isDarkMode) Color(0x22FFFFFF) else Color(0x1F000000), RoundedCornerShape(12.dp))
                    ) {
                        // Reproducir a continuación
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isSpanish) "Reproducir a continuación" else "Play next",
                                    color = textPrimary,
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onSetPlayNext?.invoke(track)
                                Toast.makeText(
                                    context,
                                    if (isSpanish) "Se reproducirá a continuación" else "Playing next",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )

                        HorizontalDivider(color = if (isDarkMode) Color(0x1AFFFFFF) else Color(0x12000000), thickness = 0.5.dp)

                        // Agregar a una playlist
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isSpanish) "Agregar a playlist" else "Add to playlist",
                                    color = textPrimary,
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PlaylistAdd,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                selectedTrackForPlaylist = track
                            }
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(120.dp))
        }
    }

    // Modal Dialog to Select or Create Playlist
    if (selectedTrackForPlaylist != null) {
        val trackToAdd = selectedTrackForPlaylist!!
        var showCreateField by remember { mutableStateOf(false) }
        var newPlaylistNameInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { selectedTrackForPlaylist = null },
            containerColor = if (isDarkMode) Color(0xFF202020) else Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isSpanish) "Agregar a playlist" else "Add to playlist",
                        color = textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = trackToAdd.title,
                        color = textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = trackToAdd.artist,
                        color = GlassTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Option to create new playlist
                    if (!showCreateField) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showCreateField = true }
                                .background(Color(0x14FFFFFF))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isSpanish) "Crear nueva playlist" else "Create new playlist",
                                color = primaryColor,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x14FFFFFF))
                                .padding(12.dp)
                        ) {
                            OutlinedTextField(
                                value = newPlaylistNameInput,
                                onValueChange = { newPlaylistNameInput = it },
                                placeholder = {
                                    Text(
                                        if (isSpanish) "Nombre de la playlist" else "Playlist name",
                                        fontSize = 13.sp,
                                        color = Color.Gray
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textPrimary,
                                    unfocusedTextColor = textPrimary,
                                    focusedBorderColor = primaryColor
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    showCreateField = false
                                    newPlaylistNameInput = ""
                                }) {
                                    Text(if (isSpanish) "Cancelar" else "Cancel", color = GlassTextMuted, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (newPlaylistNameInput.isNotBlank()) {
                                            onCreatePlaylistAndAdd?.invoke(newPlaylistNameInput.trim(), trackToAdd)
                                            Toast.makeText(
                                                context,
                                                if (isSpanish) "Playlist creada y canción añadida" else "Playlist created & song added",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            selectedTrackForPlaylist = null
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                                ) {
                                    Text(if (isSpanish) "Crear y añadir" else "Create & add", fontSize = 13.sp, color = playIconTint)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Existing Playlists List
                    if (playlists.isEmpty()) {
                        Text(
                            text = if (isSpanish) "Aún no tienes playlists creadas" else "No playlists created yet",
                            color = GlassTextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Text(
                            text = if (isSpanish) "Tus playlists" else "Your playlists",
                            color = GlassTextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                        ) {
                            items(playlists) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            onAddToPlaylist?.invoke(playlist.playlistId, trackToAdd)
                                            Toast.makeText(
                                                context,
                                                if (isSpanish) "Añadida a ${playlist.name}" else "Added to ${playlist.name}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            selectedTrackForPlaylist = null
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isDarkMode) Color(0x22FFFFFF) else Color(0x0C000000)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QueueMusic,
                                            contentDescription = null,
                                            tint = iconTint.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            color = textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedTrackForPlaylist = null }) {
                    Text(
                        text = if (isSpanish) "Cerrar" else "Close",
                        color = GlassTextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        )
    }
}
