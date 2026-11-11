package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How many words past the one being sung a scrolling line keeps in view. */
internal const val MARQUEE_WORDS_AHEAD = 2

/** A line with no word timing rests at its start for this much of its time, */
private const val MARQUEE_REST_AT_START = 0.08f

/** and has reached its end by this much of it, so the last words can be read. */
private const val MARQUEE_DONE_BY = 0.88f

/**
 * How far a one-line lyric too long for its space has scrolled, in pixels,
 * for a line timed word by word.
 *
 * As each word starts, the word [ahead] places after it is wholly in view;
 * between two starts the line glides from one such position to the next, so
 * it never jumps and never shows fewer than [ahead] words to come. Near the
 * end, with fewer than that left, it rests on the line's last word.
 *
 * [wordStartsMs] and [wordRights] are each word's start and the x of its right
 * edge in the laid-out text; [endMs] is when the last word finishes.
 */
internal fun wordMarqueeOffset(
    wordStartsMs: LongArray,
    endMs: Long,
    wordRights: FloatArray,
    positionMs: Long,
    viewport: Float,
    width: Float,
    ahead: Int = MARQUEE_WORDS_AHEAD,
): Float {
    val max = width - viewport
    val count = minOf(wordStartsMs.size, wordRights.size)
    if (max <= 0f || count == 0) return 0f
    // Where the line sits as word [index] starts.
    fun restAt(index: Int): Float {
        val shown = index + ahead
        return if (shown >= count - 1) max else (wordRights[shown] - viewport).coerceIn(0f, max)
    }
    if (positionMs <= wordStartsMs[0]) return restAt(0)
    var current = 0
    for (index in 1 until count) {
        if (wordStartsMs[index] <= positionMs) current = index else break
    }
    val from = wordStartsMs[current]
    val until = if (current + 1 < count) wordStartsMs[current + 1] else endMs
    if (until <= from) return restAt(current + 1)
    val through = ((positionMs - from).toFloat() / (until - from)).coerceIn(0f, 1f)
    val start = restAt(current)
    return start + (restAt(current + 1) - start) * through
}

/**
 * The same for a line with no word timing: it crosses its overflow at a
 * steady pace over the line's own time, from [fromMs] to [untilMs], resting a
 * moment at each end.
 */
internal fun lineMarqueeOffset(
    fromMs: Long,
    untilMs: Long,
    positionMs: Long,
    viewport: Float,
    width: Float,
): Float {
    val max = width - viewport
    if (max <= 0f || untilMs <= fromMs) return 0f
    val through = (positionMs - fromMs).toFloat() / (untilMs - fromMs)
    val moving = ((through - MARQUEE_REST_AT_START) / (MARQUEE_DONE_BY - MARQUEE_REST_AT_START))
        .coerceIn(0f, 1f)
    return max * moving
}

/** How wide the fade is at an edge the line runs off. */
private val MARQUEE_FADE = 14.dp

/**
 * Lays a one-line text out at its full width in a space that may be narrower,
 * and slides it left by [offset] — pixels, for the space's width and the
 * text's — read while drawing, so a frame of the scroll costs no layout. A
 * line that fits is left exactly as it was. An edge the text runs off fades
 * out rather than being cut.
 */
@Composable
internal fun Modifier.lyricMarquee(
    fade: Dp = MARQUEE_FADE,
    offset: (viewport: Float, width: Float) -> Float,
): Modifier {
    // The space's width and the text's, from the last layout, for the fade.
    val sizes = remember { FloatArray(2) }
    return this
        .graphicsLayer {
            // The fade erases into this layer rather than into the player.
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .drawWithContent {
            drawContent()
            val viewport = sizes[0]
            val width = sizes[1]
            if (width <= viewport || viewport <= 0f) return@drawWithContent
            val at = offset(viewport, width).coerceIn(0f, width - viewport)
            val band = fade.toPx().coerceAtMost(viewport / 3f)
            if (at > 0.5f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startX = 0f,
                        endX = band,
                    ),
                    size = size.copy(width = band),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (at < width - viewport - 0.5f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startX = size.width - band,
                        endX = size.width,
                    ),
                    topLeft = Offset(size.width - band, 0f),
                    size = size.copy(width = band),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
        .clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(
                constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity),
            )
            val width = if (constraints.hasBoundedWidth) {
                placeable.width.coerceAtMost(constraints.maxWidth)
            } else {
                placeable.width
            }
            sizes[0] = width.toFloat()
            sizes[1] = placeable.width.toFloat()
            layout(width, placeable.height) {
                if (placeable.width <= width) {
                    placeable.place(0, 0)
                } else {
                    val viewport = width.toFloat()
                    val full = placeable.width.toFloat()
                    placeable.placeWithLayer(0, 0) {
                        translationX = -offset(viewport, full).coerceIn(0f, full - viewport)
                    }
                }
            }
        }
}
