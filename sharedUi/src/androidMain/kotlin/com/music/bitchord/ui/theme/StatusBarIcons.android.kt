package com.music.bitchord.ui.theme

import android.app.Activity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Draws just the status bar glyphs dark or light, leaving the navigation bar
 * exactly as the page underneath already set it.
 *
 * The player's artwork luminance is only sampled from the top of the cover,
 * under the status bar — it says nothing about the navigation bar. Driving
 * [isAppearanceLightNavigationBars] off it anyway used to also trip Android's
 * automatic nav-bar contrast scrim on light artwork, painting the transparent,
 * page-colored navigation bar solid white.
 */
@Composable
actual fun StatusBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = findWindow(view) ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = dark
    }
}

@Composable
actual fun HideSystemBars(hidden: Boolean) = BarsAway(hidden, statusOnly = false)

@Composable
actual fun HideStatusBar(hidden: Boolean) = BarsAway(hidden, statusOnly = true)

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun stableStatusBarTop(): Dp =
    WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()

// Who wants which bars away at the moment. Counted, not switched: the full
// screen video and the player's "Hide status bar" option come and go on their
// own, and one putting the bars back must not undo the other. Main thread only.
private var allBarsAway = 0
private var statusBarAway = 0
private var behaviorBefore: Int? = null

@Composable
private fun BarsAway(hidden: Boolean, statusOnly: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = findWindow(view) ?: return
    DisposableEffect(window, hidden) {
        if (!hidden) return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        if (statusOnly) statusBarAway++ else allBarsAway++
        applyBarsAway(controller)
        onDispose {
            if (statusOnly) statusBarAway-- else allBarsAway--
            applyBarsAway(controller)
        }
    }
}

private fun applyBarsAway(controller: WindowInsetsControllerCompat) {
    if (allBarsAway > 0 || statusBarAway > 0) {
        if (behaviorBefore == null) behaviorBefore = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    when {
        allBarsAway > 0 -> controller.hide(WindowInsetsCompat.Type.systemBars())
        statusBarAway > 0 -> {
            controller.show(WindowInsetsCompat.Type.navigationBars())
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
        else -> {
            controller.show(WindowInsetsCompat.Type.systemBars())
            behaviorBefore?.let { controller.systemBarsBehavior = it }
            behaviorBefore = null
        }
    }
}

// Walks up the Compose view hierarchy to find a DialogWindowProvider (e.g. modal player) before falling back to Activity context.
fun findWindow(view: android.view.View): android.view.Window? {
    var parent = view.parent
    while (parent != null) {
        if (parent is androidx.compose.ui.window.DialogWindowProvider) {
            return parent.window
        }
        parent = parent.parent
    }
    var context = view.context
    while (context is android.content.ContextWrapper) {
        if (context is Activity) {
            return context.window
        }
        context = context.baseContext
    }
    return null
}
