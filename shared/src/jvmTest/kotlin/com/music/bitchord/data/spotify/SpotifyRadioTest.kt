package com.music.bitchord.data.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpotifyRadioTest {

    private fun pick(id: String, title: String = id, artist: String = "artist $id") = SpotifyRadio.Pick(id, title, artist)

    @Test
    fun readsFullFlatAndMetadataEntries() {
        val body = """
            {"tracks":[
              {"uri":"spotify:track:a","name":"Yellow","artists":[{"name":"Coldplay"},{"name":"Someone"}]},
              {"id":"b","name":"Clocks","artistName":"Coldplay"},
              {"uri":"spotify:track:c","metadata":{"title":"Fix You","artist_name":"Coldplay"}},
              {"uri":"spotify:track:d","uid":"x"},
              {"uri":"spotify:track:a","name":"Yellow","artists":[{"name":"Coldplay"}]},
              {"uri":"spotify:episode:e","name":"A podcast"}
            ]}
        """.trimIndent()
        assertEquals(
            listOf(
                SpotifyRadio.Pick("a", "Yellow", "Coldplay, Someone"),
                SpotifyRadio.Pick("b", "Clocks", "Coldplay"),
                SpotifyRadio.Pick("c", "Fix You", "Coldplay"),
                SpotifyRadio.Pick("d", "", ""),
            ),
            SpotifyRadio.parse(body),
        )
    }

    @Test
    fun readsItemsWhenThereAreNoTracks() {
        assertEquals(listOf("a"), SpotifyRadio.parse("""{"tracks":[],"items":[{"id":"a","name":"x"}]}""").map { it.id })
    }

    @Test
    fun anAnswerThatIsNotAStationIsEmpty() {
        assertTrue(SpotifyRadio.parse("<html>").isEmpty())
        assertTrue(SpotifyRadio.parse("""{"error":{"status":429}}""").isEmpty())
    }

    @Test
    fun namesAreFilledInAndTheNamelessDropped() {
        val picks = listOf(pick("a"), SpotifyRadio.Pick("b", "", ""), SpotifyRadio.Pick("c", "", ""))
        val named = listOf(pick("b", "Clocks", "Coldplay"))
        assertEquals(listOf(pick("a"), pick("b", "Clocks", "Coldplay")), SpotifyRadio.withNames(picks, named))
    }

    @Test
    fun interleaveTakesFromEachStationInTurn() {
        val out = SpotifyRadio.interleave(
            listOf(listOf(pick("a1"), pick("a2"), pick("a3")), listOf(pick("b1")), listOf(pick("c1"), pick("c2"))),
            limit = 5,
        )
        assertEquals(listOf("a1", "b1", "c1", "a2", "c2"), out.map { it.id })
    }

    @Test
    fun interleaveLeavesOutRepeatsAndTheSeeds() {
        val out = SpotifyRadio.interleave(
            listOf(
                listOf(pick("a", "Yellow", "Coldplay"), pick("b", "Clocks", "Coldplay")),
                listOf(pick("c", "Yellow - Remastered 2011", "Coldplay, Someone"), pick("d", "Fix You (Live)", "Coldplay")),
            ),
            limit = 10,
            exclude = setOf(SpotifyRadio.songKey("Fix You", "Coldplay")),
        )
        assertEquals(listOf("a", "b"), out.map { it.id })
    }

    @Test
    fun urlsNameTheSeed() {
        assertEquals(
            "https://spclient.wg.spotify.com/radio-apollo/v3/stations/spotify:track:abc?count=20&autoplay=true",
            SpotifyRadio.stationUrl("abc", 20),
        )
        assertEquals("https://api.spotify.com/v1/recommendations?seed_tracks=abc&limit=100", SpotifyRadio.recommendationsUrl("abc", 500))
        assertEquals("https://api.spotify.com/v1/tracks?ids=a,b", SpotifyRadio.tracksUrl(listOf("a", "b")))
    }
}
