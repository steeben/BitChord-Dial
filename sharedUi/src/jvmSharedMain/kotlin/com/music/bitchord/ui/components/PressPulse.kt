package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlin.math.hypot
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A soft glow that swells out from the middle of a button and fades as it
 * goes, once for every press [interactionSource] reports.
 *
 * Put it ahead of any clip or glass surface in the chain: it draws behind the
 * rest of the button and is meant to spill past its edges. [radius] is where
 * the glow ends up; left null, it is the button's own half-width grown by
 * [spread]. A new press restarts the pulse rather than stacking another.
 */
@Composable
fun Modifier.pressPulse(
    interactionSource: InteractionSource,
    color: Color = Color.White,
    spread: Float = 1.45f,
    radius: Dp? = null,
    maxAlpha: Float = 0.32f,
): Modifier {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        coroutineScope {
            interactionSource.interactions.collect { interaction ->
                if (interaction is PressInteraction.Press) {
                    launch {
                        pulse.snapTo(0f)
                        pulse.animateTo(1f, tween(durationMillis = 620, easing = FastOutSlowInEasing))
                    }
                }
            }
        }
    }
    return drawBehind {
        // Read here, not in composition, so the pulse redraws without recomposing.
        val p = pulse.value
        if (p >= 1f) return@drawBehind
        val end = radius?.toPx() ?: (size.minDimension / 2f * spread)
        val r = end * (0.55f + 0.45f * p)
        // Brightest just after the press, gone by the time it reaches [end].
        val a = maxAlpha * (1f - p) * (1f - p)
        if (r <= 0f || a <= 0f) return@drawBehind
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = a),
                0.5f to color.copy(alpha = a * 0.7f),
                1f to color.copy(alpha = 0f),
                center = center,
                radius = r,
            ),
            radius = r,
            center = center,
        )
    }
}

/**
 * Play and pause as one glyph that folds from one into the other.
 *
 * The triangle is drawn as two halves split down the middle; each half is the
 * same four corners as one of the pause bars, so the change is those corners
 * travelling, with their rounding growing into the bars' capsule ends. Drawn
 * on the 960-unit grid of `ic_player_play` / `ic_player_pause`, so at rest it
 * sits where those glyphs do.
 */
@Composable
fun PlayPauseGlyph(
    isPlaying: Boolean,
    tint: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val morph by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "playPauseMorph",
    )
    val path = remember { Path() }
    Canvas(
        modifier.then(
            if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else {
                Modifier
            },
        ),
    ) {
        val scale = size.minDimension / 960f
        val dx = (size.width - 960f * scale) / 2f
        val dy = (size.height - 960f * scale) / 2f
        val t = morph.coerceIn(0f, 1f)
        path.reset()
        for (half in 0..1) {
            val play = PLAY_HALVES[half]
            val pause = PAUSE_BARS[half]
            val points = Array(4) { i ->
                Offset(
                    dx + lerp(play[i].x, pause[i].x, t) * scale,
                    dy + lerp(play[i].y, pause[i].y, t) * scale,
                )
            }
            val radii = FloatArray(4) { i ->
                lerp(PLAY_RADII[half][i], PAUSE_RADIUS, t) * scale
            }
            path.addRoundedPolygon(points, radii)
        }
        drawPath(path, tint)
    }
}

/** A closed polygon with each corner rounded by up to its own radius. */
private fun Path.addRoundedPolygon(points: Array<Offset>, radii: FloatArray) {
    val n = points.size
    for (i in 0 until n) {
        val prev = points[(i + n - 1) % n]
        val here = points[i]
        val next = points[(i + 1) % n]
        val toPrev = prev - here
        val toNext = next - here
        val lenPrev = hypot(toPrev.x, toPrev.y)
        val lenNext = hypot(toNext.x, toNext.y)
        val r = minOf(radii[i], lenPrev / 2f, lenNext / 2f)
        if (r <= 0.01f || lenPrev == 0f || lenNext == 0f) {
            if (i == 0) moveTo(here.x, here.y) else lineTo(here.x, here.y)
            continue
        }
        val a = here + toPrev * (r / lenPrev)
        val b = here + toNext * (r / lenNext)
        if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
        quadraticTo(here.x, here.y, b.x, b.y)
    }
    close()
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

// The triangle of ic_player_play: (320,233), (725,480), (320,727), split at
// x = 522.5. The apex is cut flat a little short of the point so the right
// half has four corners like the bar it becomes.
private val PLAY_HALVES = arrayOf(
    arrayOf(Offset(320f, 233f), Offset(522.5f, 356.5f), Offset(522.5f, 603.5f), Offset(320f, 727f)),
    arrayOf(Offset(522.5f, 356.5f), Offset(705f, 467.8f), Offset(705f, 492.2f), Offset(522.5f, 603.5f)),
)

// The two bars of ic_player_pause, corner for corner with the halves above.
private val PAUSE_BARS = arrayOf(
    arrayOf(Offset(285f, 240f), Offset(445f, 240f), Offset(445f, 720f), Offset(285f, 720f)),
    arrayOf(Offset(515f, 240f), Offset(675f, 240f), Offset(675f, 720f), Offset(515f, 720f)),
)

// The seam down the triangle's middle stays sharp, or it would show as notches.
private val PLAY_RADII = arrayOf(
    floatArrayOf(40f, 0f, 0f, 40f),
    floatArrayOf(0f, 14f, 14f, 0f),
)

/** Half a bar's width: capsule ends, as the pause glyph has. */
private const val PAUSE_RADIUS = 80f
