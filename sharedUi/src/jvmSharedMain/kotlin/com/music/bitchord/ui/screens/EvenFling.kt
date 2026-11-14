package com.music.bitchord.ui.screens

import androidx.compose.animation.core.Animation
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.DecayAnimation
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs

/*
 * A fling that moves the same distance in every frame of the same length.
 *
 * Compose gives a fling its place from the time since it began, and cuts that
 * time to whole milliseconds first. A 60 Hz display shows a frame every
 * 16.67 ms, and the cut is one part in sixteen at most. A 165 Hz display
 * shows one every 6.04 ms: 23 frames in 24 move the list 6 ms along its
 * curve and the 24th moves it 7, a step one sixth longer than the ones beside
 * it, seven times a second. A 120 Hz display has it as 8, 8, 9. A recording
 * of the phone's screen showed exactly that, with every frame on time.
 *
 * The same recording showed a second fault, at the start. The frame in which
 * the finger lifts moves the list by what was left of the drag. Compose's
 * fling then starts its clock on its first frame, so that frame is at time
 * nought and moves nothing: a list in full flight stands still for one frame
 * as it is let go. Android's own lists start the clock at the lift, and the
 * first frame is one frame along the curve.
 *
 * Here the curve is still Compose's, read at the whole milliseconds on each
 * side of the frame's real time and the place taken between them, and the
 * clock starts one frame before the first one. Springs are cut the same way
 * by Compose and are run the same way here.
 */

private const val MILLI_NANOS = 1_000_000L

/**
 * A motion's value [playNanos] after its start, from [wholeMillis], which
 * gives it at a whole number of milliseconds only.
 */
fun evenValue(playNanos: Long, wholeMillis: (Long) -> Float): Float {
    val millis = playNanos / MILLI_NANOS
    val from = wholeMillis(millis)
    val part = playNanos - millis * MILLI_NANOS
    if (part == 0L) return from
    return from + (wholeMillis(millis + 1) - from) * (part.toFloat() / MILLI_NANOS)
}

/**
 * The time from one frame to the next, as the last few had it: the shortest
 * of them, so that a frame that came late does not pass for a slower display.
 */
class FrameBeat {
    var nanos: Long = DEFAULT_NANOS
        private set
    private val gaps = LongArray(KEPT)
    private var seen = 0

    fun seen(gapNanos: Long) {
        if (gapNanos < SHORTEST_NANOS || gapNanos > LONGEST_NANOS) return
        gaps[seen % KEPT] = gapNanos
        seen++
        var least = Long.MAX_VALUE
        for (i in 0 until minOf(seen, KEPT)) least = minOf(least, gaps[i])
        nanos = least
    }

    private companion object {
        /** 120 Hz, until a frame has been seen. */
        const val DEFAULT_NANOS = 8_333_333L
        const val SHORTEST_NANOS = 2_000_000L
        const val LONGEST_NANOS = 40_000_000L
        const val KEPT = 4
    }
}

/** The display's beat as the app's own frames last had it. Read and written on the app's thread. */
internal val frameBeat = FrameBeat()

/**
 * Takes the display's beat from the first frames of each scroll of
 * [listState], so that the fling that ends the scroll knows it. A display
 * that changes its rate changes it for a touch, which is when this looks.
 */
@Composable
internal fun FrameBeatWhileScrolling(listState: LazyListState) {
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) return@collect
            var last = withFrameNanos { it }
            repeat(BEAT_FRAMES) {
                val now = withFrameNanos { it }
                frameBeat.seen(now - last)
                last = now
            }
        }
    }
}

private const val BEAT_FRAMES = 4

/**
 * Runs this motion on the frame clock, by each frame's own time. [onFrame]
 * has the value and the velocity at each frame, and says whether to go on.
 * [scale] is the system's animation scale: 0 goes straight to the end.
 */
internal suspend fun Animation<Float, AnimationVector1D>.runEven(
    scale: Float = 1f,
    onFrame: (value: Float, velocity: Float) -> Boolean,
) {
    if (scale <= 0f) {
        onFrame(targetValue, 0f)
        return
    }
    var started = false
    var start = 0L
    var last = 0L
    do {
        val more = withFrameNanos { now ->
            if (started) {
                frameBeat.seen(now - last)
            } else {
                started = true
                // One frame in: the frame before this one was the last of
                // whatever moved the list until now.
                start = now - (frameBeat.nanos * scale.toDouble()).toLong()
            }
            last = now
            val play = ((now - start) / scale.toDouble()).toLong()
            if (play >= durationNanos) {
                onFrame(targetValue, 0f)
                false
            } else {
                onFrame(
                    evenValue(play) { getValueFromNanos(it * MILLI_NANOS) },
                    getVelocityVectorFromNanos(play).value,
                )
            }
        }
    } while (more)
}

/**
 * The list's ordinary fling along [decay], in even steps. It ends where the
 * list does, and hands back the speed it still had, as Compose's own does.
 */
internal class EvenFling(private val decay: DecayAnimationSpec<Float>) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        // Under this the curve has no numbers to give.
        if (abs(initialVelocity) <= 1f) return initialVelocity
        var left = initialVelocity
        var last = 0f
        DecayAnimation(decay, Float.VectorConverter, 0f, initialVelocity).runEven { value, velocity ->
            val delta = value - last
            val consumed = scrollBy(delta)
            last = value
            left = velocity
            abs(delta - consumed) <= 0.5f
        }
        return left
    }
}
