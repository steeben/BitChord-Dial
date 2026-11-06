package com.music.bitchord.data.canvas

import com.music.bitchord.data.canvas.AnimatedArtKind.ANIMATED_COVER
import com.music.bitchord.data.canvas.AnimatedArtKind.CANVAS
import com.music.bitchord.data.canvas.AnimatedArtKind.MUSIC_VIDEO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimatedArtPrefsTest {

    @Test
    fun clipOrderFollowsPriorityAndSkipsDisabledAndVideo() {
        val prefs = AnimatedArtPrefs(order = listOf(MUSIC_VIDEO, CANVAS, ANIMATED_COVER))
        assertEquals(listOf(CANVAS, ANIMATED_COVER), prefs.clipOrder)
        assertTrue(prefs.canvasFirst)
        assertEquals(listOf(ANIMATED_COVER), prefs.withEnabled(CANVAS, false).clipOrder)
    }

    @Test
    fun videoRankedFirstWinsWithoutWaiting() {
        val prefs = AnimatedArtPrefs(order = listOf(MUSIC_VIDEO, ANIMATED_COVER, CANVAS))
        assertTrue(prefs.videoOutranks(clip = null, clipSettled = false))
        assertTrue(prefs.videoOutranks(clip = ANIMATED_COVER, clipSettled = true))
    }

    @Test
    fun videoInTheMiddleBeatsOnlyWhatIsBelowIt() {
        val prefs = AnimatedArtPrefs(order = listOf(ANIMATED_COVER, MUSIC_VIDEO, CANVAS))
        assertFalse(prefs.videoOutranks(clip = ANIMATED_COVER, clipSettled = true))
        assertTrue(prefs.videoOutranks(clip = CANVAS, clipSettled = true))
        assertFalse(prefs.videoOutranks(clip = null, clipSettled = false))
        assertTrue(prefs.videoOutranks(clip = null, clipSettled = true))
    }

    @Test
    fun disabledKindAboveVideoDoesNotHoldItBack() {
        val prefs = AnimatedArtPrefs(order = listOf(ANIMATED_COVER, MUSIC_VIDEO, CANVAS))
            .withEnabled(ANIMATED_COVER, false)
        assertTrue(prefs.videoOutranks(clip = null, clipSettled = false))
    }

    @Test
    fun disabledVideoNeverWins() {
        val prefs = AnimatedArtPrefs().withEnabled(MUSIC_VIDEO, false)
        assertFalse(prefs.videoOutranks(clip = null, clipSettled = true))
    }

    @Test
    fun moveClampsAtTheEnds() {
        val prefs = AnimatedArtPrefs()
        assertEquals(listOf(CANVAS, ANIMATED_COVER, MUSIC_VIDEO), prefs.move(CANVAS, -1).order)
        assertEquals(prefs, prefs.move(ANIMATED_COVER, -1))
        assertEquals(prefs, prefs.move(MUSIC_VIDEO, 1))
    }

    @Test
    fun storedFormsRoundTripAndHealGaps() {
        val prefs = AnimatedArtPrefs(order = listOf(CANVAS, MUSIC_VIDEO, ANIMATED_COVER))
            .withEnabled(MUSIC_VIDEO, false)
        assertEquals(prefs.order, AnimatedArtPrefs.decodeOrder(prefs.encodeOrder()))
        assertEquals(prefs.enabled, AnimatedArtPrefs.decodeEnabled(prefs.encodeEnabled()))
        assertEquals(listOf(CANVAS, ANIMATED_COVER, MUSIC_VIDEO), AnimatedArtPrefs.decodeOrder("CANVAS,BOGUS"))
        assertEquals(AnimatedArtKind.entries.toSet(), AnimatedArtPrefs.decodeEnabled(null))
        assertEquals(emptySet(), AnimatedArtPrefs.decodeEnabled(""))
    }

    @Test
    fun clipKindComesFromItsSource() {
        assertEquals(CANVAS, CanvasArtwork(url = "a", source = CanvasSource.SPOTIFY).kind)
        assertEquals(ANIMATED_COVER, CanvasArtwork(url = "a").kind)
        assertEquals(MUSIC_VIDEO, CanvasArtwork(url = "a", source = CanvasSource.SPOTIFY, musicVideoId = "v").kind)
    }
}
