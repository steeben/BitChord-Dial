package com.music.bitchord

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Home's mark (the logo and the title beside it) is drawn over the pages,
 * above the top bar, not on Home's own page. This carries Home's page
 * transition out to it, so that between tabs the mark fades and settles in
 * as part of the page, the way the Library's heading does, in place of
 * showing by itself before or after.
 */
internal object HomeMark {
    /** Whether the page transition under way is one between tabs. Set as its spec is picked. */
    var tabSwap = false

    /** Home is on its way out to another tab: the mark stays up to fade with it. */
    var leaving by mutableStateOf(false)
        private set

    private var alphaState: State<Float>? by mutableStateOf(null)
    private var scaleState: State<Float>? by mutableStateOf(null)

    /** Read while drawing. Nothing until Home's page is up to say. */
    val alpha: Float get() = alphaState?.value ?: 0f
    val scale: Float get() = scaleState?.value ?: 1f

    fun attach(alpha: State<Float>, scale: State<Float>) {
        alphaState = alpha
        scaleState = scale
    }

    fun detach(alpha: State<Float>) {
        if (alphaState === alpha) {
            alphaState = null
            scaleState = null
            leaving = false
        }
    }

    fun leaving(now: Boolean) {
        leaving = now
    }
}

/**
 * Called from Home's page, with the page's own enter and exit [transition]:
 * runs the mark through the tabs' fade through beside it. Any other way onto
 * or off Home (a page opened on it and closed again), the mark shows and
 * goes at once, as it always has.
 */
@Composable
internal fun HomeMarkFollow(
    transition: Transition<EnterExitState>,
    fadeOutMs: Int,
    fadeInMs: Int,
    fadeInScale: Float,
) {
    val swap = remember(transition.targetState) { HomeMark.tabSwap }
    val alpha = transition.animateFloat(
        transitionSpec = {
            when {
                !HomeMark.tabSwap -> snap()
                targetState == EnterExitState.Visible ->
                    tween(fadeInMs, delayMillis = fadeOutMs, easing = LinearOutSlowInEasing)
                else -> tween(fadeOutMs, easing = FastOutLinearInEasing)
            }
        },
        label = "homeMarkAlpha",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val scale = transition.animateFloat(
        transitionSpec = {
            if (HomeMark.tabSwap && targetState == EnterExitState.Visible) {
                tween(fadeInMs, delayMillis = fadeOutMs, easing = LinearOutSlowInEasing)
            } else {
                snap()
            }
        },
        label = "homeMarkScale",
    ) { if (it == EnterExitState.PreEnter && swap) fadeInScale else 1f }
    val leaving = swap && transition.targetState == EnterExitState.PostExit
    SideEffect { HomeMark.leaving(leaving) }
    DisposableEffect(alpha, scale) {
        HomeMark.attach(alpha, scale)
        onDispose { HomeMark.detach(alpha) }
    }
}
