package com.music.bitchord

import androidx.compose.ui.graphics.Color
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.components.FROSTED_CARD_VEIL
import com.music.bitchord.ui.components.frostedCardVeil
import com.music.bitchord.ui.components.splashDarkFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashThemeTest {
    @Test
    fun theSplashFollowsTheAppsThemeNotThePhones() {
        assertFalse(splashDarkFor(ThemeMode.LIGHT, phoneDark = true))
        assertTrue(splashDarkFor(ThemeMode.DARK, phoneDark = false))
        assertTrue(splashDarkFor(ThemeMode.SYSTEM, phoneDark = true))
        assertFalse(splashDarkFor(ThemeMode.SYSTEM, phoneDark = false))
    }

    @Test
    fun settingsCardsDarkenInTheDarkThemeAndLightenInTheLight() {
        assertEquals(Color.Black.copy(alpha = FROSTED_CARD_VEIL), frostedCardVeil(dark = true))
        assertEquals(Color.White.copy(alpha = FROSTED_CARD_VEIL), frostedCardVeil(dark = false))
        assertEquals(0.35f, FROSTED_CARD_VEIL, 0f)
    }
}
