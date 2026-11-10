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
}
