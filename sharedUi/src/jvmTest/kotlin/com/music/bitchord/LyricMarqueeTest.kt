package com.music.bitchord

import com.music.bitchord.ui.player.lineMarqueeOffset
import com.music.bitchord.ui.player.panelFillAt
import com.music.bitchord.ui.player.wordMarqueeOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricMarqueeTest {
    // Six words, each 100px wide, sung a second apart; the strip shows 300px.
    private val starts = LongArray(6) { 1_000L * (it + 1) }
    private val rights = FloatArray(6) { 100f * (it + 1) }
    private fun at(positionMs: Long) =
        wordMarqueeOffset(starts, 7_000, rights, positionMs, viewport = 300f, width = 600f)

    @Test fun lineThatFitsDoesNotMove() {
        assertEquals(0f, wordMarqueeOffset(starts, 7_000, rights, 3_500, viewport = 600f, width = 600f), 0f)
        assertEquals(0f, lineMarqueeOffset(0, 4_000, 2_000, viewport = 600f, width = 500f), 0f)
    }

    @Test fun sungWordKeepsTwoWordsAheadInView() {
        // As word i starts, word i + 2 ends at the strip's right edge or inside it.
        for (word in 0 until 6) {
            val offset = at(starts[word])
            val ahead = minOf(word + 2, 5)
            assertTrue("word $word", rights[ahead] - offset <= 300f + 0.01f)
            // And the sung word itself has not gone off the left.
            assertTrue("word $word", rights[word] - offset > 0f)
        }
    }

    @Test fun glidesBetweenWordsAndNeverGoesBack() {
        assertEquals(0f, at(0), 0f)
        assertEquals(0f, at(1_000), 0f)
        assertEquals(50f, at(1_500), 0.01f)
        assertEquals(100f, at(2_000), 0.01f)
        var last = 0f
        for (ms in 0L..8_000L step 50) {
            val offset = at(ms)
            assertTrue(offset >= last)
            last = offset
        }
        assertEquals(300f, last, 0f)
    }

    @Test fun untimedLineCrossesOverItsDuration() {
        fun at(ms: Long) = lineMarqueeOffset(10_000, 14_000, ms, viewport = 300f, width = 500f)
        assertEquals(0f, at(10_000), 0f)
        assertEquals(0f, at(10_200), 0f)
        assertEquals(100f, at(11_920), 0.5f)
        assertEquals(200f, at(13_520), 0.01f)
        assertEquals(200f, at(20_000), 0f)
    }

    @Test fun pillKeepsItsOwnLookForTheFirstOfTheWay() {
        assertEquals(0f, panelFillAt(0f, 0.12f), 0f)
        assertEquals(0f, panelFillAt(0.05f, 0.12f), 0f)
        assertTrue(panelFillAt(0.1f, 0.12f) in 0.2f..0.5f)
        assertEquals(1f, panelFillAt(0.2f, 0.12f), 0f)
    }
}
