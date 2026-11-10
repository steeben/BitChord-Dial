package com.music.bitchord

import com.music.bitchord.data.mixes.MixesDial
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.spotify.SpotifyLibrary
import com.music.bitchord.data.spotify.SpotifyPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MixesDialTest {

    private fun playlist(id: String, title: String) =
        ShelfItem(title = title, subtitle = "", thumbnailUrl = null, videoId = null, browseId = id)

    @Test
    fun youTubeMixesAreTheAccountsMixesAndPlaylistsCalledOne() {
        assertTrue(MixesDial.isYouTubeMix(playlist("VLRDTMAK5uy_kset8DisdE7LSD4TNjEVvrKRTmG7a56sY", "My Mix 1")))
        assertTrue(MixesDial.isYouTubeMix(playlist("RDTMAK5uy_n_5IN6hzAOwdCnM8D8rzrs3vDl12UcZpA", "Discover Mix")))
        assertTrue(MixesDial.isYouTubeMix(playlist("VLRDCLAK5uy_abc", "Chill Mix")))
        assertFalse(MixesDial.isYouTubeMix(playlist("VLRDCLAK5uy_abc", "Chill Hits")))
        assertFalse(MixesDial.isYouTubeMix(playlist("MPREb_abc", "Mix Tape (album)")))
        assertFalse(MixesDial.isYouTubeMix(playlist("UCabc", "Mixmaster")))
        assertFalse(
            MixesDial.isYouTubeMix(
                ShelfItem(title = "Mix", subtitle = "", thumbnailUrl = null, videoId = "abc", browseId = null),
            ),
        )
    }

    @Test
    fun spotifyMixesAreSpotifysPersonalPlaylists() {
        fun sp(name: String, owner: String?, id: String = name) = SpotifyPlaylist(id, name, owner, null)
        assertTrue(MixesDial.isSpotifyMix(sp("Daily Mix 1", "Spotify")))
        assertTrue(MixesDial.isSpotifyMix(sp("Discover Weekly", "Spotify")))
        assertTrue(MixesDial.isSpotifyMix(sp("daylist • chill tuesday evening", "Spotify")))
        assertFalse(MixesDial.isSpotifyMix(sp("Today's Top Hits", "Spotify")))
        assertFalse(MixesDial.isSpotifyMix(sp("My gym mix", "steeben")))
        assertFalse(MixesDial.isSpotifyMix(sp("Liked Songs", null, SpotifyLibrary.LIKED_ID)))
    }

    @Test
    fun pickTakesFromBothSourcesInTurn() {
        val yt = (1..10).map { playlist("VLRDTMAK$it", "My Mix $it") }
        val sp = (1..3).map { playlist("spotify:playlist:$it", "Daily Mix $it") }
        val dealt = MixesDial.pick(yt, sp, Random(7))
        assertEquals(MixesDial.SIZE, dealt.size)
        // All three of Spotify's within the first six, alternating with YouTube's.
        assertEquals(3, dealt.take(6).count { it.browseId!!.startsWith("spotify:") })
        assertEquals(dealt.size, dealt.map { it.browseId }.toSet().size)
    }

    @Test
    fun pickDropsDuplicatesAndCopesWithOneSource() {
        val mix = playlist("VLRDTMAK1", "My Mix 1")
        assertEquals(listOf(mix), MixesDial.pick(listOf(mix, mix), emptyList(), Random(1)))
        assertEquals(emptyList<ShelfItem>(), MixesDial.pick(emptyList(), emptyList(), Random(1)))
    }

    @Test
    fun differentDealsShuffleDifferently() {
        val yt = (1..12).map { playlist("VLRDTMAK$it", "My Mix $it") }
        val deals = (1..5).map { seed -> MixesDial.pick(yt, emptyList(), Random(seed)) }.toSet()
        assertTrue(deals.size > 1)
    }
}
