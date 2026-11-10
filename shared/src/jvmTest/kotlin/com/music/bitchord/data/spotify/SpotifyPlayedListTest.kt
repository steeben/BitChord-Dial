package com.music.bitchord.data.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotifyPlayedListTest {

    @Test
    fun thePlaylistIsCreatedPrivate() {
        assertEquals(
            """{"name":"Played in Dial","description":"${SpotifyPlayedList.DESCRIPTION}","public":false}""",
            SpotifyPlayedList.createBody(),
        )
    }

    @Test
    fun addsOneTrackByUri() {
        assertEquals("""{"uris":["spotify:track:abc"]}""", SpotifyPlayedList.addBody("spotify:track:abc"))
    }

    @Test
    fun readsTheNewPlaylistsId() {
        assertEquals("p1", SpotifyPlayedList.createdId("""{"id":"p1","name":"Played in Dial"}"""))
        assertNull(SpotifyPlayedList.createdId("""{"error":{"status":403}}"""))
        assertNull(SpotifyPlayedList.createdId("nope"))
    }

    @Test
    fun theAppsListLeavesItOut() {
        val mine = SpotifyPlaylist("1", "Road trip", "me", null)
        val played = SpotifyPlaylist("2", SpotifyPlayedList.NAME, "me", null)
        assertEquals(listOf(mine), SpotifyPlayedList.withoutPlayedList(listOf(mine, played)))
    }

    @Test
    fun aSavedKeepThatIsNotOfferedFallsBackToTheDefault() {
        assertEquals(200, SpotifyPlayedList.keepOrDefault(200))
        assertEquals(SpotifyPlayedList.DEFAULT_KEEP, SpotifyPlayedList.keepOrDefault(0))
        assertEquals(SpotifyPlayedList.DEFAULT_KEEP, SpotifyPlayedList.keepOrDefault(250))
    }

    @Test
    fun onlyWhatIsOverTheKeepGoesAndAPageAtATime() {
        assertEquals(0, SpotifyPlayedList.excess(total = 100, keep = 100))
        assertEquals(0, SpotifyPlayedList.excess(total = 3, keep = 100))
        assertEquals(1, SpotifyPlayedList.excess(total = 101, keep = 100))
        assertEquals(SpotifyPlayedList.PAGE, SpotifyPlayedList.excess(total = 1000, keep = 100))
    }

    @Test
    fun readsTheTotalAndTheOldestSongs() {
        val body = """{"total":3,"items":[
            {"track":{"uri":"spotify:track:a"}},
            {"item":{"uri":"spotify:track:b"}},
            {"track":null},
            {"track":{"uri":"spotify:track:c"}}]}"""
        assertEquals(3, SpotifyPlayedList.total(body))
        assertEquals(listOf("spotify:track:a", "spotify:track:b"), SpotifyPlayedList.oldestUris(body, 2))
        assertEquals(emptyList(), SpotifyPlayedList.oldestUris("nope", 2))
        assertNull(SpotifyPlayedList.total("""{"error":{"status":401}}"""))
    }

    @Test
    fun songsAreTakenOutUnderTheNameTheAddressUses() {
        val (items, tracks) = SpotifyPlayedList.addUrls("p1")
        assertEquals(
            """{"items":[{"uri":"spotify:track:a"}]}""",
            SpotifyPlayedList.removeBody(items, listOf("spotify:track:a")),
        )
        assertEquals(
            """{"tracks":[{"uri":"spotify:track:a"},{"uri":"spotify:track:b"}]}""",
            SpotifyPlayedList.removeBody(tracks, listOf("spotify:track:a", "spotify:track:b")),
        )
    }
}
