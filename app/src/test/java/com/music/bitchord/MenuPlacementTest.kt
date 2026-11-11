package com.music.bitchord

import com.music.bitchord.ui.components.hungMenuTop
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuPlacementTest {
    private val insetTop = 100
    private val top = 124
    private val bottom = 2300
    private val screen = 2400

    private fun place(anchorTop: Int?, anchorBottom: Int?, panelHeight: Int) = hungMenuTop(
        anchorTop = anchorTop,
        anchorBottom = anchorBottom,
        panelHeight = panelHeight,
        insetTop = insetTop,
        top = top,
        bottom = bottom,
        screenHeight = screen,
    )

    @Test
    fun `a menu off the top bar's pill starts at the pill's top`() {
        // The pill sits 6px under the status bar, above the usual clearance.
        assertEquals(106, place(anchorTop = 106, anchorBottom = 250, panelHeight = 900))
    }

    @Test
    fun `a menu never goes under the status bar`() {
        assertEquals(insetTop, place(anchorTop = 40, anchorBottom = 184, panelHeight = 900))
    }

    @Test
    fun `a menu off a button further down hangs from the button's top`() {
        assertEquals(600, place(anchorTop = 600, anchorBottom = 700, panelHeight = 900))
    }

    @Test
    fun `a menu too tall to hang from its button is pushed up to fit`() {
        assertEquals(bottom - 1500, place(anchorTop = 1000, anchorBottom = 1100, panelHeight = 1500))
    }

    @Test
    fun `a short menu off a low button rises to cover it`() {
        // As low as it fits would leave it below the button: it is pulled up.
        assertEquals(1800, place(anchorTop = 1800, anchorBottom = 1900, panelHeight = 300))
    }

    @Test
    fun `a menu with no button sits at the bottom`() {
        assertEquals(bottom - 900, place(anchorTop = null, anchorBottom = null, panelHeight = 900))
    }
}
