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

/** The glow style's resting and held line, thinner than the plain capsule like the original. */
internal val GLOW_LINE_IDLE = 3.dp
internal val GLOW_LINE_ACTIVE = 6.dp
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
        targetValue = if (dragging) 0f else 1f,
        animationSpec = tween(durationMillis = if (dragging) 160 else 420, easing = FastOutSlowInEasing),
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
            color = inactiveColor,
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

        // Pale accent at the start, white for the last stretch before the playhead. Held, the
        // whole fill lifts toward white.
        val tint = lerp(accents.light, Color.White, 0.15f + 0.35f * held)
        val whiteRun = GLOW_WHITE_RUN.toPx()
        val whiteFrom = (1f - whiteRun / played).coerceIn(0f, 1f)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to tint.copy(alpha = alpha),
                    whiteFrom to tint.copy(alpha = alpha),
                    1f to Color.White.copy(alpha = alpha),
                ),
                startX = 0f,
                endX = played,
            ),
            topLeft = Offset(0f, top),
            size = Size(played, line),
            cornerRadius = radius,
        )

        // The comet: a long faint tail and a bright core, both stopping at the playhead and
        // sitting a little above the line, faded in over the first few dp of the song so it
        // never hangs off the bar's start.
        val strength = flare * breath.value * factor * alpha *
            (played / GLOW_FADE_IN.toPx()).coerceIn(0f, 1f)
        if (strength > 0f) {
            clipRect(left = 0f, top = 0f, right = played + line / 2f, bottom = size.height) {
                glowBlob(
                    center = Offset(played - GLOW_TAIL_BACK.toPx(), cy - GLOW_TAIL_LIFT.toPx()),
                    rx = GLOW_TAIL_RX.toPx(),
                    ry = GLOW_TAIL_RY.toPx(),
                    color = lerp(accents.light, Color.White, 0.45f),
                    alpha = 0.5f * strength,
                )
                glowBlob(
                    center = Offset(played - GLOW_CORE_BACK.toPx(), cy - GLOW_CORE_LIFT.toPx()),
                    rx = GLOW_CORE_RX.toPx(),
                    ry = GLOW_CORE_RY.toPx(),
                    color = Color.White,
                    alpha = 0.8f * strength,
                )
            }
            // A small bloom on the playhead itself, where the light is brightest.
            glowBlob(
                center = Offset(played - line / 2f, cy),
                rx = GLOW_HEAD_R.toPx(),
                ry = GLOW_HEAD_R.toPx(),
                color = Color.White,
                alpha = 0.45f * strength,
            )
        }
    }
}

/** A soft elliptical light: a radial falloff drawn as a circle and stretched sideways. */
private fun DrawScope.glowBlob(center: Offset, rx: Float, ry: Float, color: Color, alpha: Float) {
    if (rx <= 0f || ry <= 0f) return
    scale(scaleX = rx / ry, scaleY = 1f, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to color.copy(alpha = alpha),
                    0.4f to color.copy(alpha = alpha * 0.45f),
                    1f to color.copy(alpha = 0f),
                ),
                center = center,
                radius = ry,
            ),
            radius = ry,
            center = center,
        )
    }
}

private val GLOW_CANVAS_HEIGHT = 30.dp
private val GLOW_WHITE_RUN = 72.dp
private val GLOW_FADE_IN = 24.dp
private val GLOW_TAIL_BACK = 40.dp
private val GLOW_TAIL_LIFT = 5.dp
private val GLOW_TAIL_RX = 52.dp
private val GLOW_TAIL_RY = 9.dp
private val GLOW_CORE_BACK = 12.dp
private val GLOW_CORE_LIFT = 2.5.dp
private val GLOW_CORE_RX = 20.dp
private val GLOW_CORE_RY = 4.dp
private val GLOW_HEAD_R = 5.dp
private const val GLOW_BREATH_LOW = 0.72f
private const val GLOW_BREATH_MS = 1600
