package com.music.bitchord.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSearchDockTest {
    @Test
    fun fullWidthUntilTheShrinkBegins() {
        assertEquals(0f, settingsSearchDockProgress(scroll = 0f, dockScroll = 300f, travelPx = 150f), 0f)
        assertEquals(0f, settingsSearchDockProgress(scroll = 150f, dockScroll = 300f, travelPx = 150f), 0f)
    }

    @Test
    fun shrinksAlongTheTravelAndHoldsOnceDocked() {
        assertEquals(0.5f, settingsSearchDockProgress(scroll = 225f, dockScroll = 300f, travelPx = 150f), 0.0001f)
        assertEquals(1f, settingsSearchDockProgress(scroll = 300f, dockScroll = 300f, travelPx = 150f), 0f)
        assertEquals(1f, settingsSearchDockProgress(scroll = 900f, dockScroll = 300f, travelPx = 150f), 0f)
    }

    @Test
    fun widthIsDoneBeforeTheFieldDocks() {
        assertEquals(0f, settingsSearchWidthProgress(0f), 0f)
        assertEquals(1f, settingsSearchWidthProgress(0.8f), 0.0001f)
        assertEquals(1f, settingsSearchWidthProgress(1f), 0f)
    }
}
