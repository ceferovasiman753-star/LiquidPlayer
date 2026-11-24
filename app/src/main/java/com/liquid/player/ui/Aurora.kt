package com.liquid.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Slowly drifting colour glow behind everything; the glass surfaces float above it. */
@Composable
fun Aurora(accent: Color, modifier: Modifier = Modifier) {
    val c1 by animateColorAsState(accent, tween(2200, easing = FastOutSlowInEasing), label = "c1")
    val c2 by animateColorAsState(accent.hueShift(55f), tween(2800, easing = FastOutSlowInEasing), label = "c2")
    val c3 by animateColorAsState(accent.hueShift(-45f), tween(3200, easing = FastOutSlowInEasing), label = "c3")

    val t = rememberInfiniteTransition(label = "aurora")
    val p1 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(23000, easing = LinearEasing), RepeatMode.Reverse), label = "p1")
    val p2 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(31000, easing = LinearEasing), RepeatMode.Reverse), label = "p2")
    val p3 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(27000, easing = LinearEasing), RepeatMode.Reverse), label = "p3")

    Canvas(modifier.fillMaxSize().background(Ink)) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.radialGradient(
                listOf(c1.copy(alpha = 0.40f), Color.Transparent),
                center = Offset(w * (0.15f + 0.55f * p1), h * (0.10f + 0.22f * p2)),
                radius = w * 1.05f,
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(c2.copy(alpha = 0.30f), Color.Transparent),
                center = Offset(w * (0.95f - 0.6f * p2), h * (0.55f + 0.2f * p3)),
                radius = w * 1.0f,
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(c3.copy(alpha = 0.26f), Color.Transparent),
                center = Offset(w * (0.2f + 0.5f * p3), h * (0.95f - 0.25f * p1)),
                radius = w * 0.95f,
            )
        )
    }
}
