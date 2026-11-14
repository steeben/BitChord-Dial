package com.music.bitchord

import com.music.bitchord.data.model.EpisodeInfo
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.podcasts.EpisodeFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeFilterTest {
    private fun episode(id: String, played: Int) =
        Song(videoId = id, title = id, artist = "Show", thumbnailUrl = null, episode = EpisodeInfo(playedPercent = played))

    private val songs = listOf(
        episode("new", 0),
        episode("half", 40),
        episode("done", 100),
        episode("credits", 98),
        episode("appOnly", 0),
    )
    private val inApp = mapOf("appOnly" to 12)

    private fun ids(filter: EpisodeFilter) = filter.apply(songs, inApp).map { it.videoId }

    @Test
    fun allKeepsEveryEpisode() {
        assertEquals(songs.map { it.videoId }, ids(EpisodeFilter.ALL))
    }

    @Test
    fun filtersByTheLargerOfTheTwoFigures() {
        assertEquals(listOf("new"), ids(EpisodeFilter.UNPLAYED))
        assertEquals(listOf("half", "appOnly"), ids(EpisodeFilter.IN_PROGRESS))
        assertEquals(listOf("done", "credits"), ids(EpisodeFilter.PLAYED))
    }

    @Test
    fun aTrackThatIsNotAnEpisodeStays() {
        val track = Song(videoId = "track", title = "Track", artist = "Artist", thumbnailUrl = null)
        assertEquals(listOf("track"), EpisodeFilter.PLAYED.apply(listOf(track), emptyMap()).map { it.videoId })
    }

    @Test
    fun aTapGoesRoundTheFilters() {
        assertEquals(EpisodeFilter.UNPLAYED, EpisodeFilter.ALL.next())
        assertEquals(EpisodeFilter.ALL, EpisodeFilter.PLAYED.next())
    }
}
