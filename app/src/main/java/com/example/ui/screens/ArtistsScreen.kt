package com.example.ui.screens


import androidx.compose.material3.MaterialTheme
import android.content.Intent

import android.graphics.BitmapFactory

import android.net.Uri

import androidx.compose.foundation.Image

import androidx.compose.foundation.background

import androidx.compose.foundation.clickable

import androidx.compose.foundation.lazy.grid.GridCells

import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

import androidx.compose.foundation.lazy.grid.items

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.ArrowBack

import androidx.compose.material.icons.filled.MusicNote

import androidx.compose.material.icons.filled.Person

import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow

import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Refresh

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

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import coil.compose.AsyncImage
import com.example.ui.components.TrackImage

import com.example.model.Track

import com.example.ui.theme.GlassTextMuted

import com.example.ui.theme.GlassTextSecondary

import com.example.data.ArtistInfoFetcher

import com.example.data.ArtistInfo
import androidx.compose.material.icons.filled.Mic

@Composable
fun ArtistsScreen(
    settings: com.example.model.AppSettings,
    tracks: List<Track>,
    initialArtistName: String? = null,
    onPlayArtist: (List<Track>, Int) -> Unit,
    onDismissOverlay: (() -> Unit)? = null,
    onNavigateToAlbum: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val artistGroups = remember(tracks) {
        val map = mutableMapOf<String, MutableList<Track>>()
        tracks.forEach { track ->
            val artists = com.example.util.ArtistParser.extractAllArtists(track.artist, track.title)
            if (artists.isEmpty()) {
                val clean = track.artist.trim()
                if (clean.isNotBlank() && clean != "Unknown Artist" && clean != "<unknown>") {
                    map.getOrPut(clean) { mutableListOf() }.add(track)
                }
            } else {
                artists.forEach { artistName ->
                    val clean = artistName.trim()
                    if (clean.isNotBlank() && clean != "Unknown Artist" && clean != "<unknown>") {
                        map.getOrPut(clean) { mutableListOf() }.add(track)
                    }
                }
            }
        }
        map.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
    
    val cacheRevision by ArtistInfoFetcher.cacheRevision.collectAsState()
    var selectedArtist by remember { mutableStateOf<String?>(initialArtistName) }
    var searchQuery by remember { mutableStateOf("") }
    LaunchedEffect(initialArtistName) { if(initialArtistName != null) selectedArtist = initialArtistName }
    BackHandler(enabled = selectedArtist != null || onDismissOverlay != null) {
        if (selectedArtist != null) {
            if (onDismissOverlay != null) onDismissOverlay()
            else selectedArtist = null
        } else {
            onDismissOverlay?.invoke()
        }
    }
    var showAboutDialog by remember { mutableStateOf(false) }

    if (selectedArtist != null) {
        val artistTracks = artistGroups[selectedArtist] ?: emptyList()
        ArtistDetailScreen(
            artistName = selectedArtist!!,
            tracks = artistTracks,
            onBack = { 
                if (onDismissOverlay != null) onDismissOverlay()
                else selectedArtist = null 
            },
            onPlayTrack = { index -> onPlayArtist(artistTracks, index) },
            onShuffleAll = { onPlayArtist(artistTracks.shuffled(), 0) },
            onShowAbout = { showAboutDialog = true },
            onPlayQueue = { queue, index -> onPlayArtist(queue, index) },
            onNavigateToAlbum = onNavigateToAlbum,
            settings = settings
        )
        
        if (showAboutDialog) {
            ArtistAboutScreen(
                artistName = selectedArtist!!,
                language = settings.appLanguage,
                sampleTrack = artistTracks.firstOrNull(),
                onBack = { showAboutDialog = false }
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            val filteredArtists = remember(artistGroups, searchQuery) {
                if (searchQuery.isBlank()) artistGroups.keys.toList()
                else artistGroups.keys.filter { it.contains(searchQuery, ignoreCase = true) }
            }

            var isSearchingPhotos by remember { mutableStateOf(false) }
            val screenScope = rememberCoroutineScope()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onDismissOverlay != null) {
                        IconButton(onClick = onDismissOverlay) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = com.example.ui.Translations.get(settings.appLanguage, "artists"),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(
                    onClick = {
                        if (!isSearchingPhotos && filteredArtists.isNotEmpty()) {
                            isSearchingPhotos = true
                            screenScope.launch {
                                try {
                                    com.example.data.ArtistInfoFetcher.searchAndFetchAllArtistPhotos(filteredArtists)
                                } finally {
                                    isSearchingPhotos = false
                                }
                            }
                        }
                    },
                    enabled = !isSearchingPhotos
                ) {
                    if (isSearchingPhotos) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Buscar fotos de artistas en internet",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(com.example.ui.Translations.get(settings.appLanguage, "search_artist"), color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            val coroutineScope = rememberCoroutineScope()
            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            val alphabet = remember { listOf("#") + ('A'..'Z').map { it.toString() } }

            val letterIndices = remember(filteredArtists) {
                val map = mutableMapOf<String, Int>()
                filteredArtists.forEachIndexed { index, artistName ->
                    val trimmed = artistName.trim()
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
                    val hasArtists = letterIndices.containsKey(letter)
                    val isSelected = activeLetterBubble == letter
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    hasArtists -> Color.White.copy(alpha = 0.15f)
                                    else -> Color.White.copy(alpha = 0.05f)
                                }
                            )
                            .clickable { jumpToLetter(letter) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter,
                            fontSize = 13.sp,
                            fontWeight = if (hasArtists) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isSelected -> Color.Black
                                hasArtists -> Color.White
                                else -> Color.White.copy(alpha = 0.35f)
                            }
                        )
                    }
                }
            }

            if (filteredArtists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (artistGroups.isEmpty()) com.example.ui.Translations.get(settings.appLanguage, "no_artists") else com.example.ui.Translations.get(settings.appLanguage, "no_results"), color = GlassTextMuted)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredArtists, key = { it }) { artistName ->
                        var imageUrl by remember(artistName, cacheRevision) {
                            mutableStateOf(
                                com.example.data.ArtistImageRepository.getMemoryCachedImageUrl(artistName)
                                    ?: com.example.data.ArtistInfoFetcher.getCachedArtistInfo(artistName)?.imageUrl
                            )
                        }
                        
                        LaunchedEffect(artistName, cacheRevision) {
                            if (imageUrl == null) {
                                val sampleTrack = artistGroups[artistName]?.firstOrNull()
                                imageUrl = com.example.data.ArtistImageRepository.getArtistImageUrl(
                                    artistName = artistName,
                                    trackTitle = sampleTrack?.title,
                                    albumName = sampleTrack?.album,
                                    genre = sampleTrack?.genre
                                ) ?: com.example.data.ArtistInfoFetcher.fetchArtistPhoto(
                                    artistName = artistName,
                                    trackTitle = sampleTrack?.title,
                                    albumName = sampleTrack?.album,
                                    genre = sampleTrack?.genre
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedArtist = artistName },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(Color(0x1AFFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                val trackFallback = remember(artistName) { artistGroups[artistName]?.firstOrNull() }
                                if (imageUrl != null) {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = artistName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (trackFallback != null) {
                                    TrackImage(
                                        track = trackFallback,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = artistName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(100.dp)) }
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
fun ArtistDetailScreen(
    artistName: String,
    tracks: List<Track>,
    settings: com.example.model.AppSettings = com.example.model.AppSettings(),
    initialArtistName: String? = null,
    onBack: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onShuffleAll: () -> Unit,
    onShowAbout: () -> Unit,
    onPlayQueue: ((List<Track>, Int) -> Unit)? = null,
    onNavigateToAlbum: ((String) -> Unit)? = null
) {
    val cacheRevision by ArtistInfoFetcher.cacheRevision.collectAsState()
    var artistInfo by remember(artistName, cacheRevision) { mutableStateOf<ArtistInfo?>(ArtistInfoFetcher.getCachedArtistInfo(artistName)) }
    var viewingAlbumName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val primaryColor = settings.selectedTheme.primaryColor
    val isLightPrimary = (0.299f * primaryColor.red + 0.587f * primaryColor.green + 0.114f * primaryColor.blue) > 0.5f
    val playIconTint = if (isLightPrimary) Color.Black else Color.White
    
    LaunchedEffect(artistName, cacheRevision) {
        val cached = ArtistInfoFetcher.getCachedArtistInfo(artistName)
        if (cached != null && !cached.imageUrl.isNullOrBlank() && !cached.bio.isNullOrBlank()) {
            artistInfo = cached
        } else {
            if (cached != null) artistInfo = cached
            val sampleTrack = tracks.firstOrNull()
            artistInfo = ArtistInfoFetcher.fetchArtistInfo(
                rawArtistName = artistName,
                trackTitle = sampleTrack?.title,
                albumName = sampleTrack?.album,
                genre = sampleTrack?.genre
            )
        }
    }

    if (viewingAlbumName != null) {
        val albumTracks = remember(tracks, viewingAlbumName) {
            tracks.filter { it.album.equals(viewingAlbumName, ignoreCase = true) }
                .sortedWith(
                    compareBy(
                        { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE },
                        { it.title.lowercase() }
                    )
                )
        }
        BackHandler { viewingAlbumName = null }
        AlbumDetailScreen(
            albumName = viewingAlbumName!!,
            tracks = albumTracks,
            onBack = { viewingAlbumName = null },
            onPlayTrack = { idx ->
                if (onPlayQueue != null) {
                    onPlayQueue(albumTracks, idx)
                } else {
                    val trackToPlay = albumTracks.getOrNull(idx)
                    val globalIdx = if (trackToPlay != null) tracks.indexOf(trackToPlay).coerceAtLeast(0) else 0
                    onPlayTrack(globalIdx)
                }
            },
            onShuffleAll = {
                if (onPlayQueue != null) {
                    onPlayQueue(albumTracks.shuffled(), 0)
                } else {
                    onShuffleAll()
                }
            }
        )
        return
    }

    val albumsCount = tracks.map { it.album }.filter { it.isNotBlank() }.distinct().size

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .background(Color.Black)
            ) {
                val firstTrack = tracks.firstOrNull()
                val artworkBitmap: androidx.compose.ui.graphics.ImageBitmap? = null
                
                if (artistInfo?.imageUrl != null) {
                    AsyncImage(
                        model = artistInfo?.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (artworkBitmap != null) {
                    Image(bitmap = artworkBitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else if (firstTrack != null) {
                    TrackImage(
                        track = firstTrack,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                )
                
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onShowAbout() }
                ) {
                    Text(
                        text = com.example.ui.Translations.get(settings.appLanguage, "artist").uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = GlassTextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artistName,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        lineHeight = 46.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val albumsWord = if (albumsCount == 1) com.example.ui.Translations.get(settings.appLanguage, "album_capitalized") else com.example.ui.Translations.get(settings.appLanguage, "albums")
                        val songsWord = if (tracks.size == 1) com.example.ui.Translations.get(settings.appLanguage, "song_singular") else com.example.ui.Translations.get(settings.appLanguage, "songs")
                        Text(
                            text = "$albumsCount $albumsWord • ${tracks.size} $songsWord",
                            fontSize = 14.sp,
                            color = GlassTextSecondary
                        )
                        if (artistInfo?.bio != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• " + com.example.ui.Translations.get(settings.appLanguage, "biography"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                var isRefreshingDetail by remember { mutableStateOf(false) }
                val detailScope = rememberCoroutineScope()

                IconButton(
                    onClick = {
                        if (!isRefreshingDetail) {
                            isRefreshingDetail = true
                            detailScope.launch {
                                try {
                                    val sample = tracks.firstOrNull()
                                    val newInfo = com.example.data.ArtistInfoFetcher.getArtistInfo(
                                        artistName = artistName,
                                        trackTitle = sample?.title,
                                        albumName = sample?.album,
                                        genre = sample?.genre
                                    )
                                    artistInfo = newInfo
                                } finally {
                                    isRefreshingDetail = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    enabled = !isRefreshingDetail
                ) {
                    if (isRefreshingDetail) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar foto e información", tint = Color.White)
                    }
                }
            }
        }
        
        item {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { 
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/${Uri.encode(artistName)}"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF114221), contentColor = Color(0xFF1DB954)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Spotify", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = { 
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=${Uri.encode(artistName)}"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF421111), contentColor = Color(0xFFFF0000)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("YouTube", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = { 
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.apple.com/search?term=${Uri.encode(artistName)}"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF331122), contentColor = Color(0xFFFA243C)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Apple Music", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            contentDescription = "Play",
                            tint = playIconTint,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
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
                
                Spacer(modifier = Modifier.height(24.dp))

                // Albums of this artist
                val artistAlbums = remember(tracks) {
                    tracks.groupBy { it.album }
                        .filterKeys { it.isNotBlank() }
                        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                }

                if (artistAlbums.isNotEmpty()) {
                    Text(
                        text = com.example.ui.Translations.get(settings.appLanguage, "albums"),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(artistAlbums.entries.toList()) { (albumName, albumTracks) ->
                            val firstTrack = albumTracks.firstOrNull()
                            Card(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clickable {
                                        if (onNavigateToAlbum != null) {
                                            onNavigateToAlbum(albumName)
                                        } else {
                                            viewingAlbumName = albumName
                                        }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF2B2B2B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (firstTrack != null) {
                                            TrackImage(track = firstTrack, modifier = Modifier.fillMaxSize())
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = albumName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val countWord = if (albumTracks.size == 1) com.example.ui.Translations.get(settings.appLanguage, "song_singular") else com.example.ui.Translations.get(settings.appLanguage, "songs")
                                    Text(
                                        text = "${albumTracks.size} $countWord",
                                        fontSize = 12.sp,
                                        color = GlassTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                Text(
                    text = com.example.ui.Translations.get(settings.appLanguage, "all_tracks"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        itemsIndexed(tracks) { index, track ->
            val artworkBitmap: androidx.compose.ui.graphics.ImageBitmap? = null
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlayTrack(index) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}",
                    color = GlassTextMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp)
                )
                
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray)) {
                    if (artworkBitmap != null) {
                        Image(bitmap = artworkBitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        TrackImage(track = track, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = track.title, 
                            color = MaterialTheme.colorScheme.onBackground, 
                            fontSize = 16.sp, 
                            fontWeight = FontWeight.SemiBold, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (track.lyrics.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Lyrics available",
                                tint = GlassTextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(text = track.album, color = GlassTextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = String.format("%d:%02d", (track.durationMs / 60000), (track.durationMs % 60000) / 1000),
                    color = GlassTextMuted,
                    fontSize = 12.sp
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun ArtistAboutScreen(
    artistName: String,
    language: String = "en",
    sampleTrack: Track? = null,
    onBack: () -> Unit
) {
    val cacheRevision by ArtistInfoFetcher.cacheRevision.collectAsState()
    var artistInfo by remember(artistName, cacheRevision) { mutableStateOf<ArtistInfo?>(ArtistInfoFetcher.getCachedArtistInfo(artistName)) }
    
    LaunchedEffect(artistName, cacheRevision) {
        val cached = ArtistInfoFetcher.getCachedArtistInfo(artistName)
        if (cached != null && !cached.imageUrl.isNullOrBlank() && !cached.bio.isNullOrBlank()) {
            artistInfo = cached
        } else {
            if (cached != null) artistInfo = cached
            artistInfo = ArtistInfoFetcher.fetchArtistInfo(
                rawArtistName = artistName,
                trackTitle = sampleTrack?.title,
                albumName = sampleTrack?.album,
                genre = sampleTrack?.genre
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(Color.Black)
        ) {
            if (artistInfo?.imageUrl != null) {
                AsyncImage(
                    model = artistInfo?.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black))))
            
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = com.example.ui.Translations.get(language, "close"), tint = Color.White)
            }
            
            Text(
                text = artistName,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)
            )
        }
        
        Column(modifier = Modifier.padding(24.dp)) {
            Text(text = "${artistInfo?.followers ?: "0"}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(text = com.example.ui.Translations.get(language, "followers").uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GlassTextMuted, letterSpacing = 1.sp)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(text = "${artistInfo?.listeners ?: "0"}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(text = com.example.ui.Translations.get(language, "monthly_listeners").uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GlassTextMuted, letterSpacing = 1.sp)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(text = artistInfo?.origin ?: com.example.ui.Translations.get(language, "unknown_artist"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(text = com.example.ui.Translations.get(language, "origin").uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GlassTextMuted, letterSpacing = 1.sp)
            
            Spacer(modifier = Modifier.height(40.dp))
            
            val rawBio = artistInfo?.bio
            val targetLangCode = remember(language) { com.example.data.TranslationHelper.mapToLanguageCode(language) }
            val isAlreadyTarget = remember(rawBio, targetLangCode) {
                if (rawBio.isNullOrBlank()) true
                else com.example.data.TranslationHelper.isAlreadyInTargetLanguage(rawBio, targetLangCode)
            }
            var translatedBio by remember(rawBio, targetLangCode) { mutableStateOf<String?>(null) }
            var isTranslating by remember(rawBio, targetLangCode) { mutableStateOf(false) }
            var showTranslated by remember(rawBio, targetLangCode) { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()

            val displayBioText = when {
                showTranslated && !translatedBio.isNullOrBlank() -> translatedBio!!
                !rawBio.isNullOrBlank() -> rawBio
                else -> ""
            }

            if (!rawBio.isNullOrBlank() && !isAlreadyTarget) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isTranslating) {
                            if (showTranslated) {
                                showTranslated = false
                            } else if (!translatedBio.isNullOrBlank()) {
                                showTranslated = true
                            } else {
                                isTranslating = true
                                coroutineScope.launch {
                                    val result = com.example.data.TranslationHelper.translateText(rawBio, language)
                                    translatedBio = result
                                    showTranslated = true
                                    isTranslating = false
                                }
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Translate",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isTranslating -> com.example.ui.Translations.get(language, "translating_bio")
                            showTranslated -> com.example.ui.Translations.get(language, "original_text")
                            else -> com.example.ui.Translations.get(language, "translate_bio")
                        },
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = displayBioText,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

