package com.music.bitchord.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.SpeedDialSkeletonPage
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/** The title the Mixes Dial's tiles are drawn under, and a tile opened from it is remembered by. */
internal const val MIXES_DIAL_SHELF_TITLE = "Mixes dial"

private const val MIXES_PAGE = 0
private const val SPEED_DIAL_PAGE = 1

/** The slowest flick that turns a page, as a pager's own swipes have it. */
private val MIN_FLING_VELOCITY = 400.dp

/** How far off a page counts as between two, as a share of a page. */
private const val SETTLED_FRACTION = 0.001f

/**
 * The page a swipe let go at [position] (pages, with the part of the next one
 * dragged in) lands on. [velocity] is the finger's along the pager, negative
 * towards the next page. A flick faster than [minFling] turns the page its
 * way however little was dragged, as a pager's own pages do; a slower one
 * settles on the nearer page.
 */
fun dialSwipeTarget(position: Float, velocity: Float, minFling: Float, pageCount: Int): Int {
    val page = when {
        velocity <= -minFling -> ceil(position)
        velocity >= minFling -> floor(position)
        else -> round(position)
    }
    return page.toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
}

/**
 * Ends a swipe that a dial inside carried over to this pager.
 *
 * The dials are pagers themselves, so every swipe starts in one of them. Past
 * its last page the drag moves this pager, but the flick that ends it was
 * spent on the dial inside and reached this one as no speed at all: the page
 * only turned when dragged more than halfway across. This takes the flick
 * before the dial does, while this pager is between pages, and turns the page
 * with it, so the mixes swipe in as easily as the dial's own pages.
 */
private class DialFlingHandoff(
    private val state: PagerState,
    private val minFling: Float,
    private val reversed: Boolean,
) : NestedScrollConnection {
    override suspend fun onPreFling(available: Velocity): Velocity {
        val offset = state.currentPageOffsetFraction
        if (abs(offset) < SETTLED_FRACTION) return Velocity.Zero
        val target = dialSwipeTarget(
            position = state.currentPage + offset,
            velocity = if (reversed) -available.x else available.x,
            minFling = minFling,
            pageCount = state.pageCount,
        )
        state.animateScrollToPage(target, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        return available
    }
}

/**
 * The speed dial with the Mixes Dial to its left: swiping right from the
 * speed dial's first page turns to it, and swiping left from it turns back.
 * The speed dial's own pages swipe inside it as before; only once they run
 * out at the first page does the swipe carry over to the mixes.
 *
 * [mixes] is null while the first deal is on its way, which shows placeholder
 * tiles, and empty when there were none to deal, which shows [emptyLabel].
 */
@Composable
internal fun MixesDialPager(
    mixes: List<ShelfItem>?,
    rows: Int,
    maxShelfHeight: Dp?,
    pageHeight: Dp?,
    emptyLabel: String,
    mixesPage: @Composable (HomeShelf) -> Unit,
    speedDial: @Composable () -> Unit,
) {
    // Back from a page a mix opened: on the mixes, for the page to close back into its tile.
    val state = rememberPagerState(
        initialPage = if (DialLaunch.origin?.shelfTitle == MIXES_DIAL_SHELF_TITLE) MIXES_PAGE else SPEED_DIAL_PAGE,
    ) { 2 }
    // Home tapped again on Home: back to the speed dial.
    val resetRequests = HomeReset.requests
    var resetsSeen by remember { mutableIntStateOf(resetRequests) }
    LaunchedEffect(resetRequests) {
        if (resetRequests == resetsSeen) return@LaunchedEffect
        resetsSeen = resetRequests
        if (state.currentPage != SPEED_DIAL_PAGE) state.animateScrollToPage(SPEED_DIAL_PAGE)
    }
    val shelf = remember(mixes) { mixes?.let { HomeShelf(MIXES_DIAL_SHELF_TITLE, it) } }
    val minFling = with(LocalDensity.current) { MIN_FLING_VELOCITY.toPx() }
    val reversed = LocalLayoutDirection.current == LayoutDirection.Rtl
    val handoff = remember(state, minFling, reversed) { DialFlingHandoff(state, minFling, reversed) }
    HorizontalPager(
        state = state,
        verticalAlignment = Alignment.Top,
        key = { it },
        // The page out of view is kept made up beside the one in view, so the
        // mixes' covers and tags are loaded before the swipe that shows them
        // rather than coming in one by one as the page slides over.
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxWidth(),
    ) { page ->
        // Between the dials and this pager: see [DialFlingHandoff].
        Box(Modifier.fillMaxWidth().nestedScroll(handoff)) {
            MixesDialPage(page, shelf, rows, maxShelfHeight, pageHeight, emptyLabel, mixesPage, speedDial)
        }
    }
}

@Composable
private fun MixesDialPage(
    page: Int,
    shelf: HomeShelf?,
    rows: Int,
    maxShelfHeight: Dp?,
    pageHeight: Dp?,
    emptyLabel: String,
    mixesPage: @Composable (HomeShelf) -> Unit,
    speedDial: @Composable () -> Unit,
) {
    if (page == SPEED_DIAL_PAGE) {
        speedDial()
    } else {
        when {
            shelf == null -> SpeedDialSkeletonPage(rows, maxShelfHeight, pageHeight)
            shelf.items.isEmpty() -> Box(
                modifier = (if (pageHeight != null) Modifier.height(pageHeight) else Modifier.height(240.dp))
                    .fillMaxWidth()
                    .padding(horizontal = PAGE_GUTTER * 2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emptyLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> mixesPage(shelf)
        }
    }
}
