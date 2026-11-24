package com.liquid.player.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.liquid.player.PlayerViewModel
import com.liquid.player.UiState
import com.liquid.player.data.formatTime
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(state: UiState, vm: PlayerViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val t = state.current ?: return
    val fraction = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val smooth by animateFloatAsState(fraction, tween(400), label = "mini-progress")

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(66.dp)
            .pressable(onOpen)
            .glass(RoundedCornerShape(33.dp), tint = t.accent, strength = 1.1f, base = Color(0xB3121217))
            .drawBehind {
                drawRect(
                    t.accent.copy(alpha = 0.22f),
                    size = Size(size.width * smooth, size.height),
                )
            }
            .padding(start = 9.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArt(t, Modifier.size(48.dp), corner = 24.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(46.dp).pressable { vm.togglePlay() }, contentAlignment = Alignment.Center) {
            Crossfade(state.isPlaying, animationSpec = tween(300), label = "mini-pp") { p ->
                Icon(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Пауза", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        Box(Modifier.size(46.dp).pressable { vm.next() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.SkipNext, "Далее", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun FullPlayer(state: UiState, vm: PlayerViewModel, onClose: () -> Unit) {
    val t = state.current ?: return
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val artScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.84f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 90f),
        label = "art-scale",
    )
    var dragPos by remember { mutableStateOf<Float?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    val duration = state.durationMs.coerceAtLeast(1L).toFloat()

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .graphicsLayer { alpha = 1f - (offsetY.value / 1800f).coerceIn(0f, 0.6f) }
            .background(
                Brush.verticalGradient(
                    listOf(t.accent.copy(alpha = 0.30f).compositeOver(Color(0xF2101016)), Color(0xFA08080C))
                )
            )
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetY.value > 260f) onClose() else offsetY.animateTo(0f, spring(0.8f, 200f))
                        }
                    },
                    onDragCancel = { scope.launch { offsetY.animateTo(0f, spring(0.8f, 200f)) } },
                ) { change, dy ->
                    change.consume()
                    scope.launch { offsetY.snapTo((offsetY.value + dy).coerceAtLeast(0f)) }
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 44.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                )
                Box(
                    Modifier.align(Alignment.CenterStart).size(44.dp).pressable { showOptions = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Timer, "Таймер сна",
                        tint = if (state.sleepEndsAt > 0 || state.sleepAfterTrack) t.accent else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Box(
                    Modifier.align(Alignment.CenterEnd).size(44.dp).pressable(onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.KeyboardArrowDown, "Свернуть", tint = Color.White, modifier = Modifier.size(30.dp)) }
            }

            Spacer(Modifier.weight(0.6f))

            TrackArt(
                t,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = artScale
                        scaleY = artScale
                    }
                    .shadow(36.dp * artScale, RoundedCornerShape(40.dp), ambientColor = t.accent, spotColor = t.accent),
                corner = 40.dp,
            )

            Spacer(Modifier.weight(0.7f))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(t.artist, color = Color.White.copy(alpha = 0.65f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FavStar(t.key in state.favorites, { vm.toggleFavorite(t.key) }, size = 28.dp)
            }

            Spacer(Modifier.height(14.dp))

            Slider(
                value = dragPos ?: state.positionMs.toFloat().coerceIn(0f, duration),
                onValueChange = { dragPos = it },
                onValueChangeFinished = {
                    dragPos?.let { vm.seekTo(it.toLong()) }
                    dragPos = null
                },
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                ),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime((dragPos ?: state.positionMs.toFloat()).toLong()), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Text(formatTime(state.durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlIcon(Icons.Rounded.Shuffle, active = state.shuffle, accent = t.accent) { vm.toggleShuffle() }
                ControlIcon(Icons.Rounded.SkipPrevious, size = 40.dp) { vm.previous() }
                Box(
                    Modifier
                        .size(84.dp)
                        .pressable { vm.togglePlay() }
                        .glass(CircleShape, tint = t.accent, strength = 1.9f, base = Color(0x33000000), rainbowEdge = true),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(state.isPlaying, animationSpec = tween(350), label = "big-pp") { p ->
                        Icon(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Играть", tint = Color.White, modifier = Modifier.size(46.dp))
                    }
                }
                ControlIcon(Icons.Rounded.SkipNext, size = 40.dp) { vm.next() }
                Box(contentAlignment = Alignment.Center) {
                    ControlIcon(
                        if (state.repeatTimes != 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        active = state.repeatTimes != 1 || state.repeatAll,
                        accent = t.accent,
                    ) { showOptions = true }
                    if (state.repeatTimes != 1) {
                        Text(
                            if (state.repeatTimes == 0) "∞" else "${state.playsDone.coerceAtMost(state.repeatTimes)}/${state.repeatTimes}",
                            color = t.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }

            Spacer(Modifier.weight(0.5f))
        }

        AnimatedVisibility(
            visible = showOptions,
            enter = fadeIn(tween(450)),
            exit = fadeOut(tween(350)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showOptions = false }
            )
        }
        AnimatedVisibility(
            visible = showOptions,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(650, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(500)),
            exit = slideOutVertically(tween(500, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(350)),
        ) {
            OptionsPanel(state, vm, t.accent) { showOptions = false }
        }
    }
}

@Composable
private fun ControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp = 28.dp,
    active: Boolean = false,
    accent: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(Modifier.size(52.dp).pressable(onClick), contentAlignment = Alignment.Center) {
        Icon(
            icon,
            null,
            tint = if (active) accent else Color.White.copy(alpha = if (size > 30.dp) 1f else 0.7f),
            modifier = Modifier.size(size),
        )
    }
}


@Composable
private fun Chip(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val bg by animateFloatAsState(if (selected) 1f else 0f, tween(350), label = "chip")
    Box(
        Modifier
            .pressable(onClick)
            .glass(
                RoundedCornerShape(50),
                tint = if (selected) accent else Color.White,
                strength = 0.7f + bg * 1.3f,
                base = Color(0x33000000),
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun OptionsPanel(state: UiState, vm: PlayerViewModel, accent: Color, onDone: () -> Unit) {
    val remainingMin = if (state.sleepEndsAt > 0) {
        ((state.sleepEndsAt - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)
    } else 0L

    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp), strength = 1.1f, base = Color(0xE6141419))
            .pointerInput(Unit) { detectTapGestures { } }
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(width = 44.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
        Spacer(Modifier.height(16.dp))

        // ---- repeat count ----
        Text("Сколько раз играть трек", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(4.dp))
        Text(
            "Трек сыграет столько раз подряд и сам перейдёт к следующему.",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(1, 2, 3, 5, 10, 0).forEach { n ->
                Chip(if (n == 0) "∞" else "$n×", state.repeatTimes == n, accent) { vm.setRepeatTimes(n) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Своё число", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).pressable { vm.setRepeatTimes(if (state.repeatTimes == 0) 1 else state.repeatTimes - 1) }
                    .glass(CircleShape, base = Color(0x33000000)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Remove, "Меньше", tint = Color.White, modifier = Modifier.size(22.dp)) }
            Text(
                if (state.repeatTimes == 0) "∞" else "${state.repeatTimes}",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.widthIn(min = 56.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Box(
                Modifier.size(44.dp).pressable { vm.setRepeatTimes(state.repeatTimes + 1) }
                    .glass(CircleShape, base = Color(0x33000000)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "Больше", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("Повторять весь список", state.repeatAll, accent) { vm.toggleRepeatAll() }
        }

        Spacer(Modifier.height(26.dp))

        // ---- sleep timer ----
        Text("Таймер сна", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                state.sleepAfterTrack -> "Остановлюсь, когда закончится этот трек"
                remainingMin > 0 -> "Музыка плавно затихнет примерно через $remainingMin мин"
                else -> "Музыка плавно затихнет и остановится"
            },
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Chip("Выкл", state.sleepEndsAt == 0L && !state.sleepAfterTrack, accent) {
                vm.setSleepMinutes(0)
                vm.setSleepAfterTrack(false)
            }
            listOf(15, 30, 45, 60, 90).forEach { m ->
                Chip("$m мин", false, accent) { vm.setSleepMinutes(m) }
            }
            Chip("После трека", state.sleepAfterTrack, accent) { vm.setSleepAfterTrack(!state.sleepAfterTrack) }
        }
        Spacer(Modifier.height(22.dp))
        GlassButton("Готово", Icons.Rounded.KeyboardArrowDown, onDone, Modifier.align(Alignment.CenterHorizontally))
    }
}
