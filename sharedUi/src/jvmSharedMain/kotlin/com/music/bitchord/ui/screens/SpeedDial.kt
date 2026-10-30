package com.music.bitchord.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.launch
import kotlin.math.sin
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import coil3.request.ImageRequest
import coil3.compose.LocalPlatformContext
import coil3.PlatformContext
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.SideEffect
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.SearchPlayingBars
import com.music.bitchord.ui.components.contextClick
import com.music.bitchord.ui.components.longPressMenuClickable
import com.music.bitchord.ui.components.dialTileBorder
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.floor
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

/** The title a shelf is drawn as the speed dial under. */
internal const val SPEED_DIAL_SHELF_TITLE = "Speed dial"

private const val SPEED_DIAL_COLUMNS = 3
private val TILE_GAP = 8.dp

/** A little air above the grid; the shelf has no heading of its own any more. */
internal val SPEED_DIAL_HEADER_HEIGHT = 24.dp

/** The page dots' row. Reserved even with a single page, for the same reason. */
private val SPEED_DIAL_DOTS_HEIGHT = 16.dp

/** How close to a side the finger has to be, mid-drag, to turn the page. */
private val EDGE_PAGE_ZONE = 36.dp

/** [inset] is the space either side of a grid that had to shrink to fit. */
internal data class SpeedDialMetrics(val tileWidth: Dp, val tileHeight: Dp, val inset: Dp)

/**
 * The tiles' size for a grid [contentWidth] wide with [rows] rows.
 *
 * Tiles are always square. When [maxShelfHeight] — everything the shelf may
 * take, down to its page dots — is too short for the full-width squares, they
 * shrink (never below 80% of full size) and the grid centres, so the whole
 * grid sits above the miniplayer instead of running under it.
 */
internal fun speedDialMetrics(contentWidth: Dp, rows: Int, maxShelfHeight: Dp?): SpeedDialMetrics {
    val full = (contentWidth - TILE_GAP * (SPEED_DIAL_COLUMNS - 1)) / SPEED_DIAL_COLUMNS
    var side = full
    if (maxShelfHeight != null) {
        val fit = (maxShelfHeight - SPEED_DIAL_HEADER_HEIGHT - SPEED_DIAL_DOTS_HEIGHT - TILE_GAP * (rows - 1)) / rows
        side = minOf(full, maxOf(fit, full * 0.8f))
    }
    val inset = (contentWidth - (side * SPEED_DIAL_COLUMNS + TILE_GAP * (SPEED_DIAL_COLUMNS - 1))) / 2
    return SpeedDialMetrics(side, side, inset)
}

private fun ShelfItem.dialId(): String = videoId ?: browseId ?: title

/**
 * "I'm Feeling Lucky" on the speed dial: a die drawn as a tile, last on the
 * first page. [onRoll] is called on every tap, as the die starts to roll.
 */
class SpeedDialLucky(val label: String, val onRoll: () -> Unit)

/** The die's place in the dial's order: never a real item, only matched on. */
private val LUCKY_TILE = ShelfItem(
    title = "",
    subtitle = "",
    thumbnailUrl = null,
    videoId = null,
    browseId = "bitchord:feeling-lucky",
)

/** The history tile's place, last on the last page: never a real item, only matched on. */
private val HISTORY_TILE = ShelfItem(
    title = "",
    subtitle = "",
    thumbnailUrl = null,
    videoId = null,
    browseId = "bitchord:dial-history",
)

/** What a dial slot holds, when it isn't a tile's index in the order. */
private const val SLOT_BLANK = -1
private const val SLOT_LUCKY = -2
private const val SLOT_HISTORY = -3

/**
 * Where everything on a dial goes, slot by slot: each tile's index in the
 * order, the die, the history tile, or nothing.
 *
 * The dial always ends on a full page. Tiles from the end of the order (the
 * least wanted — pins come first) are left off to make it so, or, where that
 * would cut a pin or leave nothing, the last page is filled out with blank
 * slots instead. The history tile is the last slot of all; the die is the last
 * on the first page (the one before it, when the dial is a single page).
 */
private fun dialSlots(count: Int, pinned: Int, perPage: Int, lucky: Boolean, history: Boolean): IntArray {
    val extras = (if (lucky) 1 else 0) + (if (history) 1 else 0)
    val needed = count + extras
    var pages = needed / perPage
    if (pages == 0 || pages * perPage - extras < minOf(pinned, count)) {
        pages = (needed + perPage - 1) / perPage
    }
    pages = pages.coerceAtLeast(1)
    val total = pages * perPage
    val slots = IntArray(total) { SLOT_BLANK }
    if (history) slots[total - 1] = SLOT_HISTORY
    if (lucky) slots[if (history && pages == 1) perPage - 2 else perPage - 1] = SLOT_LUCKY
    val shown = minOf(count, total - extras)
    var next = 0
    for (i in 0 until total) {
        if (slots[i] == SLOT_BLANK && next < shown) slots[i] = next++
    }
    return slots
}

/**
 * YouTube Music's speed dial: a grid of tiles three across and [rows] down,
 * swiped sideways through further pages.
 *
 * Held tiles that are pinned can be dragged, as on a phone's home screen: hold,
 * then move, and the others make way. Holding at either side turns the page.
 * Lifting without having moved opens the tile's menu instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SpeedDialShelf(
    shelf: HomeShelf,
    pinnedIds: Set<String>,
    rows: Int,
    /** What the shelf may take of the screen, heading to page dots; null for no limit. */
    maxShelfHeight: Dp?,
    /**
     * When the dial is Home's whole first screen: its height, with a chevron
     * under the page dots for what is further down.
     */
    pageHeight: Dp? = null,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
    /** The pinned tiles in their new order, after a drag. */
    onReorder: ((List<ShelfItem>) -> Unit)?,
    currentSong: Song?,
    isPlaying: Boolean,
    /** The die, on the speed dial when it is switched on. */
    lucky: SpeedDialLucky? = null,
    /** Whether tiles show their titles, unless they are in [labelOverrides]. */
    showLabels: Boolean = true,
    /** Tiles (by video or browse id) that go against [showLabels]. */
    labelOverrides: Set<String> = emptySet(),
    /** Whether pinned tiles carry the pin badge. */
    showPinIcon: Boolean = true,
    /** The history tile, last on the last page: opens what was played lately. Null for none. */
    onHistory: (() -> Unit)? = null,
    historyLabel: String = "",
) {
    if (shelf.items.isEmpty()) return
    val perPage = SPEED_DIAL_COLUMNS * rows
    // The order on screen: the shelf's, until a drag changes it.
    var order by remember(shelf.items) { mutableStateOf(shelf.items) }
    val pinnedCount = remember(order, pinnedIds) { order.takeWhile { it.dialId() in pinnedIds }.size }
    // What is drawn, slot by slot — see [dialSlots]. A drag only reorders the
    // pins among themselves, so the slots stay put through one.
    val slots = remember(order.size, pinnedCount, perPage, lucky != null, onHistory != null) {
        dialSlots(order.size, pinnedCount, perPage, lucky != null, onHistory != null)
    }
    val display: List<ShelfItem?> = remember(order, slots) {
        slots.map { slot ->
            when {
                slot >= 0 -> order[slot]
                slot == SLOT_LUCKY -> LUCKY_TILE
                slot == SLOT_HISTORY -> HISTORY_TILE
                else -> null
            }
        }
    }
    val latestSlots by rememberUpdatedState(slots)
    val pageCount = (slots.size / perPage).coerceAtLeast(1)
    val latestPageCount by rememberUpdatedState(pageCount)
    // Back from a page a tile here opened, on the page that tile is on, for
    // the page to close back into it.
    val pagerState = rememberPagerState(initialPage = DialLaunch.pageOf(shelf.title)) { latestPageCount }
    val latestPinned by rememberUpdatedState(pinnedCount)
    val latestOrder by rememberUpdatedState(order)
    val latestReorder by rememberUpdatedState(onReorder)
    val latestMenu by rememberUpdatedState(onItemLongPress)
    val latestItems by rememberUpdatedState(shelf.items)

    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var edge by remember { mutableIntStateOf(0) }

    // While a tile is held to be moved, the pinned ones shrink a little and
    // jiggle, as a phone's home screen does, and the rest (which can't move)
    // dim. The clock is read only when drawing, so the jiggle recomposes nothing.
    val wiggling = dragId != null
    val wiggleLevel by animateFloatAsState(
        targetValue = if (wiggling) 1f else 0f,
        animationSpec = tween(WIGGLE_FADE_MS),
        label = "dialWiggle",
    )
    var wiggleClock by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(wiggling) {
        if (!wiggling) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { wiggleClock = (it - start) / 1000f }
    }

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    // Tiles already shown, so that one arriving later (a fresh dial replacing
    // the saved one) fades in rather than popping, while the dial's first
    // appearance and every page swiped to stay instant.
    val shownIds = remember { HashSet<String>() }
    SideEffect { display.forEach { item -> item?.let { shownIds += it.dialId() } } }
    // The first dial of a launch comes in tile by tile; see [SpeedDialIntro].
    val intro = remember { !SpeedDialIntro.played }
    SideEffect { SpeedDialIntro.played = true }

    // Turn the page while the finger rests at a side.
    LaunchedEffect(edge, dragId) {
        if (dragId == null || edge == 0) return@LaunchedEffect
        while (true) {
            delay(600)
            val target = (pagerState.currentPage + edge).coerceIn(0, latestPageCount - 1)
            if (target != pagerState.currentPage) pagerState.animateScrollToPage(target)
            delay(400)
        }
    }

    Column(if (pageHeight != null) Modifier.height(pageHeight) else Modifier.padding(bottom = 66.dp)) {
        Spacer(Modifier.height(SPEED_DIAL_HEADER_HEIGHT))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val metrics = speedDialMetrics(maxWidth - PAGE_GUTTER * 2, rows, maxShelfHeight)
            val tileW = metrics.tileWidth
            val tileH = metrics.tileHeight
            val gridHeight = tileH * rows + TILE_GAP * (rows - 1)
            val tileWPx = with(density) { tileW.toPx() }
            val tileHPx = with(density) { tileH.toPx() }
            val gapPx = with(density) { TILE_GAP.toPx() }
            val gutterPx = with(density) { PAGE_GUTTER.toPx() }
            val insetPx = with(density) { metrics.inset.toPx() }
            val widthPx = with(density) { maxWidth.toPx() }
            val edgePx = with(density) { EDGE_PAGE_ZONE.toPx() }

            fun cellOrigin(cell: Int) = Offset(
                insetPx + (cell % SPEED_DIAL_COLUMNS) * (tileWPx + gapPx),
                (cell / SPEED_DIAL_COLUMNS) * (tileHPx + gapPx),
            )

            /** The index in the order of the tile drawn at [shown], or null for the die. */
            fun toOrder(shown: Int): Int? = latestSlots.getOrNull(shown)?.takeIf { it >= 0 }

            /** Where in the order a tile dropped on slot [shown] goes: the tile there, or after those before it. */
            fun orderAt(shown: Int): Int =
                toOrder(shown) ?: (0 until shown.coerceAtMost(latestSlots.size)).count { latestSlots[it] >= 0 }

            /** The index among the tiles drawn under [pos], or null in a gap or past the last tile. */
            fun hit(pos: Offset, page: Int): Int? {
                val x = pos.x - gutterPx - insetPx
                val col = floor(x / (tileWPx + gapPx)).toInt()
                val row = floor(pos.y / (tileHPx + gapPx)).toInt()
                if (col !in 0 until SPEED_DIAL_COLUMNS || row !in 0 until rows) return null
                if (x - col * (tileWPx + gapPx) > tileWPx || pos.y - row * (tileHPx + gapPx) > tileHPx) return null
                return (page * perPage + row * SPEED_DIAL_COLUMNS + col)
                    .takeIf { it < latestSlots.size && latestSlots[it] != SLOT_BLANK }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(rows, tileWPx, tileHPx, insetPx) {
                        awaitEachGesture {
                            // Watched from the first pass, ahead of the tiles and the
                            // pager, so that once a hold has landed the drag is ours
                            // and neither takes it for a tap or a swipe.
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                var done = false
                                while (!done) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    done = change == null || change.changedToUp() ||
                                        event.changes.size > 1 ||
                                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                }
                                true
                            }
                            if (ended != null) return@awaitEachGesture // not a hold

                            val page = pagerState.currentPage
                            val shown = hit(down.position, page) ?: return@awaitEachGesture
                            // The die has no menu and no place to be dragged to.
                            val index = toOrder(shown) ?: return@awaitEachGesture
                            val item = latestOrder[index]
                            // Tiles that are not pinned have no place to be dragged
                            // to; their own hold opens the menu as it always did.
                            if (latestReorder == null || index >= latestPinned) return@awaitEachGesture

                            val id = item.dialId()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            dragId = id
                            dragPos = down.position
                            grab = down.position - cellOrigin(shown - page * perPage) - Offset(gutterPx, 0f)
                            var moved = false
                            var finished = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    // Read before consuming: a consumed change no
                                    // longer reports itself as lifted, so the drop
                                    // went unseen and the tile hung in the air until
                                    // the next touch ended the gesture.
                                    val lifted = !change.pressed
                                    change.consume()
                                    if (lifted) {
                                        finished = true
                                        if (moved) {
                                            latestReorder?.invoke(latestOrder.take(latestPinned))
                                        } else {
                                            latestMenu?.invoke(item)
                                        }
                                        break
                                    }
                                    dragPos = change.position
                                    if (!moved &&
                                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                    ) {
                                        moved = true
                                    }
                                    if (moved && !pagerState.isScrollInProgress) {
                                        val x = dragPos.x - gutterPx - insetPx
                                        val col = floor(x / (tileWPx + gapPx)).toInt().coerceIn(0, SPEED_DIAL_COLUMNS - 1)
                                        val row = floor(dragPos.y / (tileHPx + gapPx)).toInt().coerceIn(0, rows - 1)
                                        val cell = pagerState.currentPage * perPage + row * SPEED_DIAL_COLUMNS + col
                                        val target = orderAt(cell)
                                            .coerceIn(0, (latestPinned - 1).coerceAtLeast(0))
                                        val current = latestOrder.indexOfFirst { it.dialId() == id }
                                        if (current >= 0 && target != current) {
                                            order = latestOrder.toMutableList().apply { add(target, removeAt(current)) }
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                    edge = when {
                                        !moved -> 0
                                        dragPos.x < edgePx -> -1
                                        dragPos.x > widthPx - edgePx -> 1
                                        else -> 0
                                    }
                                }
                            } finally {
                                if (!finished) order = latestItems
                                dragId = null
                                edge = 0
                            }
                        }
                    },
            ) {
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 12.dp,
                    contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                    verticalAlignment = Alignment.Top,
                    userScrollEnabled = dragId == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    Box(Modifier.fillMaxWidth().height(gridHeight)) {
                        display.drop(page * perPage).take(perPage).forEachIndexed { cell, slotItem ->
                            val item = slotItem ?: return@forEachIndexed
                            key(item.dialId()) {
                                val target = cellOrigin(cell).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) }
                                val offset by animateIntOffsetAsState(
                                    targetValue = target,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "speedDialTile",
                                )
                                val id = item.dialId()
                                val entering = intro && page == 0
                                val appear = remember(id) {
                                    Animatable(if (entering || (shownIds.isNotEmpty() && id !in shownIds)) 0f else 1f)
                                }
                                LaunchedEffect(appear) {
                                    if (appear.value >= 1f) return@LaunchedEffect
                                    if (entering) {
                                        // Along each row, then the next: A1, B1, C1,
                                        // A2 and on. Not before the app is actually
                                        // on screen. A bouncy spring, so each tile
                                        // grows a little past full size and settles.
                                        snapshotFlow { SpeedDialIntro.canStart }.first { it }
                                        delay(cell * INTRO_STEP_MS)
                                        appear.animateTo(
                                            1f,
                                            spring(dampingRatio = INTRO_BOUNCE, stiffness = Spring.StiffnessMediumLow),
                                        )
                                    } else {
                                        appear.animateTo(1f, tween(TILE_FADE_MS))
                                    }
                                }
                                val pinnedTile = id in pinnedIds
                                // Each its own beat, so the jiggle isn't in step.
                                val phase = (id.hashCode() and 0xFFFF) / 0xFFFF.toFloat() * WIGGLE_TAU
                                val tileModifier = Modifier
                                    .offset { offset }
                                    .size(tileW, tileH)
                                    .graphicsLayer {
                                        val shown = appear.value
                                        var scale = if (entering) {
                                            // Grows from smaller, overshooting with
                                            // the spring; opaque well before it lands.
                                            INTRO_START_SCALE + (1f - INTRO_START_SCALE) * shown
                                        } else {
                                            1f
                                        }
                                        alpha = when {
                                            dragId == id -> 0f
                                            // Out as its page, as an open app's icon is.
                                            DialLaunch.isOut(id) -> 0f
                                            entering -> (shown * 2.5f).coerceIn(0f, 1f)
                                            else -> shown.coerceIn(0f, 1f)
                                        }
                                        val w = wiggleLevel
                                        if (w > 0f && pinnedTile) {
                                            scale *= 1f - WIGGLE_SHRINK * w
                                            rotationZ = sin(wiggleClock * WIGGLE_SPEED + phase) * WIGGLE_DEGREES * w
                                        } else {
                                            rotationZ = 0f
                                        }
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                val dim = { if (pinnedTile) 0f else WIGGLE_DIM * wiggleLevel }
                                if (item === LUCKY_TILE && lucky != null) {
                                    LuckyDiceTile(
                                        label = lucky.label,
                                        onRoll = lucky.onRoll,
                                        dim = dim,
                                        modifier = tileModifier,
                                    )
                                } else if (item === HISTORY_TILE && onHistory != null) {
                                    HistoryTile(
                                        label = historyLabel,
                                        showLabel = showLabels,
                                        onClick = onHistory,
                                        dim = dim,
                                        modifier = tileModifier,
                                    )
                                } else {
                                    SpeedDialTile(
                                        item = item,
                                        pinned = pinnedTile && showPinIcon,
                                        showLabel = showLabels != (id in labelOverrides),
                                        current = currentSong != null && item.videoId == currentSong.videoId,
                                        isPlaying = isPlaying,
                                        onClick = { bounds, pressedScale, snapshot ->
                                            val opens = item.browseId
                                            // An album, artist or playlist grows out of its tile.
                                            if (item.videoId == null && opens != null && bounds != null) {
                                                DialLaunch.origin = DialOrigin(
                                                    key = opens,
                                                    bounds = bounds,
                                                    pressedScale = pressedScale,
                                                    thumbnailUrl = item.thumbnailUrl,
                                                    shelfTitle = shelf.title,
                                                    page = page,
                                                    snapshot = snapshot,
                                                )
                                            }
                                            onItemClick(item)
                                        },
                                        // A pinned tile's hold belongs to the drag above.
                                        onLongPress = if (onReorder != null && pinnedTile) {
                                            null
                                        } else {
                                            onItemLongPress?.let { { it(item) } }
                                        },
                                        dim = dim,
                                        modifier = tileModifier,
                                    )
                                }
                            }
                        }
                    }
                }

                // The tile in the finger's hand, lifted out of the grid.
                val liftedId = dragId
                if (liftedId != null) {
                    order.firstOrNull { it.dialId() == liftedId }?.let { item ->
                        SpeedDialTile(
                            item = item,
                            pinned = showPinIcon,
                            showLabel = showLabels != (liftedId in labelOverrides),
                            current = false,
                            isPlaying = false,
                            onClick = { _, _, _ -> },
                            onLongPress = null,
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (dragPos.x - grab.x).roundToInt(),
                                        (dragPos.y - grab.y).roundToInt(),
                                    )
                                }
                                .size(tileW, tileH)
                                .graphicsLayer {
                                    scaleX = 1.08f
                                    scaleY = 1.08f
                                }
                                .shadow(14.dp, RoundedCornerShape(10.dp)),
                        )
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(SPEED_DIAL_DOTS_HEIGHT)
                .padding(top = 10.dp),
        ) {
            if (pageCount > 1) {
                repeat(pageCount) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onBackground.copy(alpha = if (selected) 0.9f else 0.3f),
                            ),
                    )
                }
            }
        }
        if (pageHeight != null) SpeedDialMoreHint()
    }
}

/**
 * A dial tile's artwork request. Fixed in size, so the copy fetched ahead of
 * time while the app starts is the very one the tile then asks for, and comes
 * straight out of the memory cache.
 */
fun speedDialArtRequest(context: PlatformContext, url: String?): ImageRequest =
    ImageRequest.Builder(context)
        .data(url?.artworkAt(CARD_ART_PX))
        .size(CARD_ART_PX)
        .build()

private const val TILE_FADE_MS = 300

/** How long new art takes to sweep across a tile, top left to bottom right. */
private const val ART_WIPE_MS = 1400

/** The soft edge of that sweep, as a share of the tile's diagonal. */
private const val ART_WIPE_FEATHER = 0.25f

/**
 * A tile's art. When it changes (a pinned mix YouTube has rebuilt), the old
 * art stays up while the new loads, then the new sweeps in over it on a soft
 * diagonal edge rather than the tile blanking to grey and popping.
 */
@Composable
private fun SpeedDialArt(url: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    var shownUrl by remember { mutableStateOf(url) }
    // False from the moment the new art takes the bottom layer until that
    // layer has drawn it, so the swap underneath never shows a grey frame.
    var settled by remember { mutableStateOf(true) }
    Box(modifier) {
        // Remembered: a request has no equals, so a new one each recomposition
        // would be a new load each time.
        val shown = remember(shownUrl, context) { speedDialArtRequest(context, shownUrl) }
        AsyncImage(
            model = shown,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            onSuccess = { settled = true },
            onError = { settled = true },
            modifier = Modifier.fillMaxSize(),
        )
        if (url != shownUrl || !settled) {
            val incoming = remember(url, context) { speedDialArtRequest(context, url) }
            var loaded by remember(url) { mutableStateOf(false) }
            val wipe = remember(url) { Animatable(0f) }
            LaunchedEffect(url, loaded) {
                if (!loaded) return@LaunchedEffect
                wipe.animateTo(1f, tween(ART_WIPE_MS, easing = FastOutSlowInEasing))
                settled = false
                shownUrl = url
            }
            AsyncImage(
                model = incoming,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // Shown up to the edge, gone past it: the edge runs
                        // along the diagonal from before the corner to past
                        // the far one, so it starts and ends fully off.
                        val edge = wipe.value * (1f + ART_WIPE_FEATHER) - ART_WIPE_FEATHER
                        val w = size.width
                        val h = size.height
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                start = Offset(w * edge, h * edge),
                                end = Offset(w * (edge + ART_WIPE_FEATHER), h * (edge + ART_WIPE_FEATHER)),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        }
    }
}

/** The launch entrance: the gap between one tile starting and the next. */
private const val INTRO_STEP_MS = 45L

/** How small a tile starts, and how much its spring overshoots (lower bounces more). */
private const val INTRO_START_SCALE = 0.35f
private const val INTRO_BOUNCE = 0.5f

/**
 * The dial's entrance when the app opens: its first page pops in one tile at
 * a time, row by row. Once per launch; the app resets [played] when the
 * activity starts fresh, and holds [canStart] false until its first frame is
 * actually on screen (behind the splash, the entrance would play unseen).
 */
object SpeedDialIntro {
    var played: Boolean = false
    var canStart: Boolean by mutableStateOf(true)
}

/** The faint chevron under the dial's page dots: there is more below. */
@Composable
internal fun SpeedDialMoreHint() {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SpeedDialTile(
    item: ShelfItem,
    pinned: Boolean,
    showLabel: Boolean = true,
    current: Boolean,
    isPlaying: Boolean,
    /**
     * Given where the tile is on screen (full size), how far in it was pressed
     * when let go, and a picture of it as drawn unpressed.
     */
    onClick: (bounds: Rect?, pressedScale: Float, snapshot: ImageBitmap?) -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** How far it is shaded over, read when drawing: see the dial's jiggle. */
    dim: () -> Float = { 0f },
) {
    val shape = RoundedCornerShape(10.dp)
    val press = rememberTilePress()
    val spot = remember { TileSpot() }
    // Every frame of the tile, kept to hand: a tap that opens a page takes a
    // picture of it from here, for the page to grow out of and land back as.
    val drawn = rememberGraphicsLayer()
    Box(
        modifier = modifier
            .onPlaced { spot.coordinates = it }
            .tilePress(press)
            .drawWithContent {
                drawn.record { this@drawWithContent.drawContent() }
                drawLayer(drawn)
            }
            .clip(shape)
            .dialTileBorder(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .longPressMenuClickable(
                onClick = {
                    press.tap {
                        val snapshot = runCatching { drawn.toImageBitmap() }.getOrNull()
                        onClick(spot.coordinates?.takeIf { it.isAttached }?.boundsInRoot(), press.scale, snapshot)
                    }
                },
                onLongClick = onLongPress,
                interactionSource = press.interactions,
            )
            .contextClick(onLongPress),
    ) {
        SpeedDialArt(item.thumbnailUrl, item.title, Modifier.fillMaxSize())
        if (showLabel) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge.let { it.copy(fontSize = (it.fontSize.value - 2f).sp) },
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        if (pinned) {
            Icon(
                imageVector = BitChordIcons.Pin,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(14.dp),
            )
        }
        if (current && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        DimOverlay(dim)
    }
}

/** The tile last on a dial's last page: a clock, opening what was played lately. */
@Composable
private fun HistoryTile(
    label: String,
    showLabel: Boolean,
    onClick: () -> Unit,
    dim: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val press = rememberTilePress()
    Box(
        modifier
            .tilePress(press)
            .clip(shape)
            .dialTileBorder(shape)
            .background(DICE_GROUND)
            .clickable(
                interactionSource = press.interactions,
                indication = null,
                onClickLabel = label,
            ) { press.tap { onClick() } },
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.Center).fillMaxSize(0.36f),
        )
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.let { it.copy(fontSize = (it.fontSize.value - 2f).sp) },
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        DimOverlay(dim)
    }
}

/** Where a tile was last laid out, kept off the snapshot: only read when it is tapped. */
private class TileSpot {
    var coordinates: LayoutCoordinates? = null
}

/**
 * A tile's push, like a button's: it sinks while the finger is on it, and a
 * tap presses it all the way in before its action runs, then lets it spring
 * back out. Held for a menu or a drag, it comes back up when let go.
 */
private class TilePress(private val scope: CoroutineScope) {
    val interactions = MutableInteractionSource()
    val depth = Animatable(0f)

    /** Between a tap and its action: the press itself is the tap's, not the finger's. */
    var tapping = false

    /**
     * The tile's scale at the current depth, read when drawing. It only
     * sinks: a dark film over it as well flashed on every quick tap.
     */
    val scale: Float get() = 1f - PRESS_SHRINK * depth.value

    fun tap(action: suspend () -> Unit) {
        tapping = true
        scope.launch {
            try {
                depth.animateTo(1f, tween(PRESS_IN_MS))
            } catch (interrupted: CancellationException) {
                // Only another push took the animation over; the tap still counts.
                ensureActive()
            }
            tapping = false
            action()
            depth.animateTo(0f, spring(dampingRatio = PRESS_BOUNCE, stiffness = Spring.StiffnessMediumLow))
        }
    }
}

@Composable
private fun rememberTilePress(): TilePress {
    val scope = rememberCoroutineScope()
    val press = remember(scope) { TilePress(scope) }
    LaunchedEffect(press) {
        press.interactions.interactions.collect { interaction ->
            if (press.tapping) return@collect
            when (interaction) {
                is PressInteraction.Press -> launch { press.depth.animateTo(1f, tween(PRESS_IN_MS)) }
                is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                    press.depth.animateTo(0f, spring(dampingRatio = PRESS_BOUNCE, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
    }
    return press
}

/** Shrinks the tile as far as it is pressed in. */
private fun Modifier.tilePress(press: TilePress): Modifier = graphicsLayer {
    val scale = press.scale
    scaleX = scale
    scaleY = scale
}

/** How far a pressed tile sinks, how quickly, and how much it bounces back out. */
private const val PRESS_SHRINK = 0.08f
private const val PRESS_IN_MS = 110
private const val PRESS_BOUNCE = 0.45f

/** A black film over a tile, as deep as [dim] says when it is drawn. */
@Composable
private fun DimOverlay(dim: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val d = dim()
                if (d > 0f) drawRect(Color.Black.copy(alpha = d))
            },
    )
}

/**
 * The "I'm Feeling Lucky" tile: a die of nine squares, like the app's own mark,
 * resting on three, on a dark ground with soft washes of colour drifting in
 * from its edges. Tapped, its lights chase round the edge like a spinner, then
 * it lands on a face from one to six. [label] is only read out, not drawn.
 */
@Composable
private fun LuckyDiceTile(
    label: String,
    onRoll: () -> Unit,
    dim: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var face by remember { mutableIntStateOf(3) }
    // The spinner's head, in squares round the edge, and how far the die has
    // gone from the spinner back to a face (1 = all face).
    val spin = remember { Animatable(0f) }
    val settle = remember { Animatable(1f) }
    var rolling by remember { mutableStateOf(false) }
    val press = rememberTilePress()
    Box(
        modifier
            .tilePress(press)
            .clip(shape)
            .dialTileBorder(shape)
            .background(DICE_GROUND)
            .clickable(interactionSource = press.interactions, indication = null, onClickLabel = label) {
                // Pressed in and let out as it starts to roll; the roll itself doesn't wait.
                press.tap {}
                if (rolling) return@clickable
                rolling = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onRoll()
                scope.launch {
                    try {
                        spin.snapTo(0f)
                        settle.animateTo(0f, tween(DICE_FADE_MS))
                        spin.animateTo(DICE_ROLL_STEPS, tween(DICE_ROLL_MS, easing = FastOutSlowInEasing))
                        face = (1..6).random()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        settle.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
                    } finally {
                        rolling = false
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // The washes: crimson in from the upper left, violet from the
            // lower right, a little cyan at the upper right.
            DICE_WASHES.forEach { wash ->
                val center = Offset(w * wash.x, h * wash.y)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(wash.color.copy(alpha = wash.alpha), wash.color.copy(alpha = 0f)),
                        center = center,
                        radius = size.maxDimension * wash.radius,
                    ),
                )
            }
            // The grid as the reference draws it: three squares of 80 with
            // gaps of 30 on a 512 ground, dead centre.
            val unit = size.minDimension / 512f
            val cellSide = 80f * unit
            val gap = 30f * unit
            val gridSide = cellSide * 3 + gap * 2
            val left = (w - gridSide) / 2f
            val top = (h - gridSide) / 2f
            val head = spin.value % DICE_RING.size
            val toFace = settle.value
            val pips = DICE_FACES[face - 1]
            for (i in 0 until 9) {
                val ring = DICE_RING.indexOf(i)
                val spinner = if (ring < 0) {
                    0f
                } else {
                    val behind = ((head - ring) % DICE_RING.size + DICE_RING.size) % DICE_RING.size
                    (1f - behind / DICE_TAIL).coerceIn(0f, 1f)
                }
                val pip = if (i in pips) 1f else 0f
                val level = (spinner + (pip - spinner) * toFace).coerceIn(0f, 1f)
                // A lit square swells a touch with the landing's overshoot.
                val pop = if (pip > 0f && toFace > 1f) 1f + (toFace - 1f) * 0.6f else 1f
                val side = cellSide * pop
                val cx = left + (i % 3) * (cellSide + gap) + cellSide / 2f
                val cy = top + (i / 3) * (cellSide + gap) + cellSide / 2f
                if (level > 0f) {
                    // The soft glow round a lit square.
                    drawCircle(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.45f * level), Color.White.copy(alpha = 0f)),
                            center = Offset(cx, cy),
                            radius = side * 1.05f,
                        ),
                        radius = side * 1.05f,
                        center = Offset(cx, cy),
                    )
                }
                drawRoundRect(
                    color = lerp(DICE_UNLIT, Color.White, level),
                    topLeft = Offset(cx - side / 2f, cy - side / 2f),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(side * 0.08f),
                )
            }
        }
        DimOverlay(dim)
    }
}

/** A soft wash of colour on the die's ground: where (as fractions of the tile), how big, how strong. */
private class DiceWash(val color: Color, val x: Float, val y: Float, val radius: Float, val alpha: Float)

private val DICE_WASHES = listOf(
    DiceWash(Color(0xFFFA5A72), 0f, 0.15f, 0.62f, 0.26f),
    DiceWash(Color(0xFF8B5CF6), 1f, 0.85f, 0.68f, 0.26f),
    DiceWash(Color(0xFF38BDF8), 1f, 0.1f, 0.5f, 0.18f),
)
private val DICE_GROUND = Color(0xFF141414)
private val DICE_UNLIT = Color(0xFF262626)

/** The die's squares round its edge, clockwise from the top left (row by row, 0–8). */
private val DICE_RING = intArrayOf(0, 1, 2, 5, 8, 7, 6, 3)

/** Which squares each face from one to six lights. */
private val DICE_FACES = listOf(
    setOf(4),
    setOf(2, 6),
    setOf(0, 4, 8),
    setOf(0, 2, 6, 8),
    setOf(0, 2, 4, 6, 8),
    setOf(0, 2, 3, 5, 6, 8),
)

/** Twice round, in this long, with a tail this many squares long. */
private const val DICE_ROLL_STEPS = 16f
private const val DICE_ROLL_MS = 950
private const val DICE_TAIL = 3.2f
private const val DICE_FADE_MS = 140

/** The jiggle while a tile is moved: how far pinned tiles shrink, how far they rock and how fast. */
private const val WIGGLE_SHRINK = 0.08f
private const val WIGGLE_DEGREES = 1.8f
private const val WIGGLE_SPEED = 22f
private const val WIGGLE_TAU = 6.2831855f
/** How dark the tiles that can't be moved go while one is. */
private const val WIGGLE_DIM = 0.45f
private const val WIGGLE_FADE_MS = 220
