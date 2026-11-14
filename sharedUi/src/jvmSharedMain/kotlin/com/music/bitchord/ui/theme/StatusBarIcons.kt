package com.music.bitchord.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp

/**
 * Draws just the status bar glyphs dark or light, leaving the navigation bar
 * as the page underneath set it. Nothing on a platform without one.
 */
@Composable
expect fun StatusBarIcons(dark: Boolean)

/**
 * Takes both system bars off the window while [hidden], for a video playing
 * full screen; a swipe in from an edge shows them for a moment. Puts them back
 * when this leaves composition.
 */
@Composable
expect fun HideSystemBars(hidden: Boolean)

/**
 * Takes only the status bar off the window while [hidden], for the player's
 * "Hide status bar" option; a swipe down from the top edge shows it for a
 * moment. Puts it back when [hidden] goes false or this leaves composition,
 * unless [HideSystemBars] still holds the bars away.
 */
@Composable
expect fun HideStatusBar(hidden: Boolean)

/**
 * The status bar's height whether or not it is showing, so what is laid out
 * under it stays where it is while [HideStatusBar] takes the bar away.
 */
@Composable
expect fun stableStatusBarTop(): Dp

/**
 * Whether the page under the player draws its status bar glyphs dark, as the
 * app last worked it out from its theme and that page's banner.
 *
 * The player has the status bar for as long as its window is up, which is to
 * the very end of a close. Its own choice of glyphs is made for its artwork;
 * on the way down to the mini player that artwork is no longer under the bar,
 * the page is, so the player gives the bar the page's glyphs from here.
 */
object PageStatusBarIcons {
    var dark by mutableStateOf(false)
}
