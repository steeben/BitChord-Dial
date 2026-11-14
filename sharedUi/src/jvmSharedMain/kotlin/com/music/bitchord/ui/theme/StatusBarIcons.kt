package com.music.bitchord.ui.theme

import androidx.compose.runtime.Composable
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
