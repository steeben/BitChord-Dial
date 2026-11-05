package com.music.bitchord

import com.music.bitchord.ui.components.HideOnScrollConnection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HideOnScrollConnectionTest {
    @Test
    fun hidesAfterScrollingDownPastTheThreshold() {
        val c = HideOnScrollConnection(thresholdPx = 24f)
        c.update(consumedY = -10f, availableY = 0f)
        assertFalse(c.hidden)
        c.update(consumedY = -20f, availableY = 0f)
        assertTrue(c.hidden)
    }

    @Test
    fun comesBackOnScrollingUp() {
        val c = HideOnScrollConnection(thresholdPx = 24f)
        c.update(-100f, 0f)
        c.update(10f, 0f)
        assertTrue(c.hidden)
        c.update(20f, 0f)
        assertFalse(c.hidden)
    }

    @Test
    fun directionChangeRestartsTheCount() {
        val c = HideOnScrollConnection(thresholdPx = 24f)
        c.update(20f, 0f)
        c.update(-20f, 0f)
        assertFalse(c.hidden)
    }

    @Test
    fun pullingPastTheTopShowsItAndResetClears() {
        val c = HideOnScrollConnection(thresholdPx = 24f)
        c.update(-100f, 0f)
        c.update(0f, 5f)
        assertFalse(c.hidden)
        c.update(-100f, 0f)
        c.reset()
        assertFalse(c.hidden)
    }
}
