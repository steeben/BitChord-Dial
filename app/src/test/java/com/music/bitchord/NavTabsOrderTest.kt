package com.music.bitchord

import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.data.settings.NavPage
import com.music.bitchord.data.settings.speedDialId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bar's pages as they are read back from what was saved, and where the Podcasts page lands in them. */
class NavTabsOrderTest {

    private fun read(stored: String?): List<Pair<NavPage, Boolean>> =
        CustomizationSettings.readOrdered(stored, NavPage.pillPages, NavPage.defaultPill.toSet()) { name ->
            NavPage.entries.firstOrNull { it.name == name }
        }.map { it.item to it.enabled }

    private fun shown(stored: String?) = read(stored).filter { it.second }.map { it.first }

    @After
    fun reset() {
        CustomizationSettings.setPodcastDialPins(emptyList())
        CustomizationSettings.setSpeedDialPins(emptyList())
    }

    @Test
    fun nothingSaved_showsHomeExplorePodcastsLibrary() {
        assertEquals(listOf(NavPage.HOME, NavPage.EXPLORE, NavPage.PODCASTS, NavPage.LIBRARY), shown(null))
        assertEquals(NavPage.PLAYLISTS to false, read(null).last())
    }

    @Test
    fun orderSavedBeforeThereWasAPodcastsPage_getsItBeforeLibrary() {
        assertEquals(
            listOf(NavPage.HOME, NavPage.EXPLORE, NavPage.PODCASTS, NavPage.LIBRARY),
            shown("HOME:1,EXPLORE:1,LIBRARY:1,PLAYLISTS:0"),
        )
        // With Explore switched off, and Playlists on: still ahead of Library.
        assertEquals(
            listOf(NavPage.HOME, NavPage.PODCASTS, NavPage.LIBRARY, NavPage.PLAYLISTS),
            shown("HOME:1,EXPLORE:0,LIBRARY:1,PLAYLISTS:1"),
        )
    }

    @Test
    fun aSavedChoiceForPodcastsIsKept() {
        assertEquals(
            listOf(NavPage.HOME, NavPage.LIBRARY, NavPage.EXPLORE),
            shown("HOME:1,LIBRARY:1,PODCASTS:0,EXPLORE:1,PLAYLISTS:0"),
        )
        assertEquals(
            listOf(NavPage.PODCASTS, NavPage.HOME),
            shown("PODCASTS:1,HOME:1,EXPLORE:0,LIBRARY:0,PLAYLISTS:0"),
        )
    }

    @Test
    fun aPageTheSavedOrderLacks_withNothingAfterIt_goesLast() {
        assertEquals(
            listOf(NavPage.HOME, NavPage.EXPLORE, NavPage.PODCASTS, NavPage.LIBRARY, NavPage.PLAYLISTS),
            read("HOME:1,EXPLORE:1,PODCASTS:1,LIBRARY:1").map { it.first },
        )
    }

    @Test
    fun aPodcastShowIsPinnedToThePodcastsDial_notTheSpeedDial() {
        val show = ShelfItem("A show", "Publisher", null, null, "MPSPPL123")

        assertTrue(CustomizationSettings.toggleSpeedDial(show))
        assertEquals(listOf("MPSPPL123"), CustomizationSettings.podcastDialPins.value.map { it.speedDialId })
        assertTrue(CustomizationSettings.speedDialPins.value.isEmpty())
        assertTrue(CustomizationSettings.isPinnedToSpeedDial("MPSPPL123"))

        assertFalse(CustomizationSettings.toggleSpeedDial(show))
        assertTrue(CustomizationSettings.podcastDialPins.value.isEmpty())
        assertFalse(CustomizationSettings.isPinnedToSpeedDial("MPSPPL123"))
    }

    @Test
    fun aShowPinnedToTheSpeedDialBefore_comesOffTheSpeedDial() {
        val show = ShelfItem("A show", "Publisher", null, null, "MPSPPL456")
        CustomizationSettings.setSpeedDialPins(listOf(show))

        assertFalse(CustomizationSettings.toggleSpeedDial(show))
        assertTrue(CustomizationSettings.speedDialPins.value.isEmpty())
        assertTrue(CustomizationSettings.podcastDialPins.value.isEmpty())
    }
}
