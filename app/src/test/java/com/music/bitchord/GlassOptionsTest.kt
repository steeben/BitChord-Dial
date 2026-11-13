package com.music.bitchord

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.music.bitchord.data.settings.AppStyle
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.ui.components.GlassTint
import com.music.bitchord.ui.components.PLAYER_BUTTON_GLASS_BLUR_DP
import com.music.bitchord.ui.components.PLAYER_BUTTON_GLASS_SATURATION
import com.music.bitchord.ui.components.PLAYER_BUTTON_GLASS_TRANSPARENCY
import com.music.bitchord.ui.components.LEGIBLE_LIGHT_FILL
import com.music.bitchord.ui.components.classicGlassFillColor
import com.music.bitchord.ui.components.clearGlassFillColor
import com.music.bitchord.ui.components.clearGlassStrength
import com.music.bitchord.ui.components.frostButtonFill
import com.music.bitchord.ui.components.frostPageButtonFill
import com.music.bitchord.ui.components.legibleFloor
import com.music.bitchord.ui.components.legibleOnDark
import com.music.bitchord.ui.components.legibleOnLight
import com.music.bitchord.ui.components.playerMenuDark
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
        CustomizationSettings.setClearGlassTint(GlassTint.WHITE, dark = false)
        CustomizationSettings.setClearGlassTransparency(0.25f, dark = false)
        assertEquals(20f, light.blur.value, 0f)
        assertEquals(GlassTint.WHITE, light.tint.value)
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
        assertEquals(GlassTint.WHITE, light.tint.value)
        assertEquals(0f, light.darkness.value, 0f)
    }

    @Test
    fun theGlassShipsAsAsked() {
        for (clear in listOf(true, false)) {
            CustomizationSettings.resetClearGlassOptions(dark = true)
            CustomizationSettings.resetClearGlassOptions(dark = false)
            CustomizationSettings.resetGlassOptions(dark = true)
            CustomizationSettings.resetGlassOptions(dark = false)
            val dark = CustomizationSettings.glassOptions(clear = clear, dark = true)
            val light = CustomizationSettings.glassOptions(clear = clear, dark = false)
            for (set in listOf(dark, light)) {
                assertEquals(0.9f, set.transparency.value, 0f)
                assertEquals(4f, set.blur.value, 0f)
                assertEquals(0.9f, set.saturation.value, 0f)
            }
            assertEquals(GlassTint.BLACK, dark.tint.value)
            assertEquals(0.3f, dark.darkness.value, 0f)
            assertEquals(GlassTint.WHITE, light.tint.value)
            assertEquals(0f, light.darkness.value, 0f)
        }
    }

    @Test
    fun whiteTextGetsADarkFloorAsBlackTextGetsAPaleOne() {
        val faint = Color.White.copy(alpha = 0.03f)
        val floored = faint.legibleOnDark()
        assertEquals(LEGIBLE_LIGHT_FILL, floored.alpha, 0.01f)
        assertTrue(floored.red < 0.25f)
        val dense = Color(0xFF121212).copy(alpha = 0.8f)
        assertEquals(dense, dense.legibleOnDark())
        assertEquals(0f, Color.Transparent.legibleOnDark(0f).alpha, 0f)
    }

    @Test
    fun theCausticGoesAsTheFillDoes() {
        assertEquals(1f, clearGlassStrength(0.5f), 0f)
        assertEquals(1f, clearGlassStrength(CustomizationSettings.CLEAR_GLASS_REFERENCE_TRANSPARENCY), 0f)
        assertEquals(0.5f, clearGlassStrength(0.9f), 0.001f)
        assertEquals(0f, clearGlassStrength(1f), 0f)
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

    @Test
    fun fullTransparencyLeavesNoColourWhateverTheTint() {
        val veil = Color.White.copy(alpha = 0.14f)
        val surface = Color(0xFFFAFAFA)
        for (tint in listOf(null, Color.White, Color.Black, Color(0xFFFF4F9A))) {
            assertEquals(0f, clearGlassFillColor(veil, surface, tint, darkness = 1f, transparency = 1f).alpha, 0f)
            assertEquals(0f, classicGlassFillColor(surface.copy(alpha = 0.4f), tint, darkness = 1f, transparency = 1f).alpha, 0f)
        }
    }

    @Test
    fun noTintAddsNoColourAtAnyTransparency() {
        for (transparency in listOf(0f, 0.6f, 0.8f, 1f)) {
            assertEquals(0f, clearGlassFillColor(null, null, null, darkness = 0f, transparency = transparency).alpha, 0f)
            assertEquals(0f, classicGlassFillColor(null, null, darkness = 0f, transparency = transparency).alpha, 0f)
        }
    }

    @Test
    fun theDefaultsKeepTheLookTheyShipWith() {
        val veil = Color.White.copy(alpha = 0.14f)
        val surface = Color(0xFFFAFAFA)
        val shipped = Color.Black.copy(alpha = 0.28f)
            .compositeOver(veil.compositeOver(surface.copy(alpha = 0.2f)))
        val now = clearGlassFillColor(
            veil, surface, Color.Black, darkness = 0f,
            transparency = CustomizationSettings.CLEAR_GLASS_REFERENCE_TRANSPARENCY,
        )
        assertEquals(shipped.alpha, now.alpha, 0.001f)
        assertEquals(shipped.red, now.red, 0.001f)
        val base = surface.copy(alpha = 0.4f)
        assertEquals(base, classicGlassFillColor(base, null, 0f, CustomizationSettings.GLASS_REFERENCE_TRANSPARENCY))
    }

    @Test
    fun theSongComesFirstThenWhiteAndBlack() {
        assertEquals(listOf(GlassTint.SONG, GlassTint.WHITE, GlassTint.BLACK), GlassTint.entries.take(3))
        assertEquals(0xFFFFFFFF, GlassTint.WHITE.argb)
        assertEquals(0xFF000000, GlassTint.BLACK.argb)
        assertTrue(GlassTint.entries.none { it.name in setOf("NONE", "DEFAULT", "ACCENT") })
    }

    @Test
    fun retiredTintsLandOnTheNearestOneLeft() {
        // No tint and Default were the theme's own surface: white in the light theme, black in the dark.
        for (old in listOf("NONE", "DEFAULT")) {
            assertEquals(GlassTint.WHITE, GlassTint.fromSaved(old, dark = false))
            assertEquals(GlassTint.BLACK, GlassTint.fromSaved(old, dark = true))
        }
        // Accent was the theme's primary: black on light, white on dark.
        assertEquals(GlassTint.BLACK, GlassTint.fromSaved("ACCENT", dark = false))
        assertEquals(GlassTint.WHITE, GlassTint.fromSaved("ACCENT", dark = true))
        assertEquals(GlassTint.PINK, GlassTint.fromSaved("PINK", dark = true))
        assertEquals(null, GlassTint.fromSaved(null, dark = true))
    }

    @Test
    fun theMenuFloorIsGoneAtFullTransparency() {
        val shipped = CustomizationSettings.CLEAR_GLASS_REFERENCE_TRANSPARENCY
        assertEquals(LEGIBLE_LIGHT_FILL, legibleFloor(shipped, shipped), 0f)
        assertEquals(LEGIBLE_LIGHT_FILL, legibleFloor(0.2f, shipped), 0f)
        assertEquals(0f, legibleFloor(1f, shipped), 0f)
        // No floor: a clear fill stays clear.
        assertEquals(0f, Color.Transparent.legibleOnLight(legibleFloor(1f, shipped)).alpha, 0f)
    }

    @Test
    fun `a frosted button on dark ground is a pale lift, not the dense dark fill`() {
        val dense = Color(0xFF0D0D0F).copy(alpha = 0.65f)
        val fill = frostButtonFill(dark = true)
        // Over a mid-dark sleeve it comes out brighter than the sleeve, where
        // the dense fill came out darker.
        val sleeve = Color(0xFF3A3A22)
        assertTrue(fill.compositeOver(sleeve).luminance() > sleeve.luminance())
        assertTrue(dense.compositeOver(sleeve).luminance() < sleeve.luminance())
    }

    @Test
    fun `the player's glass buttons carry no colour whatever the options are`() {
        // The dark theme's stock options: a black tint, darkened.
        val classic = classicGlassFillColor(
            base = Color(0xFF121212).copy(alpha = 0.4f),
            tint = Color.Black,
            darkness = 0.3f,
            transparency = PLAYER_BUTTON_GLASS_TRANSPARENCY,
        )
        val clear = clearGlassFillColor(
            veil = Color.White.copy(alpha = 0.03f),
            themeSurface = Color(0xFF121212),
            tint = Color.Black,
            darkness = 0.3f,
            transparency = PLAYER_BUTTON_GLASS_TRANSPARENCY,
        )
        assertEquals(0f, classic.alpha, 0.0001f)
        assertEquals(0f, clear.alpha, 0.0001f)
        assertEquals(0f, clearGlassStrength(PLAYER_BUTTON_GLASS_TRANSPARENCY), 0.0001f)
        assertEquals(10f, PLAYER_BUTTON_GLASS_BLUR_DP, 0.0001f)
        assertEquals(1f, PLAYER_BUTTON_GLASS_SATURATION, 0.0001f)
    }

    @Test
    fun `a frosted button on bright ground is a veil, not the dense light fill`() {
        val dense = Color(0xFFF7F7F9).copy(alpha = 0.6f)
        assertTrue(frostButtonFill(dark = false).alpha < dense.alpha)
        assertEquals(frostPageButtonFill(dark = false), frostButtonFill(dark = false))
    }

    @Test
    fun `a frosted button on a scrimmed sleeve is the scrimmed sleeve lifted`() {
        // A bright sleeve under the player's dark scrim: the button's backdrop
        // holds the sleeve alone, so the scrim has to come with the fill.
        val sleeve = Color(0xFFE0E0E0)
        val scrim = Color.Black.copy(alpha = 0.56f)
        val ground = scrim.compositeOver(sleeve)
        val bare = frostButtonFill(dark = true).compositeOver(sleeve)
        val button = frostButtonFill(dark = true, underlay = scrim).compositeOver(sleeve)
        // Still a lift over the ground it is on, but a slight one, where
        // without the scrim it was the bright sleeve showing through.
        assertTrue(button.luminance() > ground.luminance())
        assertTrue(button.luminance() - ground.luminance() < 0.1f)
        assertTrue(bare.luminance() - ground.luminance() > 0.4f)
        // No underlay leaves the plain lift.
        assertEquals(frostButtonFill(dark = true), frostButtonFill(dark = true, underlay = Color.Transparent))
    }

    @Test
    fun `a frosted button on a page shows the page through it in either theme`() {
        // The bars' fills, which left a header button reading as a solid disc.
        val denseDark = Color(0xFF0D0D0F).copy(alpha = 0.65f)
        val denseLight = Color(0xFFF7F7F9).copy(alpha = 0.6f)
        assertTrue(frostPageButtonFill(dark = true).alpha < denseDark.alpha / 2f)
        assertTrue(frostPageButtonFill(dark = false).alpha < denseLight.alpha)
        // A veil of white both ways: it lifts a dark page and pales a light one.
        val page = Color(0xFF3A3A22)
        assertTrue(frostPageButtonFill(dark = true).compositeOver(page).luminance() > page.luminance())
    }

    @Test
    fun `the player's menu is dark in the dark theme whatever the style or sleeve`() {
        for (style in AppStyle.entries) {
            for (bright in listOf(true, false)) {
                assertTrue(playerMenuDark(appThemeDark = true, style = style, sleeveBright = bright))
            }
        }
    }

    @Test
    fun `the player's menu is light in the light theme in Frost and Solid`() {
        for (style in listOf(AppStyle.FROST, AppStyle.SOLID)) {
            for (bright in listOf(true, false)) {
                assertFalse(playerMenuDark(appThemeDark = false, style = style, sleeveBright = bright))
            }
        }
    }

    @Test
    fun `liquid glass in the light theme goes by the sleeve under the menu`() {
        assertFalse(playerMenuDark(appThemeDark = false, style = AppStyle.GLASS, sleeveBright = true))
        assertTrue(playerMenuDark(appThemeDark = false, style = AppStyle.GLASS, sleeveBright = false))
    }
}
