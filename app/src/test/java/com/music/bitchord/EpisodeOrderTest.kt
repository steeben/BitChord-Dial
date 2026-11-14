package com.music.bitchord

import com.music.bitchord.data.model.EpisodeInfo
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.podcasts.EpisodeOrder
import com.music.bitchord.data.podcasts.EpisodeShows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeOrderTest {

    private fun episode(id: String, show: String = "Show") =
        Song(videoId = id, title = "Episode $id", artist = show, thumbnailUrl = null, episode = EpisodeInfo())

    private fun card(id: String, show: String, length: String? = "40:00") =
        ShelfItem(title = "Episode $id", subtitle = show, thumbnailUrl = null, videoId = id, browseId = null, durationText = length)

    // A show's page lists the newest first.
    private val show = listOf(episode("e5"), episode("e4"), episode("e3"), episode("e2"), episode("e1"))

    @Test
    fun next_isTheEpisodeThatCameOutAfter_thenTheOneAfterThat() {
        assertEquals(listOf("e4", "e5"), EpisodeOrder.newerThan("e3", show).map { it.videoId })
    }

    @Test
    fun theNewestEpisode_hasNoNext() {
        assertTrue(EpisodeOrder.newerThan("e5", show).isEmpty())
    }

    @Test
    fun anEpisodeNotOnTheList_hasNoNext() {
        assertTrue(EpisodeOrder.newerThan("old", show).isEmpty())
    }

    @Test
    fun recommended_isOneEpisodeForEachShow_noneOfTheSeedsShow_noneQueued() {
        val shelves = listOf(
            HomeShelf("Popular", listOf(card("a1", "Show A"), card("a2", "Show A"), card("s1", "Show"))),
            HomeShelf("News", listOf(card("b1", "Show B"), card("q1", "Show Q"), card("c1", "Show C"))),
        )
        val picked = EpisodeOrder.recommended(shelves, episode("e3"), queued = setOf("q1"), limit = 10)
        assertEquals(listOf("a1", "b1", "c1"), picked.map { it.videoId })
        assertTrue(picked.all { it.episode != null })
    }

    @Test
    fun recommended_leavesOutShowCards_andKeepsToTheLimit() {
        val shelves = listOf(
            HomeShelf(
                "Shows",
                listOf(
                    ShelfItem("A show", "Publisher", null, null, "MPSPx"),
                    card("a1", "Show A"),
                    card("b1", "Show B"),
                    card("c1", "Show C"),
                ),
            ),
        )
        assertEquals(listOf("a1", "b1"), EpisodeOrder.recommended(shelves, episode("e3"), emptySet(), 2).map { it.videoId })
    }

    @Test
    fun theShowIsKnown_fromTheRow_orThePageItWasPlayedFrom() {
        assertEquals("MPSProw", EpisodeShows.knownIdOf(episode("e1").copy(episode = EpisodeInfo(showId = "MPSProw"))))
        assertEquals("MPSPpage", EpisodeShows.knownIdOf(episode("e1").copy(playbackSourceId = "MPSPpage")))
        assertNull(EpisodeShows.knownIdOf(episode("e1").copy(artistId = "UCchannel")))
    }
}
