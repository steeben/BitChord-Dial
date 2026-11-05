package com.music.bitchord.ui.screens

/**
 * How far Settings' search field has shrunk toward its place in the top bar:
 * 0 at rest under the title, 1 once it has risen to the bar's row.
 *
 * The shrink runs the whole climb, so the field narrows in step with the
 * scroll rather than shrinking first and rising after. [dockScroll] is the
 * scroll offset at which the field's centre meets the bar's row.
 */
internal fun settingsSearchDockProgress(scroll: Float, dockScroll: Float): Float {
    if (dockScroll <= 0f) return if (scroll >= dockScroll) 1f else 0f
    return (scroll / dockScroll).coerceIn(0f, 1f)
}

/**
 * Where one of the field's edges sits at [progress], between where it rests
 * in the page and where the bar's slot has it. The page's field and the slot
 * share their right edge, so in practice only the left edge travels: it draws
 * in to clear the back button. The right is still interpolated, so a slot
 * that ends short of the page's edge is met rather than jumped to.
 */
internal fun settingsSearchDockEdge(rest: Float, slot: Float, progress: Float): Float =
    rest + (slot - rest) * progress.coerceIn(0f, 1f)
