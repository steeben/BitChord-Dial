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
 *
 * Over a bright sleeve, where the player's ink has gone dark ([LocalPlayerInkDark]), white
 * light has nothing to show against: the played part is then the ink's own colour, the same
 * as the controls around it, blending through the song's bright accent into white at the
 * playhead, under a white light (see [glowBarColors]).
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
    // The white end of the fill is lit by the music too: paused, it fades into the fill's
    // colour along with the light, and comes back when the song plays (or a finger holds it).
    val lit by animateFloatAsState(
        targetValue = if (playing || dragging) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "glowLit",
    )
    // How far the bar has gone over to its dark-ink colours, on the ink's own clock.
    val inkDark by animateFloatAsState(
        targetValue = if (LocalPlayerInkDark.current) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "glowInkDark",
    )
    val ink = PlayerInk
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
        // before the playhead. Held, the whole fill lifts a little toward white. Under
        // dark ink: the ink's colour, through the song's bright accent, into white, over
        // a longer stretch so the three run into one another with no band between them.
        val colors = glowBarColors(accents, ink, inkDark, held, lit)
        val tint = colors.fill
        val head = colors.head
        val whiteRun = (GLOW_WHITE_RUN + (GLOW_INK_RUN - GLOW_WHITE_RUN) * inkDark).toPx()
        val solidWhite = (GLOW_WHITE_HEAD + (GLOW_INK_HEAD - GLOW_WHITE_HEAD) * inkDark).toPx()
        val whiteFrom = (1f - whiteRun / played).coerceIn(0f, 1f)
        val whiteTo = (1f - solidWhite / played).coerceIn(whiteFrom, 1f)
        val stops = if (inkDark <= 0f) {
            arrayOf(
                0f to tint.copy(alpha = alpha),
                whiteFrom to tint.copy(alpha = alpha),
                whiteTo to head.copy(alpha = alpha),
                1f to head.copy(alpha = alpha),
            )
        } else {
            arrayOf(
                0f to tint.copy(alpha = alpha),
                whiteFrom to tint.copy(alpha = alpha),
                whiteFrom + (whiteTo - whiteFrom) * GLOW_INK_ACCENT_AT to colors.mid.copy(alpha = alpha),
                whiteTo to head.copy(alpha = alpha),
                1f to head.copy(alpha = alpha),
            )
        }
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colorStops = stops,
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
            val glowColor = colors.light
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
 * The glow bar's colours: the played [fill], its [head] at the playhead, the colour it
 * passes through between the two ([mid]), and the [light].
 */
internal data class GlowBarColors(val fill: Color, val head: Color, val mid: Color, val light: Color)

/**
 * The glow bar's colours for [accents], with the player's [ink] and how far that ink has
 * gone dark ([inkDark], 0 to 1). [held] is how far a finger has the bar, [lit] how far the
 * music is lighting its playhead end.
 *
 * Under light ink (0) the fill is a pale tint of the accent that runs into white at the
 * playhead, under a white light. Under dark ink (1) the fill is the ink itself, so the bar
 * is the colour of the controls around it; on its way to the white playhead end it passes
 * through that same pale tint of the accent ([GlowBarColors.mid]). The light there is
 * plain white: it lies over the white playhead end, and in the accent's colour it tinted
 * that end, so the bar no longer ran evenly into white.
 */
internal fun glowBarColors(
    accents: WaveAccents,
    ink: Color,
    inkDark: Float,
    held: Float,
    lit: Float,
): GlowBarColors {
    val bright = lerp(accents.accent, accents.light, 0.4f)
    if (inkDark <= 0f) {
        // Light ink: the bar exactly as it always was.
        val fill = lerp(bright, Color.White, 0.25f * held)
        val head = lerp(fill, Color.White, lit)
        return GlowBarColors(fill, head, lerp(fill, head, 0.5f), lerp(fill, Color.White, 0.75f))
    }
    // What a held bar lifts toward: white, or the bright accent under dark ink.
    val lift = lerp(Color.White, bright, inkDark)
    val fill = lerp(lerp(bright, ink.copy(alpha = 1f), inkDark), lift, 0.25f * held)
    val head = lerp(fill, Color.White, lit)
    return GlowBarColors(
        fill = fill,
        head = head,
        // Paused, the accent and the white both settle back into the fill.
        mid = lerp(lerp(fill, head, 0.5f), lerp(fill, bright, lit), inkDark),
        light = lerp(lerp(fill, Color.White, 0.75f), Color.White, inkDark),
    )
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
/**
 * The same under dark ink: a longer run, since it holds two blends (ink to accent, accent
 * to white), with the accent [GLOW_INK_ACCENT_AT] of the way along it and only the very
 * end pure white.
 */
private val GLOW_INK_RUN = 120.dp
private val GLOW_INK_HEAD = 3.dp
private const val GLOW_INK_ACCENT_AT = 0.55f
/** The light is [GLOW_LAYERS] teardrops, centred every [GLOW_STEP] back from the playhead. */
private const val GLOW_LAYERS = 7
private val GLOW_STEP = 10.dp
/** Each teardrop's half-height: a base plus this share of its distance behind the playhead. */
private val GLOW_SPREAD_BASE = 2.dp
private const val GLOW_SPREAD_SLOPE = 0.22f
private const val GLOW_LAYER_ALPHA = 0.34f
private const val GLOW_BREATH_LOW = 0.82f
private const val GLOW_BREATH_MS = 1600
