package com.example.ui.components
import androidx.compose.ui.draw.blur
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.data.ArtistProfile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import java.io.File
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.example.ui.Translations
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ArtistInfo
import com.example.model.Track
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Brush

/**
 * Spotify-style inline Artist Info Card rendered directly below the player controls.
 * Supports both single artists and Multi-Artist "Tarjetero" card decks with skeleton loading.
 */
@Composable
fun ArtistInfoCard(
    track: Track?,
    artistInfo: ArtistInfo?,
    language: String,
    modifier: Modifier = Modifier,
    scrollState: androidx.compose.foundation.ScrollState? = null,
    onNavigateToArtist: ((String) -> Unit)? = null,
    externalProfiles: List<ArtistProfile>? = null,
    isLoading: Boolean = false
) {
    val cacheRevision by com.example.data.ArtistInfoFetcher.cacheRevision.collectAsState()
    val currentArtist = track?.artist?.trim() ?: ""
    val isExternalProfileMatchingTrack = remember(track?.id, track?.artist, externalProfiles) {
        !externalProfiles.isNullOrEmpty() && externalProfiles.any { profile ->
            currentArtist.isNotBlank() && (
                currentArtist.contains(profile.name, ignoreCase = true) ||
                profile.name.contains(currentArtist, ignoreCase = true)
            )
        }
    }

    var profiles by remember(track?.id, track?.artist, externalProfiles, cacheRevision) {
        mutableStateOf(
            if (isExternalProfileMatchingTrack && !externalProfiles.isNullOrEmpty()) externalProfiles
            else com.example.data.ArtistInfoFetcher.getCachedArtistsForTrack(track)
        )
    }

    LaunchedEffect(track?.id, track?.artist, externalProfiles, cacheRevision) {
        if (isExternalProfileMatchingTrack && !externalProfiles.isNullOrEmpty()) {
            profiles = externalProfiles
            return@LaunchedEffect
        }
        if (track != null) {
            profiles = com.example.data.ArtistInfoFetcher.getCachedArtistsForTrack(track)
            val allComplete = profiles.isNotEmpty() && profiles.all {
                !it.info.imageUrl.isNullOrBlank() && !it.info.bio.isNullOrBlank()
            }
            if (allComplete) {
                return@LaunchedEffect
            }
            // Breve respiro para no saturar memoria mientras se extraen portada o letras
            delay(500)
            val fetched = com.example.data.ArtistInfoFetcher.fetchArtistsForTrack(track)
            if (fetched.isNotEmpty()) {
                profiles = fetched
            }
        }
    }

    val effectiveProfiles = if (profiles.isNotEmpty()) {
        profiles
    } else if (artistInfo != null) {
        val name = track?.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: Translations.get(language, "unknown_artist")
        listOf(com.example.data.ArtistProfile(name = name, isPrimary = true, info = artistInfo))
    } else {
        emptyList()
    }

    // Indicador visual de si estamos en estado de carga o esperando datos
    val isActivelyLoading = isLoading || (effectiveProfiles.isEmpty() && track != null)

    if (isActivelyLoading && effectiveProfiles.isEmpty()) {
        // Mostrar skeleton loader de referencia visual mientras se busca la info del artista
        ArtistCardSkeleton(modifier = modifier, language = language)
        return
    }

    val trackAlbumArt = track?.albumArtUri

    if (effectiveProfiles.size > 1) {
        // Multi-artist "Tarjetero" Card Deck
        ArtistTarjeteroDeck(
            profiles = effectiveProfiles,
            language = language,
            modifier = modifier,
            trackArtFallback = trackAlbumArt,
            parentScrollState = scrollState,
            onNavigateToArtist = onNavigateToArtist
        )
    } else if (effectiveProfiles.isNotEmpty()) {
        // Single artist card
        ArtistSingleCardView(
            profile = effectiveProfiles[0],
            language = language,
            modifier = modifier,
            trackArtFallback = trackAlbumArt,
            onNavigateToArtist = onNavigateToArtist
        )
    } else {
        // Fallback placeholder card
        val fallbackName = track?.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: Translations.get(language, "unknown_artist")
        ArtistSingleCardView(
            profile = com.example.data.ArtistProfile(
                name = fallbackName,
                isPrimary = true,
                info = ArtistInfo(imageUrl = null, bio = "", followers = "", listeners = "", origin = "")
            ),
            language = language,
            modifier = modifier,
            trackArtFallback = trackAlbumArt,
            onNavigateToArtist = onNavigateToArtist
        )
    }
}

/**
 * Skeleton Loader con animación de brillo suave (shimmer) para las tarjetas de artistas
 * Proporciona feedback visual inmediato cuando se cargan las tarjetas.
 */
@Composable
fun ArtistCardSkeleton(
    modifier: Modifier = Modifier,
    language: String = "en"
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val baseCardBg = if (isLight) Color(0xFFF3F4F6) else Color(0xFF1E1E24)
    val shimmerBase = if (isLight) Color(0xFFE5E7EB) else Color(0xFF2A2A35)
    val shimmerHighlight = if (isLight) Color(0xFFF9FAFB) else Color(0xFF383846)
    val cardBorder = if (isLight) Color(0x1F000000) else Color.White.copy(alpha = 0.08f)

    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerAnim"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(shimmerBase, shimmerHighlight, shimmerBase),
        start = Offset(translateAnim - 400f, translateAnim - 400f),
        end = Offset(translateAnim, translateAnim)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = baseCardBg),
        border = BorderStroke(1.dp, cardBorder),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Skeleton Banner Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(shimmerBrush)
            ) {
                // Header skeleton badge / label
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(160.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isLight) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.15f))
                    )
                    Box(
                        modifier = Modifier
                            .height(20.dp)
                            .width(70.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    )
                }
            }

            // Skeleton Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .width(70.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .height(12.dp)
                            .width(55.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .width(80.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .height(12.dp)
                            .width(45.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                }
            }

            // Skeleton Bio Lines
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
        }
    }
}

/**
 * Interactive Multi-Artist "Tarjetero" Card Deck.
 * Presenta las tarjetas apiladas dinámicamente con interpolación matemática (`lerp`)
 * y `graphicsLayer`. Conforme el usuario hace scroll hacia abajo en la pantalla,
 * las tarjetas participantes se van desapilando una debajo de la otra de forma fluida.
 */
@Composable
fun ArtistTarjeteroDeck(
    profiles: List<com.example.data.ArtistProfile>,
    language: String,
    modifier: Modifier = Modifier,
    trackArtFallback: Any? = null,
    parentScrollState: androidx.compose.foundation.ScrollState? = null,
    onNavigateToArtist: ((String) -> Unit)? = null
) {
    if (profiles.isEmpty()) return

    // Si solo hay un artista, se muestra directamente sin apilar
    if (profiles.size == 1) {
        ArtistSingleCardView(
            profile = profiles[0],
            language = language,
            showRoleBadge = true,
            trackArtFallback = trackArtFallback,
            modifier = modifier.fillMaxWidth(),
            onNavigateToArtist = onNavigateToArtist
        )
        return
    }

    // Scroll de referencia: utiliza el scrollState del contenedor o uno local
    val localScrollState = rememberScrollState()
    val scrollState = parentScrollState ?: localScrollState
    val scrollY = scrollState.value.toFloat()

    // Configuración de la animación matemática de desapilado
    val maxScroll = 600f
    val progreso = (scrollY / maxScroll).coerceIn(0f, 1f)
    val asomoInicialPx = 72f // Lo que se asoma la cabecera de cada tarjeta cuando están apiladas detrás

    // Altura medida dinámicamente de cada tarjeta para garantizar que nunca se traslapen al final
    val cardHeights = remember(profiles.size) { mutableStateMapOf<Int, Int>() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy((18 * progreso).dp)
    ) {
        profiles.forEachIndexed { index, prof ->
            // Prevenir flasheo al cambiar de canción: si aún no se ha medido la tarjeta anterior (cardHeights[index-1] == null),
            // usar 0f de translationY para que se ubique ordenadamente de arriba hacia abajo sin dar un salto en medio de la pantalla
            val prevHeight = cardHeights[index - 1]
            val currentTranslationY = if (index == 0 || prevHeight == null) {
                0f
            } else {
                val measuredPrevHeight = prevHeight.toFloat()
                val fullOverlapPx = (measuredPrevHeight - asomoInicialPx).coerceAtLeast(0f)
                lerp(-fullOverlapPx, 0f, progreso)
            }

            // Escala progresiva de profundidad (las de atrás son sutilmente más chicas)
            val escalaInicial = 1f - ((profiles.size - 1 - index) * 0.035f).coerceIn(0f, 0.12f)
            val currentScale = if (prevHeight != null || index == 0) lerp(escalaInicial, 1f, progreso) else 1f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        cardHeights[index] = size.height
                    }
                    .graphicsLayer {
                        translationY = currentTranslationY
                        scaleX = currentScale
                        scaleY = currentScale
                        shadowElevation = if (progreso < 0.95f) 8f else 0f
                    }
            ) {
                ArtistSingleCardView(
                    profile = prof,
                    language = language,
                    showRoleBadge = true,
                    trackArtFallback = trackArtFallback,
                    modifier = Modifier.fillMaxWidth(),
                    onNavigateToArtist = onNavigateToArtist
                )
            }
        }
    }
}

/**
 * Sleek cardholder slot representing an inactive/stacked card in the Tarjetero deck.
 */
@Composable
fun ArtistTarjeteroSlot(
    profile: com.example.data.ArtistProfile,
    language: String,
    index: Int,
    totalInDeck: Int,
    onClick: () -> Unit,
    trackArtFallback: Any? = null
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardBg = if (isLight) Color(0xFFF7F7F9) else Color(0xFF22222A)
    val textPrimary = if (isLight) Color(0xFF111111) else Color.White
    val textSecondary = if (isLight) Color(0xFF666666) else Color.White.copy(alpha = 0.65f)
    val borderColor = if (isLight) Color(0x22000000) else Color(0x33FFFFFF)

    val thumbModel = profile.info.imageUrl ?: trackArtFallback

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card slot thumbnail
                if (thumbModel != null) {
                    val modelToLoad: Any = if (thumbModel is String && thumbModel.startsWith("/")) {
                        File(thumbModel)
                    } else thumbModel

                    AsyncImage(
                        model = modelToLoad,
                        contentDescription = profile.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isLight) Color(0x18000000) else Color(0x28FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = textPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        color = textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (profile.isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else (if (isLight) Color(0x12000000) else Color(0x1FFFFFFF))
                        ) {
                            Text(
                                text = if (profile.isPrimary) {
                                    if (language.startsWith("es", ignoreCase = true)) "Principal" else "Main Artist"
                                } else {
                                    if (language.startsWith("es", ignoreCase = true)) "Colaborador" else "Featured"
                                },
                                color = if (profile.isPrimary) MaterialTheme.colorScheme.primary else textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (profile.info.origin.isNotBlank() && profile.info.origin != "Unknown") {
                            Text(
                                text = "• ${profile.info.origin}",
                                color = textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (profile.info.followers.isNotBlank()) {
                            Text(
                                text = "• ${profile.info.followers}",
                                color = textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Peek action pill
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (language.startsWith("es", ignoreCase = true)) "Abrir" else "Open",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Individual Layered Card inside the Tarjetero deck.
 */
@Composable
fun ArtistDeckCardItem(
    profile: com.example.data.ArtistProfile,
    isExpanded: Boolean,
    language: String,
    indexInDeck: Int,
    onToggle: () -> Unit,
    onNavigateToArtist: ((String) -> Unit)? = null
) {
    ArtistSingleCardView(
        profile = profile,
        language = language,
        showRoleBadge = true,
        modifier = Modifier.fillMaxWidth(),
        onNavigateToArtist = onNavigateToArtist
    )
}

@Suppress("unused")
@Composable
private fun UnusedDeckLegacyBody(
    profile: com.example.data.ArtistProfile,
    isExpanded: Boolean,
    language: String,
    onToggle: () -> Unit,
    onNavigateToArtist: ((String) -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardBg = if (isLight) Color(0xFFFFFFFF) else Color(0xFF1E1E28)
    val textPrimary = if (isLight) Color(0xFF111111) else Color.White
    val textSecondary = if (isLight) Color(0xFF555555) else Color.White.copy(alpha = 0.65f)
    val borderColor = if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else (if (isLight) Color(0x1F000000) else Color(0x22FFFFFF))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Card Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (profile.info.imageUrl != null) {
                        AsyncImage(
                            model = profile.info.imageUrl,
                            contentDescription = profile.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isLight) Color(0x15000000) else Color(0x22FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name,
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (profile.isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else (if (isLight) Color(0x12000000) else Color(0x1FFFFFFF))
                            ) {
                                Text(
                                    text = if (profile.isPrimary) {
                                        if (language.startsWith("es", ignoreCase = true)) "Principal" else "Main Artist"
                                    } else {
                                        if (language.startsWith("es", ignoreCase = true)) "Colaborador" else "Featured"
                                    },
                                    color = if (profile.isPrimary) MaterialTheme.colorScheme.primary else textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (profile.info.origin.isNotBlank() && profile.info.origin != "Unknown") {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• ${profile.info.origin}",
                                    color = textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = { onToggle() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = if (isExpanded) MaterialTheme.colorScheme.primary else textSecondary
                    )
                }
            }

            // Expanded Layer Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (profile.info.imageUrl != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        ) {
                            AsyncImage(
                                model = profile.info.imageUrl,
                                contentDescription = profile.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = if (isLight) {
                                                listOf(Color.Transparent, Color(0x88FFFFFF), Color(0xFFFFFFFF))
                                            } else {
                                                listOf(Color.Transparent, Color(0x881E1E28), Color(0xFF1E1E28))
                                            },
                                            startY = 40f
                                        )
                                    )
                            )
                        }
                    }

                    // Stats row
                    val hasFollowers = profile.info.followers.isNotBlank()
                    val hasOrigin = profile.info.origin.isNotBlank() && profile.info.origin != "Unknown"
                    if (hasFollowers || hasOrigin) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            if (hasFollowers) {
                                Column {
                                    Text(
                                        text = profile.info.followers,
                                        color = textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = if (language.startsWith("es", ignoreCase = true)) "Seguidores" else "Followers",
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            if (hasOrigin) {
                                Column {
                                    Text(
                                        text = profile.info.origin,
                                        color = textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = if (language.startsWith("es", ignoreCase = true)) "Origen" else "Origin",
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Biography with conditional translation
                    var isBioExpanded by remember { mutableStateOf(false) }
                    val rawBio = profile.info.bio
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
                        else -> Translations.get(language, "no_info_available")
                    }

                    Text(
                        text = displayBioText,
                        color = if (isLight) Color(0xFF333333) else Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        maxLines = if (isBioExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { isBioExpanded = !isBioExpanded }
                    )

                    // Bio Action Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!rawBio.isNullOrBlank() && rawBio.length > 120) {
                            Text(
                                text = if (isBioExpanded) Translations.get(language, "show_less") else Translations.get(language, "read_more"),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { isBioExpanded = !isBioExpanded }
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        if (!rawBio.isNullOrBlank()) {
                            if (isAlreadyTarget) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isLight) Color(0x10000000) else Color(0x15FFFFFF)
                                ) {
                                    Text(
                                        text = if (language.startsWith("es", ignoreCase = true)) "✓ En español" else "✓ In app language",
                                        color = textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            } else {
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
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "Translate",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when {
                                            isTranslating -> Translations.get(language, "translating_bio")
                                            showTranslated -> Translations.get(language, "original_text")
                                            else -> Translations.get(language, "translate_bio")
                                        },
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Social links
                    val hasSocials = !profile.info.spotify.isNullOrBlank() ||
                            !profile.info.instagram.isNullOrBlank() ||
                            !profile.info.youtube.isNullOrBlank() ||
                            !profile.info.appleMusic.isNullOrBlank() ||
                            !profile.info.twitter.isNullOrBlank() ||
                            !profile.info.facebook.isNullOrBlank() ||
                            !profile.info.website.isNullOrBlank()

                    if (hasSocials) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!profile.info.spotify.isNullOrBlank()) {
                                IconButton(onClick = { uriHandler.openUri(getSpotifyArtistUrl(profile.name, profile.info.spotify)) }) {
                                    Icon(SpotifyIcon, contentDescription = "Spotify", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            if (!profile.info.instagram.isNullOrBlank()) {
                                IconButton(onClick = { uriHandler.openUri(formatUrl(profile.info.instagram!!)) }) {
                                    Icon(InstagramIcon, contentDescription = "Instagram", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            if (!profile.info.youtube.isNullOrBlank()) {
                                IconButton(onClick = { uriHandler.openUri(formatUrl(profile.info.youtube!!)) }) {
                                    Icon(YouTubeIcon, contentDescription = "YouTube", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            if (!profile.info.appleMusic.isNullOrBlank()) {
                                IconButton(onClick = { uriHandler.openUri(formatUrl(profile.info.appleMusic!!)) }) {
                                    Icon(AppleMusicIcon, contentDescription = "Apple Music", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            if (!profile.info.twitter.isNullOrBlank()) {
                                IconButton(onClick = { uriHandler.openUri(formatUrl(profile.info.twitter!!)) }) {
                                    Icon(TwitterIcon, contentDescription = "Twitter", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            if (!profile.info.website.isNullOrBlank()) {
                                IconButton(onClick = { uriHandler.openUri(formatUrl(profile.info.website!!)) }) {
                                    Icon(Icons.Default.Language, contentDescription = "Website", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    // Navigate to artist discography button
                    if (onNavigateToArtist != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable { onNavigateToArtist(profile.name) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (language.startsWith("es", ignoreCase = true)) "Ver más de ${profile.name}" else "More from ${profile.name}",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

/**
 * Artist Card View displaying complete artist info, banner image, followers, origin, biography and socials.
 * Used for both single-artist tracks and multi-artist tracks (where each artist gets their own full card).
 */
@Composable
fun ArtistSingleCardView(
    profile: com.example.data.ArtistProfile,
    language: String,
    modifier: Modifier = Modifier,
    showRoleBadge: Boolean = false,
    trackArtFallback: Any? = null,
    onNavigateToArtist: ((String) -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val artistName = profile.name
    val artistInfo = profile.info
    val displayBanner = artistInfo.imageUrl ?: trackArtFallback
    val bannerModel: Any? = if (displayBanner is String && displayBanner.startsWith("/")) {
        File(displayBanner)
    } else displayBanner

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardBg = if (isLight) Color(0xFFFFFFFF) else Color(0xFF1E1E1E)
    val textPrimary = if (isLight) Color(0xFF111111) else Color.White
    val textSecondary = if (isLight) Color(0xFF555555) else Color.White.copy(alpha = 0.6f)
    val cardBorder = if (isLight) Color(0x1F000000) else Color.White.copy(alpha = 0.12f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (displayBanner != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    AsyncImage(
                        model = bannerModel,
                        contentDescription = "Artist Banner",
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = if (isLight) {
                                        listOf(
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color(0x99FFFFFF),
                                            Color(0xFFFFFFFF)
                                        )
                                    } else {
                                        listOf(
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color(0x881E1E1E),
                                            Color(0xFF1E1E1E)
                                        )
                                    },
                                    startY = 180f
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = artistName,
                            color = textPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (showRoleBadge) {
                            val isSpanish = language.startsWith("es", ignoreCase = true)
                            val badgeText = if (profile.isPrimary) {
                                if (isSpanish) "Principal" else "Main Artist"
                            } else {
                                if (isSpanish) "Colaborador" else "Featured"
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (profile.isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else (if (isLight) Color(0x1A000000) else Color(0x28FFFFFF)),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (profile.isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else (if (isLight) Color(0x28000000) else Color(0x35FFFFFF))
                                )
                            ) {
                                Text(
                                    text = badgeText,
                                    color = if (profile.isPrimary) MaterialTheme.colorScheme.primary else textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(if (isLight) Color(0x12000000) else Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = textPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = artistName,
                                color = textPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            if (showRoleBadge) {
                                val isSpanish = language.startsWith("es", ignoreCase = true)
                                val badgeText = if (profile.isPrimary) {
                                    if (isSpanish) "Principal" else "Main Artist"
                                } else {
                                    if (isSpanish) "Colaborador" else "Featured"
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (profile.isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else (if (isLight) Color(0x1A000000) else Color(0x28FFFFFF)),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (profile.isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else (if (isLight) Color(0x28000000) else Color(0x35FFFFFF))
                                    )
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = if (profile.isPrimary) MaterialTheme.colorScheme.primary else textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Stats row (Followers, Origin)
            val hasFollowers = artistInfo.followers.isNotBlank()
            val hasOrigin = artistInfo.origin.isNotBlank() && artistInfo.origin != "Unknown"
            if (hasFollowers || hasOrigin) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (hasFollowers) {
                        Column {
                            Text(
                                text = artistInfo.followers,
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = Translations.get(language, "followers"),
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (hasOrigin) {
                        Column {
                            Text(
                                text = artistInfo.origin,
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = Translations.get(language, "origin"),
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Biography with conditional translation
            var isBioExpanded by remember { mutableStateOf(false) }
            val rawBio = artistInfo.bio
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
                else -> Translations.get(language, "no_info_available")
            }

            Text(
                text = displayBioText,
                color = if (isLight) Color(0xFF333333) else Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = if (isBioExpanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .clickable { isBioExpanded = !isBioExpanded }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!rawBio.isNullOrBlank() && rawBio.length > 130) {
                    Text(
                        text = if (isBioExpanded) Translations.get(language, "show_less") else Translations.get(language, "read_more"),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { isBioExpanded = !isBioExpanded }
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (!rawBio.isNullOrBlank()) {
                    if (isAlreadyTarget) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isLight) Color(0x10000000) else Color(0x15FFFFFF)
                        ) {
                            Text(
                                text = if (language.startsWith("es", ignoreCase = true)) "✓ En español" else "✓ In app language",
                                color = textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    } else {
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
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Translate",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    isTranslating -> Translations.get(language, "translating_bio")
                                    showTranslated -> Translations.get(language, "original_text")
                                    else -> Translations.get(language, "translate_bio")
                                },
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Social links
            val hasSocials = !artistInfo.website.isNullOrBlank() ||
                    !artistInfo.facebook.isNullOrBlank() ||
                    !artistInfo.twitter.isNullOrBlank() ||
                    !artistInfo.instagram.isNullOrBlank() ||
                    !artistInfo.spotify.isNullOrBlank() ||
                    !artistInfo.youtube.isNullOrBlank() ||
                    !artistInfo.appleMusic.isNullOrBlank()

            if (hasSocials) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!artistInfo.spotify.isNullOrBlank()) {
                        IconButton(onClick = { uriHandler.openUri(getSpotifyArtistUrl(artistName, artistInfo.spotify)) }) {
                            Icon(SpotifyIcon, contentDescription = "Spotify", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!artistInfo.instagram.isNullOrBlank()) {
                        IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo.instagram!!)) }) {
                            Icon(InstagramIcon, contentDescription = "Instagram", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!artistInfo.youtube.isNullOrBlank()) {
                        IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo.youtube!!)) }) {
                            Icon(YouTubeIcon, contentDescription = "YouTube", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!artistInfo.appleMusic.isNullOrBlank()) {
                        IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo.appleMusic!!)) }) {
                            Icon(AppleMusicIcon, contentDescription = "Apple Music", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!artistInfo.twitter.isNullOrBlank()) {
                        IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo.twitter!!)) }) {
                            Icon(TwitterIcon, contentDescription = "Twitter", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!artistInfo.facebook.isNullOrBlank()) {
                        IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo.facebook!!)) }) {
                            Icon(FacebookIcon, contentDescription = "Facebook", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!artistInfo.website.isNullOrBlank()) {
                        IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo.website!!)) }) {
                            Icon(Icons.Default.Language, contentDescription = "Website", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Navigate to artist discography button
            if (onNavigateToArtist != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                        .clickable { onNavigateToArtist(profile.name) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 9.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language.startsWith("es", ignoreCase = true)) "Ver más de ${profile.name}" else "More from ${profile.name}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
fun ArtistInfoTab(
    track: Track?,
    artistInfo: ArtistInfo?,
    language: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    // Collapse when track changes
    LaunchedEffect(track?.id) {
        expanded = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xFF121212))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) // Glass style
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header (Always visible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (artistInfo?.imageUrl != null) {
                        AsyncImage(
                            model = artistInfo.imageUrl,
                            contentDescription = "Artist Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track?.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: Translations.get(language, "unknown_artist"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = Translations.get(language, "about_artist"),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
                
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }

            // Expanded Content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    // Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = artistInfo?.followers ?: "-", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(text = Translations.get(language, "followers"), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = artistInfo?.origin ?: "-", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(text = Translations.get(language, "origin"), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Bio
                    var tabTranslatedBio by remember(artistInfo?.bio, language) { mutableStateOf<String?>(null) }
                    var tabIsTranslating by remember(artistInfo?.bio, language) { mutableStateOf(false) }
                    var tabShowTranslated by remember(artistInfo?.bio, language) { mutableStateOf(false) }
                    val tabScope = rememberCoroutineScope()
                    val tabRawBio = artistInfo?.bio
                    val tabTargetLang = remember(language) { com.example.data.TranslationHelper.mapToLanguageCode(language) }
                    val tabIsAlreadyTarget = remember(tabRawBio, tabTargetLang) {
                        if (tabRawBio.isNullOrBlank()) true
                        else com.example.data.TranslationHelper.isAlreadyInTargetLanguage(tabRawBio, tabTargetLang)
                    }

                    val tabDisplayBio = when {
                        tabShowTranslated && !tabTranslatedBio.isNullOrBlank() -> tabTranslatedBio!!
                        !tabRawBio.isNullOrBlank() -> tabRawBio
                        else -> Translations.get(language, "no_info_available")
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = tabDisplayBio,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }

                    if (!tabRawBio.isNullOrBlank() && !tabIsAlreadyTarget) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !tabIsTranslating) {
                                    if (tabShowTranslated) {
                                        tabShowTranslated = false
                                    } else if (!tabTranslatedBio.isNullOrBlank()) {
                                        tabShowTranslated = true
                                    } else {
                                        tabIsTranslating = true
                                        tabScope.launch {
                                            val res = com.example.data.TranslationHelper.translateText(tabRawBio, language)
                                            tabTranslatedBio = res
                                            tabShowTranslated = true
                                            tabIsTranslating = false
                                        }
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Translate",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    tabIsTranslating -> Translations.get(language, "translating_bio")
                                    tabShowTranslated -> Translations.get(language, "original_text")
                                    else -> Translations.get(language, "translate_bio")
                                },
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    
                    // Social Links
                    val hasSocials = !artistInfo?.website.isNullOrBlank() || !artistInfo?.facebook.isNullOrBlank() || !artistInfo?.twitter.isNullOrBlank() || !artistInfo?.instagram.isNullOrBlank() || !artistInfo?.spotify.isNullOrBlank() || !artistInfo?.youtube.isNullOrBlank() || !artistInfo?.appleMusic.isNullOrBlank() || !artistInfo?.deezer.isNullOrBlank()
                    if (hasSocials) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (!artistInfo?.website.isNullOrBlank()) {
                                IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo!!.website!!)) }) {
                                    Icon(Icons.Default.Language, contentDescription = "Website", tint = Color.White)
                                }
                            }
                            if (!artistInfo?.facebook.isNullOrBlank()) {
                                IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo!!.facebook!!)) }) {
                                    Icon(FacebookIcon, contentDescription = "Facebook", tint = Color.White) // FB icon replacement
                                }
                            }
                            if (!artistInfo?.twitter.isNullOrBlank()) {
                                IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo!!.twitter!!)) }) {
                                    Icon(TwitterIcon, contentDescription = "Twitter", tint = Color.White) // Twitter icon replacement
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatUrl(url: String): String {
    return if (!url.startsWith("http://") && !url.startsWith("https://")) {
        "https://$url"
    } else {
        url
    }
}

private fun getSpotifyArtistUrl(artistName: String, candidateUrl: String?): String {
    val cleanName = artistName.trim()
    val encoded = android.net.Uri.encode(cleanName)
    if (!candidateUrl.isNullOrBlank() && 
        (candidateUrl.contains("spotify.com/artist/") || candidateUrl.contains(encoded, ignoreCase = true))
    ) {
        return formatUrl(candidateUrl)
    }
    return "https://open.spotify.com/search/$encoded"
}


val FacebookIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Facebook",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.White)) {
            moveTo(18f, 2f)
            horizontalLineToRelative(-3f)
            arcToRelative(5f, 5f, 0f, false, false, -5f, 5f)
            verticalLineToRelative(3f)
            horizontalLineTo(7f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(8f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(-8f)
            horizontalLineToRelative(3f)
            lineToRelative(1f, -4f)
            horizontalLineToRelative(-4f)
            verticalLineTo(7f)
            arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
            horizontalLineToRelative(3f)
            close()
        }
    }.build()


val TwitterIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Twitter",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.White)) {
            moveTo(23.643f, 4.937f)
            curveToRelative(-0.835f, 0.37f, -1.732f, 0.62f, -2.675f, 0.733f)
            curveToRelative(0.962f, -0.576f, 1.7f, -1.49f, 2.048f, -2.578f)
            curveToRelative(-0.9f, 0.534f, -1.897f, 0.922f, -2.958f, 1.13f)
            curveToRelative(-0.85f, -0.904f, -2.06f, -1.47f, -3.4f, -1.47f)
            curveToRelative(-2.572f, 0f, -4.658f, 2.086f, -4.658f, 4.66f)
            curveToRelative(0f, 0.364f, 0.042f, 0.718f, 0.12f, 1.06f)
            curveToRelative(-3.873f, -0.195f, -7.304f, -2.05f, -9.602f, -4.868f)
            curveToRelative(-0.4f, 0.69f, -0.63f, 1.49f, -0.63f, 2.342f)
            curveToRelative(0f, 1.616f, 0.823f, 3.043f, 2.072f, 3.878f)
            curveToRelative(-0.764f, -0.025f, -1.482f, -0.234f, -2.11f, -0.583f)
            verticalLineToRelative(0.06f)
            curveToRelative(0f, 2.257f, 1.605f, 4.14f, 3.737f, 4.568f)
            curveToRelative(-0.392f, 0.106f, -0.803f, 0.162f, -1.227f, 0.162f)
            curveToRelative(-0.3f, 0f, -0.593f, -0.028f, -0.877f, -0.082f)
            curveToRelative(0.593f, 1.85f, 2.313f, 3.198f, 4.352f, 3.234f)
            curveToRelative(-1.595f, 1.25f, -3.604f, 1.995f, -5.786f, 1.995f)
            curveToRelative(-0.376f, 0f, -0.747f, -0.022f, -1.112f, -0.065f)
            curveToRelative(2.062f, 1.323f, 4.51f, 2.093f, 7.14f, 2.093f)
            curveToRelative(8.57f, 0f, 13.255f, -7.098f, 13.255f, -13.254f)
            curveToRelative(0f, -0.2f, -0.005f, -0.402f, -0.014f, -0.602f)
            curveToRelative(0.91f, -0.658f, 1.7f, -1.477f, 2.323f, -2.41f)
            close()
        }
    }.build()


val InstagramIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Instagram",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.White)) {
            moveTo(12f, 2.163f)
            curveToRelative(3.204f, 0f, 3.584f, 0.012f, 4.85f, 0.07f)
            curveToRelative(3.252f, 0.148f, 4.771f, 1.691f, 4.919f, 4.919f)
            curveToRelative(0.058f, 1.265f, 0.069f, 1.645f, 0.069f, 4.849f)
            curveToRelative(0f, 3.205f, -0.012f, 3.584f, -0.069f, 4.849f)
            curveToRelative(-0.149f, 3.225f, -1.664f, 4.771f, -4.919f, 4.919f)
            curveToRelative(-1.266f, 0.058f, -1.644f, 0.07f, -4.85f, 0.07f)
            curveToRelative(-3.204f, 0f, -3.584f, -0.012f, -4.849f, -0.07f)
            curveToRelative(-3.26f, -0.149f, -4.771f, -1.699f, -4.919f, -4.92f)
            curveToRelative(-0.058f, -1.265f, -0.07f, -1.644f, -0.07f, -4.849f)
            curveToRelative(0f, -3.204f, 0.012f, -3.584f, 0.07f, -4.849f)
            curveToRelative(0.149f, -3.227f, 1.664f, -4.771f, 4.919f, -4.919f)
            curveToRelative(1.266f, -0.057f, 1.645f, -0.069f, 4.849f, -0.069f)
            close()
            moveTo(12f, 0f)
            curveTo(8.741f, 0f, 8.333f, 0.014f, 7.053f, 0.072f)
            curveTo(2.695f, 0.272f, 0.273f, 2.69f, 0.073f, 7.052f)
            curveTo(0.014f, 8.333f, 0f, 8.741f, 0f, 12f)
            curveToRelative(0f, 3.259f, 0.014f, 3.668f, 0.072f, 4.948f)
            curveToRelative(0.2f, 4.358f, 2.618f, 6.78f, 6.98f, 6.98f)
            curveTo(8.333f, 23.986f, 8.741f, 24f, 12f, 24f)
            curveToRelative(3.259f, 0f, 3.668f, -0.014f, 4.948f, -0.072f)
            curveToRelative(4.358f, -0.2f, 6.78f, -2.618f, 6.98f, -6.98f)
            curveTo(23.986f, 15.668f, 24f, 15.259f, 24f, 12f)
            curveToRelative(0f, -3.259f, -0.014f, -3.668f, -0.072f, -4.948f)
            curveToRelative(-0.2f, -4.358f, -2.618f, -6.78f, -6.98f, -6.98f)
            curveTo(15.668f, 0.014f, 15.259f, 0f, 12f, 0f)
            close()
            moveTo(12f, 5.838f)
            arcToRelative(6.162f, 6.162f, 0f, true, false, 0f, 12.324f)
            arcToRelative(6.162f, 6.162f, 0f, false, false, 0f, -12.324f)
            close()
            moveTo(12f, 16f)
            arcToRelative(4f, 4f, 0f, true, true, 0f, -8f)
            arcToRelative(4f, 4f, 0f, false, true, 0f, 8f)
            close()
            moveTo(18.406f, 5.594f)
            arcToRelative(1.44f, 1.44f, 0f, true, false, -2.88f, 0f)
            arcToRelative(1.44f, 1.44f, 0f, false, false, 2.88f, 0f)
            close()
        }
    }.build()


val SpotifyIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Spotify",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(17.37f, 17.29f)
            curveTo(17.15f, 17.65f, 16.68f, 17.77f, 16.32f, 17.55f)
            curveTo(13.56f, 15.86f, 10.07f, 15.48f, 5.92f, 16.42f)
            curveTo(5.51f, 16.51f, 5.09f, 16.25f, 5.0f, 15.84f)
            curveTo(4.91f, 15.43f, 5.17f, 15.01f, 5.58f, 14.92f)
            curveTo(10.12f, 13.88f, 14.0f, 14.33f, 17.07f, 16.21f)
            curveTo(17.43f, 16.43f, 17.55f, 16.91f, 17.37f, 17.29f)
            close()
            moveTo(18.82f, 14.0f)
            curveTo(18.52f, 14.47f, 17.89f, 14.62f, 17.42f, 14.33f)
            curveTo(14.23f, 12.38f, 9.47f, 11.81f, 5.67f, 12.96f)
            curveTo(5.13f, 13.12f, 4.56f, 12.82f, 4.39f, 12.28f)
            curveTo(4.23f, 11.73f, 4.53f, 11.17f, 5.08f, 11.0f)
            curveTo(9.44f, 9.68f, 14.7f, 10.33f, 18.33f, 12.56f)
            curveTo(18.8f, 12.85f, 18.96f, 13.48f, 18.82f, 14.0f)
            close()
            moveTo(18.99f, 10.51f)
            curveTo(15.17f, 8.24f, 8.79f, 8.03f, 5.12f, 9.15f)
            curveTo(4.45f, 9.35f, 3.75f, 8.97f, 3.55f, 8.3f)
            curveTo(3.35f, 7.63f, 3.73f, 6.93f, 4.4f, 6.73f)
            curveTo(8.65f, 5.43f, 15.68f, 5.68f, 20.14f, 8.33f)
            curveTo(20.75f, 8.69f, 20.95f, 9.48f, 20.59f, 10.09f)
            curveTo(20.23f, 10.7f, 19.44f, 10.9f, 18.83f, 10.53f)
            close()
        }
    }.build()

val YouTubeIcon: ImageVector
    get() = ImageVector.Builder(
        name = "YouTube",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(21.58f, 7.19f)
            curveTo(21.34f, 6.27f, 20.61f, 5.54f, 19.69f, 5.29f)
            curveTo(18.02f, 4.84f, 12.0f, 4.84f, 12.0f, 4.84f)
            curveTo(12.0f, 4.84f, 5.98f, 4.84f, 4.31f, 5.29f)
            curveTo(3.39f, 5.54f, 2.66f, 6.27f, 2.42f, 7.19f)
            curveTo(1.97f, 8.86f, 1.97f, 12.0f, 1.97f, 12.0f)
            curveTo(1.97f, 12.0f, 1.97f, 15.14f, 2.42f, 16.81f)
            curveTo(2.66f, 17.73f, 3.39f, 18.46f, 4.31f, 18.71f)
            curveTo(5.98f, 19.16f, 12.0f, 19.16f, 12.0f, 19.16f)
            curveTo(12.0f, 19.16f, 18.02f, 19.16f, 19.69f, 18.71f)
            curveTo(20.61f, 18.46f, 21.34f, 17.73f, 21.58f, 16.81f)
            curveTo(22.03f, 15.14f, 22.03f, 12.0f, 22.03f, 12.0f)
            curveTo(22.03f, 12.0f, 22.03f, 8.86f, 21.58f, 7.19f)
            close()
            moveTo(9.97f, 14.86f)
            lineTo(9.97f, 9.14f)
            lineTo(14.97f, 12.0f)
            lineTo(9.97f, 14.86f)
            close()
        }
    }.build()

val AppleMusicIcon: ImageVector
    get() = ImageVector.Builder(
        name = "AppleMusic",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(20.5f, 4.0f)
            lineTo(9.5f, 6.0f)
            curveTo(8.67f, 6.15f, 8.0f, 6.87f, 8.0f, 7.71f)
            lineTo(8.0f, 15.54f)
            curveTo(7.48f, 15.2f, 6.78f, 15.0f, 6.0f, 15.0f)
            curveTo(3.79f, 15.0f, 2.0f, 16.34f, 2.0f, 18.0f)
            curveTo(2.0f, 19.66f, 3.79f, 21.0f, 6.0f, 21.0f)
            curveTo(8.21f, 21.0f, 10.0f, 19.66f, 10.0f, 18.0f)
            lineTo(10.0f, 9.71f)
            lineTo(19.0f, 8.07f)
            lineTo(19.0f, 13.54f)
            curveTo(18.48f, 13.2f, 17.78f, 13.0f, 17.0f, 13.0f)
            curveTo(14.79f, 13.0f, 13.0f, 14.34f, 13.0f, 16.0f)
            curveTo(13.0f, 17.66f, 14.79f, 19.0f, 17.0f, 19.0f)
            curveTo(19.21f, 19.0f, 21.0f, 17.66f, 21.0f, 16.0f)
            lineTo(21.0f, 5.5f)
            curveTo(21.0f, 4.67f, 20.33f, 4.0f, 20.5f, 4.0f)
            close()
        }
    }.build()
