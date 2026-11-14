package com.music.bitchord

import com.music.bitchord.ui.performance.refreshRateRefused
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshRateRefusedTest {

    @Test
    fun `a display that runs its everyday rate for a request of its top rate has turned it down`() {
        assertTrue(refreshRateRefused(askedHz = 165, runningHz = 120.00001f))
        assertTrue(refreshRateRefused(askedHz = 120, runningHz = 60f))
    }

    @Test
    fun `a display at the rate asked for has taken the request`() {
        assertFalse(refreshRateRefused(askedHz = 165, runningHz = 165f))
        assertFalse(refreshRateRefused(askedHz = 120, runningHz = 120.00001f))
    }

    @Test
    fun `a rate that is not a round number is the rate it rounds to`() {
        assertFalse(refreshRateRefused(askedHz = 120, runningHz = 119.88f))
        assertFalse(refreshRateRefused(askedHz = 60, runningHz = 59.94f))
    }

    @Test
    fun `a display faster than asked has not turned anything down`() {
        assertFalse(refreshRateRefused(askedHz = 120, runningHz = 165f))
    }

    @Test
    fun `a display that gives no rate is not judged`() {
        assertFalse(refreshRateRefused(askedHz = 165, runningHz = 0f))
        assertFalse(refreshRateRefused(askedHz = 0, runningHz = 120f))
    }
}
