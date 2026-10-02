package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.player.LoopMode

import com.example.ui.theme.OrangeGlow
import com.example.ui.theme.PurpleAccent

@Composable
fun GlassControls(
    isPlaying: Boolean,
    isShuffle: Boolean,
    loopMode: LoopMode,
    isFavorite: Boolean,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffleToggle: () -> Unit,
    onLoopCycle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle Button
        IconButton(
            onClick = onShuffleToggle,
            modifier = Modifier.testTag("shuffle_button")
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (isShuffle) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f)
            )
        }

        // Previous Button
        IconButton(
            onClick = onPrevious,
            modifier = Modifier
                .size(48.dp)
                .testTag("previous_button")
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous Track",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        // Main Play/Pause Hero Button (High Density Design Theme)
        Box(
            modifier = Modifier
                .size(68.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = MaterialTheme.colorScheme.primary,
                    spotColor = PurpleAccent
                )
                .clip(CircleShape)
                .background(Color.White)
                .clickable { onPlayPauseToggle() }
                .testTag("play_pause_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.Black,
                modifier = Modifier.size(36.dp)
            )
        }

        // Next Button
        IconButton(
            onClick = onNext,
            modifier = Modifier
                .size(48.dp)
                .testTag("next_button")
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next Track",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        // Loop / Repeat Button
        IconButton(
            onClick = onLoopCycle,
            modifier = Modifier.testTag("loop_button")
        ) {
            val (icon, tint) = when (loopMode) {
                LoopMode.OFF -> Icons.Default.Repeat to Color.White.copy(alpha = 0.4f)
                LoopMode.REPEAT_ALL -> Icons.Default.Repeat to MaterialTheme.colorScheme.primary
                LoopMode.REPEAT_ONE -> Icons.Default.RepeatOne to PurpleAccent
            }
            Icon(
                imageVector = icon,
                contentDescription = "Repeat Mode",
                tint = tint
            )
        }
    }
}

