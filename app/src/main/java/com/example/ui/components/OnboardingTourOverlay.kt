package com.example.ui.components

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.Translations

data class TourStep(
    val titleKey: String,
    val descKey: String,
    val icon: ImageVector,
    val defaultTitle: String,
    val defaultDesc: String,
    val targetType: TargetPlacement,
    val pointerLabel: String
)

enum class TargetPlacement {
    TOP_BAR,
    BOTTOM_NAV,
    MINI_PLAYER,
    CENTER_WELCOME,
    READY_FINISH
}

@Composable
fun OnboardingTourOverlay(
    language: String,
    isDarkMode: Boolean,
    primaryColor: Color,
    onTourFinished: () -> Unit
) {
    val context = LocalContext.current
    var currentStepIndex by remember { mutableIntStateOf(0) }

    val steps = remember {
        listOf(
            TourStep(
                titleKey = "tour_step1_title",
                descKey = "tour_step1_desc",
                icon = Icons.Default.Home,
                defaultTitle = "¡Bienvenido a Fuzion Player!",
                defaultDesc = "Tu música favorita, álbumes recomendados y accesos rápidos se reúnen aquí en una interfaz moderna y fluida.",
                targetType = TargetPlacement.CENTER_WELCOME,
                pointerLabel = "Inicio y Recomendaciones"
            ),
            TourStep(
                titleKey = "tour_step2_title",
                descKey = "tour_step2_desc",
                icon = Icons.Default.LibraryMusic,
                defaultTitle = "Navega tu Biblioteca",
                defaultDesc = "Explora cómodamente entre Canciones, Carpetas locales, Álbumes, Artistas y Listas de reproducción desde la barra inferior.",
                targetType = TargetPlacement.BOTTOM_NAV,
                pointerLabel = "Barra de Navegación"
            ),
            TourStep(
                titleKey = "tour_step3_title",
                descKey = "tour_step3_desc",
                icon = Icons.Default.PlayCircle,
                defaultTitle = "Mini-Reproductor y Gestos",
                defaultDesc = "Toca la barra para abrir el reproductor a pantalla completa. Para minimizarlo, simplemente desliza la carátula o pantalla hacia abajo con suavidad.",
                targetType = TargetPlacement.MINI_PLAYER,
                pointerLabel = "Reproductor y Gestos"
            ),
            TourStep(
                titleKey = "tour_step4_title",
                descKey = "tour_step4_desc",
                icon = Icons.Default.Settings,
                defaultTitle = "Ajustes y Personalización",
                defaultDesc = "En la parte superior accede a tu Perfil y Ajustes: cambia el tema OLED, ajusta el Ecualizador Líquido, idioma y escanea nueva música.",
                targetType = TargetPlacement.TOP_BAR,
                pointerLabel = "Perfil y Ajustes"
            ),
            TourStep(
                titleKey = "tour_step5_title",
                descKey = "tour_step5_desc",
                icon = Icons.Default.CheckCircle,
                defaultTitle = "¡Todo Listo para Escuchar!",
                defaultDesc = "Tu reproductor está configurado y optimizado con sonido de máxima fidelidad. ¡Disfruta de la mejor experiencia musical!",
                targetType = TargetPlacement.READY_FINISH,
                pointerLabel = "¡A disfrutar!"
            )
        )
    }

    val totalSteps = steps.size
    val currentStep = steps[currentStepIndex]

    fun completeTour() {
        val prefs = context.getSharedPreferences("app_guide_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("guide_completed", true).apply()
        onTourFinished()
    }

    BackHandler {
        if (currentStepIndex > 0) {
            currentStepIndex--
        } else {
            completeTour()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (currentStepIndex < totalSteps - 1) {
                    currentStepIndex++
                } else {
                    completeTour()
                }
            }
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val density = LocalDensity.current

        val screenWidthPx = with(density) { screenWidth.toPx() }
        val screenHeightPx = with(density) { screenHeight.toPx() }

        // Spotlight Cutout Bounds calculated per step
        val targetRect = remember(currentStepIndex, screenWidthPx, screenHeightPx) {
            when (currentStep.targetType) {
                TargetPlacement.TOP_BAR -> {
                    Rect(
                        left = with(density) { 10.dp.toPx() },
                        top = with(density) { 34.dp.toPx() },
                        right = screenWidthPx - with(density) { 10.dp.toPx() },
                        bottom = with(density) { 96.dp.toPx() }
                    )
                }
                TargetPlacement.BOTTOM_NAV -> {
                    Rect(
                        left = with(density) { 8.dp.toPx() },
                        top = screenHeightPx - with(density) { 70.dp.toPx() },
                        right = screenWidthPx - with(density) { 8.dp.toPx() },
                        bottom = screenHeightPx - with(density) { 6.dp.toPx() }
                    )
                }
                TargetPlacement.MINI_PLAYER -> {
                    Rect(
                        left = with(density) { 8.dp.toPx() },
                        top = screenHeightPx - with(density) { 142.dp.toPx() },
                        right = screenWidthPx - with(density) { 8.dp.toPx() },
                        bottom = screenHeightPx - with(density) { 74.dp.toPx() }
                    )
                }
                TargetPlacement.CENTER_WELCOME -> {
                    Rect(
                        left = with(density) { 14.dp.toPx() },
                        top = with(density) { 108.dp.toPx() },
                        right = screenWidthPx - with(density) { 14.dp.toPx() },
                        bottom = with(density) { 270.dp.toPx() }
                    )
                }
                TargetPlacement.READY_FINISH -> {
                    Rect(
                        left = screenWidthPx * 0.22f,
                        top = screenHeightPx * 0.16f,
                        right = screenWidthPx * 0.78f,
                        bottom = screenHeightPx * 0.36f
                    )
                }
            }
        }

        // Smooth morphing coordinates between steps
        val animLeft by animateFloatAsState(targetValue = targetRect.left, animationSpec = tween(380, easing = FastOutSlowInEasing), label = "animLeft")
        val animTop by animateFloatAsState(targetValue = targetRect.top, animationSpec = tween(380, easing = FastOutSlowInEasing), label = "animTop")
        val animRight by animateFloatAsState(targetValue = targetRect.right, animationSpec = tween(380, easing = FastOutSlowInEasing), label = "animRight")
        val animBottom by animateFloatAsState(targetValue = targetRect.bottom, animationSpec = tween(380, easing = FastOutSlowInEasing), label = "animBottom")

        // Pulse Animation for Spotlight Border and Halo
        val infiniteTransition = rememberInfiniteTransition(label = "SpotlightPulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(850, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        val arrowBounce by infiniteTransition.animateFloat(
            initialValue = -7f,
            targetValue = 7f,
            animationSpec = infiniteRepeatable(
                animation = tween(650, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "arrowBounce"
        )

        // Scrim canvas with BlendMode.Clear cutting out the illuminated spotlight
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            // Dark Scrim Backdrop
            drawRect(color = Color.Black.copy(alpha = 0.82f))

            // Cut out spotlight window
            val cornerRadius = CornerRadius(with(density) { 20.dp.toPx() })
            val currentWidth = animRight - animLeft
            val currentHeight = animBottom - animTop

            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(animLeft, animTop),
                size = Size(currentWidth, currentHeight),
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Clear
            )

            // Outer Glowing Pulsing Accent Border around the cutout
            drawRoundRect(
                color = primaryColor.copy(alpha = pulseAlpha),
                topLeft = Offset(animLeft - 3f, animTop - 3f),
                size = Size(currentWidth + 6f, currentHeight + 6f),
                cornerRadius = cornerRadius,
                style = Stroke(width = with(density) { 2.5.dp.toPx() })
            )
        }

        val isTargetAtBottom = currentStep.targetType == TargetPlacement.BOTTOM_NAV ||
                currentStep.targetType == TargetPlacement.MINI_PLAYER

        val isTargetAtTop = currentStep.targetType == TargetPlacement.TOP_BAR

        // Interactive Tour Dialog Card placement
        val cardAlignment = if (isTargetAtBottom) Alignment.TopCenter else Alignment.BottomCenter
        val cardPaddingTop = if (isTargetAtBottom) screenHeight * 0.12f else if (isTargetAtTop) screenHeight * 0.22f else 0.dp
        val cardPaddingBottom = if (isTargetAtBottom || isTargetAtTop) 0.dp else screenHeight * 0.10f

        // Floating Pointer Arrow pointing at the spotlight element
        Box(modifier = Modifier.fillMaxSize()) {
            if (isTargetAtBottom) {
                // Arrow pointing DOWN toward the bottom elements
                val arrowTargetY = with(density) { (animTop - 40.dp.toPx() + arrowBounce).dp }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = arrowTargetY),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(primaryColor)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentStep.pointerLabel,
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else if (isTargetAtTop) {
                // Arrow pointing UP toward top bar
                val arrowTargetY = with(density) { (animBottom + 12.dp.toPx() + arrowBounce).dp }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = arrowTargetY),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(primaryColor)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentStep.pointerLabel,
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Tour Step Information Card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = cardPaddingTop, bottom = cardPaddingBottom, start = 18.dp, end = 18.dp),
            contentAlignment = cardAlignment
        ) {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkMode) Color(0xFF1B1B22) else Color(0xFFFFFFFF)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.6f),
                                if (isDarkMode) Color(0x33FFFFFF) else Color(0x1F000000)
                            )
                        ),
                        shape = RoundedCornerShape(26.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Prevent click on card from dismissing tour accidentally
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                ) {
                    // Header Row: Step Badge + Skip Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Step Counter Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(primaryColor.copy(alpha = 0.20f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = Translations.get(language, "tour_step", "Paso ${currentStepIndex + 1} de $totalSteps")
                                    .replace("%d", (currentStepIndex + 1).toString()),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        }

                        // Skip Button
                        TextButton(
                            onClick = { completeTour() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = Translations.get(language, "tour_skip", "Saltar"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDarkMode) Color(0xFFAAAAAA) else Color(0xFF666666)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title + Icon Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(primaryColor.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = currentStep.icon,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Text(
                            text = Translations.get(language, currentStep.titleKey, currentStep.defaultTitle),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color.White else Color(0xFF111111)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description text
                    Text(
                        text = Translations.get(language, currentStep.descKey, currentStep.defaultDesc),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = if (isDarkMode) Color(0xFFD4D4DC) else Color(0xFF3D3D45)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Bottom Navigation: Indicator Dots + Back + Next/Finish
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Animated Progress Dots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until totalSteps) {
                                val isSelected = i == currentStepIndex
                                Box(
                                    modifier = Modifier
                                        .height(6.dp)
                                        .width(if (isSelected) 22.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) primaryColor else (if (isDarkMode) Color(0x33FFFFFF) else Color(0x22000000))
                                        )
                                )
                            }
                        }

                        // Action Buttons Row
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (currentStepIndex > 0) {
                                OutlinedButton(
                                    onClick = { currentStepIndex-- },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = Translations.get(language, "tour_prev", "Anterior"),
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (currentStepIndex < totalSteps - 1) {
                                        currentStepIndex++
                                    } else {
                                        completeTour()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (currentStepIndex == totalSteps - 1) {
                                            Translations.get(language, "tour_finish", "¡Empezar a escuchar!")
                                        } else {
                                            Translations.get(language, "tour_next", "Siguiente")
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (currentStepIndex == totalSteps - 1) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
