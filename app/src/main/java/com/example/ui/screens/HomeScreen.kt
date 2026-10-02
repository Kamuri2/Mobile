package com.example.ui.screens

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material.icons.filled.PlaylistPlay
import com.example.data.ArtistImageRepository
import com.example.data.SocialRepository
import com.example.data.local.PlaylistEntity
import com.example.data.local.UserEntity
import com.example.model.AppSettings
import com.example.model.Track
import com.example.ui.Translations
import com.example.ui.components.TrackImage
import com.example.ui.theme.GlassTextMuted
import com.example.ui.theme.GlassTextSecondary

@Composable
fun HomeScreen(
    tracks: List<Track>,
    currentTrack: Track?,
    currentIndex: Int,
    isPlaying: Boolean,
    userProfile: UserEntity?,
    socialRepository: SocialRepository,
    onTrackSelect: (List<Track>, Int) -> Unit,
    onShuffleAll: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFolder: () -> Unit = {},
    onRequestPermissions: () -> Unit = {},
    onNavigateToPlaylist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    settings: AppSettings = AppSettings(),
    modifier: Modifier = Modifier
) {
    val playlists by socialRepository.getAllPlaylists().collectAsState(initial = emptyList())
    val topArtists by socialRepository.getTopArtistsToday().collectAsState(initial = emptyList())
    
    val lang = settings.appLanguage

    val context = LocalContext.current
    val playlistPrefs = remember { context.getSharedPreferences("playlist_custom_covers", Context.MODE_PRIVATE) }
    var favoritesCoverUri by remember { mutableStateOf(playlistPrefs.getString("cover_favorites", null)) }
    var allSongsCoverUri by remember { mutableStateOf(playlistPrefs.getString("cover_all_songs", null)) }
    var prefsUpdateTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "cover_favorites") favoritesCoverUri = prefs.getString("cover_favorites", null)
            if (key == "cover_all_songs") allSongsCoverUri = prefs.getString("cover_all_songs", null)
            prefsUpdateTrigger++
        }
        playlistPrefs.registerOnSharedPreferenceChangeListener(listener)
        favoritesCoverUri = playlistPrefs.getString("cover_favorites", null)
        allSongsCoverUri = playlistPrefs.getString("cover_all_songs", null)
        onDispose {
            playlistPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Recommendations (Random Albums changing every hour)
    val currentHour = (System.currentTimeMillis() / 3600000).toInt()
    val recommendedAlbums = remember(currentHour, tracks) {
        if (tracks.isNotEmpty()) {
            val random = java.util.Random(currentHour.toLong())
            val allAlbums = tracks.distinctBy { it.album }
            allAlbums.shuffled(random).take(10)
        } else {
            emptyList()
        }
    }

    if (tracks.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = Translations.get(lang, "empty_library_title"),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = Translations.get(lang, "empty_library_desc"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Text(
                        text = Translations.get(lang, "grant_permissions"),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onOpenProfile() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (userProfile?.profileImageUri != null) {
                            AsyncImage(
                                model = Uri.parse(userProfile.profileImageUri),
                                contentDescription = "Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = userProfile?.name?.take(1)?.uppercase() ?: "U",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (!userProfile?.name.isNullOrBlank()) "${userProfile?.name}" else Translations.get(lang, "welcome_music_player"),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Top Grid (Liked Songs, All Songs & Playlists)
        item {
            data class GridItem(val label: String, val imageUri: String?, val isSpecial: Int, val onClick: () -> Unit)
            val gridItems = remember(playlists, favoritesCoverUri, allSongsCoverUri, prefsUpdateTrigger) {
                val list = mutableListOf<GridItem>()
                list.add(GridItem(Translations.get(lang, "favorite_songs"), favoritesCoverUri, 1, { onNavigateToPlaylist("Mis Favoritas") }))
                list.add(GridItem(Translations.get(lang, "all_tracks"), allSongsCoverUri, 2, { onNavigateToPlaylist("All Songs") }))
                
                // Add playlists up to 4 more to make max 6
                playlists.take(4).forEach { p ->
                    val pCover = p.imageUri
                        ?: playlistPrefs.getString("cover_${p.playlistId}", null)
                        ?: playlistPrefs.getString("cover_${p.name}", null)
                    list.add(GridItem(p.name, pCover, 0, { onNavigateToPlaylist(p.name) }))
                }
                list
            }

            Column {
                val chunked = gridItems.chunked(2)
                chunked.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { item.onClick() },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .background(
                                                when(item.isSpecial) {
                                                    1 -> Color(0xFF5A3598)
                                                    2 -> Color(0xFF1E88E5)
                                                    else -> Color(0xFF888888)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!item.imageUri.isNullOrBlank()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(item.imageUri)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = item.label,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else if (item.isSpecial == 1) {
                                            Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                                        } else if (item.isSpecial == 2) {
                                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                                        } else {
                                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = item.label,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f)) // filler
                        }
                    }
                }
            }
        }

        // Playlists Section (Tus Listas de reproducción)
        if (playlists.isNotEmpty()) {
            item {
                Text(
                    text = Translations.get(lang, "playlists"),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(playlists) { p ->
                        val pCover = p.imageUri
                            ?: playlistPrefs.getString("cover_${p.playlistId}", null)
                            ?: playlistPrefs.getString("cover_${p.name}", null)
                        Column(
                            modifier = Modifier
                                .width(145.dp)
                                .clickable { onNavigateToPlaylist(p.name) },
                            horizontalAlignment = Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(145.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF282828)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!pCover.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(pCover)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = p.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = p.name,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (!p.description.isNullOrBlank()) p.description!! else "Playlist",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Middle Section: Top Artists
        if (topArtists.isNotEmpty()) {
            item {
                Text(
                    text = Translations.get(lang, "top_artists_today"),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(topArtists) { artist ->
                        var artistImageUrl by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(artist.artist) {
                            artistImageUrl = ArtistImageRepository.getArtistImageUrl(artist.artist)
                        }
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(100.dp)
                                .clickable { onNavigateToArtist(artist.artist) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF333333)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (artistImageUrl != null) {
                                    AsyncImage(
                                        model = artistImageUrl,
                                        contentDescription = artist.artist,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = artist.artist.take(1).uppercase(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = artist.artist,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Bottom Section: Recommendations (Albums)
        if (recommendedAlbums.isNotEmpty()) {
            item {
                Text(
                    text = Translations.get(lang, "recommended_albums"),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(recommendedAlbums) { trackAlbum ->
                        Column(
                            modifier = Modifier
                                .width(140.dp)
                                .clickable { onNavigateToAlbum(trackAlbum.album) },
                            horizontalAlignment = Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                TrackImage(track = trackAlbum, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = trackAlbum.album,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = trackAlbum.artist,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}
