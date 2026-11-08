package com.music.bitchord.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/**
 * How far the glow style's bar row leans with the finger, in px: a little with every drag,
 * and a rubber-band stretch when the drag runs past either end. Shared between the bar, which
 * drives it, and the row around it — timestamps included — which is drawn shifted by it.
 */
@Stable
class GlowLean {
    internal val offset = Animatable(0f)

    /** Read in a graphics layer, so the lean redraws the row without recomposing it. */
    val value: Float get() = offset.value

    internal suspend fun follow(px: Float) = offset.snapTo(px)

    /** Let go: springs back through centre with a small overshoot, like the stretch it undoes. */
    internal suspend fun release() = offset.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 380f))
}

/**
 * The lean for a drag that has moved [dx] px and would put the playhead at [raw] (unclamped)
 * on a bar [width] px wide. Inside the bar it follows the finger a touch; past an end it
 * stretches, ever more stiffly, toward [maxStretch].
 */
internal fun glowLeanFor(dx: Float, raw: Float, width: Float, follow: Float, maxStretch: Float): Float {
    val past = when {
        raw < 0f -> raw * width
        raw > 1f -> (raw - 1f) * width
        else -> 0f
    }
    val drift = (dx * GLOW_LEAN_FOLLOW).coerceIn(-follow, follow)
    val stretch = sign(past) * maxStretch * (1f - exp(-abs(past) / (maxStretch * 3f)))
    return drift + stretch
}

private const val GLOW_LEAN_FOLLOW = 0.03f

/** The glow style's resting and held line: a solid pill, thicker than the hairline styles. */
internal val GLOW_LINE_IDLE = 4.5.dp
internal val GLOW_LINE_ACTIVE = 7.dp
internal val GLOW_LEAN_FOLLOW_MAX = 4.dp
internal val GLOW_STRETCH_MAX = 14.dp

/**
 * The glow style's bar, after the media player on OnePlus phones: a slim line whose played part
 * runs from a pale tint of the song's accent into white at the playhead, with a soft comet of
 * light trailing back from the playhead just above the line. The light breathes while the
 * music plays and steps aside while a finger holds the bar, which thickens and brightens.
 */
@Composable
internal fun GlowTrack(
    value: Float,
    lineHeight: Dp,
    dragging: Boolean,
    fillFactor: () -> Float,
    mixPulse: MixPulse?,
    transitionWindow: ClosedFloatingPointRange<Float>?,
    accents: WaveAccents,
    playing: Boolean,
    animate: Boolean,
    inactiveColor: Color,
    markerColor: Color,
) {
    val flare by animateFloatAsState(
        // Out while a finger holds the bar, and while the song is paused: the light is the
        // music moving, so it fades away with it and comes back when it plays.
        targetValue = if (dragging || !playing) 0f else 1f,
        animationSpec = tween(
            durationMillis = when {
                dragging -> 160
                !playing -> 700
                else -> 420
            },
            easing = FastOutSlowInEasing,
        ),
        label = "glowFlare",
    )
    val held by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "glowHeld",
    )
    // A slow swell of the light while the song moves; paused, it settles full and the clock stops.
    val breath = remember { Animatable(1f) }
    LaunchedEffect(playing, animate) {
        if (playing && animate) {
            breath.animateTo(
                GLOW_BREATH_LOW,
                infiniteRepeatable(tween(GLOW_BREATH_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            )
        } else {
            breath.animateTo(1f, tween(400))
        }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(GLOW_CANVAS_HEIGHT),
    ) {
        val line = lineHeight.toPx()
        val cy = size.height / 2f
        val top = cy - line / 2f
        val radius = CornerRadius(line / 2f)
        val width = size.width
        drawRoundRect(
            color = inactiveColor.copy(alpha = inactiveColor.alpha * 0.65f),
            topLeft = Offset(0f, top),
            size = Size(width, line),
            cornerRadius = radius,
        )
        transitionWindow?.let { window ->
            val from = width * window.start.coerceIn(0f, 1f)
            val to = width * window.endInclusive.coerceIn(0f, 1f)
            if (to > from) {
                drawRoundRect(
                    color = markerColor,
                    topLeft = Offset(from, top),
                    size = Size(to - from, line),
                    cornerRadius = radius,
                )
            }
        }
        val factor = fillFactor()
        val cover = if (dragging) 0f else mixPulse?.cover ?: 0f
        val base = value.coerceIn(0f, 1f)
        val played = (width * (base + (1f - base) * cover) * factor)
            .coerceAtLeast(line * factor)
            .coerceAtMost(width)
        if (played <= 0f) return@Canvas
        val alpha = if (dragging) 1f else mixPulse?.alpha(1f) ?: 1f

        // The song's colour along the played part, turning to white over the last stretch
        // before the playhead. Held, the whole fill lifts a little toward white.
        val tint = lerp(lerp(accents.accent, accents.light, 0.4f), Color.White, 0.25f * held)
        val whiteRun = GLOW_WHITE_RUN.toPx()
        val solidWhite = GLOW_WHITE_HEAD.toPx()
        val whiteFrom = (1f - whiteRun / played).coerceIn(0f, 1f)
        val whiteTo = (1f - solidWhite / played).coerceIn(whiteFrom, 1f)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to tint.copy(alpha = alpha),
                    whiteFrom to tint.copy(alpha = alpha),
                    whiteTo to Color.White.copy(alpha = alpha),
                    1f to Color.White.copy(alpha = alpha),
                ),
                startX = 0f,
                endX = played,
            ),
            topLeft = Offset(0f, top),
            size = Size(played, line),
            cornerRadius = radius,
        )

        // The light: a soft cone of white lying along the line, pointed at the playhead and
        // widening back over the played part, above and below alike. Built from a run of
        // teardrops that all reach the playhead, each centred further back, taller and fainter
        // than the last, so the light gathers brightest by the playhead, fans out behind it
        // and has no edge anywhere. Early in the song it shrinks with the fill so it never
        // spills past the bar's start.
        val strength = flare * breath.value * factor * alpha
        if (strength > 0f) {
            val step = GLOW_STEP.toPx()
            val reach = (played / (step * GLOW_LAYERS)).coerceIn(0f, 1f)
            val glowColor = lerp(tint, Color.White, 0.75f)
            val base = GLOW_SPREAD_BASE.toPx()
            for (i in 0 until GLOW_LAYERS) {
                val back = step * (i + 1) * reach
                teardrop(
                    center = Offset(played - back, cy),
                    front = back,
                    back = back * 0.5f,
                    ry = base + back * GLOW_SPREAD_SLOPE,
                    color = glowColor,
                    alpha = GLOW_LAYER_ALPHA * (1f - 0.65f * i / (GLOW_LAYERS - 1)) * strength,
                )
            }
        }
    }
}

/**
 * A soft light shaped like a teardrop lying on its side: a radial falloff around [center],
 * stretched to [front] px ahead of it and [back] px behind it, [ry] px tall either side. The
 * two halves meet on the centre line with the same vertical profile, so there's no seam.
 */
private fun DrawScope.teardrop(center: Offset, front: Float, back: Float, ry: Float, color: Color, alpha: Float) {
    if (ry <= 0f) return
    val brush = Brush.radialGradient(
        colorStops = arrayOf(
            0f to color.copy(alpha = alpha),
            0.3f to color.copy(alpha = alpha * 0.55f),
            0.65f to color.copy(alpha = alpha * 0.15f),
            1f to color.copy(alpha = 0f),
        ),
        center = center,
        radius = ry,
    )
    if (back > 0f) {
        clipRect(left = center.x - back - 1f, top = center.y - ry, right = center.x, bottom = center.y + ry) {
            scale(scaleX = back / ry, scaleY = 1f, pivot = center) {
                drawCircle(brush = brush, radius = ry, center = center)
            }
        }
    }
    if (front > 0f) {
        clipRect(left = center.x, top = center.y - ry, right = center.x + front + 1f, bottom = center.y + ry) {
            scale(scaleX = front / ry, scaleY = 1f, pivot = center) {
                drawCircle(brush = brush, radius = ry, center = center)
            }
        }
    }
}

private val GLOW_CANVAS_HEIGHT = 30.dp
/** How far back from the playhead the fill starts turning white, and how much is pure white. */
private val GLOW_WHITE_RUN = 52.dp
private val GLOW_WHITE_HEAD = 12.dp
/** The light is [GLOW_LAYERS] teardrops, centred every [GLOW_STEP] back from the playhead. */
private const val GLOW_LAYERS = 7
private val GLOW_STEP = 10.dp
/** Each teardrop's half-height: a base plus this share of its distance behind the playhead. */
private val GLOW_SPREAD_BASE = 2.dp
private const val GLOW_SPREAD_SLOPE = 0.22f
private const val GLOW_LAYER_ALPHA = 0.34f
private const val GLOW_BREATH_LOW = 0.82f
private const val GLOW_BREATH_MS = 1600
