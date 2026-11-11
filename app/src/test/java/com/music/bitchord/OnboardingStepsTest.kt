package com.music.bitchord

import com.music.bitchord.data.OnboardingStep
import com.music.bitchord.data.TourTarget
import com.music.bitchord.data.settings.AppStyle
import com.music.bitchord.data.settings.TapRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStepsTest {

    @Test
    fun theTourRunsFromTheDialToTheLibraryAndEnds() {
        val seen = generateSequence(OnboardingStep.WELCOME) { it.next() }.toList()
        assertEquals(OnboardingStep.entries, seen)
        assertEquals(OnboardingStep.FINISH, seen.last())
        assertNull(OnboardingStep.FINISH.next())
        // The dial and its menu before the library, as the tour is described.
        assertTrue(OnboardingStep.DIAL_MENU.ordinal < OnboardingStep.LIBRARY.ordinal)
        assertTrue(OnboardingStep.MIXES.ordinal < OnboardingStep.DIAL_MENU.ordinal)
    }

    @Test
    fun nothingGoesBackToTheWelcome() {
        assertNull(OnboardingStep.WELCOME.previous())
        assertNull(OnboardingStep.DIAL.previous())
        assertEquals(OnboardingStep.DIAL, OnboardingStep.PINS.previous())
    }

    @Test
    fun stepsAreNumberedWithoutTheWelcome() {
        assertEquals(1, OnboardingStep.DIAL.number)
        assertEquals(OnboardingStep.count, OnboardingStep.FINISH.number)
    }

    @Test
    fun onlyTheLibraryStepIsOverTheLibrary() {
        assertEquals(listOf(OnboardingStep.LIBRARY), OnboardingStep.entries.filter { it.onLibrary })
        assertEquals(TourTarget.LIBRARY_MENU, OnboardingStep.LIBRARY.target)
        assertEquals(TourTarget.DIAL_MENU, OnboardingStep.DIAL_MENU.target)
    }

    @Test
    fun theWelcomeOffersEveryStyleTheDeviceCanDraw() {
        assertEquals(AppStyle.entries, AppStyle.choices(glassSupported = true))
        assertEquals(listOf(AppStyle.FROST, AppStyle.SOLID), AppStyle.choices(glassSupported = false))
        // What is chosen by default is among them, glass or not.
        assertTrue(AppStyle.of(liquidGlass = true, reduceDynamicBlur = false) in AppStyle.choices(true))
        assertTrue(AppStyle.of(liquidGlass = false, reduceDynamicBlur = false) in AppStyle.choices(false))
    }

    @Test
    fun tenTapsInARowUnlockAndAPauseStartsOver() {
        val run = TapRun(needed = 10, gapMs = 1_000)
        var now = 0L
        repeat(9) { assertFalse(run.tap(now)); now += 100 }
        assertTrue(run.tap(now))
        // The count starts again after a run.
        repeat(9) { now += 100; assertFalse(run.tap(now)) }
        // A pause: the next tap is the first of a new run, not the tenth of the old.
        now += 5_000
        assertFalse(run.tap(now))
        repeat(8) { now += 100; assertFalse(run.tap(now)) }
        now += 100
        assertTrue(run.tap(now))
    }
}
