package com.music.bitchord

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.settings.ArtistDialFill
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistDialFillTest {

    private fun artist(id: String) =
        ShelfItem(title = id, subtitle = "", thumbnailUrl = null, videoId = null, browseId = id)

    private fun song(id: String) =
        ShelfItem(title = id, subtitle = "", thumbnailUrl = null, videoId = id, browseId = null)

    private fun ids(items: List<ShelfItem>) = items.map { it.browseId }

    @Test
    fun withNoPinsAndNothingPlayedTheLibraryAndTheFeedFillTheDial() {
        val tiles = ArtistDialFill.tiles(
            pins = emptyList(),
            mostPlayed = emptyList(),
            library = listOf(
                HomeShelf("Albums", listOf(artist("MPREb_album"))),
                HomeShelf("Artists", listOf(artist("MPLAUCtrack"), artist("UCsub"))),
                HomeShelf("Subscriptions", listOf(artist("UCsub"))),
            ),
            feed = listOf(
                HomeShelf("Speed dial", listOf(song("abc"), artist("UCfeed"), artist("VLplaylist"))),
                HomeShelf("Listen again", listOf(artist("UCsub"), artist("UCfeed2"))),
            ),
            hidden = emptySet(),
            size = 9,
        )
        assertEquals(listOf("UCsub", "UCtrack", "UCfeed", "UCfeed2"), ids(tiles))
    }

    @Test
    fun pinsThenMostPlayedComeFirstAndHiddenArtistsStayOff() {
        val tiles = ArtistDialFill.tiles(
            pins = listOf(artist("UCpin")),
            mostPlayed = listOf(artist("UCtop"), artist("UCpin"), artist("UChidden")),
            library = emptyList(),
            feed = listOf(HomeShelf("Listen again", listOf(artist("UCfeed"), artist("UCfeed2")))),
            hidden = setOf("UChidden"),
            size = 3,
        )
        assertEquals(listOf("UCpin", "UCtop", "UCfeed"), ids(tiles))
    }

    @Test
    fun withNoArtistAnywhereThereAreNoTiles() {
        val tiles = ArtistDialFill.tiles(
            pins = emptyList(),
            mostPlayed = emptyList(),
            library = emptyList(),
            feed = listOf(HomeShelf("Quick picks", listOf(song("abc")))),
            hidden = emptySet(),
            size = 9,
        )
        assertEquals(emptyList<String?>(), ids(tiles))
    }
}
