package com.music.bitchord

import com.music.bitchord.ui.screens.KeptPages
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeptPageTest {

    @Test
    fun `a page never asked for is not built off stage`() {
        assertFalse(KeptPages.away(entered = false, leaving = false, arriving = false, placeDone = null))
    }

    @Test
    fun `the page on screen is in its place`() {
        assertFalse(KeptPages.away(entered = true, leaving = true, arriving = true, placeDone = false))
    }

    @Test
    fun `a page on its way out stays in its place until it is out`() {
        assertFalse(KeptPages.away(entered = true, leaving = true, arriving = false, placeDone = false))
        assertTrue(KeptPages.away(entered = true, leaving = true, arriving = false, placeDone = true))
    }

    @Test
    fun `a page whose place was taken away before the change was over is off stage`() {
        // A tab's fade out is over well before the next tab's fade in.
        assertTrue(KeptPages.away(entered = true, leaving = true, arriving = false, placeDone = null))
    }

    @Test
    fun `with another page up the page is off stage`() {
        assertTrue(KeptPages.away(entered = true, leaving = false, arriving = false, placeDone = null))
        assertTrue(KeptPages.away(entered = true, leaving = false, arriving = false, placeDone = true))
        // A back swipe let go of early: its place is still there, and about to go.
        assertTrue(KeptPages.away(entered = true, leaving = false, arriving = false, placeDone = false))
    }

    @Test
    fun `a page coming back is in its place from the first frame`() {
        // Under a back swipe too, where its old place's way out is long over.
        assertFalse(KeptPages.away(entered = true, leaving = false, arriving = true, placeDone = true))
        assertFalse(KeptPages.away(entered = true, leaving = false, arriving = true, placeDone = null))
    }
}
