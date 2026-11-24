package com.liquid.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private val tabIcons = listOf(Icons.Rounded.Home, Icons.Rounded.GridView, Icons.Rounded.Layers, Icons.Rounded.Star)
private val tabNames = listOf("Главная", "Обзор", "Медиатека", "Избранное")

/**
 * Floating glass tab bar. [position] is the continuous pager position (0..4):
 * the glass "lens" follows the finger, stretches while moving and magnifies when pressed.
 * Dragging along the bar scrolls the pager through [onDragBy] (in pages).
 */
@Composable
fun LiquidNavBar(
    position: Float,
    onSelect: (Int) -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    val lens by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 200f),
        label = "lens",
    )
    val onDragByS by rememberUpdatedState(onDragBy)
    val onDragEndS by rememberUpdatedState(onDragEnd)
    val searchFrac = (position - 3f).coerceIn(0f, 1f)
    val barBase = Color(0xB3121217)

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(70.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val itemW = maxWidth / 4
            val itemWpx = with(density) { itemW.toPx() }
            val itemWpxS by rememberUpdatedState(itemWpx)
            val groupPos = position.coerceIn(0f, 3f)
            val d = abs(position - position.roundToInt()).coerceAtMost(0.5f)

            // glass bar
            Box(Modifier.fillMaxSize().glass(CircleShape, base = barBase))

            // liquid lens
            Box(
                Modifier
                    .offset { IntOffset((groupPos * itemWpx).roundToInt(), 0) }
                    .width(itemW)
                    .fillMaxHeight()
                    .padding(5.dp)
                    .graphicsLayer {
                        scaleX = 1f + d * 1.1f + 0.16f * lens
                        scaleY = 1f - d * 0.3f + 0.16f * lens
                        alpha = 1f - searchFrac
                    }
                    .glass(CircleShape, strength = 1.5f + lens, rainbowEdge = true)
            )

            // icons + drag handling
            Row(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                onDragEndS()
                            },
                            onDragCancel = {
                                dragging = false
                                onDragEndS()
                            },
                        ) { change, dx ->
                            change.consume()
                            onDragByS(dx / itemWpxS)
                        }
                    }
            ) {
                tabIcons.forEachIndexed { i, icon ->
                    val near = (1f - abs(position - i)).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pressable { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = tabNames[i],
                            tint = lerp(Color.White.copy(alpha = 0.62f), Color.White, near),
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer {
                                    val s = 1f + 0.16f * near + 0.1f * lens * near
                                    scaleX = s
                                    scaleY = s
                                },
                        )
                    }
                }
            }
        }

        // search button
        Box(
            Modifier
                .size(70.dp)
                .pressable { onSelect(4) }
                .glass(
                    CircleShape,
                    tint = lerp(Color.White, Color(0xFFFF9A4D), searchFrac),
                    strength = 1f + searchFrac * 1.2f,
                    base = barBase,
                    rainbowEdge = searchFrac > 0.5f,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = "Поиск и загрузка",
                tint = lerp(Color.White.copy(alpha = 0.7f), Color.White, searchFrac),
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        val s = 1f + 0.14f * searchFrac
                        scaleX = s
                        scaleY = s
                    },
            )
        }
    }
}
