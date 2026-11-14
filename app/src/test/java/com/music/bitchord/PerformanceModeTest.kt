package com.music.bitchord

import com.music.bitchord.data.settings.AppStyle
import com.music.bitchord.data.settings.EffectSettings
import com.music.bitchord.ui.components.DialTileStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerformanceModeTest {
    private val stock = EffectSettings(
        appStyle = AppStyle.GLASS,
        reduceAnimation = false,
        ambientBackground = true,
        dialTileStyle = DialTileStyle.GLASS,
        lyricsBlur = true,
        animatedCanvas = true,
        disableHaptics = false,
    )

    /** [stock] as a build from before the haptics joined the list kept it. */
    private val stockBeforeHaptics = stock.copy(disableHaptics = EffectSettings.LOW_COST.disableHaptics)

    @Test
    fun `what is kept reads back the same`() {
        assertEquals(stock, EffectSettings.decode(stock.encode()))
        assertEquals(EffectSettings.LOW_COST, EffectSettings.decode(EffectSettings.LOW_COST.encode()))
    }

    @Test
    fun `nothing is read from what was not written here`() {
        assertNull(EffectSettings.decode(null))
        assertNull(EffectSettings.decode(""))
        assertNull(EffectSettings.decode("GLASS;false;false"))
        assertNull(EffectSettings.decode("MARBLE;false;true;GLASS;true;true"))
        assertNull(EffectSettings.decode("GLASS;no;true;GLASS;true;true"))
        assertNull(EffectSettings.decode("v2;GLASS;false;true;GLASS;true;true"))
        assertNull(EffectSettings.decode("v2;GLASS;false;true;GLASS;true;true;perhaps"))
        assertNull(EffectSettings.decode("v3;GLASS;false;true;GLASS;true;true;false"))
    }

    @Test
    fun `the mode turns the haptics off`() {
        assertEquals(true, EffectSettings.LOW_COST.disableHaptics)
    }

    @Test
    fun `haptics that were on come back, and haptics that were off stay off`() {
        assertEquals(false, EffectSettings.restored(saved = stock, now = EffectSettings.LOW_COST).disableHaptics)
        val off = stock.copy(disableHaptics = true)
        assertEquals(true, EffectSettings.restored(saved = off, now = EffectSettings.LOW_COST).disableHaptics)
    }

    @Test
    fun `haptics turned back on while the mode was on stay on`() {
        val now = EffectSettings.LOW_COST.copy(disableHaptics = false)
        val saved = stock.copy(disableHaptics = true)
        assertEquals(false, EffectSettings.restored(saved, now).disableHaptics)
    }

    @Test
    fun `what a build without the haptics in the list kept is still read`() {
        assertEquals(stockBeforeHaptics, EffectSettings.decode("GLASS;false;true;GLASS;true;true"))
    }

    @Test
    fun `a line kept without the haptics leaves them as they are now`() {
        // That build's mode did not touch the haptics, so they are not its to
        // put back, on or off.
        val kept = EffectSettings.decode("GLASS;false;true;GLASS;true;true")!!
        assertEquals(true, EffectSettings.restored(kept, EffectSettings.LOW_COST).disableHaptics)
        val hapticsOn = EffectSettings.LOW_COST.copy(disableHaptics = false)
        assertEquals(false, EffectSettings.restored(kept, hapticsOn).disableHaptics)
    }

    @Test
    fun `what a build with the refresh rate in the list kept is still read`() {
        // Third of seven then. The refresh rate is no longer Performance
        // mode's to put back, so the field is passed over.
        assertEquals(stockBeforeHaptics, EffectSettings.decode("GLASS;false;true;true;GLASS;true;true"))
        assertEquals(stockBeforeHaptics, EffectSettings.decode("GLASS;false;false;true;GLASS;true;true"))
        assertNull(EffectSettings.decode("GLASS;false;maybe;true;GLASS;true;true"))
        assertNull(EffectSettings.decode("MARBLE;false;false;true;GLASS;true;true"))
    }

    @Test
    fun `turned off untouched, every setting goes back`() {
        assertEquals(stock, EffectSettings.restored(saved = stock, now = EffectSettings.LOW_COST))
    }

    @Test
    fun `a setting changed while the mode was on stays as it was changed`() {
        val now = EffectSettings.LOW_COST.copy(appStyle = AppStyle.FROST, animatedCanvas = true)
        val saved = stock.copy(animatedCanvas = false)
        val out = EffectSettings.restored(saved, now)
        assertEquals(AppStyle.FROST, out.appStyle)
        assertEquals(true, out.animatedCanvas)
        // The rest go back.
        assertEquals(false, out.reduceAnimation)
        assertEquals(true, out.ambientBackground)
        assertEquals(DialTileStyle.GLASS, out.dialTileStyle)
        assertEquals(true, out.lyricsBlur)
    }

    @Test
    fun `settings that were already low cost stay low cost`() {
        val saved = stock.copy(appStyle = AppStyle.SOLID, reduceAnimation = true, dialTileStyle = DialTileStyle.NONE)
        assertEquals(saved, EffectSettings.restored(saved, EffectSettings.LOW_COST))
    }
}
