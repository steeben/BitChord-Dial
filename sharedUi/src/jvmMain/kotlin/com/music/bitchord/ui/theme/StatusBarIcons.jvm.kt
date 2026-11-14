package com.music.bitchord.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

// A desktop window has no status bar to tint.
@Composable
actual fun StatusBarIcons(dark: Boolean) = Unit

// Nor bars to hide.
@Composable
actual fun HideSystemBars(hidden: Boolean) = Unit

@Composable
actual fun HideStatusBar(hidden: Boolean) = Unit

@Composable
actual fun stableStatusBarTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
