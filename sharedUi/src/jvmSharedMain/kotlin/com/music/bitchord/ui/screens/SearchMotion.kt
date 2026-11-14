package com.music.bitchord.ui.screens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.ui.player.PlayerPlatform
import kotlinx.coroutines.channels.Channel

/**
 * The search page's motion, held for as long as the page is up. The rules
 * are [SearchEntrance]'s; this is the clock they are read off and the two
 * things a row does besides coming in: slide to a new place when the rows
 * above it come or go, and fade away when it leaves.
 *
 * One clock for the whole list rather than an animation per row: a single
 * frame loop that runs only while a row is still coming in, read where each
 * row is drawn. A frame of it redraws those rows' layers and nothing else;
 * nothing is composed or measured again.
 */
@Stable
internal class SearchMotion {
    /** False with Reduce animations on: every row is in place at once and nothing slides. */
    var enabled: Boolean = true

    /** The clock, in milliseconds. It only moves while a row is coming in. */
    var nowMs by mutableLongStateOf(0L)
        private set

    private var run: SearchEntrance.Run? = null
    private var busyUntilMs = 0L
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** A list has arrived: the rows new to it come in at [pace]. */
    fun arrive(pace: SearchEntrance.Pace) {
        run = if (enabled) SearchEntrance.Run(clock(), pace) else null
    }

    /** Asked once by each row as it is first drawn: its part in the arrival, or null when it has none. */
    fun join(): Entry? {
        val run = run ?: return null
        val startAt = run.join(clock()) ?: return null
        busyUntilMs = maxOf(busyUntilMs, startAt + run.pace.durationMs)
        wake.trySend(Unit)
        return Entry(startAt, run.pace)
    }

    /** Runs the clock for as long as the page is up; idle between arrivals. */
    suspend fun keepTime() {
        for (ignored in wake) {
            while (true) {
                val now = clock()
                nowMs = now
                if (now >= busyUntilMs) break
                withFrameNanos { }
            }
        }
    }

    /** One row's part in an arrival. */
    class Entry(val startAtMs: Long, val pace: SearchEntrance.Pace) {
        /** Set once the row is in; it then stops reading the clock. */
        var settled = false
    }

    private fun clock(): Long = System.nanoTime() / 1_000_000
}

/** The page's [SearchMotion], with its clock running. */
@Composable
internal fun rememberSearchMotion(): SearchMotion {
    val motion = remember { SearchMotion() }
    val reduceAnimation by PlayerPlatform.host.settings.reduceAnimation.collectAsStateWithLifecycle()
    motion.enabled = !reduceAnimation
    LaunchedEffect(motion) { motion.keepTime() }
    return motion
}

/** Quick to set off and long to settle, as the pages' own entrance is. */
private val EntranceEasing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

/** A row sliding to a new place: quick, and it does not swing past where it is going. */
private val RowPlacement = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 380f, visibilityThreshold = IntOffset(1, 1))

/** How long a row that appears outside an arrival takes to fade in. */
private const val ROW_FADE_IN_MS = 200

/** How long a row that leaves takes to fade out: short, so it is not read under what replaces it. */
private const val ROW_FADE_OUT_MS = 120

/**
 * One item of the search list.
 *
 * It slides when the rows above it come or go, and fades out when it leaves.
 * When it is new to a list that has just arrived it comes in in its turn,
 * top to bottom ([joins]); a row that appears at any other time fades in
 * where it stands. [leaveAfterMs] holds its fade out back, for placeholders
 * that give way to the rows arriving over them one after another.
 */
@Composable
internal fun LazyItemScope.SearchItem(
    motion: SearchMotion,
    joins: Boolean = true,
    leaveAfterMs: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    val moves = remember { motion.enabled }
    if (!moves) {
        Column(content = content)
        return
    }
    val entry = remember { if (joins) motion.join() else null }
    val fadeIn: FiniteAnimationSpec<Float>? = if (entry == null) tween(ROW_FADE_IN_MS) else null
    val placed = Modifier.animateItem(
        fadeInSpec = fadeIn,
        placementSpec = RowPlacement,
        fadeOutSpec = tween(ROW_FADE_OUT_MS, delayMillis = leaveAfterMs),
    )
    if (entry == null) {
        Column(modifier = placed, content = content)
        return
    }
    val rise = with(LocalDensity.current) { entry.pace.riseDp.dp.toPx() }
    Column(
        modifier = placed.graphicsLayer {
            if (entry.settled) return@graphicsLayer
            val fraction = SearchEntrance.fraction(entry.startAtMs, entry.pace.durationMs, motion.nowMs)
            if (fraction >= 1f) {
                entry.settled = true
            } else {
                val shown = EntranceEasing.transform(fraction)
                // Opaque well before it has stopped moving, so the settling is seen.
                alpha = (shown * 1.6f).coerceAtMost(1f)
                translationY = (1f - shown) * rise
            }
        },
        content = content,
    )
}
