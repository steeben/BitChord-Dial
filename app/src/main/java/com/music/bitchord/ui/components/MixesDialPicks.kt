package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import com.music.bitchord.data.mixes.MixesDial
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem

/**
 * The Mixes Dial's tiles for this round (app load or pull to refresh), dealt
 * once the round's feed is [ready] and kept until the next. Null until the
 * first deal is in. A new round keeps the last deal up until its own arrives,
 * so the page never blanks to placeholders mid-refresh. [hidden] (tiles taken
 * off the dials) is applied as it changes, without dealing again.
 *
 * Sleep mixes are on the page only at night ([MixesDial.isSleepTime]): the
 * clock is looked at once a minute, and the page is dealt again as the night
 * hours start and end, so they come in at 9:30pm and are gone after 4:00am.
 */
@Composable
fun rememberMixesDial(
    on: Boolean,
    round: Int,
    ready: Boolean,
    spotify: Boolean,
    hidden: Set<String>,
    shelves: () -> List<HomeShelf>,
): List<ShelfItem>? {
    val latestShelves by rememberUpdatedState(shelves)
    val sleepTime by produceState(MixesDial.isSleepTime(), on) {
        while (on) {
            value = MixesDial.isSleepTime()
            delay(CLOCK_CHECK_MS)
        }
    }
    val dealt by produceState<List<ShelfItem>?>(null, on, round, ready, spotify, sleepTime) {
        if (!on || !ready) return@produceState
        value = MixesDial.deal(latestShelves(), spotify, hidden = emptySet(), sleepTime = sleepTime)
    }
    if (!on) return null
    return dealt?.filterNot { (it.videoId ?: it.browseId) in hidden }
}

/** How often the clock is checked for the sleep mixes' hours. */
private const val CLOCK_CHECK_MS = 60_000L
