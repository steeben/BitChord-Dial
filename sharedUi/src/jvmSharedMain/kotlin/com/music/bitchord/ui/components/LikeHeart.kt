package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The red the heart flashes while it is being liked. It never rests on it. */
private val LikeRed = Color(0xFFFF2D55)
private val LikeRedBright = Color(0xFFFF7A93)

private const val LIKE_ANIMATION_MS = 1100

/**
 * Drives the like burst for one [LikeHeart]. Played from the tap rather than
 * from the like status changing, so a song that arrives already liked, a
 * status that loads late, or a skip to the next track never sets it off.
 */
@Stable
class LikeAnimation internal constructor(private val scope: CoroutineScope) {
    internal val progress = Animatable(1f)
    private var job: Job? = null

    /** True while the burst is on screen; the heart shows filled throughout. */
    internal val playing: Boolean get() = progress.value < 1f

    /**
     * Call from the like control's click, with the state it had *before* the
     * tap: liking plays the burst, unliking cuts a running one short.
     */
    fun onToggle(wasLiked: Boolean) {
        job?.cancel()
        job = scope.launch {
            if (wasLiked) {
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(LIKE_ANIMATION_MS, easing = LinearEasing))
            }
        }
    }
}

@Composable
fun rememberLikeAnimation(): LikeAnimation {
    val scope = rememberCoroutineScope()
    return remember(scope) { LikeAnimation(scope) }
}

private fun segment(p: Float, from: Float, to: Float): Float =
    ((p - from) / (to - from)).coerceIn(0f, 1f)

/**
 * A like heart that, on [animation]'s cue, pops in filled and red with a ring
 * of short lines bursting off it, then settles back to [tint] — the resting
 * liked state stays monochrome, and the red only lives in the animation.
 *
 * Modeled on the familiar web "heart checkbox": the fill scales 0 → 1.2 → 1
 * with a brightness peak, and the burst scales out to 1.4× while it fades.
 */
@Composable
fun LikeHeart(
    liked: Boolean,
    animation: LikeAnimation,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    filledIcon: ImageVector = BitChordIcons.HeartFilled,
    outlineIcon: ImageVector = BitChordIcons.Heart,
) {
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val p = animation.progress.value
                if (p >= 1f) return@drawBehind
                // The burst: the first ~45% of the run, like the CSS's 0.5s
                // of a 1s fill. It grows from the heart's edge to 1.4× while
                // fading out over its second half.
                val t = segment(p, 0.08f, 0.5f)
                if (t <= 0f || t >= 1f) return@drawBehind
                val grow = FastOutSlowInEasing.transform(t)
                val alpha = if (t < 0.5f) 1f else 1f - (t - 0.5f) * 2f
                val half = this.size.minDimension / 2f
                val outer = half * (0.75f + 0.65f * grow)
                val length = half * 0.32f * (1f - grow * 0.6f)
                val stroke = this.size.minDimension * 0.08f
                val color = lerp(LikeRed, LikeRedBright, alpha * 0.5f).copy(alpha = alpha)
                for (i in 0 until 8) {
                    // Rotated off the vertical so no ray lands in the heart's
                    // notch, and every other one shorter for a less mechanical
                    // ring.
                    val angle = (i * 45f + 22.5f) * (PI / 180f).toFloat()
                    val reach = if (i % 2 == 0) outer else outer * 0.88f
                    val dir = Offset(cos(angle), sin(angle))
                    drawLine(
                        color = color,
                        start = center + dir * (reach - length),
                        end = center + dir * reach,
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val p = animation.progress.value
        val playing = p < 1f
        val filled = liked || playing
        val color = if (!playing) {
            tint
        } else {
            // Red as it pops in, brightest as it lands, then back to the
            // ordinary ink over the last stretch.
            val bright = if (p < 0.5f) segment(p, 0.2f, 0.5f) else 1f - segment(p, 0.5f, 0.65f)
            val red = lerp(LikeRed, LikeRedBright, bright * 0.6f)
            lerp(red, tint, FastOutSlowInEasing.transform(segment(p, 0.62f, 1f)))
        }
        Icon(
            imageVector = if (filled) filledIcon else outlineIcon,
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    // Read in the layer so the scale doesn't recompose per frame.
                    val q = animation.progress.value
                    val s = when {
                        q >= 1f -> 1f
                        q < 0.25f -> 1.2f * FastOutSlowInEasing.transform(segment(q, 0f, 0.25f))
                        q < 0.5f -> 1.2f - 0.2f * FastOutSlowInEasing.transform(segment(q, 0.25f, 0.5f))
                        else -> 1f
                    }
                    scaleX = s
                    scaleY = s
                },
        )
    }
}
