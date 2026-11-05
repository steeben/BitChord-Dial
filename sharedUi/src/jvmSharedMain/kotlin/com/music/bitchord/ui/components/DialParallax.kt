package com.music.bitchord.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * How far the phone is tilted from where it was held, each axis from -1 to 1,
 * written by the platform (the gyroscope on Android) and read while drawing,
 * so a tilt redraws the dials without recomposing them.
 */
@Stable
class DialTilt {
    /** Rolled right is positive. */
    var x by mutableFloatStateOf(0f)

    /** Top tipped away is positive. */
    var y by mutableFloatStateOf(0f)
}

/** The tilt the dials follow; null while parallax is off or nothing can sense it. */
val LocalDialTilt = staticCompositionLocalOf<DialTilt?> { null }

/** How far a tile's artwork travels at full tilt, either way. */
private val DIAL_PARALLAX_TRAVEL = 6.dp

/**
 * A dial tile's artwork, drifting against the phone's tilt inside its tile,
 * as if it sat a little way behind the glass. The art is drawn just large
 * enough to cover the tile at either end of its travel; the tile's own clip
 * keeps the overflow hidden.
 */
fun Modifier.dialParallax(): Modifier = composed {
    val tilt = LocalDialTilt.current ?: return@composed Modifier
    Modifier.graphicsLayer {
        val travel = DIAL_PARALLAX_TRAVEL.toPx()
        if (size.width > 0f && size.height > 0f) {
            scaleX = (size.width + 2f * travel) / size.width
            scaleY = (size.height + 2f * travel) / size.height
        }
        translationX = -tilt.x * travel
        translationY = tilt.y * travel
    }
}
