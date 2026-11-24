package com.liquid.player.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liquid.player.PlayerViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private const val PAGES = 5 // 0 home, 1 browse, 2 library, 3 favorites, 4 search

/** Pages shrink, fade, parallax and blur while you drag between tabs. */
private fun Modifier.liquidPage(state: PagerState, page: Int): Modifier = graphicsLayer {
    val offset = (state.currentPage - page) + state.currentPageOffsetFraction
    val a = offset.absoluteValue.coerceIn(0f, 1f)
    val s = 1f - 0.10f * a
    scaleX = s
    scaleY = s
    alpha = 1f - 0.8f * a
    translationX = offset * size.width * 0.16f
    if (Build.VERSION.SDK_INT >= 31 && a > 0.02f) {
        renderEffect = BlurEffect(a * 30f, a * 30f)
    }
}

@Composable
fun AppRoot(vm: PlayerViewModel, onRequestPermission: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pager = rememberPagerState(pageCount = { PAGES })
    val scope = rememberCoroutineScope()
    var showPlayer by remember { mutableStateOf(false) }
    val accent = state.current?.accent ?: Color(0xFF6C5CE7)

    val slowSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 130f)

    fun goTo(page: Int) {
        scope.launch { pager.animateScrollToPage(page.coerceIn(0, PAGES - 1), animationSpec = slowSpring) }
    }

    BackHandler(enabled = showPlayer) { showPlayer = false }
    BackHandler(enabled = !showPlayer && pager.currentPage != 0) { goTo(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }

        Aurora(accent)

        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            flingBehavior = PagerDefaults.flingBehavior(
                state = pager,
                snapAnimationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
            ),
        ) { page ->
            Box(Modifier.fillMaxSize().liquidPage(pager, page)) {
                when (page) {
                    0 -> HomeScreen(state, vm, onRequestPermission, onOpenSearch = { goTo(4) })
                    1 -> BrowseScreen(state, vm)
                    2 -> LibraryScreen(state, vm, onRequestPermission)
                    3 -> FavoritesScreen(state, vm)
                    else -> SearchScreen(state, vm)
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            AnimatedVisibility(
                visible = state.current != null && !showPlayer,
                enter = slideInVertically(tween(700, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(700)),
                exit = slideOutVertically(tween(500, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(400)),
            ) {
                MiniPlayer(state, vm, onOpen = { showPlayer = true })
            }
            LiquidNavBar(
                position = pager.currentPage + pager.currentPageOffsetFraction,
                onSelect = { goTo(it) },
                onDragBy = { pages -> pager.dispatchRawDelta(pages * widthPx) },
                onDragEnd = {
                    val target = (pager.currentPage + pager.currentPageOffsetFraction).roundToInt()
                    goTo(target)
                },
            )
        }

        AnimatedVisibility(
            visible = showPlayer && state.current != null,
            enter = slideInVertically(tween(750, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(600)),
            exit = slideOutVertically(tween(650, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(500)),
        ) {
            FullPlayer(state, vm, onClose = { showPlayer = false })
        }
    }
}
