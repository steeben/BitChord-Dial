package com.music.bitchord

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.ShelfLayout
import com.music.bitchord.data.podcasts.PodcastsDial
import com.music.bitchord.data.settings.CustomizationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastsDialTest {

    private fun show(id: String) = ShelfItem("Show $id", "Publisher", "art/$id", null, "MPSP$id")

    private fun episode(id: String) = ShelfItem("Episode $id", "A show", "still/$id", id, null, durationText = "34:00")

    private fun shows(title: String, vararg ids: String) = HomeShelf(title, ids.map(::show))

    private fun episodes(title: String, vararg ids: String) =
        HomeShelf(title, ids.map(::episode), layout = ShelfLayout.VIDEO)

    private fun ids(items: List<ShelfItem>) = items.map { it.browseId?.removePrefix("MPSP") }

    @Test
    fun picks_putTheListenersShowsAheadOfTheLandingPages_eachShowOnce() {
        val home = listOf(episodes("Comedy", "e1", "e2"), shows("Shows for you", "a", "b"))
        val landing = listOf(shows("Start exploring", "b", "c"), shows("Popular shows", "c", "d"))

        assertEquals(listOf("a", "b", "c", "d"), ids(PodcastsDial.picks(home, landing)))
    }

    @Test
    fun picks_areTheLandingPagesAlone_whenHomeHasNoShows() {
        val home = listOf(episodes("Comedy", "e1"), episodes("News", "e2"))
        val landing = listOf(shows("Start exploring", "a"), episodes("Popular episodes", "e3"), shows("Comedy", "b"))

        assertEquals(listOf("a", "b"), ids(PodcastsDial.picks(home, landing)))
    }

    @Test
    fun showShelves_leaveOutEpisodes_andWhatIsNotAShow() {
        val mixed = HomeShelf(
            "Mixed",
            listOf(show("a"), ShelfItem("An album", "Someone", null, null, "MPREb_1"), episode("e1")),
        )
        val shelves = PodcastsDial.showShelves(listOf(episodes("News", "e2"), mixed))

        assertEquals(listOf("Mixed"), shelves.map { it.title })
        assertEquals(listOf("a"), ids(shelves.single().items))
    }

    @Test
    fun feed_listsAShelfTitleOnce_homesFirst() {
        val home = listOf(shows("Shows for you", "a"), shows("Popular shows", "b"))
        val landing = listOf(shows("Start exploring", "c"), shows("popular shows ", "d"), shows("Comedy", "e"))

        val feed = PodcastsDial.feed(home, landing)

        assertEquals(listOf("Shows for you", "Popular shows", "Start exploring", "Comedy"), feed.shelves.map { it.title })
        // A show on a shelf that is left out is still one for the dial.
        assertEquals(listOf("a", "b", "c", "d", "e"), ids(feed.picks))
    }

    @Test
    fun tiles_arePinsThenPicks_withoutWhatWasTakenOffTheDial() {
        val pins = listOf(show("p1"), show("p2"))
        val picks = listOf(show("a"), show("p1"), show("b"), show("c"))

        val tiles = PodcastsDial.tiles(pins, picks, hidden = setOf("MPSPb"))

        assertEquals(listOf("p1", "p2", "a", "c"), ids(tiles))
    }

    @Test
    fun tiles_holdOnlyShows_andNoMoreThanADial() {
        val picks = (1..60).map { show("s$it") } + episode("e1")

        val tiles = PodcastsDial.tiles(emptyList(), picks, hidden = emptySet())

        assertEquals(CustomizationSettings.SPEED_DIAL_SIZE, tiles.size)
        assertTrue(tiles.all { it.videoId == null })
    }

    @Test
    fun page_leadsWithTheDial_underTheTitleTheSpeedDialGoesBy() {
        val shelves = listOf(shows("Popular shows", "a"))

        val page = PodcastsDial.page(listOf(show("p1")), shelves)

        assertEquals(listOf(CustomizationSettings.SPEED_DIAL_TITLE, "Popular shows"), page.map { it.title })
        assertEquals(shelves, PodcastsDial.page(emptyList(), shelves))
    }
}
