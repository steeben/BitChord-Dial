package com.music.bitchord.ui.screens

/**
 * How far Settings' search field has shrunk toward its place in the top bar:
 * 0 while it still sits full width in the page, 1 once it has reached the
 * bar's row and fits the gap between the back button and the actions.
 *
 * [dockScroll] is the scroll offset at which the field's centre meets the
 * bar's row, and [travelPx] is how much scrolling before that the shrink
 * takes. The width finishes over the first [WIDTH_SHARE] of that travel so
 * the field is already narrow by the time it rises past the back button.
 */
internal fun settingsSearchDockProgress(scroll: Float, dockScroll: Float, travelPx: Float): Float {
    if (travelPx <= 0f) return if (scroll >= dockScroll) 1f else 0f
    return (1f - (dockScroll - scroll) / travelPx).coerceIn(0f, 1f)
}

/** The width's share of the shrink, eased so it is done before the field docks. */
internal fun settingsSearchWidthProgress(dockProgress: Float): Float {
    val t = (dockProgress / WIDTH_SHARE).coerceIn(0f, 1f)
    // Ease out: most of the narrowing happens early, then it settles in.
    return 1f - (1f - t) * (1f - t)
}

private const val WIDTH_SHARE = 0.8f
