package com.music.bitchord

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.settings.SpeedDialPicker
import com.music.bitchord.data.settings.SpeedDialSource
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedDialPickerTest {
    private fun song(id: String) = ShelfItem(title = id, subtitle = "", thumbnailUrl = null, videoId = id, browseId = null)
    private fun album(id: String) = ShelfItem(title = id, subtitle = "", thumbnailUrl = null, videoId = null, browseId = id)

    private val history = (1..20).map { song("h$it") }
    private val recommends = (1..20).map { song("r$it") }
    private val releases = (1..10).map { album("n$it") }

    private fun pick(source: SpeedDialSource, seed: Int, recs: List<ShelfItem> = recommends) = SpeedDialPicker.pick(
        source = source,
        history = history,
        recommends = recs,
        newReleases = releases,
        fill = history,
        firstPage = 12,
        size = 36,
        random = Random(seed),
    )

    @Test
    fun history_keepsTheHistoryOrderWithoutNewReleases() {
        val picks = pick(SpeedDialSource.HISTORY, 1)
        assertEquals(history, picks.items)
        assertTrue(picks.newReleaseIds.isEmpty())
    }

    @Test
    fun recommends_carriesOneToThreeNewReleasesOnTheFirstPage() {
        repeat(50) { seed ->
            val picks = pick(SpeedDialSource.RECOMMENDS, seed)
            val ids = picks.items.map { it.videoId ?: it.browseId }
            assertTrue(picks.newReleaseIds.size in 1..3)
            assertEquals(picks.newReleaseIds.size, ids.count { it!!.startsWith("n") })
            picks.newReleaseIds.forEach { assertTrue(ids.indexOf(it) < 12) }
            assertEquals(ids.size, ids.toSet().size)
        }
    }

    @Test
    fun recommends_drawsFromThePicksBeforeTheFill() {
        val picks = pick(SpeedDialSource.RECOMMENDS, 7)
        val rest = picks.items.filter { it.videoId != null }.map { it.videoId!! }
        assertEquals(recommends.map { it.videoId }.toSet(), rest.take(20).toSet())
    }

    @Test
    fun recommends_topsUpFromTheFillWhenThePicksRunShort() {
        val picks = pick(SpeedDialSource.RECOMMENDS, 3, recs = recommends.take(2))
        assertTrue(picks.items.size >= 22)
    }

    @Test
    fun mix_blendsHistoryAndRecommends() {
        val picks = pick(SpeedDialSource.MIX, 11)
        val firstHalf = picks.items.take(20).mapNotNull { it.videoId }
        assertTrue(firstHalf.any { it.startsWith("h") })
        assertTrue(firstHalf.any { it.startsWith("r") })
    }

    @Test
    fun youtube_keepsTheFeedOrderWithoutNewReleases() {
        val picks = pick(SpeedDialSource.YOUTUBE, 5)
        assertEquals(recommends, picks.items.take(20))
        assertTrue(picks.newReleaseIds.isEmpty())
        assertTrue(picks.items.none { it.browseId?.startsWith("n") == true })
    }

    @Test
    fun youtube_topsUpFromTheFillUpToTheDialSize() {
        val picks = pick(SpeedDialSource.YOUTUBE, 5, recs = recommends.take(2))
        assertEquals(recommends.take(2), picks.items.take(2))
        assertEquals(history, picks.items.drop(2))
    }

    @Test
    fun shelfTitles_areRecognised() {
        assertTrue(SpeedDialPicker.isRecommendShelf("Quick picks"))
        assertTrue(SpeedDialPicker.isRecommendShelf("Forgotten favorites"))
        assertTrue(SpeedDialPicker.isRecommendShelf("Listen again"))
        assertTrue(SpeedDialPicker.isRecommendShelf("Recents"))
        assertTrue(SpeedDialPicker.isNewReleaseShelf("New releases"))
        val feed = listOf(HomeShelf("New releases", listOf(album("a"))), HomeShelf("Charts", listOf(album("b"))))
        assertEquals(listOf("a", "x"), SpeedDialPicker.newReleasesOf(feed, listOf(album("x"), album("a"))).map { it.browseId })
    }
}
