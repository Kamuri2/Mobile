package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LiquidGlassBorder


@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = Color(0x1AFFFFFF),
    borderColor: Color = LiquidGlassBorder,
    borderWidth: Dp = 1.dp,
    glowColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else Modifier

    val glowModifier = if (glowColor != null) {
        Modifier.shadow(
            elevation = 16.dp,
            shape = shape,
            ambientColor = glowColor,
            spotColor = glowColor
        )
    } else Modifier

    Box(
        modifier = modifier
            .then(glowModifier)
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        backgroundColor,
                        backgroundColor.copy(alpha = (backgroundColor.alpha * 0.6f).coerceAtLeast(0.05f))
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(400f, 800f)
                )
            )
            .border(
                border = BorderStroke(
                    width = borderWidth,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            borderColor,
                            Color.White.copy(alpha = 0.4f),
                            borderColor.copy(alpha = 0.15f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(500f, 500f)
                    )
                ),
                shape = shape
            )
            .drawBehind {
                // Liquid Specular Edge Reflection Highlight at Top-Left
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = size.width * 0.4f,
                    center = Offset(size.width * 0.15f, 0f)
                )
            }
            .then(clickModifier)
    ) {
        content()
    }
}
