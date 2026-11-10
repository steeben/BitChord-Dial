package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import com.music.bitchord.data.mixes.MixesDial
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem

/**
 * The Mixes Dial's tiles for this round (app load or pull to refresh), dealt
 * once the round's feed is [ready] and kept until the next. Null until the
 * first deal is in. A new round keeps the last deal up until its own arrives,
 * so the page never blanks to placeholders mid-refresh. [hidden] (tiles taken
 * off the dials) is applied as it changes, without dealing again.
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
    val dealt by produceState<List<ShelfItem>?>(null, on, round, ready, spotify) {
        if (!on || !ready) return@produceState
        value = MixesDial.deal(latestShelves(), spotify, hidden = emptySet())
    }
    if (!on) return null
    return dealt?.filterNot { (it.videoId ?: it.browseId) in hidden }
}
