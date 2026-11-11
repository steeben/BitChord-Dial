package com.music.bitchord.ui.components

/**
 * Where the top of a menu hung off a button goes, in px, for [IosMenuPopover].
 *
 * Over the button wherever the panel settles, so it can grow out of it: from
 * the button's top down when the button is in the upper half of the screen,
 * else as low as it fits, pulled up until it covers the button (a short panel
 * for the player's ⋯ rises from the button rather than sitting below it).
 * With no button it sits at the bottom.
 *
 * A panel keeps [top], a little under the status bar, clear above it, except
 * one hung off a button that is itself higher than that: the top bar's pill
 * on an album or playlist page. That one starts at the pill's own top, down
 * to [insetTop]. Held to [top] it sat a few dp below the pill it grew out
 * of, with the pill's upper edge cut off the window it opens through.
 */
internal fun hungMenuTop(
    anchorTop: Int?,
    anchorBottom: Int?,
    panelHeight: Int,
    insetTop: Int,
    top: Int,
    bottom: Int,
    screenHeight: Int,
): Int {
    if (anchorTop == null || anchorBottom == null) return (bottom - panelHeight).coerceAtLeast(top)
    val ceiling = anchorTop.coerceIn(minOf(insetTop, top), top)
    val lowest = (bottom - panelHeight).coerceAtLeast(ceiling)
    val hangsDown = (anchorTop + anchorBottom) / 2f < screenHeight / 2f
    val preferred = if (hangsDown) anchorTop else lowest
    val coverLow = anchorBottom - panelHeight
    return preferred.coerceIn(minOf(coverLow, anchorTop), anchorTop).coerceIn(ceiling, lowest)
}
