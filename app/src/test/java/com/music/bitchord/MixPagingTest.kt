package com.music.bitchord

import com.music.bitchord.data.MixPaging
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MixPagingTest {
    @Test
    fun tellsEndlessMixesFromListsWithAnEnd() {
        assertTrue(MixPaging.isEndlessMix("VLRDTMAK5uy_kset8DisdE7LSD4TNjEVvrKRTmG7a56sY"))
        assertTrue(MixPaging.isEndlessMix("RDTMAK5uy_n_5IN6hzAOwdCnM8D8rzrs3vDl12UcZpA"))
        assertTrue(MixPaging.isEndlessMix("VLRDAMPLPLabc"))
        assertFalse(MixPaging.isEndlessMix("VLRDCLAK5uy_abc"))
        assertFalse(MixPaging.isEndlessMix("VLPLabc"))
        assertFalse(MixPaging.isEndlessMix("VLLM"))
        assertFalse(MixPaging.isEndlessMix("MPREb_abc"))
        assertFalse(MixPaging.isEndlessMix(null))
    }

    @Test
    fun anOrdinaryPlaylistIsLeftAlone() {
        val paging = MixPaging(maxSongs = 3)
        val rows = songs(1..5) + song("v1", setVideoId = "again")

        val first = paging.first("VLPLabc", rows, "t1")
        assertEquals(rows, first.songs)
        assertEquals("t1", first.continuation)

        val next = paging.next("t1", rows, "t2")
        assertEquals(rows, next.songs)
        assertEquals("t2", next.continuation)
    }

    @Test
    fun aMixStopsAtItsLimitHoweverManyPagesYouTubeOffers() {
        val paging = MixPaging(maxSongs = 100)
        var page = paging.first("VLRDTMAK1", songs(1..40), "t1")
        var total = page.songs.size
        var from = 41
        var pages = 1
        while (page.continuation != null) {
            val token = page.continuation!!
            page = paging.next(token, songs(from until from + 40), "t${pages + 1}")
            total += page.songs.size
            from += 40
            pages++
            assertTrue("the mix never ended", pages < 10)
        }
        assertEquals(100, total)
        assertEquals(3, pages)
        assertEquals("v100", page.songs.last().videoId)
    }

    @Test
    fun aFirstPageThatAlreadyFillsTheMixHasNoMore() {
        val page = MixPaging(maxSongs = 100).first("VLRDTMAK1", songs(1..120), "t1")
        assertEquals(100, page.songs.size)
        assertNull(page.continuation)
    }

    @Test
    fun aPageOfRepeatsUnderNewRowIdsEndsTheMix() {
        val paging = MixPaging(maxSongs = 100)
        val first = paging.first("VLRDTMAK1", songs(1..20), "t1")
        assertEquals("t1", first.continuation)

        val repeats = (1..20).map { song("v$it", setVideoId = "second-deal-$it") }
        val next = paging.next("t1", repeats, "t2")
        assertTrue(next.songs.isEmpty())
        assertNull(next.continuation)
    }

    @Test
    fun repeatsAreDroppedButNewSongsOnTheSamePageAreKept() {
        val paging = MixPaging(maxSongs = 100)
        paging.first("VLRDTMAK1", songs(1..20), "t1")

        val next = paging.next("t1", songs(15..25), "t2")
        assertEquals(songs(21..25).map(Song::videoId), next.songs.map(Song::videoId))
        assertEquals("t2", next.continuation)
    }

    @Test
    fun twoLoadersAskingForTheSameTokenGetTheSamePage() {
        val paging = MixPaging(maxSongs = 100)
        paging.first("VLRDTMAK1", songs(1..20), "t1")

        val page = paging.next("t1", songs(21..40), "t2")
        val service = paging.next("t1", songs(21..40), "t2")
        assertEquals(page, service)
        assertEquals(20, service.songs.size)
        assertEquals("t2", service.continuation)
    }

    @Test
    fun aMixTokenLeftOverFromAnEarlierRunEndsTheWalk() {
        val paging = MixPaging()
        val stale = paging.next("saved-token", songs(1..20), "t2", browseId = "VLRDTMAK1")
        assertTrue(stale.songs.isEmpty())
        assertNull(stale.continuation)

        val playlist = paging.next("saved-token", songs(1..20), "t2", browseId = "VLPLabc")
        assertEquals(20, playlist.songs.size)
        assertEquals("t2", playlist.continuation)
    }

    @Test
    fun aMixWithNoTokenSimplyEnds() {
        val page = MixPaging().first("VLRDTMAK1", songs(1..50), null)
        assertEquals(50, page.songs.size)
        assertNull(page.continuation)
    }

    private fun songs(range: IntRange) = range.map { song("v$it") }

    private fun song(videoId: String, setVideoId: String? = null) =
        Song(videoId = videoId, title = videoId, artist = "artist", thumbnailUrl = null, setVideoId = setVideoId)
}
