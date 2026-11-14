package com.music.bitchord

import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.podcasts.PodcastSource
import com.music.bitchord.data.podcasts.PodcastSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastSourcesTest {

    private fun show(id: String, title: String = "Show $id") = ShelfItem(title, "Publisher", "art/$id", null, "MPSP$id")

    private fun ids(items: List<ShelfItem>) = items.map { it.browseId?.removePrefix("MPSP") }

    private val youtube = listOf(show("y1"), show("y2"), show("both"), show("y3"))
    private val spotify = listOf(show("s1"), show("both"), show("s2"))

    @Test
    fun youtube_isYouTubesAlone() {
        assertEquals(listOf("y1", "y2", "both", "y3"), ids(PodcastSources.picks(youtube, spotify, PodcastSource.YOUTUBE)))
    }

    @Test
    fun spotify_isSpotifysThenYouTubes_eachShowOnce() {
        assertEquals(
            listOf("s1", "both", "s2", "y1", "y2", "y3"),
            ids(PodcastSources.picks(youtube, spotify, PodcastSource.SPOTIFY)),
        )
    }

    @Test
    fun both_takesTurns_spotifysFirst() {
        assertEquals(
            listOf("s1", "y1", "both", "y2", "s2", "y3"),
            ids(PodcastSources.picks(youtube, spotify, PodcastSource.BOTH)),
        )
    }

    @Test
    fun aChoiceOfSpotify_isYouTube_withoutASpotifyAccount() {
        assertEquals(PodcastSource.YOUTUBE, PodcastSource.BOTH.effective(spotifyConnected = false))
        assertEquals(PodcastSource.BOTH, PodcastSource.BOTH.effective(spotifyConnected = true))
        assertEquals(listOf(PodcastSource.YOUTUBE), PodcastSource.choices(spotifyConnected = false))
        assertEquals(PodcastSource.entries.toList(), PodcastSource.choices(spotifyConnected = true))
    }

    @Test
    fun match_isTheShowOfTheSameName_howeverItIsWritten() {
        val results = listOf(show("a", "The Daily Show: Ears Edition"), show("b", "the daily"), show("c", "Daily Tech"))

        assertEquals("b", ids(listOfNotNull(PodcastSources.match("The Daily", results))).single())
        // With or without "the", and whatever its marks.
        assertEquals("b", ids(listOfNotNull(PodcastSources.match("DAILY!", results))).single())
    }

    @Test
    fun match_takesANameThatBeginsTheSame_whenNoneIsTheSame() {
        val results = listOf(show("a", "Something Else"), show("b", "Huberman Lab with Andrew Huberman"))

        assertEquals("b", ids(listOfNotNull(PodcastSources.match("Huberman Lab", results))).single())
    }

    @Test
    fun match_isNone_ratherThanAnotherShow() {
        val results = listOf(show("a", "News Roundup"), show("b", "Newsroom Stories"))

        // "News" begins both, and is neither.
        assertNull(PodcastSources.match("News", results))
        assertNull(PodcastSources.match("A Show That Is Not There", results))
        assertNull(PodcastSources.match("!!!", results))
    }
}
