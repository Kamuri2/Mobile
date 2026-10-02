package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.model.Track
import com.example.ui.components.TrackImage
import com.example.ui.theme.GlassTextMuted
import com.example.ui.theme.GlassTextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<Track>,
    currentIndex: Int,
    onDismissRequest: () -> Unit,
    onTrackSelect: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onSetPlayNext: (Track) -> Unit,
    onMoveInQueue: ((Int, Int) -> Unit)? = null,
    language: String = "English"
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Only display from currentIndex onwards (hiding already played songs)
    val visibleStartIndex = if (currentIndex in queue.indices) currentIndex else 0
    val visibleQueue = remember(queue, currentIndex) {
        if (currentIndex in queue.indices) queue.subList(visibleStartIndex, queue.size) else queue
    }
    val hasCurrentPlayingAtTop = currentIndex in queue.indices
    val minReorderSlot = if (hasCurrentPlayingAtTop) 1 else 0

    val isSpanish = language.startsWith("Esp", ignoreCase = true)
    val nowPlayingLabel = if (isSpanish) "REPRODUCIENDO AHORA" else "NOW PLAYING"
    val nextUpLabel = if (isSpanish) "A CONTINUACIÓN" else "NEXT UP"

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }
    var draggedCardY by remember { mutableFloatStateOf(0f) }
    var listContainerHeightPx by remember { mutableFloatStateOf(0f) }

    val cardHeightPx = with(density) { 64.dp.toPx() }
    val cardHalfHeightPx = with(density) { 32.dp.toPx() }

    // Haptic feedback tick whenever the target slot changes during drag
    var lastHapticTarget by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(targetIndex) {
        val target = targetIndex
        if (draggingIndex != null && target != null && target != lastHapticTarget) {
            lastHapticTarget = target
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Smooth auto-scroll when dragging near the edges
    LaunchedEffect(draggingIndex, draggedCardY) {
        if (draggingIndex != null) {
            val topThreshold = with(density) { 48.dp.toPx() }
            val bottomThreshold = (listContainerHeightPx - with(density) { 72.dp.toPx() }).coerceAtLeast(topThreshold + 10f)

            while (draggingIndex != null) {
                if (draggedCardY < topThreshold && lazyListState.canScrollBackward) {
                    lazyListState.scrollBy(-14f)
                } else if (listContainerHeightPx > 0f && draggedCardY > bottomThreshold && lazyListState.canScrollForward) {
                    lazyListState.scrollBy(14f)
                }
                delay(16)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A),
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.4f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // Header area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = com.example.ui.Translations.get(language, "play_queue"),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                val remainingCount = (queue.size - (currentIndex + 1)).coerceAtLeast(0)
                Text(
                    text = "$remainingCount ${com.example.ui.Translations.get(language, "songs")}",
                    fontSize = 14.sp,
                    color = GlassTextMuted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (visibleQueue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(com.example.ui.Translations.get(language, "no_track_playing"), color = GlassTextMuted)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.72f)
                        .padding(horizontal = 16.dp)
                        .onGloballyPositioned { coordinates ->
                            listContainerHeightPx = coordinates.size.height.toFloat()
                        }
                        // Container-level pointer tracker: NEVER LOSES GESTURES OR RECYCLES
                        .pointerInput(draggingIndex) {
                            if (draggingIndex == null) return@pointerInput
                            awaitPointerEventScope {
                                while (draggingIndex != null) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull() ?: break

                                    if (change.pressed) {
                                        change.consume()
                                        val fingerY = change.position.y
                                        val maxCardY = (listContainerHeightPx - cardHeightPx).coerceAtLeast(0f)
                                        val newCardY = (fingerY - cardHalfHeightPx).coerceIn(0f, maxCardY)
                                        draggedCardY = newCardY

                                        // Update drop target in real-time based on card vertical center
                                        val centerCardY = newCardY + cardHalfHeightPx
                                        val items = lazyListState.layoutInfo.visibleItemsInfo
                                        if (items.isNotEmpty()) {
                                            val match = items.find { item ->
                                                centerCardY >= item.offset && centerCardY <= (item.offset + item.size)
                                            }
                                            val newTarget = when {
                                                match != null -> match.index
                                                centerCardY < (items.firstOrNull()?.offset ?: 0) -> minReorderSlot
                                                else -> (items.lastOrNull()?.index ?: visibleQueue.lastIndex)
                                            }.coerceIn(minReorderSlot, visibleQueue.lastIndex)

                                            targetIndex = newTarget
                                        }
                                    } else {
                                        // User released finger - commit move!
                                        change.consume()
                                        val from = draggingIndex
                                        val to = targetIndex
                                        if (from != null && to != null && from != to) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val realFrom = visibleStartIndex + from
                                            val realTo = visibleStartIndex + to
                                            onMoveInQueue?.invoke(realFrom, realTo)
                                        }
                                        draggingIndex = null
                                        targetIndex = null
                                        break
                                    }
                                }
                            }
                        }
                ) {
                    LazyColumn(
                        state = lazyListState,
                        userScrollEnabled = draggingIndex == null,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = visibleQueue,
                            key = { visibleIdx, track -> "${track.id}_${visibleStartIndex + visibleIdx}" }
                        ) { visibleIdx, track ->
                            val realIndex = visibleStartIndex + visibleIdx
                            val isCurrent = visibleIdx == 0 && hasCurrentPlayingAtTop
                            val isBeingDragged = draggingIndex == visibleIdx
                            val isDropTarget = draggingIndex != null && targetIndex == visibleIdx && draggingIndex != visibleIdx
                            val isTargetAbove = draggingIndex != null && draggingIndex!! > visibleIdx
                            val isTargetBelow = draggingIndex != null && draggingIndex!! < visibleIdx
                            val canDragThisItem = if (hasCurrentPlayingAtTop) visibleIdx >= 1 else true

                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    when (value) {
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            onRemoveFromQueue(realIndex)
                                            true
                                        }
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            onSetPlayNext(track)
                                            true
                                        }
                                        else -> false
                                    }
                                }
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        // Dim the source item in list while floating card is being dragged
                                        alpha = if (isBeingDragged) 0.2f else 1f
                                    }
                            ) {
                                // Section header for currently playing track
                                if (visibleIdx == 0 && hasCurrentPlayingAtTop) {
                                    Text(
                                        text = nowPlayingLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                                    )
                                } else if (visibleIdx == 1 && hasCurrentPlayingAtTop) {
                                    Text(
                                        text = nextUpLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = GlassTextMuted,
                                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                                    )
                                }

                                // Highlighted landing indicator above slot
                                if (isDropTarget && isTargetAbove) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 4.dp)
                                            .height(4.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .shadow(6.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                                    )
                                }

                                // Card container
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = if (isDropTarget) 1.5.dp else 0.dp,
                                            color = if (isDropTarget) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else Color.Transparent,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                ) {
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = draggingIndex == null,
                                        enableDismissFromEndToStart = draggingIndex == null,
                                        backgroundContent = {
                                            val direction = dismissState.dismissDirection
                                            val color by animateColorAsState(
                                                when (direction) {
                                                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFDC2626) // Red for ELIMINAR
                                                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF10B981) // Green for SIGUIENTE
                                                    else -> Color.Transparent
                                                },
                                                label = "swipe_bg"
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(color)
                                                    .padding(horizontal = 20.dp),
                                                contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
                                            ) {
                                                if (direction == SwipeToDismissBoxValue.EndToStart) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color.White)
                                                    }
                                                } else if (direction == SwipeToDismissBoxValue.StartToEnd) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(imageVector = Icons.Default.SkipNext, contentDescription = null, tint = Color.White)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "SIGUIENTE",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        content = {
                                            QueueTrackRowItem(
                                                track = track,
                                                isCurrent = isCurrent,
                                                isBeingDragged = false,
                                                showDragHandle = canDragThisItem,
                                                onGrab = {
                                                    val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.find { it.index == visibleIdx }
                                                    val initialY = itemInfo?.offset?.toFloat() ?: (visibleIdx * cardHeightPx)
                                                    draggedCardY = initialY.coerceIn(0f, (listContainerHeightPx - cardHeightPx).coerceAtLeast(0f))
                                                    targetIndex = visibleIdx
                                                    draggingIndex = visibleIdx
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onClick = { onTrackSelect(realIndex) }
                                            )
                                        }
                                    )
                                }

                                // Highlighted landing indicator below slot
                                if (isDropTarget && isTargetBelow) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 4.dp)
                                            .height(4.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .shadow(6.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }

                    // THE FLOATING CARD OVERLAY (NEVER GETS RECYCLED OR DISAPPEARS)
                    if (draggingIndex != null) {
                        val draggedTrack = visibleQueue.getOrNull(draggingIndex!!)
                        if (draggedTrack != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset { IntOffset(0, draggedCardY.roundToInt()) }
                                    .zIndex(99f)
                                    .shadow(20.dp, RoundedCornerShape(16.dp), spotColor = MaterialTheme.colorScheme.primary)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF282828))
                            ) {
                                QueueTrackRowItem(
                                    track = draggedTrack,
                                    isCurrent = draggingIndex == 0 && hasCurrentPlayingAtTop,
                                    isBeingDragged = true,
                                    showDragHandle = true,
                                    onGrab = {},
                                    onClick = {}
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QueueTrackRowItem(
    track: Track,
    isCurrent: Boolean,
    isBeingDragged: Boolean,
    showDragHandle: Boolean = true,
    onGrab: () -> Unit = {},
    onClick: () -> Unit
) {
    val pinnableContainer = LocalPinnableContainer.current

    // Pin this item when being dragged so LazyColumn never recycles it
    DisposableEffect(isBeingDragged) {
        val handle = if (isBeingDragged) pinnableContainer?.pin() else null
        onDispose {
            handle?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isBeingDragged -> Color(0xFF2C2C2E)
                    isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else -> Color(0x1AFFFFFF)
                }
            )
            .clickable(enabled = !isBeingDragged) { onClick() }
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Album art thumbnail
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x1AFFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                TrackImage(track = track, modifier = Modifier.fillMaxSize())
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Track title & artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isBeingDragged || isCurrent) MaterialTheme.colorScheme.primary else Color.White,
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

            if (showDragHandle) {
                Spacer(modifier = Modifier.width(8.dp))

                // Drag handle on the RIGHT side: immediate touch claim
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(track.id) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                onGrab()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Arrastrar para reordenar en la cola",
                        tint = if (isBeingDragged || isCurrent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
