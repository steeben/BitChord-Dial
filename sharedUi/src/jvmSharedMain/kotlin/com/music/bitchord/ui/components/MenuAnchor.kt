package com.music.bitchord.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
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
 * Marks this — a ⋮ or ⋯ button — as what a menu hangs off: its bounds go to
 * [MenuAnchor] the moment it is pressed, without taking the press from the
 * button's own click.
 */
@Composable
fun Modifier.menuAnchor(): Modifier {
    val anchor = remember { AnchorCoordinates() }
    return this
        .onPlaced { anchor.coordinates = it }
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
