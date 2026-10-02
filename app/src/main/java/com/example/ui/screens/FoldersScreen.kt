package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Track
import com.example.ui.components.TrackImage
import com.example.ui.theme.GlassTextMuted
import com.example.ui.theme.GlassTextSecondary

@Composable
fun FoldersScreen(
    settings: com.example.model.AppSettings,
    tracks: List<Track>,
    currentTrack: Track?,
    onPlayFolder: (List<Track>, Int) -> Unit,
    onPickFolderUri: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = selectedFolder != null) {
        selectedFolder = null
    }

    val folderGroups = remember(tracks) {
        tracks.groupBy { it.folderName }
    }

    if (selectedFolder != null) {
        val folderTracks = folderGroups[selectedFolder] ?: emptyList()
        FolderDetailScreen(
            folderName = selectedFolder!!,
            tracks = folderTracks,
            currentTrack = currentTrack,
            settings = settings,
            onBack = { selectedFolder = null },
            onPlayFolder = onPlayFolder,
            onShuffleFolder = {
                if (folderTracks.isNotEmpty()) {
                    onPlayFolder(folderTracks.shuffled(), 0)
                }
            }
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = com.example.ui.Translations.get(settings.appLanguage, "folders"),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Folder Grid/List
            if (folderGroups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No music folders found", color = GlassTextMuted)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(folderGroups.keys.toList()) { folderName ->
                        val folderTracks = folderGroups[folderName] ?: emptyList()
                        val representativeTrack = remember(folderTracks) {
                            folderTracks.firstOrNull { it.albumArtUri != null } ?: folderTracks.firstOrNull()
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFolder = folderName },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF222222)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (representativeTrack != null) {
                                        TrackImage(track = representativeTrack, modifier = Modifier.fillMaxSize())
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(2.dp)
                                                .size(18.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.Black.copy(alpha = 0.75f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folderName,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    val subText = if (representativeTrack != null && representativeTrack.artist.isNotBlank() && representativeTrack.artist != "Unknown Artist") {
                                        "${folderTracks.size} audio • ${representativeTrack.artist}"
                                    } else {
                                        "${folderTracks.size} audio files"
                                    }
                                    Text(
                                        text = subText,
                                        fontSize = 13.sp,
                                        color = GlassTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(onClick = { onPlayFolder(folderTracks, 0) }) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play Folder", tint = Color.White)
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(100.dp)) }
                }
            }
        }
    }
}

/**
 * Folder Detail Screen designed with the EXACT same aesthetic and layout as AlbumDetailScreen (Screenshot 3).
 */
@Composable
fun FolderDetailScreen(
    folderName: String,
    tracks: List<Track>,
    currentTrack: Track?,
    settings: com.example.model.AppSettings,
    onBack: () -> Unit,
    onPlayFolder: (List<Track>, Int) -> Unit,
    onShuffleFolder: () -> Unit
) {
    val primaryColor = settings.selectedTheme.primaryColor
    val isDarkMode = settings.isDarkMode
    val surfaceBg = if (isDarkMode) Color(0xFF121212) else Color(0xFFF7F7F9)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF111111)
    val textSecondary = if (isDarkMode) Color(0xB3FFFFFF) else Color(0xFF555555)
    val iconTint = if (isDarkMode) Color.White else Color(0xFF1E1E1E)
    val isLightPrimary = (0.299f * primaryColor.red + 0.587f * primaryColor.green + 0.114f * primaryColor.blue) > 0.5f
    val playIconTint = if (isLightPrimary) Color.Black else Color.White
    val firstTrack = remember(tracks) { tracks.firstOrNull() }

    val backgroundBrush = remember(primaryColor, settings.selectedTheme, isDarkMode) {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                settings.selectedTheme.bgGradientStart.copy(alpha = if (isDarkMode) 0.5f else 0.25f),
                primaryColor.copy(alpha = if (isDarkMode) 0.22f else 0.12f),
                surfaceBg,
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

        // 2. Centered Folder Cover (matches 240.dp Album Cover in Screenshot 3)
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
                        .clip(RoundedCornerShape(16.dp))
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
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // 3. Folder Metadata Section (Left aligned, identical to Screenshot 3)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Folder Title
                Text(
                    text = folderName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    lineHeight = 30.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Artist / Folder chip
                val representativeArtist = firstTrack?.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Varios Artistas"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .padding(vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isDarkMode) Color(0x33FFFFFF) else Color(0x1A000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = representativeArtist,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle: "Carpeta • X canciones"
                val metadataText = "Carpeta • ${tracks.size} " + if (tracks.size == 1) "canción" else "canciones"
                Text(
                    text = metadataText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = textSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Action Bar: Big Play Button + Shuffle Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Big round Play Button
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                            .clickable { if (tracks.isNotEmpty()) onPlayFolder(tracks, 0) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Folder",
                            tint = playIconTint,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Shuffle Button
                    IconButton(
                        onClick = onShuffleFolder,
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

        // 4. Track List matching Screenshot 3
        items(tracks) { track ->
            val isCurrent = currentTrack?.id == track.id
            val trackIndex = tracks.indexOf(track)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlayFolder(tracks, trackIndex) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = if (isCurrent) primaryColor else textPrimary,
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

                IconButton(onClick = { onPlayFolder(tracks, trackIndex) }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}
