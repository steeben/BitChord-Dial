package com.music.bitchord.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/** The green the tick flashes as something is saved. It never rests on it. */
private val SaveGreen = Color(0xFF30D158)
private val SaveGreenBright = Color(0xFF8CF0A8)

/** How long the plus takes to fold into a tick, and back. */
private const val MORPH_MS = 420

/** The same 24-unit grid and weight as BitChordIcons' Plus and Check. */
private const val GRID = 24f
private const val STROKE = 2.2f

/**
 * The add-to-library glyph: a plus that folds into a tick when [saved], and
 * back when it isn't. The plus's crossbar becomes the tick's short stroke and
 * its upright swings over into the long one, with a quarter turn along the
 * way, so the one glyph is seen to become the other.
 *
 * On [animation]'s cue (a tap that saves, see [LikeAnimation.onToggle]) the
 * tick also pops and flashes green with the like heart's ring of lines, then
 * settles back to [tint]. Undoing reverses the fold with no colour.
 */
@Composable
fun LibraryCheck(
    saved: Boolean,
    animation: LikeAnimation,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    // Starts where the state is, so a page opened on a saved album shows the
    // tick outright rather than folding into it.
    val fold by animateFloatAsState(
        targetValue = if (saved) 1f else 0f,
        animationSpec = tween(MORPH_MS, easing = FastOutSlowInEasing),
        label = "libraryFold",
    )
    Box(
        modifier = modifier
            .size(size)
            .drawBehind { drawLikeBurst(animation.progress.value, SaveGreen, SaveGreenBright) }
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(size)
                .graphicsLayer {
                    // Gentler than the heart's 0 -> 1.2 -> 1: the tick is
                    // already on screen, folding, so it only swells and lands.
                    val q = animation.progress.value
                    val s = when {
                        q >= 0.5f -> 1f
                        q < 0.25f -> 1f + 0.2f * FastOutSlowInEasing.transform(segment(q, 0f, 0.25f))
                        else -> 1.2f - 0.2f * FastOutSlowInEasing.transform(segment(q, 0.25f, 0.5f))
                    }
                    scaleX = s
                    scaleY = s
                },
        ) {
            val p = animation.progress.value
            val color = if (p >= 1f) {
                tint
            } else {
                // Green as it lands, brightest at the pop, then back to the
                // ordinary ink over the last stretch.
                val bright = if (p < 0.5f) segment(p, 0.2f, 0.5f) else 1f - segment(p, 0.5f, 0.65f)
                val green = lerp(SaveGreen, SaveGreenBright, bright * 0.6f)
                // In from the ink over the first moments, so the tap doesn't cut.
                val into = lerp(tint, green, FastOutSlowInEasing.transform(segment(p, 0f, 0.12f)))
                lerp(into, tint, FastOutSlowInEasing.transform(segment(p, 0.62f, 1f)))
            }
            val unit = this.size.minDimension / GRID
            fun at(x: Float, y: Float) = Offset(x * unit, y * unit)
            val t = fold
            fun mix(a: Float, b: Float) = a + (b - a) * t
            // A quarter turn while it folds: the plus looks the same turned,
            // so neither resting state shows it.
            rotate(degrees = -90f * (1f - t)) {
                // Crossbar -> short stroke.
                drawLine(
                    color = color,
                    start = at(mix(5f, 5f), mix(12f, 12.8f)),
                    end = at(mix(19f, 9.6f), mix(12f, 17.4f)),
                    strokeWidth = STROKE * unit,
                    cap = StrokeCap.Round,
                )
                // Upright -> long stroke, pivoting down to meet the short one.
                drawLine(
                    color = color,
                    start = at(mix(12f, 9.6f), mix(19f, 17.4f)),
                    end = at(mix(12f, 19f), mix(5f, 6.9f)),
                    strokeWidth = STROKE * unit,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
