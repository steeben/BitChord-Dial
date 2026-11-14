package com.music.bitchord

import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.settings.SpeedDialPicker
import com.music.bitchord.data.settings.SpeedDialSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedDialPickerTest {
    private fun song(id: String) = ShelfItem(title = id, subtitle = "", thumbnailUrl = null, videoId = id, browseId = null)
    private fun album(id: String) = ShelfItem(title = id, subtitle = "", thumbnailUrl = null, videoId = null, browseId = id)

    private fun items(prefix: String, count: Int) = (1..count).map { album("$prefix$it") }
    private val history = items("h", 20)

    private fun ids(items: List<ShelfItem>) = items.map { it.videoId ?: it.browseId!! }

    @Test
    fun theDialIsMusicOnly() {
        val show = album("MPSPPLshow")
        val episode = song("ep1").copy(isPodcastEpisode = true)
        val shelfEpisode = song("ep2").copy(durationText = "34:00")
        val later = album("VLSE")
        val picks = SpeedDialPicker.pick(
            SpeedDialSource.YOUTUBE,
            history = listOf(episode, song("s3")),
            official = listOf(show, song("s1")),
            listenAgain = listOf(shelfEpisode, later, song("s2")),
            size = 3,
        )
        assertEquals(listOf("s1", "s2", "s3"), ids(picks.items))
        val fromHistory = SpeedDialPicker.pick(SpeedDialSource.HISTORY, listOf(show, song("s1"), episode), size = 3)
        assertEquals(listOf("s1"), ids(fromHistory.items))
    }

    @Test
    fun history_keepsTheHistoryOrder() {
        val picks = SpeedDialPicker.pick(SpeedDialSource.HISTORY, history, official = items("o", 5), size = 36)
        assertEquals(history, picks.items)
        assertTrue(picks.newReleaseIds.isEmpty())
    }

    @Test
    fun youtube_takesTheOfficialDialWholeAndInOrder() {
        val official = items("o", 40)
        val picks = SpeedDialPicker.pick(SpeedDialSource.YOUTUBE, history, official = official, size = 36)
        assertEquals(official, picks.items)
        assertTrue(picks.newReleaseIds.isEmpty())
    }

    @Test
    fun youtube_fillsTheRestInYouTubesOrder() {
        val picks = SpeedDialPicker.pick(
            SpeedDialSource.YOUTUBE,
            history = history,
            official = items("o", 3),
            listenAgain = items("l", 3),
            recents = items("r", 3),
            forgotten = items("f", 3),
            quickPicks = (1..3).map { song("q$it") },
            size = 20,
        )
        assertEquals(
            listOf("o", "l", "r", "f", "q", "h").flatMap { p -> (1..3).map { "$p$it" } }.take(18) + listOf("h4", "h5"),
            ids(picks.items),
        )
    }

    @Test
    fun youtube_skipsWhatTheDialAlreadyHas() {
        val picks = SpeedDialPicker.pick(
            SpeedDialSource.YOUTUBE,
            history = listOf(album("o1"), album("h1")),
            official = listOf(album("o1"), album("o2")),
            listenAgain = listOf(album("o2"), album("l1")),
            size = 36,
        )
        assertEquals(listOf("o1", "o2", "l1", "h1"), ids(picks.items))
    }

    @Test
    fun shelfTitles_areRecognised() {
        assertTrue(SpeedDialPicker.isQuickPicksShelf("Quick picks"))
        assertTrue(SpeedDialPicker.isForgottenShelf("Forgotten favorites"))
        assertTrue(SpeedDialPicker.isListenAgainShelf("Listen again "))
    }

    @Test
    fun spotify_isOnlyAChoiceWithASpotifyAccount() {
        assertEquals(listOf(SpeedDialSource.HISTORY, SpeedDialSource.YOUTUBE), SpeedDialSource.choices(spotifyConnected = false))
        assertEquals(SpeedDialSource.entries.toList(), SpeedDialSource.choices(spotifyConnected = true))
        assertEquals(SpeedDialSource.YOUTUBE, SpeedDialSource.SPOTIFY.effective(spotifyConnected = false))
        assertEquals(SpeedDialSource.SPOTIFY, SpeedDialSource.SPOTIFY.effective(spotifyConnected = true))
        assertEquals(SpeedDialSource.HISTORY, SpeedDialSource.HISTORY.effective(spotifyConnected = false))
    }

    @Test
    fun spotify_fillsFromYouTubesDial() {
        val official = items("o", 40)
        assertEquals(official, SpeedDialPicker.pick(SpeedDialSource.SPOTIFY, history, official = official, size = 36).items)
    }
}
