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

    @Test
    fun theWebPlayerMakesThePlaylistByNameAndAnswersWithItsUri() {
        assertEquals(
            """{"ops":[{"kind":"UPDATE_LIST_ATTRIBUTES","updateListAttributes":{"newAttributes":{"values":{"name":"Played in Dial"}}}}]}""",
            SpotifyPlayedList.webCreateBody(),
        )
        assertEquals("p1", SpotifyPlayedList.webCreatedId("""{"uri":"spotify:playlist:p1","revision":"r"}"""))
        assertNull(SpotifyPlayedList.webCreatedId("""{"error":"no"}"""))
    }

    @Test
    fun aNewPlaylistIsPutAtTheTopOfTheLibrary() {
        assertEquals(
            "https://spclient.wg.spotify.com/playlist/v2/user/a%20b/rootlist/changes",
            SpotifyPlayedList.rootlistUrl("a b"),
        )
        assertEquals(
            """{"deltas":[{"ops":[{"kind":"ADD","add":{"items":[{"uri":"spotify:playlist:p1","attributes":{"timestamp":"42"}}],"addFirst":true}}],"info":{"source":{"client":"WEBPLAYER"}}}]}""",
            SpotifyPlayedList.rootlistAddBody("p1", 42),
        )
        assertEquals(true, SpotifyPlayedList.rootlistChanged("""{"revision":"AAAA"}"""))
        assertEquals(false, SpotifyPlayedList.rootlistChanged("""{"revision":null}"""))
        assertEquals(false, SpotifyPlayedList.rootlistChanged("nope"))
    }

    @Test
    fun readsTheAccountsUsername() {
        assertEquals(
            """{"variables":{},"operationName":"profileAttributes","extensions":{"persistedQuery":{"version":1,"sha256Hash":"h"}}}""",
            SpotifyPlayedList.queryBody(SpotifyPlayedList.PROFILE_OPERATION, "h"),
        )
        assertEquals("stee", SpotifyPlayedList.username("""{"data":{"me":{"profile":{"username":"stee","name":"S"}}}}"""))
        assertNull(SpotifyPlayedList.username("""{"errors":[{"message":"PersistedQueryNotFound"}]}"""))
    }

    @Test
    fun theWebPlayerAddsASongAtTheEnd() {
        assertEquals(
            """{"variables":{"playlistItemUris":["spotify:track:abc"],"playlistUri":"spotify:playlist:p1","newPosition":{"moveType":"BOTTOM_OF_PLAYLIST","fromUid":null}},"operationName":"addToPlaylist","extensions":{"persistedQuery":{"version":1,"sha256Hash":"h"}}}""",
            SpotifyPlayedList.webAddBody("p1", "spotify:track:abc", "h"),
        )
        assertEquals(
            SpotifyPlayedList.Answer.Done,
            SpotifyPlayedList.webAdded("""{"data":{"addItemsToPlaylist":{"__typename":"AddItemsToPlaylistPayload"}}}"""),
        )
    }

    @Test
    fun aRefusedChangeSaysWhyAndWhetherTheHashIsStale() {
        assertEquals(
            SpotifyPlayedList.Answer.Refused("PersistedQueryNotFound", staleHash = true),
            SpotifyPlayedList.webAdded("""{"errors":[{"message":"PersistedQueryNotFound"}]}"""),
        )
        assertEquals(
            SpotifyPlayedList.Answer.Refused("PlaylistNotFound"),
            SpotifyPlayedList.webAdded("""{"data":{"addItemsToPlaylist":{"__typename":"PlaylistNotFound"}}}"""),
        )
        assertEquals(SpotifyPlayedList.Answer.Refused("no addItemsToPlaylist"), SpotifyPlayedList.webAdded("""{"data":{}}"""))
    }

    @Test
    fun theWebPlayerTakesEntriesOutByTheirPlace() {
        assertEquals(
            """{"variables":{"uids":["u1","u2"],"playlistUri":"spotify:playlist:p1"},"operationName":"removeFromPlaylist","extensions":{"persistedQuery":{"version":1,"sha256Hash":"h"}}}""",
            SpotifyPlayedList.webRemoveBody("p1", listOf("u1", "u2"), "h"),
        )
        assertEquals(
            SpotifyPlayedList.Answer.Done,
            SpotifyPlayedList.webRemoved("""{"data":{"removeItemsFromPlaylist":{"__typename":"RemoveItemsFromPlaylistPayload"}}}"""),
        )
    }

    @Test
    fun theLastOutcomeSurvivesBeingSaved() {
        val status = SpotifyPlayedList.Status(SpotifyPlayedList.Status.Kind.FAILED, "adding 'A\tB': http 429", 7)
        assertEquals(status, SpotifyPlayedList.Status.decode(status.encode()))
        assertNull(SpotifyPlayedList.Status.decode(null))
        assertNull(SpotifyPlayedList.Status.decode("WHAT\t1\tx"))
    }
}
