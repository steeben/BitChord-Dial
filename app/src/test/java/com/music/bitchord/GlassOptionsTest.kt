package com.music.bitchord

import androidx.compose.ui.graphics.Color
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.ui.components.GlassTint
import com.music.bitchord.ui.components.LEGIBLE_LIGHT_FILL
import com.music.bitchord.ui.components.legibleOnLight
import com.music.bitchord.ui.screens.collagePageIsDark
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassOptionsTest {
    @After
    fun putBack() {
        for (dark in listOf(true, false)) {
            CustomizationSettings.resetGlassOptions(dark)
            CustomizationSettings.resetClearGlassOptions(dark)
        }
    }

    @Test
    fun eachThemeAndStyleHasItsOwnSet() {
        val sets = listOf(
            CustomizationSettings.glassOptions(clear = false, dark = true),
            CustomizationSettings.glassOptions(clear = false, dark = false),
            CustomizationSettings.glassOptions(clear = true, dark = true),
            CustomizationSettings.glassOptions(clear = true, dark = false),
        )
        for (i in sets.indices) for (j in i + 1 until sets.size) {
            assertNotSame(sets[i].transparency, sets[j].transparency)
            assertNotSame(sets[i].tint, sets[j].tint)
        }
    }

    @Test
    fun aLightThemeChangeLeavesTheDarkThemeAlone() {
        val dark = CustomizationSettings.glassOptions(clear = true, dark = true)
        val light = CustomizationSettings.glassOptions(clear = true, dark = false)
        val darkBlur = dark.blur.value
        CustomizationSettings.setClearGlassBlur(20f, dark = false)
        CustomizationSettings.setClearGlassTint(GlassTint.DEFAULT, dark = false)
        CustomizationSettings.setClearGlassTransparency(0.25f, dark = false)
        assertEquals(20f, light.blur.value, 0f)
        assertEquals(GlassTint.DEFAULT, light.tint.value)
        assertEquals(0.25f, light.transparency.value, 0f)
        assertEquals(darkBlur, dark.blur.value, 0f)
        assertEquals(CustomizationSettings.DEFAULT_CLEAR_GLASS_TINT, dark.tint.value)
        assertEquals(CustomizationSettings.DEFAULT_CLEAR_GLASS_TRANSPARENCY, dark.transparency.value, 0f)
    }

    @Test
    fun aDarkThemeChangeLeavesTheLightThemeAlone() {
        val dark = CustomizationSettings.glassOptions(clear = false, dark = true)
        val light = CustomizationSettings.glassOptions(clear = false, dark = false)
        CustomizationSettings.setGlassTint(GlassTint.PINK)
        CustomizationSettings.setGlassTintDarkness(0.5f)
        assertEquals(GlassTint.PINK, dark.tint.value)
        assertEquals(0.5f, dark.darkness.value, 0f)
        assertEquals(GlassTint.DEFAULT, light.tint.value)
        assertEquals(0f, light.darkness.value, 0f)
    }

    @Test
    fun resetPutsBackOnlyTheThemeAsked() {
        CustomizationSettings.setClearGlassBlur(12f, dark = true)
        CustomizationSettings.setClearGlassBlur(14f, dark = false)
        CustomizationSettings.setClearGlassTintDarkness(0.9f, dark = false)
        CustomizationSettings.resetClearGlassOptions(dark = false)
        val light = CustomizationSettings.glassOptions(clear = true, dark = false)
        assertEquals(CustomizationSettings.DEFAULT_CLEAR_GLASS_BLUR, light.blur.value, 0f)
        assertEquals(CustomizationSettings.DEFAULT_CLEAR_GLASS_TINT_DARKNESS_LIGHT, light.darkness.value, 0f)
        assertEquals(12f, CustomizationSettings.glassOptions(clear = true, dark = true).blur.value, 0f)
    }

    @Test
    fun aFaintFillIsBroughtUpToTheLegibleFloor() {
        val veil = Color.White.copy(alpha = 0.14f)
        assertEquals(LEGIBLE_LIGHT_FILL, veil.legibleOnLight().alpha, 0.01f)
        assertEquals(LEGIBLE_LIGHT_FILL, Color.Transparent.legibleOnLight().alpha, 0.01f)
        // Light enough for the light theme's black text.
        assertTrue(Color.Black.copy(alpha = 0.1f).legibleOnLight().red > 0.75f)
    }

    @Test
    fun aDenseFillIsLeftAsItIs() {
        val dense = Color(0xFFF7F7F9).copy(alpha = 0.8f)
        assertEquals(dense, dense.legibleOnLight())
    }

    @Test
    fun aCollagePageIsDarkByItsTextNotItsWash() {
        // The light theme's palette: black text, whatever its wash measures.
        assertFalse(collagePageIsDark(Color.Black))
        assertTrue(collagePageIsDark(Color.White))
    }
}
