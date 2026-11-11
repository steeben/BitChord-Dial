package com.music.bitchord

import com.music.bitchord.ui.graphics.ColorUtils
import com.music.bitchord.ui.screens.mixTagGround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MixSourceTagTest {
    private fun hsl(argb: Int) = FloatArray(3).also { ColorUtils.colorToHSL(argb, it) }
    private fun whiteContrast(argb: Int) = 1.05 / (ColorUtils.calculateLuminance(argb) + 0.05)

    private val covers = listOf(
        0xFFFFD36B, 0xFFFF5FA2, 0xFF7A2CFF, 0xFF141A28, 0xFFF4EFE6, 0xFF1FB3A6,
        0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFFFFFFFF, 0xFF000000, 0xFF808080,
    ).map { it.toInt() }

    @Test
    fun theWhiteMarkReadsOnEveryCover() {
        for (cover in covers) {
            val ground = mixTagGround(cover)
            assertTrue("contrast on ${Integer.toHexString(cover)}", whiteContrast(ground) >= 4.5)
        }
    }

    @Test
    fun theGroundIsOpaqueDarkAndNeverNeon() {
        for (cover in covers) {
            val ground = mixTagGround(cover)
            assertEquals(0xFF, ground ushr 24)
            val (_, saturation, lightness) = hsl(ground)
            assertTrue("saturation of ${Integer.toHexString(cover)}", saturation <= 0.62f)
            assertTrue("lightness of ${Integer.toHexString(cover)}", lightness in 0.16f..0.34f)
        }
    }

    @Test
    fun theGroundKeepsTheCoversHue() {
        for (cover in listOf(0xFFFF5FA2, 0xFF7A2CFF, 0xFF1FB3A6, 0xFFFF9A3D).map { it.toInt() }) {
            val from = hsl(cover)[0]
            val to = hsl(mixTagGround(cover))[0]
            val apart = abs(from - to).let { minOf(it, 360f - it) }
            assertTrue("hue of ${Integer.toHexString(cover)} moved by $apart", apart < 6f)
        }
    }

    @Test
    fun aGreyCoverStaysGreyAndABlackOneIsLifted() {
        assertTrue(hsl(mixTagGround(0xFF808080.toInt()))[1] < 0.02f)
        assertTrue(hsl(mixTagGround(0xFF000000.toInt()))[2] >= 0.16f)
    }
}
