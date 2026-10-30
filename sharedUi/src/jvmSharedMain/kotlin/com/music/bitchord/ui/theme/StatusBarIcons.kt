package com.music.bitchord.ui.theme

import androidx.compose.runtime.Composable

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
