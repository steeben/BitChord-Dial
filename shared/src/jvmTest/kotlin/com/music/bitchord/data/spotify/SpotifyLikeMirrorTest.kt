package com.music.bitchord.data.spotify

import com.music.bitchord.data.canvas.SpotifyCanvasQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpotifyLikeMirrorTest {

    private val searchAnswer = """
        {"data":{"searchV2":{"tracksV2":{"items":[
          {"item":{"data":{"uri":"spotify:track:cover","name":"Yellow",
            "artists":{"items":[{"profile":{"name":"Some Cover Band"}}]}}}},
          {"item":{"data":{"uri":"spotify:track:real","name":"Yellow - Remastered 2011",
            "artists":{"items":[{"profile":{"name":"Coldplay"}}]}}}}
        ]}}}}
    """.trimIndent()

    @Test
    fun picksTheSameSongByTheSameArtistNotTheTopHit() {
        val candidates = SpotifyLikeMirror.pathfinderCandidates(searchAnswer)
        assertEquals(2, candidates.size)
        assertEquals("spotify:track:real", SpotifyLikeMirror.bestMatch(candidates, "Yellow", "Coldplay")?.uri)
    }

    @Test
    fun savesNothingWhenNoCandidateMatches() {
        val candidates = SpotifyLikeMirror.pathfinderCandidates(searchAnswer)
        assertNull(SpotifyLikeMirror.bestMatch(candidates, "Fix You", "Coldplay"))
        assertNull(SpotifyLikeMirror.bestMatch(candidates, "Yellow", "Somebody Else"))
    }

    @Test
    fun featuredCreditsOnEitherSideStillMatch() {
        val candidates = listOf(
            SpotifyLikeMirror.Candidate("spotify:track:x", "Crazy In Love (feat. JAY-Z)", listOf("Beyoncé", "JAY-Z")),
        )
        assertEquals("spotify:track:x", SpotifyLikeMirror.bestMatch(candidates, "Crazy in Love", "Beyonce")?.uri)
    }

    @Test
    fun readsRestSearchAnswers() {
        val body = """{"tracks":{"items":[{"uri":"spotify:track:r","name":"Song","artists":[{"name":"Artist"}]}]}}"""
        val match = SpotifyLikeMirror.bestMatch(SpotifyLikeMirror.restCandidates(body), "Song", "Artist")
        assertEquals("spotify:track:r", match?.uri)
        assertEquals("r", SpotifyLikeMirror.trackId(match!!.uri))
    }

    @Test
    fun mutationBodyCarriesTheTrackAndHash() {
        val body = SpotifyLikeMirror.mutationBody("addToLibrary", "spotify:track:abc", "h")
        assertTrue("\"libraryItemUris\":[\"spotify:track:abc\"]" in body)
        assertTrue("\"sha256Hash\":\"h\"" in body)
    }

    @Test
    fun mutationAnswers() {
        assertIs<SpotifyLikeMirror.MutationAnswer.Done>(SpotifyLikeMirror.parseMutation("""{"data":{"addLibraryItems":{}}}"""))
        val stale = SpotifyLikeMirror.parseMutation("""{"errors":[{"message":"PersistedQueryNotFound"}]}""")
        assertIs<SpotifyLikeMirror.MutationAnswer.Failed>(stale)
        assertTrue(stale.staleHash)
    }

    @Test
    fun mutationHashesAreReadOffThePlayerToo() {
        val hash = "a".repeat(64)
        val script = """x=new o.l("addToLibrary","mutation","$hash",null)"""
        assertEquals(hash, SpotifyCanvasQuery.findQueryHash(script, "addToLibrary"))
        val hashes = SpotifyCanvasQuery.QueryHashes(
            fetch = { url ->
                if (url == "https://open.spotify.com/") "https://open.spotifycdn.com/cdn/build/web-player/web-player.1.js"
                else script
            },
            operation = "addToLibrary",
            fallback = SpotifyLikeMirror.KNOWN_ADD_HASH,
        )
        assertEquals(hash, hashes.hash())
    }
}
