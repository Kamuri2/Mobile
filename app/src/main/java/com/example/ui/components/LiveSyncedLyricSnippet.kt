package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LyricsParser
import com.example.model.Track
import com.example.ui.theme.*

@Composable
fun LiveSyncedLyricSnippet(
    track: Track?,
    currentPositionMs: Long,
    onOpenLyrics: () -> Unit,
    onRetryScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val parseResult = remember(track?.id, track?.lyrics) {
        LyricsParser.parse(track?.lyrics, track?.durationMs ?: 0L)
    }

    val lines = parseResult.lines
    val activeIndex = remember(lines, currentPositionMs) {
        LyricsParser.getActiveLineIndex(lines, currentPositionMs)
    }

    val currentLine = if (activeIndex in lines.indices) lines[activeIndex].text else null
    val nextLine = if (activeIndex + 1 in lines.indices) lines[activeIndex + 1].text else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MidnightSurfaceVariant.copy(alpha = 0.65f))
            .clickable { onOpenLyrics() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("live_lyrics_snippet_box"),
        contentAlignment = Alignment.Center
    ) {
        if (lines.isNotEmpty() && !currentLine.isNullOrBlank()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = currentLine,
                    transitionSpec = {
                        (slideInVertically { height -> height / 2 } + fadeIn())
                            .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut())
                    },
                    label = "lyric_line_animation"
                ) { lineText ->
                    Text(
                        text = lineText,
                        color = NeonCyan,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                }

                if (!nextLine.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = nextLine,
                        color = TextTertiary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            // When no lyrics are available yet or track has no lyrics loaded
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Letras",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (track?.lyrics.isNullOrBlank()) "Toca para ver letras o escanear" else "♪ Instrumental...",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                if (track?.lyrics.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = onRetryScan,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("rescan_extras_quick_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reintentar escaneo de extras",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
