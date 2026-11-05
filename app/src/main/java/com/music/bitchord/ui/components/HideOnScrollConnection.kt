package com.music.bitchord.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Watches a page's scroll for which way it is going, so a floating control
 * (the library's shuffle button) can tuck away while the list is read
 * downwards and come back as soon as it is scrolled back up.
 *
 * It only listens: nothing is consumed, so the page and any other connection
 * on the chain see the scroll as before. A little travel in one direction is
 * needed before it flips, so a jittery finger doesn't flicker the button.
 */
class HideOnScrollConnection(private val thresholdPx: Float = 24f) : NestedScrollConnection {
    /** True while the page is being scrolled down, through its content. */
    var hidden by mutableStateOf(false)
        private set

    // Distance moved in the current direction: negative down the page, positive up.
    private var travel = 0f

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        update(consumed.y, available.y)
        return Offset.Zero
    }

    /**
     * [consumedY] is what the page scrolled by (negative moving further down
     * it); [availableY] is what was left over, positive when pulling past the
     * top, which always brings the control back.
     */
    fun update(consumedY: Float, availableY: Float) {
        when {
            availableY > 0f -> {
                travel = 0f
                hidden = false
            }
            consumedY < 0f -> {
                travel = travel.coerceAtMost(0f) + consumedY
                if (travel < -thresholdPx) hidden = true
            }
            consumedY > 0f -> {
                travel = travel.coerceAtLeast(0f) + consumedY
                if (travel > thresholdPx) hidden = false
            }
        }
    }

    /** Shows the control again, as a new page comes up. */
    fun reset() {
        travel = 0f
        hidden = false
    }
}
