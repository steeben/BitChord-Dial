package com.music.bitchord.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings

/**
 * Home's list, with the rows next to the ones on screen made before the
 * scroll gets to them.
 *
 * A list makes a row on the frame that first shows it. Home opens on the
 * dial, which fills the screen, so the row under it (the artists' dial, a
 * page of tiles) was made on the first frame of the first swipe after every
 * launch: a stop of 45 to 60 ms in the logs, as the finger starts to move.
 * The rows after it each cost a frame of the scroll the same way.
 *
 * With a window ahead, the list makes the rows in it between frames, a part
 * at a time, from Home's first layout on and again as the scroll moves. Half
 * a screen is the row under the dial while Home is at its top, and the next
 * row or two in the feed.
 *
 * With a window behind, a row that has just been scrolled past is kept as it
 * is, not taken down, so the way back up (to the dial, from the artists'
 * dial) has nothing to make either. That is memory held for a row nobody is
 * looking at, so it goes by the same setting as Home kept under other pages.
 *
 * A row in either window is composed but has no place on the screen. What a
 * row does only for someone looking at it has to ask whether it has one: see
 * Top Picks' clips in [HomeScreen].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberHomeListState(): LazyListState {
    val keep by AppSettings.keepHome.collectAsStateWithLifecycle()
    // The state is made again if the window changes: the setting is changed
    // from Settings, with Home out of sight, and Home is then back at its top.
    return rememberLazyListState(cacheWindow = if (keep) AheadAndBehind else AheadOnly)
}

/** Of the list's height. */
private const val ROWS_AHEAD = 0.5f
private const val ROWS_BEHIND = 1f

@OptIn(ExperimentalFoundationApi::class)
private val AheadOnly = LazyLayoutCacheWindow(aheadFraction = ROWS_AHEAD)

@OptIn(ExperimentalFoundationApi::class)
private val AheadAndBehind = LazyLayoutCacheWindow(aheadFraction = ROWS_AHEAD, behindFraction = ROWS_BEHIND)
