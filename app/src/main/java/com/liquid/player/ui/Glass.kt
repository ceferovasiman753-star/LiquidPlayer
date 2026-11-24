package com.liquid.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * "Liquid Glass" surface: translucent body, soft top-left specular light and a
 * bright refractive edge. With [rainbowEdge] the rim gets the cyan / magenta
 * dispersion seen on the iOS lens bubble.
 */
fun Modifier.glass(
    shape: Shape,
    tint: Color = Color.White,
    strength: Float = 1f,
    base: Color = Color.Transparent,
    rainbowEdge: Boolean = false,
): Modifier {
    val edge = if (rainbowEdge) {
        Brush.sweepGradient(
            listOf(
                Color(0xFF7FE8FF).copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.95f),
                Color(0xFFFF8AE6).copy(alpha = 0.75f),
                Color.White.copy(alpha = 0.35f),
                Color(0xFF7FE8FF).copy(alpha = 0.85f),
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.60f),
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.28f),
            )
        )
    }
    return this
        .clip(shape)
        .background(base)
        .background(
            Brush.verticalGradient(
                listOf(
                    tint.copy(alpha = (0.22f * strength).coerceAtMost(0.9f)),
                    tint.copy(alpha = (0.07f * strength).coerceAtMost(0.9f)),
                )
            )
        )
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (0.24f * strength).coerceAtMost(0.8f)),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.18f, 0f),
                    radius = size.maxDimension * 0.85f,
                )
            )
        }
        .border(1.dp, edge, shape)
}

fun Color.hueShift(deg: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[0] = (hsv[0] + deg + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
