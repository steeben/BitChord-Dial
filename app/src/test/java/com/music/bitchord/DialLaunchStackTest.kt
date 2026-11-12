package com.music.bitchord

import com.music.bitchord.ui.screens.Launched
import com.music.bitchord.ui.screens.cardLaunchKey
import com.music.bitchord.ui.screens.launchBetween
import com.music.bitchord.ui.screens.stackSettledOn
import com.music.bitchord.ui.screens.stackWith
import com.music.bitchord.ui.screens.stackWithout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which page a launched tile's page opens from and closes onto, one launch on top of another. */
class DialLaunchStackTest {
    private data class Page(override val key: String, override val parent: String) : Launched

    private val home = "tab:0"
    private val explore = "tab:1"
    private val mood = Page("mood:chill|", parent = explore)
    private val playlist = Page("playlist", parent = mood.key)

    @Test
    fun aTilesPageOpensFromAndClosesOntoThePageItsTileIsOn() {
        val stack = stackWith(emptyList(), Page("album", parent = home))
        assertEquals("album", launchBetween(stack, home, "album")?.key)
        assertEquals("album", launchBetween(stack, "album", home)?.key)
        // Reached from anywhere else, it is an ordinary page change.
        assertNull(launchBetween(stack, explore, "album"))
        assertNull(launchBetween(stack, "album", "artist"))
    }

    @Test
    fun aCardOnALaunchedPageStacksOnIt() {
        val stack = stackWith(stackWith(emptyList(), mood), playlist)
        assertEquals(listOf(mood, playlist), stack)
        // The playlist closes onto the mood's page, the mood's page onto Explore.
        assertEquals(playlist, launchBetween(stack, playlist.key, mood.key))
        assertEquals(mood, launchBetween(stack, mood.key, explore))
        assertNull(launchBetween(stack, playlist.key, explore))
    }

    @Test
    fun backOnAPageOnlyWhatClosedOntoItIsLetGoOf() {
        val stack = listOf(mood, playlist)
        assertEquals(listOf(mood), stackSettledOn(stack, mood.key))
        assertTrue(stackSettledOn(stack, explore).isEmpty())
        // Arriving on the page just opened lets go of nothing.
        assertEquals(stack, stackSettledOn(stack, playlist.key))
    }

    @Test
    fun aTileOpenedFromElsewhereStartsOver() {
        val album = Page("album", parent = home)
        val artist = Page("artist", parent = home)
        assertEquals(listOf(artist), stackWith(listOf(album), artist))
        // A second card on the mood's page replaces the first, over the mood.
        val other = Page("other", parent = mood.key)
        assertEquals(listOf(mood, other), stackWith(listOf(mood, playlist), other))
        assertTrue(stackWith(listOf(mood, playlist), null).isEmpty())
    }

    @Test
    fun droppingALaunchDropsWhatWasOpenedFromItsPage() {
        assertTrue(stackWithout(listOf(mood, playlist), mood).isEmpty())
        assertEquals(listOf(mood), stackWithout(listOf(mood, playlist), playlist))
        assertEquals(listOf(mood), stackWithout(listOf(mood), playlist))
    }

    @Test
    fun aCardThatOpensAPageLaunchesItAndOneThatPlaysDoesNot() {
        assertEquals("MPREb_album", cardLaunchKey("MPREb_album", null))
        // A track on a shelf plays where it is, whatever album it carries.
        assertNull(cardLaunchKey("MPREb_album", "videoId"))
        assertNull(cardLaunchKey(null, null))
    }

    @Test
    fun whatIsKeptOnTheDeviceUnderACoverLaunchesAndItsFoldersDoNot() {
        // The Library's own albums and artists, and the device's playlists.
        assertEquals("local:group:album:Blue|Joni", cardLaunchKey("local:group:album:Blue|Joni", null))
        assertEquals("local:playlist:PL1", cardLaunchKey("local:playlist:PL1", null))
        assertEquals("local:offline:7", cardLaunchKey("local:offline:7", null))
        // Folders are drawn, not pictured: nothing for a page to grow out of.
        assertNull(cardLaunchKey("local:downloads", null))
        assertNull(cardLaunchKey("local:all", null))
        assertNull(cardLaunchKey("local:library", null))
    }
}
