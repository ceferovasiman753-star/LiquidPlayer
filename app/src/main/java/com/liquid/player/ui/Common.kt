package com.liquid.player.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquid.player.data.Track
import com.liquid.player.data.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Soft, slow "float in" for list items / headers. */
@Composable
fun Modifier.appear(index: Int, stepMs: Int = 80): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceIn(0, 12) * stepMs).toLong())
        a.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 80f
        val s = 0.93f + 0.07f * a.value
        scaleX = s
        scaleY = s
    }
}

/** Tactile press: shrinks with a springy feel and springs back. */
fun Modifier.pressable(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 260f),
        label = "press",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

object ArtCache {
    private val lru = LruCache<String, ImageBitmap>(80)
    fun get(key: String?): ImageBitmap? = if (key == null) null else lru.get(key)
    fun put(key: String, bmp: ImageBitmap) {
        lru.put(key, bmp)
    }
}

private fun loadArt(ctx: Context, t: Track): ImageBitmap? {
    if (t.uri.scheme == "content" && Build.VERSION.SDK_INT >= 29) {
        try {
            return ctx.contentResolver.loadThumbnail(t.uri, Size(512, 512), null).asImageBitmap()
        } catch (_: Throwable) {
        }
    }
    val art = t.artUri ?: return null
    return try {
        ctx.contentResolver.openInputStream(art)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    } catch (_: Throwable) {
        null
    }
}

/** Cover art with a coloured gradient + note icon fallback. */
@Composable
fun TrackArt(track: Track?, modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(ArtCache.get(track?.key), track?.key) {
        val t = track
        if (t == null) {
            value = null
        } else {
            val cached = ArtCache.get(t.key)
            if (cached != null) {
                value = cached
            } else {
                val loaded = withContext(Dispatchers.IO) { loadArt(ctx, t) }
                if (loaded != null) ArtCache.put(t.key, loaded)
                value = loaded
            }
        }
    }
    val base = track?.accent ?: Color(0xFF55556A)
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(base, base.hueShift(48f)))),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = bmp, animationSpec = tween(700), label = "art") { b ->
            if (b != null) {
                Image(
                    bitmap = b,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun EqBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(740, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(630, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "c")
    Row(
        modifier.size(width = 18.dp, height = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(a, b, c).forEach { v ->
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight(if (playing) v else 0.25f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun FavStar(favorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val scale by animateFloatAsState(
        targetValue = if (favorite) 1.12f else 0.95f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "fav",
    )
    Box(modifier.size(44.dp).pressable(onClick), contentAlignment = Alignment.Center) {
        Icon(
            if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = "Избранное",
            tint = if (favorite) Color(0xFFFFC94A) else Color.White.copy(alpha = 0.55f),
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .pressable(onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            TrackArt(track, Modifier.fillMaxSize(), corner = 16.dp)
            if (isCurrent) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) { EqBars(isPlaying, Color.White) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (isCurrent) track.accent else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                buildString {
                    append(track.artist)
                    if (track.durationMs > 0) append(" · ").append(formatTime(track.durationMs))
                },
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FavStar(favorite, onFavorite)
    }
}

@Composable
fun GlassButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Row(
        modifier
            .pressable(onClick)
            .glass(RoundedCornerShape(50), tint = tint, base = Color(0x33000000))
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .glass(RoundedCornerShape(50), base = Color(0x55000000))
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(hint, color = Color.White.copy(alpha = 0.45f), fontSize = 16.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        trailing?.invoke()
    }
}
