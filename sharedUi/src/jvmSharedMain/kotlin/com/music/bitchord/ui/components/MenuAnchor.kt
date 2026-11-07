package com.music.bitchord.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced

/**
 * Where the ⋮ or ⋯ that opened a menu sits, so the menu can hang off that
 * button rather than rise from the bottom of the screen.
 *
 * The counterpart to [LongPressOrigin] for a tap: a single slot, written when
 * a finger lands on the button and read by the menu's host in the click that
 * follows. Every ⋮ opens its menu through the same callback a hold does, so
 * the host asks [consume] rather than having bounds threaded through every
 * list. The timestamp keeps a press that never became a click — dragged off,
 * or a scroll that started on the button — from being claimed by a later,
 * unrelated opening.
 */
object MenuAnchor {
    private var bounds: Rect? = null
    private var recordedAt = 0L

    internal fun record(bounds: Rect) {
        this.bounds = bounds
        recordedAt = System.nanoTime()
    }

    /** The button this opening was a tap on, in window coordinates, or null. */
    fun consume(): Rect? {
        val anchor = bounds?.takeIf { System.nanoTime() - recordedAt < FRESH_NANOS }
        bounds = null
        return anchor
    }

    private const val FRESH_NANOS = 3_000_000_000L
}

/**
 * The menu that is open out of an anchor right now, morphing from it: the
 * window bounds it grew from and how far open it is, 0 to 1. The anchor reads
 * this to fade itself out while the menu covers it, so the button (or the pill
 * it sits in) reads as having become the menu, and fades back in as the menu
 * folds back into it.
 */
object MenuMorph {
    internal var source by mutableStateOf<Rect?>(null)
        private set
    internal var progress by mutableFloatStateOf(0f)
        private set

    /** The menu grown out of [from] is [open] of the way open. */
    fun publish(from: Rect, open: Float) {
        if (source != from) source = from
        progress = open
    }

    /** The menu grown out of [from] is gone. */
    fun clear(from: Rect?) {
        if (from == null || source == from) {
            source = null
            progress = 0f
        }
    }

    /** How visible an anchor at [bounds] is: gone once a menu covers it. */
    internal fun anchorAlpha(bounds: Rect): Float {
        val from = source ?: return 1f
        val same = kotlin.math.abs(from.left - bounds.left) < 2f &&
            kotlin.math.abs(from.top - bounds.top) < 2f &&
            kotlin.math.abs(from.right - bounds.right) < 2f &&
            kotlin.math.abs(from.bottom - bounds.bottom) < 2f
        return if (same) (1f - progress / ANCHOR_FADE).coerceIn(0f, 1f) else 1f
    }

    /** The share of the opening over which the anchor fades away. */
    private const val ANCHOR_FADE = 0.3f
}

/**
 * Marks this — a ⋮ or ⋯ button, or the pill of buttons a top bar menu opens
 * out of — as what a menu hangs off: its bounds go to [MenuAnchor] the moment
 * it is pressed, without taking the press from the button's own click. While
 * the menu that opened out of it is up it fades away (see [MenuMorph]).
 */
@Composable
fun Modifier.menuAnchor(): Modifier {
    val anchor = remember { AnchorCoordinates() }
    return this
        .onPlaced { anchor.coordinates = it }
        .graphicsLayer {
            // Only read while a menu is morphing, so idle rows skip the bounds.
            if (MenuMorph.source != null) {
                alpha = anchor.coordinates?.takeIf { it.isAttached }
                    ?.let { MenuMorph.anchorAlpha(it.boundsInWindow()) } ?: 1f
                // Keep the progress read so the fade follows each frame.
                MenuMorph.progress
            } else {
                alpha = 1f
            }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                anchor.coordinates?.takeIf { it.isAttached }?.let { MenuAnchor.record(it.boundsInWindow()) }
            }
        }
}

private class AnchorCoordinates {
    var coordinates: LayoutCoordinates? = null
}
