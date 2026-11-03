package com.music.bitchord.ui.components

import com.music.bitchord.ui.screens.ARTIST_DIAL_PAGE_KEY
import com.music.bitchord.ui.screens.SPEED_DIAL_PAGE_KEYS
import com.music.bitchord.ui.theme.LocalNowPlayingTint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.math.min

/** The logo is three squares across and four down, in a 3:4 box. */
const val SQUARE_LOGO_ASPECT = 3f / 4f

/** Columns and rows of the lit (true) and dim (false) squares that make the mark. */
private val LOGO_LIT = arrayOf(
    booleanArrayOf(false, true, true),
    booleanArrayOf(false, true, false),
    booleanArrayOf(true, true, false),
    booleanArrayOf(true, true, false),
)

private const val DIM_ALPHA = 0.2f

/** Every square in the grid, lit or not: 3 x 4. */
private const val LOGO_SQUARES = 12

/**
 * Where each edge square sits on the loop the spinner runs round, clockwise
 * from the top left: A1, B1, C1, C2, C3, C4, B4, A4, A3, A2. The two in the
 * middle (B2, B3) aren't on it (-1). Indexed by `row * 3 + column`.
 */
private val LOOP_POSITION = floatArrayOf(
    0f, 1f, 2f,
    9f, -1f, 3f,
    8f, -1f, 4f,
    7f, 6f, 5f,
)

private const val LOOP_LENGTH = 10f

/** Where the spinner comes to rest when a refresh ends: A3. */
private const val STOP_AT = 8f

/**
 * The order the squares light in as the logo builds itself back up, the same
 * as the launch splash's: A3, A4, B4, B3, B2, B1, C1. Each is `row * 3 + column`.
 */
private val BUILD_ORDER = intArrayOf(6, 9, 10, 7, 4, 1, 2)

/** One lap of the spinner. */
private const val SPIN_SECONDS = 1.1f

/** How far, in squares, the light trails behind the spinner's head: short, a single square's tail. */
private const val TRAIL_LENGTH = 0.8f

/** How long the logo takes to build itself back up after a refresh. */
private const val BUILD_MS = 7 * 110

private enum class LogoMode { Rest, Spin, Build }

/**
 * The app's mark, drawn as squares so it can come apart and go back together.
 *
 * At rest it is the logo: the lit squares solid, the others faint. Pulled down,
 * the squares light row by row from the top as [pullFraction] goes to 1. While
 * [refreshing], a single light runs clockwise round the edge of the grid; when
 * the refresh ends it carries on to A3, stops there, and the logo builds itself
 * back up from that square in the launch splash's order.
 */
@Composable
fun SquareLogo(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    refreshing: Boolean = false,
    pullFraction: () -> Float = { 0f },
) {
    var mode by remember { mutableStateOf(LogoMode.Rest) }
    var head by remember { mutableFloatStateOf(STOP_AT) }
    // How far the spin has taken over from the pulled squares, and how far the
    // logo has built itself back up.
    val spinIn = remember { Animatable(0f) }
    val built = remember { Animatable(1f) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            if (mode != LogoMode.Spin) {
                mode = LogoMode.Spin
                launch {
                    spinIn.snapTo(0f)
                    spinIn.animateTo(1f, tween(180))
                }
            }
            var last = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                head = (head + (now - last) / 1_000_000_000f / SPIN_SECONDS * LOOP_LENGTH) % LOOP_LENGTH
                last = now
            }
        } else if (mode == LogoMode.Spin) {
            // On round to A3, then stop there.
            var last = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                val step = (now - last) / 1_000_000_000f / SPIN_SECONDS * LOOP_LENGTH
                last = now
                val left = ((STOP_AT - head) % LOOP_LENGTH + LOOP_LENGTH) % LOOP_LENGTH
                if (step >= left) {
                    head = STOP_AT
                    break
                }
                head = (head + step) % LOOP_LENGTH
            }
            built.snapTo(0f)
            mode = LogoMode.Build
            built.animateTo(1f, tween(BUILD_MS, easing = LinearEasing))
            mode = LogoMode.Rest
        }
    }

    Canvas(modifier = modifier.aspectRatio(SQUARE_LOGO_ASPECT)) {
        // The logo's own 180 x 240 grid: 40-unit squares every 60.
        val unit = min(size.width / 180f, size.height / 240f)
        val cell = 40f * unit
        val pitch = 60f * unit
        val originX = (size.width - (2 * pitch + cell)) / 2f
        val originY = (size.height - (3 * pitch + cell)) / 2f
        val corner = CornerRadius(8f * unit)
        val pull = pullFraction().coerceIn(0f, 1f)
        val pullBlend = (pull * 5f).coerceIn(0f, 1f)
        val currentMode = mode
        val spin = spinIn.value
        val buildLit = 1f + built.value * (BUILD_ORDER.size - 1)

        for (row in 0 until 4) {
            for (col in 0 until 3) {
                val index = row * 3 + col
                val rest = if (LOGO_LIT[row][col]) 1f else DIM_ALPHA
                // Pulled: rows light from the top, A1 first, as the pull goes on.
                val pulled = DIM_ALPHA + (1f - DIM_ALPHA) * (pull * 12f - index).coerceIn(0f, 1f)
                val beforeSpin = rest + (pulled - rest) * pullBlend
                val alpha = when (currentMode) {
                    LogoMode.Rest -> beforeSpin
                    LogoMode.Spin -> {
                        val loopAt = LOOP_POSITION[index]
                        val spinning = if (loopAt < 0f) {
                            DIM_ALPHA
                        } else {
                            var behind = (head - loopAt) % LOOP_LENGTH
                            if (behind < 0f) behind += LOOP_LENGTH
                            DIM_ALPHA + (1f - DIM_ALPHA) * exp(-behind / TRAIL_LENGTH)
                        }
                        beforeSpin + (spinning - beforeSpin) * spin
                    }
                    LogoMode.Build -> {
                        val order = BUILD_ORDER.indexOf(index)
                        if (order < 0) {
                            DIM_ALPHA
                        } else {
                            DIM_ALPHA + (1f - DIM_ALPHA) * (buildLit - order).coerceIn(0f, 1f)
                        }
                    }
                }
                drawRoundRect(
                    color = color.copy(alpha = color.alpha * alpha),
                    topLeft = Offset(originX + col * pitch, originY + row * pitch),
                    size = Size(cell, cell),
                    cornerRadius = corner,
                )
            }
        }
    }
}

/** The mark's height at rest, in the list's first row. */
val HOME_LOGO_LARGE = 60.dp

/** And pinned, once the list has been scrolled. */
val HOME_LOGO_SMALL = 34.dp

/** Space the list reserves at its top for the mark, so the first section clears it. */
val HOME_LOGO_RESERVE = 72.dp

/**
 * The mark on Home: in the list's first row at rest, and there for both
 * dials; scrolled on past them, it rides up to the top left, level with the
 * account button, at the same size.
 *
 * Meant to be drawn above the top bar, which is why it is not part of the list:
 * the fade the bar lays over scrolled content would otherwise wash it out.
 * [topPadding] is the list's own top content padding, from which the mark's
 * two resting places are measured.
 */
@Composable
fun HomeStickyLogo(
    listState: LazyListState,
    topPadding: Dp,
    refreshing: Boolean,
    pullFraction: () -> Float,
    modifier: Modifier = Modifier,
    /** Drawn beside the mark and moving with it, e.g. the app's name. */
    trailing: @Composable RowScope.() -> Unit = {},
) {
    // Where the mark sits at rest, and where it ends up pinned: centred on the
    // top bar, whose content is 52dp high with TopBarContentGap (12dp) below it.
    // It rides up there with the page but never shrinks.
    // One subtle bump for each square that fills as Home is pulled down, the
    // same count [SquareLogo] lights them by. Only on the way down: letting
    // go, or the pull springing back, stays quiet.
    val haptics = rememberHaptics()
    val latestRefreshing by rememberUpdatedState(refreshing)
    LaunchedEffect(Unit) {
        var filled = 0
        snapshotFlow { (pullFraction().coerceIn(0f, 1f) * LOGO_SQUARES).toInt() }
            .collect { now ->
                if (now > filled && !latestRefreshing) haptics.play(Haptic.Bump)
                filled = now
            }
    }
    val restTop = topPadding - 20.dp
    val pinnedTop = topPadding - 12.dp - 26.dp - HOME_LOGO_LARGE / 2
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            // Clear of the account button on the right.
            .padding(start = PAGE_GUTTER, end = 84.dp)
            .height(HOME_LOGO_LARGE)
            .graphicsLayer {
                val travel = (restTop - pinnedTop).toPx()
                // Measured from the last dial page showing, so the mark stays
                // put while the dials turn (both rest in the same place under
                // it) and only rides up once the list goes on past them.
                val rest = HOME_LOGO_RESERVE.toPx()
                val dial = listState.layoutInfo.visibleItemsInfo.lastOrNull {
                    it.index <= 2 && (it.key == ARTIST_DIAL_PAGE_KEY || it.key in SPEED_DIAL_PAGE_KEYS)
                }
                val scrolled = when {
                    dial != null -> rest - dial.offset
                    listState.firstVisibleItemIndex == 0 -> listState.firstVisibleItemScrollOffset.toFloat()
                    else -> travel
                }
                translationY = restTop.toPx() - scrolled.coerceIn(0f, travel)
            },
    ) {
        SquareLogo(
            // Wears the playing song's colour; its own while nothing plays.
            color = LocalNowPlayingTint.current ?: MaterialTheme.colorScheme.onBackground,
            refreshing = refreshing,
            pullFraction = pullFraction,
            modifier = Modifier.fillMaxHeight(),
        )
        trailing()
    }
}
