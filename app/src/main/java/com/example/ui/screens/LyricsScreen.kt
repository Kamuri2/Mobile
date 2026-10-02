package com.example.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LyricsLine
import com.example.model.LyricsParser
import com.example.model.Track
import com.example.ui.components.GlassCard
import com.example.ui.theme.GlassTextMuted
import com.example.ui.theme.GlassTextPrimary
import com.example.ui.theme.GlassTextSecondary

import com.example.ui.theme.OrangeGlow
import com.example.ui.theme.PurpleAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    currentTrack: Track?,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    onSaveCustomLyrics: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(currentTrack?.id) {
        currentTrack?.let { track ->
            if (track.lyrics.isBlank()) {
                com.example.player.AudioPlayerManager.getInstance(context).loadLyricsForTrack(track)
            }
        }
    }

    val lyricsText = currentTrack?.lyrics ?: ""
    val parsedLyrics = remember(lyricsText) { LyricsParser.parseLrc(lyricsText) }

    var showEditSheet by remember { mutableStateOf(false) }
    var editableLyricsText by remember(lyricsText) { mutableStateOf(lyricsText) }

    val listState = rememberLazyListState()

    // Determine current active lyrics line based on audio position
    val isSynced = remember(parsedLyrics) { parsedLyrics.any { it.timestampMs > 0L } }

    val activeLineIndex = remember(parsedLyrics, currentPositionMs, isSynced) {
        if (parsedLyrics.isEmpty() || !isSynced) -1
        else {
            val idx = parsedLyrics.indexOfLast { it.timestampMs <= currentPositionMs }
            if (idx != -1) idx else 0
        }
    }

    // Auto-scroll to active lyrics line only if synced
    LaunchedEffect(activeLineIndex, isSynced) {
        if (isSynced && activeLineIndex in parsedLyrics.indices) {
            listState.animateScrollToItem((activeLineIndex - 2).coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Letras de la Canción",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlassTextPrimary
                    )
                    Text(
                        text = currentTrack?.title ?: "Sin Canción",
                        fontSize = 12.sp,
                        color = GlassTextSecondary
                    )
                }
            }

            IconButton(onClick = { showEditSheet = true }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editar Letras",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (parsedLyrics.isEmpty()) {
            if (lyricsText.isNotBlank()) {
                // Static / Unsynced Plain Text Lyrics Glass Card
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = Color(0x14FFFFFF),
                        borderColor = Color.White.copy(alpha = 0.15f)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = lyricsText,
                                fontSize = 16.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Medium,
                                color = GlassTextPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                // Empty Lyrics Fallback Card
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        backgroundColor = Color(0x1AFFFFFF)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No se encontraron letras integradas",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlassTextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Puedes pegar letras en formato LRC o texto plano pulsando el botón de editar arriba.",
                                fontSize = 13.sp,
                                color = GlassTextMuted,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showEditSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Agregar Letras Manualmente", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // Synchronized Glass Karaoke Lyrics View
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(parsedLyrics) { index, line ->
                    val isActive = index == activeLineIndex

                    val textColor by animateColorAsState(
                        targetValue = if (isActive || !isSynced) MaterialTheme.colorScheme.primary else GlassTextSecondary,
                        animationSpec = tween(300),
                        label = "lyrics_color"
                    )

                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lyric_line_$index"),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color(0x10FFFFFF),
                        borderColor = if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                        glowColor = if (isActive) MaterialTheme.colorScheme.primary else null,
                        onClick = {
                            if (line.timestampMs > 0) {
                                onSeek(line.timestampMs)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = line.text,
                                fontSize = if (isActive) 18.sp else 15.sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                            if (line.timestampMs > 0) {
                                Text(
                                    text = line.formatTime(),
                                    fontSize = 11.sp,
                                    color = if (isActive) PurpleAccent else GlassTextMuted
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // Modal to View or Edit Lyrics
    if (showEditSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            containerColor = Color(0xFF140D09)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Editar Letras de la Canción",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Soporta formato timestamp LRC (e.g. [00:12.50]Texto) o texto normal.",
                    fontSize = 12.sp,
                    color = GlassTextMuted
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = editableLyricsText,
                    onValueChange = { editableLyricsText = it },
                    placeholder = { Text("Escribe o pega aquí las letras...", color = GlassTextMuted) },
                    minLines = 8,
                    maxLines = 12,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            onSaveCustomLyrics(editableLyricsText)
                            showEditSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Guardar Letras", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

