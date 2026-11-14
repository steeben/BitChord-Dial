package com.music.bitchord

import com.music.bitchord.data.model.EpisodeInfo
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.ShelfLayout
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.podcasts.PodcastsDial
import com.music.bitchord.data.settings.CustomizationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun feed_takesHomesShowsForTheCards_andKeepsTheShelvesApart() {
        val home = listOf(episodes("Comedy", "e1"), shows("Shows for you", "a", "b"), episodes("News", "e2"))
        val landing = listOf(shows("Start exploring", "c"), episodes("Popular episodes", "e3"))

        val feed = PodcastsDial.feed(home, "more", landing)

        assertEquals("Shows for you", feed.forYou?.title)
        assertEquals(listOf("Comedy", "Shows for you", "News"), feed.shelves.map { it.title })
        assertEquals(listOf("Start exploring", "Popular episodes"), feed.landing.map { it.title })
        assertEquals(listOf("a", "b", "c"), ids(feed.picks))
        assertEquals("more", feed.continuation)
    }

    @Test
    fun feed_usesTheSavedShows_whenThisAnswerHasNone_aheadOfTheLandingPages() {
        val home = listOf(episodes("Comedy", "e1"))
        val landing = listOf(shows("Start exploring", "c", "a"))
        val saved = shows("Shows for you", "a", "b")

        val feed = PodcastsDial.feed(home, null, landing, savedForYou = saved)

        assertEquals(saved, feed.forYou)
        assertEquals(listOf("a", "b", "c"), ids(feed.picks))
    }

    @Test
    fun feed_usesTheLandingPagesFirstShows_whenThereAreNoOthers() {
        val home = listOf(episodes("Comedy", "e1"))
        val landing = listOf(episodes("Popular episodes", "e3"), shows("Start exploring", "c"), shows("Popular shows", "d"))

        val feed = PodcastsDial.feed(home, null, landing)

        assertEquals("Start exploring", feed.forYou?.title)
        assertEquals(listOf("c", "d"), ids(feed.picks))
        assertNull(PodcastsDial.feed(home, null, emptyList()).forYou)
    }

    @Test
    fun shelvesUnder_areHomesAlone_untilTheyHaveEnded_thenTheLandingPagesToo() {
        val home = listOf(shows("Shows for you", "a"), episodes("Comedy", "e1"))
        val landing = listOf(shows("Start exploring", "c"), episodes("Popular episodes", "e3"))

        val reading = PodcastsDial.feed(home, "more", landing)
        // The shelf that the large cards are is not listed again.
        assertEquals(listOf("Comedy"), PodcastsDial.shelvesUnder(reading).map { it.title })

        val ended = PodcastsDial.more(reading, listOf(episodes("News", "e2")), null)
        assertEquals(
            listOf("Comedy", "News", "Start exploring", "Popular episodes"),
            PodcastsDial.shelvesUnder(ended).map { it.title },
        )
    }

    @Test
    fun shelvesUnder_listAShelfTitleOnce_homesFirst() {
        val home = listOf(episodes("Comedy", "e1"), shows("Popular shows", "b"))
        val landing = listOf(shows("Start exploring", "c"), shows("popular shows ", "d"), shows("Comedy", "e"))

        val feed = PodcastsDial.feed(home, null, landing)

        assertEquals(
            listOf("Comedy", "Start exploring"),
            PodcastsDial.shelvesUnder(feed).map { it.title },
        )
        // A show on a shelf that is left out is still one for the dial.
        assertEquals(listOf("b", "c", "d", "e"), ids(feed.picks))
    }

    @Test
    fun more_addsAPage_andStopsAtTheMostPages() {
        var feed = PodcastsDial.feed(listOf(episodes("Comedy", "e1")), "t1", emptyList())
        feed = PodcastsDial.more(feed, listOf(episodes("News", "e2"), HomeShelf("Empty", emptyList())), "t2")

        assertEquals(listOf("Comedy", "News"), feed.shelves.map { it.title })
        assertEquals("t2", feed.continuation)
        assertEquals(2, feed.pages)

        repeat(PodcastsDial.MAX_PAGES - 2) { feed = PodcastsDial.more(feed, listOf(episodes("Page $it", "p$it")), "next") }
        assertEquals(PodcastsDial.MAX_PAGES, feed.pages)
        assertNull(feed.continuation)
    }

    @Test
    fun tiles_arePinsThenPicks_withoutWhatWasTakenOffTheDial() {
        val pins = listOf(show("p1"), show("p2"))
        val picks = listOf(show("a"), show("p1"), show("b"), show("c"))

        val tiles = PodcastsDial.tiles(pins, picks, hidden = setOf("MPSPb"))

        assertEquals(listOf("p1", "p2", "a", "c"), ids(tiles))
    }

    private fun newEpisode(id: String, show: String?, played: Int = 0) = Song(
        videoId = id,
        title = "Episode $id",
        artist = "Show $show",
        thumbnailUrl = "still/$id",
        episode = EpisodeInfo(playedPercent = played, showId = show?.let { "MPSP$it" }),
    )

    @Test
    fun fresh_isOneForEachShow_byItsNewestEpisode_whenThatWasNotStarted() {
        val episodes = listOf(
            newEpisode("e1", "a"),
            newEpisode("e2", "b", played = 40),
            // An older one of a show whose newest was started does not make the show new.
            newEpisode("e3", "b"),
            newEpisode("e4", "a"),
            newEpisode("e5", null),
            newEpisode("e6", "c"),
        )
        val fresh = PodcastsDial.fresh(episodes, followed = listOf(show("a")), known = listOf(show("c"), show("a")))

        assertEquals(listOf("a", "c"), ids(fresh.map { it.show }))
        assertEquals(listOf("e1", "e6"), fresh.map { it.episodeId })
        // The library's own tile for the show, where it has one.
        assertEquals(show("a"), fresh.first().show)
    }

    @Test
    fun fresh_makesATileFromTheEpisode_forAShowNoListHas() {
        val made = PodcastsDial.fresh(listOf(newEpisode("e1", "z")), followed = emptyList()).single().show

        assertEquals("MPSPz", made.browseId)
        assertEquals("Show z", made.title)
        assertEquals("still/e1", made.thumbnailUrl)
    }

    @Test
    fun freshShows_leaveOutAShowWhoseEpisodeWasStartedHere() {
        val fresh = PodcastsDial.fresh(listOf(newEpisode("e1", "a"), newEpisode("e2", "b")), listOf(show("a"), show("b")))

        assertEquals(listOf("a", "b"), ids(PodcastsDial.freshShows(fresh, emptyMap())))
        assertEquals(listOf("b"), ids(PodcastsDial.freshShows(fresh, mapOf("e1" to 3))))
    }

    @Test
    fun tiles_putShowsWithANewEpisodeAfterThePins_andLeaveAPinnedOneWhereItIs() {
        val pins = listOf(show("p1"), show("n2"))
        val picks = listOf(show("a"), show("n1"), show("b"))
        val fresh = listOf(show("n1"), show("n2"))

        val tiles = PodcastsDial.tiles(pins, picks, hidden = emptySet(), fresh = fresh)

        assertEquals(listOf("p1", "n2", "n1", "a", "b"), ids(tiles))
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
