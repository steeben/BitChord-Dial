package com.music.bitchord.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.playback.PlaybackPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The miniplayer's content following a sideways swipe: it moves with the
 * finger, and a swipe past the threshold carries it off that side while the
 * next (or previous) song slides in from the other, instead of the bar cutting
 * straight to it. Written by [miniPlayerGestures], drawn by [miniPlayerSwipeContent].
 */
@Stable
class MiniPlayerSwipe {
    /** How far the content is moved sideways, in pixels. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** The content's width, how far it travels to leave. */
    var width by mutableFloatStateOf(0f)
        internal set

    private var settling: Job? = null

    /** The finger has moved the content to [dragged] pixels from where it started. */
    fun follow(dragged: Float) {
        settling?.cancel()
        offset = dragged
    }

    /**
     * The finger lifted. [direction] is -1 to leave to the left (the next song),
     * 1 to leave to the right (the previous one), 0 to go back where it was.
     * [onSwitch] is called once the old song is off, before the new one comes in.
     */
    fun settle(scope: CoroutineScope, direction: Int, onSwitch: (() -> Unit)?) {
        settling?.cancel()
        settling = scope.launch {
            if (direction == 0 || width <= 0f) {
                animate(offset, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                    offset = v
                }
                onSwitch?.invoke()
                return@launch
            }
            val away = direction * width
            animate(offset, away, animationSpec = tween(SWIPE_OUT_MS, easing = FastOutLinearInEasing)) { v, _ ->
                offset = v
            }
            onSwitch?.invoke()
            // In from the other side.
            offset = -away * SWIPE_IN_FROM
            animate(offset, 0f, animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                offset = v
            }
        }
    }
}

@Composable
fun rememberMiniPlayerSwipe(): MiniPlayerSwipe = remember { MiniPlayerSwipe() }

/** Moves this (the miniplayer's row) with [swipe], fading as it goes. */
fun Modifier.miniPlayerSwipeContent(swipe: MiniPlayerSwipe): Modifier =
    onSizeChanged { swipe.width = it.width.toFloat() }
        .graphicsLayer {
            val offset = swipe.offset
            translationX = offset
            val width = swipe.width
            alpha = if (width > 0f) 1f - (abs(offset) / width).coerceIn(0f, 1f) * 0.85f else 1f
        }

/**
 * How far through the song is, drawn along the bottom of the miniplayer in the
 * cover's colour, brightening towards the playhead.
 *
 * Plain ([glow] false): a thin line that stays inside the bar. Glass: a soft
 * glow rising inside the bar from its bottom edge, with a brighter line along
 * the edge and a little light spilling below it along the flat of the bottom,
 * all of it tapering away over the last stretch before the playhead.
 * [shape] is the bar's, so the inside part follows its corners; [corner] is how
 * far its rounded ends reach in, where nothing spills.
 *
 * The playhead is read when drawing, so it redraws the bar twice a second and
 * recomposes nothing. See [PlaybackPosition].
 */
fun Modifier.miniPlayerProgress(
    position: PlaybackPosition?,
    durationMs: Long,
    color: Color,
    glow: Boolean,
    shape: Shape,
    corner: (Size) -> Float,
): Modifier = drawWithContent {
    drawContent()
    if (position == null || durationMs <= 0L) return@drawWithContent
    val fraction = (position.positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    if (fraction <= 0f) return@drawWithContent
    val end = size.width * fraction
    val light = lerp(color, Color.White, 0.45f)
    val line = Brush.horizontalGradient(listOf(color, light), startX = 0f, endX = end.coerceAtLeast(1f))
    val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
    if (!glow) {
        val h = PLAIN_LINE.toPx()
        clipPath(outline) {
            drawRect(line, topLeft = Offset(0f, size.height - h), size = Size(end, h))
        }
        return@drawWithContent
    }
    val rise = GLOW_RISE.toPx()
    val spill = GLOW_SPILL.toPx()
    val edge = GLASS_LINE.toPx()
    val inset = corner(size)
    // Drawn into a layer of its own and then faded out towards the playhead as
    // a whole, so the glow and the line taper off together rather than
    // stopping at a hard edge.
    val layer = Rect(0f, 0f, size.width, size.height + spill)
    drawContext.canvas.saveLayer(layer, Paint())
    clipPath(outline) {
        // The light inside the glass, strongest at the bottom edge.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to color.copy(alpha = 0.42f),
                startY = size.height - rise,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - rise),
            size = Size(end, rise),
        )
        drawRect(line, topLeft = Offset(0f, size.height - edge), size = Size(end, edge))
    }
    // A little of it spilling out below, only along the flat of the bottom.
    val spillEnd = end.coerceAtMost(size.width - inset)
    if (spillEnd > inset) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.32f), Color.Transparent),
                startY = size.height,
                endY = size.height + spill,
            ),
            topLeft = Offset(inset, size.height),
            size = Size(spillEnd - inset, spill),
        )
    }
    val taper = minOf(GLOW_TAPER.toPx(), end * 0.5f)
    // The mask covers the whole layer, not just up to the playhead. DstIn only
    // touches the pixels it draws, so a mask that stopped at `end` left the
    // antialiased column at the playhead partly unmasked, and as `end` crept
    // across a pixel that column flickered in and out as a faint bright line.
    // Past `end` the gradient clamps to transparent and clears it.
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Black,
            ((end - taper) / end).coerceIn(0f, 1f) to Color.Black,
            1f to Color.Transparent,
            startX = 0f,
            endX = end,
        ),
        topLeft = Offset.Zero,
        size = Size(size.width, size.height + spill),
        blendMode = BlendMode.DstIn,
    )
    drawContext.canvas.restore()
}

/**
 * The cover's colour for the progress glow: the most vivid of [colors], made
 * bright enough to read as light on the bar. A cover with no real colour in it
 * (black, white, greys) gets a soft white rather than a tint — a grey has no hue
 * of its own, and boosting one used to invent one (red, read as pink).
 */
fun progressTint(colors: List<Color>): Color {
    val hsv = FloatArray(3)
    fun chroma(color: Color): Float {
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        return hsv[1] * hsv[2]
    }
    // Nothing read off the cover yet: a neutral light, not a guess at a hue.
    val vivid = colors.maxByOrNull(::chroma) ?: return NEUTRAL_GLOW
    if (chroma(vivid) < MIN_CHROMA) return NEUTRAL_GLOW
    return progressTint(vivid)
}

/** [color] made bright enough to read as light on the bar. */
fun progressTint(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtLeast(0.45f)
    hsv[2] = hsv[2].coerceAtLeast(0.8f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** How much colour (saturation × brightness) a cover needs before the glow takes its hue. */
private const val MIN_CHROMA = 0.12f

private val NEUTRAL_GLOW = Color(0xFFE8E8EC)

private val PLAIN_LINE: Dp = 2.5.dp
private val GLASS_LINE: Dp = 2.dp
private val GLOW_RISE: Dp = 16.dp
private val GLOW_SPILL: Dp = 6.dp
private val GLOW_TAPER: Dp = 36.dp
private const val SWIPE_OUT_MS = 150
private const val SWIPE_IN_FROM = 0.45f
