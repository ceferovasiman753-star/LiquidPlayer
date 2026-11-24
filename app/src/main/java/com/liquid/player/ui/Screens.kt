package com.liquid.player.ui

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.liquid.player.DlState
import com.liquid.player.PlayerViewModel
import com.liquid.player.UiState
import com.liquid.player.data.Track
import com.liquid.player.data.formatTime
import com.liquid.player.download.VideoItem

private val PageBottom = 210.dp

@Composable
private fun PageTitle(text: String, index: Int = 0) {
    Text(
        text,
        style = TitleStyle,
        modifier = Modifier
            .padding(start = 24.dp, top = 64.dp, bottom = 22.dp)
            .appear(index),
    )
}

@Composable
private fun PermissionCard(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(30.dp), base = Color(0x33000000))
            .padding(22.dp),
    ) {
        Text("Нужен доступ к музыке", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Разреши приложению читать аудио на телефоне — тогда здесь появится вся твоя музыка. Скачанное из YouTube работает и без этого.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        GlassButton("Разрешить", Icons.Rounded.Check, onRequest)
    }
}

// --------------------------------------------------------------------------------------
// Home
// --------------------------------------------------------------------------------------

@Composable
fun HomeScreen(
    state: UiState,
    vm: PlayerViewModel,
    onRequestPermission: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val recent = remember(state.tracks) { state.tracks.sortedByDescending { it.addedAt }.take(15) }
    val top = remember(state.tracks, state.playCounts) {
        state.tracks.filter { (state.playCounts[it.key] ?: 0) > 0 }
            .sortedByDescending { state.playCounts[it.key] ?: 0 }
            .take(15)
    }
    val downloaded = remember(state.tracks) { state.tracks.filter { it.downloaded }.take(15) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = PageBottom),
    ) {
        item { PageTitle("Главная") }

        if (!state.hasPermission) {
            item { PermissionCard(onRequestPermission, Modifier.appear(1)) }
            item { Spacer(Modifier.height(16.dp)) }
        }

        item {
            Column(
                Modifier
                    .appear(1)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(34.dp), tint = (state.current?.accent ?: Color.White), strength = 1.2f, base = Color(0x33000000))
                    .padding(24.dp),
            ) {
                Text(
                    if (state.current != null) "Сейчас играет" else "Твоя музыка",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    state.current?.title ?: "${state.tracks.size} треков офлайн",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.tracks.isNotEmpty()) {
                        GlassButton("Играть", Icons.Rounded.PlayArrow, {
                            if (state.current != null) vm.togglePlay()
                            else vm.play(state.tracks.first(), state.tracks)
                        })
                        GlassButton("Вперемешку", Icons.Rounded.Shuffle, { vm.playAllShuffled(state.tracks) })
                    } else {
                        GlassButton("Скачать музыку", Icons.Rounded.Download, onOpenSearch)
                    }
                }
            }
        }

        shelf("Недавно добавлено", recent, state, vm, 2)
        shelf("Часто слушаю", top, state, vm, 4)
        shelf("Скачано с YouTube", downloaded, state, vm, 6)

        if (state.tracks.isEmpty() && state.hasPermission && !state.loading) {
            item {
                Text(
                    "Пока пусто. Открой поиск (лупа справа внизу) и скачай первый трек.",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp).appear(2),
                )
            }
        }
    }
}

private fun LazyListScope.shelf(
    title: String,
    list: List<Track>,
    state: UiState,
    vm: PlayerViewModel,
    delayIndex: Int,
) {
    if (list.isEmpty()) return
    item(key = "h_$title") {
        Text(
            title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(start = 24.dp, top = 30.dp, bottom = 12.dp).appear(delayIndex),
        )
    }
    item(key = "r_$title") {
        LazyRow(
            Modifier.appear(delayIndex + 1),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(list, key = { it.key }) { t ->
                Column(
                    Modifier.width(148.dp).pressable { vm.play(t, list) },
                ) {
                    Box {
                        TrackArt(t, Modifier.size(148.dp), corner = 28.dp)
                        if (state.currentKey == t.key) {
                            Box(
                                Modifier.matchParentSize().clip(RoundedCornerShape(28.dp))
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center,
                            ) { EqBars(state.isPlaying, Color.White) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(t.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------
// Browse (Обзор)
// --------------------------------------------------------------------------------------

private class Mood(
    val title: String,
    val color: Color,
    val shuffle: Boolean,
    val pick: (UiState) -> List<Track>,
)

private val moods = listOf(
    Mood("В дороге", Color(0xFFE5A800), true) { it.tracks },
    Mood("Вечеринка", Color(0xFF9B6BE0), true) { s ->
        s.tracks.filter { it.durationMs in 1..210_000 }.ifEmpty { s.tracks }
    },
    Mood("Заряд энергии", Color(0xFFE0CC6A), true) { it.tracks.shuffled().take(25) },
    Mood("Новое", Color(0xFF7AA0E0), false) { s -> s.tracks.sortedByDescending { it.addedAt }.take(30) },
    Mood("Скачанные", Color(0xFFD1403A), false) { s -> s.tracks.filter { it.downloaded } },
    Mood("Избранное", Color(0xFFC21F3A), true) { it.favoriteTracks },
    Mood("Отдых", Color(0xFF2FB8A6), true) { s -> s.tracks.filter { it.durationMs >= 300_000 } },
    Mood("Сон", Color(0xFF6A35C2), false) { s -> s.tracks.sortedBy { it.title.lowercase() } },
)

@Composable
fun BrowseScreen(state: UiState, vm: PlayerViewModel) {
    val ctx = LocalContext.current
    val lists = remember(state.tracks, state.favorites) { moods.map { it.pick(state) } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = PageBottom),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Обзор", style = TitleStyle, modifier = Modifier.padding(start = 2.dp, top = 64.dp, bottom = 6.dp).appear(0))
        }
        gridItemsIndexed(moods) { i, mood ->
            val list = lists[i]
            MoodCard(
                mood = mood,
                art = list.firstOrNull(),
                modifier = Modifier.appear(i / 2 + 1),
                onClick = {
                    if (list.isEmpty()) {
                        Toast.makeText(ctx, "В «${mood.title}» пока пусто", Toast.LENGTH_SHORT).show()
                    } else if (mood.shuffle) {
                        vm.playAllShuffled(list)
                    } else {
                        vm.play(list.first(), list)
                    }
                },
            )
        }
    }
}

@Composable
private fun MoodCard(mood: Mood, art: Track?, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(136.dp)
            .pressable(onClick)
            .clip(shape)
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.25f))),
                shape,
            ),
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(0.28f)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(mood.color, mood.color.copy(alpha = 0.78f))))
            )
            Box(Modifier.weight(0.72f).fillMaxHeight()) {
                TrackArt(art, Modifier.fillMaxSize(), corner = 0.dp)
                Box(Modifier.fillMaxSize().background(mood.color.copy(alpha = 0.55f)))
            }
        }
        Text(
            mood.title,
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Thin, color = Color.White),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 14.dp, end = 12.dp),
        )
    }
}

// --------------------------------------------------------------------------------------
// Library (Медиатека)
// --------------------------------------------------------------------------------------

@Composable
fun LibraryScreen(state: UiState, vm: PlayerViewModel, onRequestPermission: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val list = remember(state.tracks, q) {
        val s = q.trim().lowercase()
        if (s.isEmpty()) state.tracks
        else state.tracks.filter { it.title.lowercase().contains(s) || it.artist.lowercase().contains(s) }
    }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = PageBottom),
    ) {
        item { PageTitle("Медиатека") }
        if (!state.hasPermission) {
            item { PermissionCard(onRequestPermission, Modifier.appear(1)) }
            item { Spacer(Modifier.height(16.dp)) }
        }
        item {
            Column(Modifier.padding(horizontal = 22.dp).appear(1)) {
                GlassField(q, { q = it }, "Поиск по моей музыке")
                Spacer(Modifier.height(14.dp))
                if (state.tracks.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassButton("Играть", Icons.Rounded.PlayArrow, { vm.play(list.firstOrNull() ?: state.tracks.first(), list.ifEmpty { state.tracks }) })
                        GlassButton("Вперемешку", Icons.Rounded.Shuffle, { vm.playAllShuffled(list.ifEmpty { state.tracks }) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("${list.size} треков", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            }
        }
        itemsIndexed(list, key = { _, t -> t.key }) { i, t ->
            TrackRow(
                track = t,
                isCurrent = state.currentKey == t.key,
                isPlaying = state.isPlaying,
                favorite = t.key in state.favorites,
                onClick = { vm.play(t, list) },
                onFavorite = { vm.toggleFavorite(t.key) },
                modifier = if (i < 10) Modifier.appear(i / 2 + 2) else Modifier,
            )
        }
    }
}

// --------------------------------------------------------------------------------------
// Favorites (Избранное)
// --------------------------------------------------------------------------------------

@Composable
fun FavoritesScreen(state: UiState, vm: PlayerViewModel) {
    val favs = state.favoriteTracks
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = PageBottom),
    ) {
        item { PageTitle("Избранное") }
        if (favs.isEmpty()) {
            item {
                val t = rememberInfiniteTransition(label = "star")
                val s by t.animateFloat(
                    0.9f, 1.12f,
                    infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "pulse",
                )
                Column(
                    Modifier.fillMaxWidth().padding(top = 60.dp, start = 32.dp, end = 32.dp).appear(1),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(110.dp)
                            .graphicsLayer {
                                scaleX = s
                                scaleY = s
                            }
                            .glass(CircleShape, tint = Color(0xFFFFC94A), strength = 1.4f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFD36A), modifier = Modifier.size(52.dp))
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("Тут будут твои любимые треки", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(6.dp))
                    Text("Нажми звёздочку рядом с песней", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
                }
            }
        } else {
            item {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 4.dp).appear(1), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton("Играть", Icons.Rounded.PlayArrow, { vm.play(favs.first(), favs) })
                    GlassButton("Вперемешку", Icons.Rounded.Shuffle, { vm.playAllShuffled(favs) })
                }
            }
            itemsIndexed(favs, key = { _, t -> t.key }) { i, t ->
                TrackRow(
                    track = t,
                    isCurrent = state.currentKey == t.key,
                    isPlaying = state.isPlaying,
                    favorite = true,
                    onClick = { vm.play(t, favs) },
                    onFavorite = { vm.toggleFavorite(t.key) },
                    modifier = if (i < 10) Modifier.appear(i / 2 + 2) else Modifier,
                )
            }
        }
    }
}

// --------------------------------------------------------------------------------------
// Search / Download
// --------------------------------------------------------------------------------------

private fun friendlyError(msg: String): String {
    val m = msg.lowercase()
    return if (
        "unable to download" in m || "resolve" in m || "network" in m ||
        "timed out" in m || "connection" in m || "unreachable" in m
    ) {
        "Нет интернета. Плеер работает офлайн, а скачивать можно только с сетью."
    } else {
        msg.lines().lastOrNull { it.isNotBlank() }?.take(160) ?: "Ошибка"
    }
}

@Composable
fun SearchScreen(state: UiState, vm: PlayerViewModel) {
    var q by rememberSaveable { mutableStateOf("") }
    val clip = LocalClipboardManager.current
    val focus = LocalFocusManager.current

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = PageBottom),
    ) {
        item { PageTitle("Скачать") }
        item {
            Column(Modifier.padding(horizontal = 22.dp).appear(1)) {
                Text(
                    "Найди трек на YouTube или вставь ссылку — он скачается в MP3 и будет играть без интернета.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(16.dp))
                GlassField(
                    value = q,
                    onValueChange = { q = it },
                    hint = "Название или ссылка",
                    onSearch = {
                        focus.clearFocus()
                        vm.search(q)
                    },
                    trailing = {
                        if (q.isEmpty()) {
                            Box(
                                Modifier.size(44.dp).pressable { q = clip.getText()?.text.orEmpty() },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.ContentPaste, "Вставить", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }
                        } else {
                            Box(
                                Modifier.size(44.dp).pressable { q = "" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Close, "Очистить", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }
                        }
                    },
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassButton("Найти", Icons.Rounded.Search, {
                        focus.clearFocus()
                        vm.search(q)
                    })
                    Spacer(Modifier.width(14.dp))
                    if (!state.ytReady) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Готовлю загрузчик…", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                    }
                }
            }
        }

        if (state.searching) {
            item {
                Row(Modifier.padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Ищу…", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
        state.searchError?.let { err ->
            item {
                Text(
                    friendlyError(err),
                    color = Color(0xFFFF9C9C),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                    fontSize = 14.sp,
                )
            }
        }

        itemsIndexed(state.searchResults, key = { _, v -> v.id }) { i, v ->
            ResultRow(
                v = v,
                dl = state.downloads[v.id] ?: DlState.Idle,
                onDownload = { vm.download(v) },
                modifier = if (i < 8) Modifier.appear(i / 2) else Modifier,
            )
        }
    }
}

@Composable
private fun ResultRow(v: VideoItem, dl: DlState, onDownload: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = v.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 104.dp, height = 68.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                v.uploader + if (v.durationSec > 0) " · " + formatTime(v.durationSec * 1000L) else "",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (dl is DlState.Failed) {
                Text(friendlyError(dl.message), color = Color(0xFFFF9C9C), fontSize = 12.sp, maxLines = 2)
            }
        }
        Spacer(Modifier.width(10.dp))
        DownloadButton(dl, onDownload)
    }
}

@Composable
private fun DownloadButton(dl: DlState, onClick: () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = (dl as? DlState.Running)?.progress ?: 0f,
        animationSpec = tween(600),
        label = "dl",
    )
    val tint = when (dl) {
        is DlState.Done -> Color(0xFF56E39F)
        is DlState.Failed -> Color(0xFFFF7B7B)
        else -> Color.White
    }
    Box(
        Modifier
            .size(50.dp)
            .pressable { if (dl is DlState.Idle || dl is DlState.Failed) onClick() }
            .glass(CircleShape, tint = tint, strength = 1.1f, base = Color(0x33000000)),
        contentAlignment = Alignment.Center,
    ) {
        when (dl) {
            is DlState.Running -> {
                if (dl.progress <= 0.01f) {
                    CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 2.5.dp)
                } else {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(26.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        strokeWidth = 2.5.dp,
                    )
                }
            }
            is DlState.Done -> Icon(Icons.Rounded.Check, "Готово", tint = Color.White, modifier = Modifier.size(24.dp))
            is DlState.Failed -> Icon(Icons.Rounded.Refresh, "Повторить", tint = Color.White, modifier = Modifier.size(24.dp))
            else -> Icon(Icons.Rounded.Download, "Скачать", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}
