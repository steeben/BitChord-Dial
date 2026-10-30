package com.music.bitchord.ui.theme

import androidx.compose.runtime.Composable

// A desktop window has no status bar to tint.
@Composable
actual fun StatusBarIcons(dark: Boolean) = Unit

// Nor bars to hide.
@Composable
actual fun HideSystemBars(hidden: Boolean) = Unit
