package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.SpeedDialSkeletonPage

/** The title the Mixes Dial's tiles are drawn under, and a tile opened from it is remembered by. */
internal const val MIXES_DIAL_SHELF_TITLE = "Mixes dial"

private const val MIXES_PAGE = 0
private const val SPEED_DIAL_PAGE = 1

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
    HorizontalPager(
        state = state,
        verticalAlignment = Alignment.Top,
        key = { it },
        modifier = Modifier.fillMaxWidth(),
    ) { page ->
        if (page == SPEED_DIAL_PAGE) {
            speedDial()
        } else when {
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
