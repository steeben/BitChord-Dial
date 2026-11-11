package com.music.bitchord.ui.screens

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How far along its way back to its tile a closing page is, [t] (0 to 1) of
 * the way through the close: 0 at full size, 1 at its tile, and a little over
 * 1 while it is past the tile (smaller than it) and on its way back out.
 *
 * A spring let go of, as a phone's own app closing onto its icon is: it gathers
 * speed from a standstill rather than leaving at full pace, comes down to the
 * tile, dips [CLOSE_DIP] or so under it and eases back out, with no second
 * swing to speak of. Every part of the move runs into the next, where a
 * bezier's overshoot had a corner in it.
 */
fun closeCurve(t: Float): Float {
    if (t <= 0f) return 0f
    if (t >= 1f) return 1f
    // What is left of the swing when the time is up, taken out evenly along
    // the way so the close ends on the tile exactly.
    return spring(t) - t * (spring(1f) - 1f)
}

/** A damped spring's travel from 0 towards 1, [t] in the close's own time. */
private fun spring(t: Float): Float {
    val damped = CLOSE_PULL * sqrt(1f - CLOSE_DAMPING * CLOSE_DAMPING)
    val swing = cos(damped * t) + CLOSE_DAMPING / sqrt(1f - CLOSE_DAMPING * CLOSE_DAMPING) * sin(damped * t)
    return 1f - exp(-CLOSE_DAMPING * CLOSE_PULL * t) * swing
}

/** How much of its swing the spring loses on the way: 1 would not bounce at all. */
private const val CLOSE_DAMPING = 0.7f

/** How hard the spring pulls, against the close's length: the tile is reached about 47% of the way through. */
private const val CLOSE_PULL = 7f

/** How far under its tile's size the close dips at the bottom of its bounce (near 63% of the way through). */
val CLOSE_DIP: Float = exp(-CLOSE_DAMPING * PI.toFloat() / sqrt(1f - CLOSE_DAMPING * CLOSE_DAMPING))

/**
 * Where one edge of a launching page is at [progress]: at the [tile]'s edge at
 * 0, the [page]'s at 1, and in between on the way. Below 0 (the close's
 * bounce) the tile is a little smaller about its [centre], and the edge is
 * drawn in towards it by as much.
 *
 * The same holds for an edge of what can be seen of a tile that is partly off
 * its page ([tile] is then that edge): the page's window is cut where the tile
 * is, so it closes onto the part in view at the tile's own proportions.
 */
fun launchEdge(tile: Float, page: Float, centre: Float, progress: Float): Float =
    if (progress >= 0f) tile + (page - tile) * progress else centre + (tile - centre) * (1f + progress)
