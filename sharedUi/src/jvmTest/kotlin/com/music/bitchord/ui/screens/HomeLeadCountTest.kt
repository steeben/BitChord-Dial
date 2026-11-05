package com.music.bitchord.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLeadCountTest {
    @Test
    fun bothDialsLead() {
        assertEquals(2, homeLeadCount(listOf(SPEED_DIAL_SHELF_TITLE, ARTIST_DIAL_SHELF_TITLE, "Recents")))
    }

    @Test
    fun oneDialLeads() {
        assertEquals(1, homeLeadCount(listOf(SPEED_DIAL_SHELF_TITLE, "Recents")))
        assertEquals(1, homeLeadCount(listOf(ARTIST_DIAL_SHELF_TITLE, "Recents")))
    }

    // With every dial off, Recents is first in the feed but is not a lead, so
    // Top Picks still goes above it.
    @Test
    fun noDialsMeansNoLead() {
        assertEquals(0, homeLeadCount(listOf("Recents", "Quick picks")))
        assertEquals(0, homeLeadCount(emptyList()))
    }
}
