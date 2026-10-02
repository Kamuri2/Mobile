package com.example.ui.components
import androidx.compose.ui.draw.blur

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
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
 * Replaces the old expandable bottom tab.
 */
@Composable
fun ArtistInfoCard(
    track: Track?,
    artistInfo: ArtistInfo?,
    language: String,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val artistName = track?.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown Artist"

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
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header: Hero Banner if image is available, otherwise stylized avatar row
            if (artistInfo?.imageUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    AsyncImage(
                        model = artistInfo.imageUrl,
                        contentDescription = "Artist Banner",
                        contentScale = ContentScale.Crop,
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
                                            Color(0x99FFFFFF),
                                            Color(0xFFFFFFFF)
                                        )
                                    } else {
                                        listOf(
                                            Color.Transparent,
                                            Color(0x771E1E1E),
                                            Color(0xFF1E1E1E)
                                        )
                                    },
                                    startY = 50f
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp)
                    ) {
                        Text(
                            text = artistName,
                            color = textPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                        Text(
                            text = artistName,
                            color = textPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Stats row (Followers, Origin)
            val hasFollowers = !artistInfo?.followers.isNullOrBlank()
            val hasOrigin = !artistInfo?.origin.isNullOrBlank()
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
                                text = artistInfo!!.followers!!,
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
                                text = artistInfo!!.origin!!,
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

            // Biography
            var isBioExpanded by remember { mutableStateOf(false) }
            val rawBio = artistInfo?.bio
            val bioText = if (!rawBio.isNullOrBlank()) rawBio else Translations.get(language, "no_info_available")

            Text(
                text = bioText,
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

            if (!rawBio.isNullOrBlank() && rawBio.length > 130) {
                Text(
                    text = if (isBioExpanded) (if (language.startsWith("Esp", ignoreCase = true)) "Mostrar menos" else "Show less") else (if (language.startsWith("Esp", ignoreCase = true)) "Leer más" else "Read more"),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 2.dp)
                        .clickable { isBioExpanded = !isBioExpanded }
                )
            }

            // Social links
            val hasSocials = !artistInfo?.website.isNullOrBlank() ||
                    !artistInfo?.facebook.isNullOrBlank() ||
                    !artistInfo?.twitter.isNullOrBlank() ||
                    !artistInfo?.instagram.isNullOrBlank() ||
                    !artistInfo?.spotify.isNullOrBlank() ||
                    !artistInfo?.youtube.isNullOrBlank() ||
                    !artistInfo?.appleMusic.isNullOrBlank()

            if (hasSocials) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!artistInfo?.spotify.isNullOrBlank()) {
                        IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo!!.spotify!!)) }) {
                            Icon(SpotifyIcon, contentDescription = "Spotify", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!artistInfo?.instagram.isNullOrBlank()) {
                        IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo!!.instagram!!)) }) {
                            Icon(InstagramIcon, contentDescription = "Instagram", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!artistInfo?.youtube.isNullOrBlank()) {
                        IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo!!.youtube!!)) }) {
                            Icon(YouTubeIcon, contentDescription = "YouTube", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!artistInfo?.appleMusic.isNullOrBlank()) {
                        IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo!!.appleMusic!!)) }) {
                            Icon(AppleMusicIcon, contentDescription = "Apple Music", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!artistInfo?.twitter.isNullOrBlank()) {
                        IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo!!.twitter!!)) }) {
                            Icon(TwitterIcon, contentDescription = "Twitter", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!artistInfo?.facebook.isNullOrBlank()) {
                        IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo!!.facebook!!)) }) {
                            Icon(FacebookIcon, contentDescription = "Facebook", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!artistInfo?.website.isNullOrBlank()) {
                        IconButton(onClick = { uriHandler.openUri(formatUrl(artistInfo!!.website!!)) }) {
                            Icon(Icons.Default.Language, contentDescription = "Website", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
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
                            text = track?.artist ?: "Unknown Artist",
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
                    contentDescription = "Expandir/Contraer",
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = artistInfo?.bio ?: Translations.get(language, "no_info_available"),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
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
