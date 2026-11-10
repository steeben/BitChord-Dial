package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.data.settings.DialPicks
import com.music.bitchord.data.settings.SpeedDialSource
import com.music.bitchord.data.spotify.SpotifyRecommendations

/**
 * The speed dial's tiles past the pins for this round (app load or pull to
 * refresh), dealt once and kept so a song starting doesn't move them. Null
 * while there is nothing dealt: for History, which is built as it goes, and
 * until the round's feed is [ready].
 *
 * Spotify's picks take a few requests to arrive. Until they have, this stays
 * null, so Home keeps the dial it showed last rather than showing YouTube's
 * tiles and then swapping them. They go ahead of YouTube's dial, which fills
 * whatever Spotify couldn't, and is the whole dial when Spotify has nothing.
 */
@Composable
fun rememberDealtDialPicks(
    round: Int,
    source: SpeedDialSource,
    ready: Boolean,
    shelves: () -> List<HomeShelf>,
): DialPicks? {
    val dealt = remember(round, source, ready) {
        if (source == SpeedDialSource.HISTORY || !ready) null else CustomizationSettings.pickSpeedDial(source, shelves())
    }
    val spotify by produceState<List<ShelfItem>?>(null, round, source, ready) {
        value = null
        if (source == SpeedDialSource.SPOTIFY && ready) value = SpotifyRecommendations.dialPicks()
    }
    if (source != SpeedDialSource.SPOTIFY) return dealt
    val tiles = spotify ?: return null
    return remember(dealt, tiles) { dealt?.let { DialPicks(tiles + it.items, it.newReleaseIds) } }
}
