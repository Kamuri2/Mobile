package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.SocialRepository
import com.example.data.UserRepository
import com.example.data.local.PlaylistEntity
import com.example.model.Track
import com.example.ui.components.TrackImage
import com.example.ui.theme.GlassTextMuted
import com.example.ui.theme.GlassTextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    settings: com.example.model.AppSettings,
    socialRepository: SocialRepository,
    favorites: Set<Long>,
    allTracks: List<Track>,
    initialPlaylistName: String?,
    onCreatePlaylist: (String) -> Unit,
    onPlayPlaylist: (List<Track>, Int) -> Unit,
    onDismissOverlay: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playlists by socialRepository.getAllPlaylists().collectAsState(initial = emptyList())
    val userRepository = remember { UserRepository.getInstance(context) }
    val userProfile by userRepository.getUserProfile().collectAsState(initial = null)

    val isDarkMode = settings.isDarkMode
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF111111)
    val textSecondary = if (isDarkMode) Color(0xB3FFFFFF) else Color(0xFF555555)
    val iconTint = if (isDarkMode) Color.White else Color(0xFF1E1E1E)
    val cardBg = if (isDarkMode) Color(0x1AFFFFFF) else Color(0x0C000000)
    val dialogBg = if (isDarkMode) Color(0xFF202020) else Color.White

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistDesc by remember { mutableStateOf("") }

    var selectedPlaylistName by remember { mutableStateOf<String?>(initialPlaylistName) }
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }

    // For editing playlist
    var editPlaylistTarget by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showAddSongsSheetForPlaylist by remember { mutableStateOf<Long?>(null) }

    val playlistPrefs = remember { context.getSharedPreferences("playlist_custom_covers", Context.MODE_PRIVATE) }
    var favoritesCoverUri by remember { mutableStateOf(playlistPrefs.getString("cover_favorites", null)) }
    var allSongsCoverUri by remember { mutableStateOf(playlistPrefs.getString("cover_all_songs", null)) }
    var pendingPhotoTarget by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val savedUriString = socialRepository.savePlaylistImage(context, uri) ?: uri.toString()
                when (pendingPhotoTarget) {
                    "favorites" -> {
                        playlistPrefs.edit().putString("cover_favorites", savedUriString).apply()
                        favoritesCoverUri = savedUriString
                    }
                    "all_songs" -> {
                        playlistPrefs.edit().putString("cover_all_songs", savedUriString).apply()
                        allSongsCoverUri = savedUriString
                    }
                    else -> {
                        val pId = pendingPhotoTarget?.toLongOrNull() ?: editPlaylistTarget?.playlistId
                        if (pId != null) {
                            val currentTarget = playlists.find { it.playlistId == pId } ?: editPlaylistTarget
                            if (currentTarget != null) {
                                val updated = currentTarget.copy(imageUri = savedUriString)
                                socialRepository.updatePlaylist(updated)
                                playlistPrefs.edit()
                                    .putString("cover_${pId}", savedUriString)
                                    .putString("cover_${currentTarget.name}", savedUriString)
                                    .apply()
                                if (editPlaylistTarget?.playlistId == pId) {
                                    editPlaylistTarget = updated
                                }
                            }
                        }
                    }
                }
                pendingPhotoTarget = null
            }
        }
    }

    LaunchedEffect(initialPlaylistName) {
        if (initialPlaylistName != null) {
            selectedPlaylistName = initialPlaylistName
        }
    }

    LaunchedEffect(selectedPlaylistName, playlists) {
        if (selectedPlaylistName != null && selectedPlaylistName != "Mis Favoritas" && selectedPlaylistName != "All Songs") {
            val p = playlists.find { it.name == selectedPlaylistName || it.playlistId == selectedPlaylistId }
            if (p != null) {
                selectedPlaylistId = p.playlistId
                selectedPlaylistName = p.name
            }
        }
    }

    BackHandler(enabled = selectedPlaylistName != null || editPlaylistTarget != null || showAddSongsSheetForPlaylist != null || onDismissOverlay != null) {
        if (showAddSongsSheetForPlaylist != null) {
            showAddSongsSheetForPlaylist = null
        } else if (editPlaylistTarget != null) {
            editPlaylistTarget = null
        } else if (selectedPlaylistName != null) {
            if (onDismissOverlay != null) onDismissOverlay()
            else {
                selectedPlaylistName = null
                selectedPlaylistId = null
            }
        } else {
            onDismissOverlay?.invoke()
        }
    }

    val favoriteTracks = remember(allTracks, favorites) {
        allTracks.filter { favorites.contains(it.id) }
    }

    // Modal to Edit Playlist
    if (editPlaylistTarget != null) {
        val currentEdit = editPlaylistTarget!!
        var editName by remember(currentEdit) { mutableStateOf(currentEdit.name) }
        var editDesc by remember(currentEdit) { mutableStateOf(currentEdit.description ?: "") }

        AlertDialog(
            onDismissRequest = { editPlaylistTarget = null },
            title = {
                Text(
                    text = "Editar Playlist",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cover photo preview with clickable edit icon
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF282828))
                            .clickable {
                                pendingPhotoTarget = currentEdit.playlistId.toString()
                                imagePicker.launch("image/*")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val currentImageUri = editPlaylistTarget?.imageUri ?: currentEdit.imageUri
                        if (currentImageUri != null) {
                            AsyncImage(
                                model = Uri.parse(currentImageUri),
                                contentDescription = "Cover preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = "Cambiar imagen", tint = Color.White)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Cambiar foto", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nombre de la playlist") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Descripción (opcional)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            coroutineScope.launch {
                                val updated = currentEdit.copy(name = editName.trim(), description = editDesc.trim().ifEmpty { null })
                                socialRepository.updatePlaylist(updated)
                                if (selectedPlaylistId == updated.playlistId) {
                                    selectedPlaylistName = updated.name
                                }
                                editPlaylistTarget = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Guardar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editPlaylistTarget = null }) {
                    Text("Cancelar", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }

    // Modal to Add Songs to Playlist
    if (showAddSongsSheetForPlaylist != null) {
        val targetPlaylistId = showAddSongsSheetForPlaylist!!
        var searchQuery by remember { mutableStateOf("") }
        var currentPlaylistTrackIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

        LaunchedEffect(targetPlaylistId) {
            socialRepository.getPlaylistWithTracks(targetPlaylistId).collect { pwt ->
                if (pwt != null) {
                    currentPlaylistTrackIds = pwt.tracks.map { it.id }.toSet()
                }
            }
        }

        val filteredSongs = remember(allTracks, searchQuery) {
            if (searchQuery.isBlank()) allTracks
            else allTracks.filter { it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true) }
        }

        ModalBottomSheet(
            onDismissRequest = { showAddSongsSheetForPlaylist = null },
            containerColor = Color(0xFF161616)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Agregar canciones a la playlist",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar canción o artista...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSongs) { track ->
                        val isAlreadyIn = currentPlaylistTrackIds.contains(track.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isAlreadyIn) Color(0x221DB954) else Color(0x14FFFFFF))
                                .clickable {
                                    coroutineScope.launch {
                                        if (isAlreadyIn) {
                                            socialRepository.removeTrackFromPlaylist(targetPlaylistId, track.id)
                                        } else {
                                            socialRepository.addTrackToPlaylist(targetPlaylistId, track.id)
                                        }
                                    }
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            ) {
                                TrackImage(track = track, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    fontSize = 12.sp,
                                    color = GlassTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    if (isAlreadyIn) {
                                        socialRepository.removeTrackFromPlaylist(targetPlaylistId, track.id)
                                    } else {
                                        socialRepository.addTrackToPlaylist(targetPlaylistId, track.id)
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = if (isAlreadyIn) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = if (isAlreadyIn) "Remove" else "Add",
                                    tint = if (isAlreadyIn) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (selectedPlaylistName != null) {
        // DETAILED SPOTIFY-LIKE PLAYLIST SCREEN (Screenshot 4 style)
        val isFavorites = selectedPlaylistName == "Mis Favoritas"
        val isAllSongs = selectedPlaylistName == "All Songs"
        val currentPlaylistEntity = playlists.find { it.playlistId == selectedPlaylistId || it.name == selectedPlaylistName }

        val tracks = if (isFavorites) {
            favoriteTracks
        } else if (isAllSongs) {
            allTracks
        } else {
            var pTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
            LaunchedEffect(selectedPlaylistId) {
                if (selectedPlaylistId != null) {
                    socialRepository.getPlaylistWithTracks(selectedPlaylistId!!).collect { pwt ->
                        if (pwt != null) {
                            val dbTracks = pwt.tracks.mapNotNull { entity -> allTracks.find { it.id == entity.id } }
                            pTracks = dbTracks
                        }
                    }
                }
            }
            pTracks
        }

        SpotifyStylePlaylistDetail(
            playlistName = when {
                isFavorites -> com.example.ui.Translations.get(settings.appLanguage, "favorite_songs")
                isAllSongs -> "All Songs"
                else -> currentPlaylistEntity?.name ?: selectedPlaylistName!!
            },
            description = when {
                isFavorites -> "Tus canciones favoritas marcadas con me gusta"
                isAllSongs -> "Biblioteca completa de música"
                else -> currentPlaylistEntity?.description ?: ""
            },
            imageUri = when {
                isFavorites -> favoritesCoverUri
                isAllSongs -> allSongsCoverUri
                else -> currentPlaylistEntity?.imageUri
            },
            isFavorites = isFavorites,
            isAllSongs = isAllSongs,
            creatorName = userProfile?.name ?: "Usuario",
            creatorImageUri = userProfile?.profileImageUri,
            tracks = tracks,
            onBack = {
                if (onDismissOverlay != null) {
                    onDismissOverlay()
                } else {
                    selectedPlaylistName = null
                    selectedPlaylistId = null
                }
            },
            onPlayTrack = { idx -> onPlayPlaylist(tracks, idx) },
            onShuffleAll = { onPlayPlaylist(tracks.shuffled(), 0) },
            onChangeCover = {
                when {
                    isFavorites -> {
                        pendingPhotoTarget = "favorites"
                        imagePicker.launch("image/*")
                    }
                    isAllSongs -> {
                        pendingPhotoTarget = "all_songs"
                        imagePicker.launch("image/*")
                    }
                    currentPlaylistEntity != null -> {
                        pendingPhotoTarget = currentPlaylistEntity.playlistId.toString()
                        imagePicker.launch("image/*")
                    }
                }
            },
            onEdit = {
                if (currentPlaylistEntity != null) {
                    editPlaylistTarget = currentPlaylistEntity
                    pendingPhotoTarget = currentPlaylistEntity.playlistId.toString()
                }
            },
            onAddSongs = {
                if (currentPlaylistEntity != null) {
                    showAddSongsSheetForPlaylist = currentPlaylistEntity.playlistId
                }
            },
            onRemoveTrack = { trackId ->
                if (currentPlaylistEntity != null) {
                    coroutineScope.launch {
                        socialRepository.removeTrackFromPlaylist(currentPlaylistEntity.playlistId, trackId)
                    }
                }
            },
            onDeletePlaylist = {
                if (currentPlaylistEntity != null) {
                    coroutineScope.launch {
                        socialRepository.deletePlaylist(currentPlaylistEntity)
                        selectedPlaylistName = null
                        selectedPlaylistId = null
                    }
                }
            }
        )
    } else {
        // MAIN PLAYLISTS LIST SCREEN
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onDismissOverlay != null) {
                            IconButton(onClick = onDismissOverlay) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = com.example.ui.Translations.get(settings.appLanguage, "playlists"),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(com.example.ui.Translations.get(settings.appLanguage, "create"), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }

            if (showCreateDialog) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            com.example.ui.Translations.get(settings.appLanguage, "new_playlist"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text(com.example.ui.Translations.get(settings.appLanguage, "list_name"), color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPlaylistDesc,
                            onValueChange = { newPlaylistDesc = it },
                            placeholder = { Text("Descripción opcional...", color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { showCreateDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                                Text(com.example.ui.Translations.get(settings.appLanguage, "cancel"), color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newPlaylistName.isNotBlank()) {
                                        coroutineScope.launch {
                                            socialRepository.createPlaylist(
                                                name = newPlaylistName.trim(),
                                                description = newPlaylistDesc.trim().ifEmpty { null }
                                            )
                                        }
                                        newPlaylistName = ""
                                        newPlaylistDesc = ""
                                        showCreateDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(com.example.ui.Translations.get(settings.appLanguage, "save"), color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    // Favorites Special Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPlaylistName = "Mis Favoritas"
                            },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1510))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE53935))
                                    .clickable {
                                        pendingPhotoTarget = "favorites"
                                        imagePicker.launch("image/*")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (favoritesCoverUri != null) {
                                    AsyncImage(
                                        model = Uri.parse(favoritesCoverUri),
                                        contentDescription = "Cover",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(2.dp)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.7f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(com.example.ui.Translations.get(settings.appLanguage, "favorite_songs"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("${favoriteTracks.size} " + com.example.ui.Translations.get(settings.appLanguage, "songs_marked"), fontSize = 13.sp, color = GlassTextSecondary)
                            }
                            IconButton(onClick = {
                                pendingPhotoTarget = "favorites"
                                imagePicker.launch("image/*")
                            }) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = "Cambiar foto", tint = Color.White.copy(alpha = 0.75f))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = GlassTextSecondary)
                        }
                    }
                }

                item {
                    // All Songs Special Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPlaylistName = "All Songs"
                            },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A5F))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1976D2))
                                    .clickable {
                                        pendingPhotoTarget = "all_songs"
                                        imagePicker.launch("image/*")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (allSongsCoverUri != null) {
                                    AsyncImage(
                                        model = Uri.parse(allSongsCoverUri),
                                        contentDescription = "Cover",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(2.dp)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.7f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("All Songs", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("${allTracks.size} songs", fontSize = 13.sp, color = GlassTextSecondary)
                            }
                            IconButton(onClick = {
                                pendingPhotoTarget = "all_songs"
                                imagePicker.launch("image/*")
                            }) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = "Cambiar foto", tint = Color.White.copy(alpha = 0.75f))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = GlassTextSecondary)
                        }
                    }
                }

                // Custom User Playlists
                items(playlists) { playlist ->
                    var trackCount by remember { mutableIntStateOf(0) }
                    LaunchedEffect(playlist.playlistId) {
                        socialRepository.getPlaylistWithTracks(playlist.playlistId).collect { pwt ->
                            if (pwt != null) trackCount = pwt.tracks.size
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x1AFFFFFF))
                            .clickable {
                                selectedPlaylistName = playlist.name
                                selectedPlaylistId = playlist.playlistId
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF222222))
                                .clickable {
                                    pendingPhotoTarget = playlist.playlistId.toString()
                                    imagePicker.launch("image/*")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (playlist.imageUri != null) {
                                AsyncImage(
                                    model = Uri.parse(playlist.imageUri),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(2.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = playlist.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            val descText = if (!playlist.description.isNullOrBlank()) playlist.description!! else "$trackCount canciones"
                            Text(text = descText, fontSize = 13.sp, color = GlassTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        IconButton(onClick = {
                            pendingPhotoTarget = playlist.playlistId.toString()
                            imagePicker.launch("image/*")
                        }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Cambiar portada", tint = GlassTextMuted)
                        }

                        IconButton(onClick = {
                            editPlaylistTarget = playlist
                            pendingPhotoTarget = playlist.playlistId.toString()
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Playlist", tint = GlassTextMuted)
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
        }
    }
}

@Composable
fun SpotifyStylePlaylistDetail(
    playlistName: String,
    description: String,
    imageUri: String?,
    isFavorites: Boolean,
    isAllSongs: Boolean,
    creatorName: String,
    creatorImageUri: String?,
    tracks: List<Track>,
    onBack: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onShuffleAll: () -> Unit,
    onChangeCover: () -> Unit,
    onEdit: () -> Unit,
    onAddSongs: () -> Unit,
    onRemoveTrack: (Long) -> Unit,
    onDeletePlaylist: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("¿Eliminar playlist?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Esta acción eliminará la playlist '$playlistName'. Las canciones de tu biblioteca permanecerán intactas.", color = GlassTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePlaylist()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }

    // Gradient background based on playlist theme
    val topGradientColor = when {
        isFavorites -> Color(0xFF5C1D1D)
        isAllSongs -> Color(0xFF1E3A5F)
        else -> Color(0xFF384428) // Deep aesthetic mossy/olive green as in Screenshot 4
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        topGradientColor,
                        topGradientColor,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    ),
                    startY = 0f,
                    endY = 1200f
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top App Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    if (!isFavorites && !isAllSongs) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Eliminar playlist",
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Centered Large Artwork
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .shadow(elevation = 16.dp, shape = RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1F1F1F))
                            .clickable { onChangeCover() },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            imageUri != null -> {
                                AsyncImage(
                                    model = Uri.parse(imageUri),
                                    contentDescription = "Playlist Cover",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            isFavorites -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFF8E0000)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(90.dp)
                                    )
                                }
                            }
                            isAllSongs -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.linearGradient(listOf(Color(0xFF1976D2), Color(0xFF0D47A1)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(90.dp)
                                    )
                                }
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.linearGradient(listOf(Color(0xFF333333), Color(0xFF1A1A1A)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(80.dp)
                                    )
                                }
                            }
                        }

                        // Floating Camera Overlay Badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Cambiar foto",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Playlist Title & Description
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = playlistName,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontStyle = FontStyle.Normal
                    )

                    if (description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = description,
                            fontSize = 14.sp,
                            color = GlassTextSecondary,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // User avatar & metadata row
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            if (creatorImageUri != null) {
                                AsyncImage(
                                    model = Uri.parse(creatorImageUri),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = creatorName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = " • ${tracks.size} canciones",
                            fontSize = 13.sp,
                            color = GlassTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action buttons (Add, Edit, Shuffle, Play)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = onChangeCover,
                                label = { Text("Cambiar foto", color = Color.White, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0x33FFFFFF)),
                                shape = RoundedCornerShape(20.dp)
                            )
                            if (!isAllSongs && !isFavorites) {
                                AssistChip(
                                    onClick = onAddSongs,
                                    label = { Text("Agregar", color = Color.White, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0x33FFFFFF)),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                AssistChip(
                                    onClick = onEdit,
                                    label = { Text("Editar", color = Color.White, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0x33FFFFFF)),
                                    shape = RoundedCornerShape(20.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(onClick = onShuffleAll) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Big Spotify Green Play Button
                            IconButton(
                                onClick = { if (tracks.isNotEmpty()) onPlayTrack(0) },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play all",
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0x1FFFFFFF), modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Songs List
            if (tracks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Esta playlist está vacía",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Agrega tus canciones preferidas para empezar a escuchar.",
                            color = GlassTextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        if (!isAllSongs && !isFavorites) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onAddSongs,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Agregar canciones", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(tracks) { index, track ->
                    var showTrackMenu by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayTrack(index) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF222222))
                        ) {
                            TrackImage(track = track, modifier = Modifier.fillMaxSize())
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist,
                                fontSize = 12.sp,
                                color = GlassTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!isAllSongs && !isFavorites) {
                            Box {
                                IconButton(onClick = { showTrackMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Opciones",
                                        tint = GlassTextSecondary
                                    )
                                }
                                DropdownMenu(
                                    expanded = showTrackMenu,
                                    onDismissRequest = { showTrackMenu = false },
                                    modifier = Modifier.background(Color(0xFF282828))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Quitar de esta playlist", color = Color(0xFFEF4444)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                                        },
                                        onClick = {
                                            showTrackMenu = false
                                            onRemoveTrack(track.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}
