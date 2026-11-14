package com.music.bitchord.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
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
        if (statusOnly) keepStatusBarInset(view)
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

// The app's pages that have had their status bar inset pinned, so it is done
// once for each activity.
private val pinnedPages = java.util.WeakHashMap<View, Boolean>()

/**
 * Keeps the status bar's inset on the app's pages at the bar's full height,
 * whether the bar is showing or not.
 *
 * The player is a window of its own over the pages, but the status bar it
 * hides is the one bar of the screen: the pages' window lost its top inset
 * too, and everything on them that sits under the bar (the top bar, the
 * logo, the dials) went up by the bar's height as the player landed and came
 * back down as it left. The player hides them while it is up, but not during
 * the trip, which is when the jump showed.
 *
 * Done on the activity's content view, above Compose's own listener, so every
 * reader of `WindowInsets.statusBars` on the pages gets the pinned value, on
 * each step of the bar's own slide as well.
 */
private fun keepStatusBarInset(view: View) {
    var context = view.context
    while (context is android.content.ContextWrapper && context !is Activity) context = context.baseContext
    val content = (context as? Activity)?.window?.decorView?.findViewById<View>(android.R.id.content) ?: return
    if (pinnedPages.put(content, true) != null) return
    val statusBars = WindowInsetsCompat.Type.statusBars()
    var full = Insets.NONE
    fun pinned(insets: WindowInsetsCompat): WindowInsetsCompat =
        if (full == Insets.NONE) insets else WindowInsetsCompat.Builder(insets).setInsets(statusBars, full).build()
    ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
        full = insets.getInsetsIgnoringVisibility(statusBars)
        pinned(insets)
    }
    // Before Android 11 a bar does not slide its inset in steps, so there is
    // nothing more to pin.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        ViewCompat.setWindowInsetsAnimationCallback(
            content,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                ): WindowInsetsCompat = pinned(insets)
            },
        )
    }
    ViewCompat.requestApplyInsets(content)
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
