package com.music.bitchord.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.theme.LocalNowPlayingTint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The two coloured waves' colours: the app's accent and a second colour off the same sleeve. */
data class WaveAccents(val primary: Color, val secondary: Color)

/**
 * The accent the rest of the app wears for this song ([LocalNowPlayingTint]) and the artwork
 * colour furthest round the hue wheel from it, both lifted to a bright, saturated tone so the
 * waves read over any backdrop. A grey sleeve has no second hue to offer, so the secondary is
 * then the accent turned part way round the wheel. Changes crossfade with the track.
 */
@Composable
fun rememberWaveAccents(artworkUrl: String?): WaveAccents {
    val palette = rememberArtworkColors(artworkUrl)
    val tint = LocalNowPlayingTint.current
    val target = remember(palette, tint) {
        val candidates = (palette.art + palette.colors).map { it.toHsl() }
        val primary = (tint?.toHsl() ?: candidates.maxByOrNull { it.s } ?: Hsl(25f, 0.9f, 0.6f))
        val secondary = candidates
            .filter { it.s > MIN_SECONDARY_SATURATION && hueDistance(it.h, primary.h) > MIN_SECONDARY_HUE_GAP }
            .maxByOrNull { hueDistance(it.h, primary.h) * it.s }
            ?: primary.copy(h = (primary.h + FALLBACK_HUE_TURN) % 360f)
        WaveAccents(primary.vivid(), secondary.vivid())
    }
    val spec = tween<Color>(durationMillis = 900, easing = FastOutSlowInEasing)
    return WaveAccents(
        primary = animateColorAsState(target.primary, spec, label = "wavePrimary").value,
        secondary = animateColorAsState(target.secondary, spec, label = "waveSecondary").value,
    )
}

/**
 * The waves' clock and swell. [time] runs only while the music does, so a paused bar costs no
 * frames; [swell] eases the waves up out of the line as playback starts and back down into it
 * when it stops. Both are read in draw, so the motion redraws the bar without recomposing it.
 */
@Stable
internal class WaveMotion {
    var time by mutableFloatStateOf(0f)
    val swell = Animatable(0f)
}

@Composable
internal fun rememberWaveMotion(playing: Boolean, animate: Boolean): WaveMotion {
    val motion = remember { WaveMotion() }
    LaunchedEffect(playing, animate) {
        if (!playing) {
            motion.swell.animateTo(0f, tween(durationMillis = SWELL_MS, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        if (!animate) {
            // Reduced motion: the waves stand still, at full height.
            motion.swell.snapTo(1f)
            return@LaunchedEffect
        }
        var last = System.nanoTime()
        while (true) {
            val dt = withFrameNanos {
                val now = System.nanoTime()
                val elapsed = (now - last).coerceIn(0L, MAX_WAVE_FRAME_NANOS) / 1_000_000_000f
                last = now
                elapsed
            }
            // Wrapped well past every layer's period, so float precision never wears down.
            motion.time = (motion.time + dt) % WAVE_TIME_WRAP
            if (motion.swell.value < 1f) {
                motion.swell.snapTo(min(1f, motion.swell.value + dt * 1000f / SWELL_MS))
            }
        }
    }
    return motion
}

/**
 * One UI 9's media progress: three soft ranges of humps rising out of the played part of the
 * line and drifting toward the playhead — a white one at the back, then the secondary and the
 * primary accent over it, translucent, so where they cross their colours mix. The whole set
 * tapers to nothing at the start of the bar and again where it meets the playhead.
 *
 * [baseline] is the y of the line's top edge; [played] the played length in px.
 */
internal fun DrawScope.drawWaves(
    path: Path,
    played: Float,
    baseline: Float,
    time: Float,
    swell: Float,
    accents: WaveAccents,
    alpha: Float,
) {
    if (played <= 1f || swell <= 0f) return
    val maxHeight = WAVE_HEIGHT.toPx() * swell
    val taper = min(played * TAPER_SHARE, WAVE_TAPER.toPx())
    val step = WAVE_STEP.toPx()
    WAVE_LAYERS.forEach { layer ->
        val wavelength = layer.wavelength.toPx()
        val travel = layer.speed.toPx() * time
        path.reset()
        path.moveTo(0f, baseline)
        var x = 0f
        while (true) {
            val envelope = smoothstep(x / taper) * smoothstep((played - x) / taper)
            val u = (x - travel) / wavelength + layer.phase
            val c = 0.5f + 0.5f * cos(2f * PI.toFloat() * u)
            // A slower beat across the humps, so neighbouring ones don't stand at the same height.
            val beat = 1f - layer.unevenness * (0.5f + 0.5f * sin(2f * PI.toFloat() * (u * UNEVEN_RATIO + layer.phase)))
            path.lineTo(x, baseline - maxHeight * layer.height * envelope * c * c * beat)
            if (x >= played) break
            x = min(played, x + step)
        }
        path.lineTo(played, baseline)
        path.close()
        val color = when (layer.tone) {
            WaveTone.WHITE -> Color.White
            WaveTone.SECONDARY -> accents.secondary
            WaveTone.PRIMARY -> accents.primary
        }
        drawPath(path, color.copy(alpha = layer.alpha * alpha))
    }
}

/** The playhead: a white bead with an accent core and a soft halo. */
internal fun DrawScope.drawWaveThumb(center: Offset, scale: Float, accent: Color, alpha: Float) {
    val halo = THUMB_HALO.toPx() * scale
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.45f * alpha), Color.Transparent),
            center = center,
            radius = halo,
        ),
        radius = halo,
        center = center,
    )
    drawCircle(Color.White.copy(alpha = alpha), radius = THUMB_RADIUS.toPx() * scale, center = center)
    drawCircle(accent.copy(alpha = alpha), radius = THUMB_CORE.toPx() * scale, center = center)
}

private enum class WaveTone { WHITE, SECONDARY, PRIMARY }

private class WaveLayer(
    val tone: WaveTone,
    /** Share of [WAVE_HEIGHT] the tallest hump reaches. */
    val height: Float,
    val wavelength: androidx.compose.ui.unit.Dp,
    /** Drift toward the playhead, per second. */
    val speed: androidx.compose.ui.unit.Dp,
    val phase: Float,
    val alpha: Float,
    val unevenness: Float,
)

// Back to front. Different wavelengths and speeds, so the humps slide past one another and
// the overlaps keep changing rather than marching in lockstep.
private val WAVE_LAYERS = listOf(
    WaveLayer(WaveTone.WHITE, height = 0.55f, wavelength = 92.dp, speed = 30.dp, phase = 0.35f, alpha = 0.85f, unevenness = 0.35f),
    WaveLayer(WaveTone.SECONDARY, height = 0.72f, wavelength = 128.dp, speed = 40.dp, phase = 0.5f, alpha = 0.72f, unevenness = 0.3f),
    WaveLayer(WaveTone.PRIMARY, height = 1f, wavelength = 150.dp, speed = 48.dp, phase = 0f, alpha = 0.72f, unevenness = 0.25f),
)

private val WAVE_HEIGHT = 9.dp
private val WAVE_TAPER = 72.dp
private const val TAPER_SHARE = 0.4f
private val WAVE_STEP = 2.dp
private const val UNEVEN_RATIO = 0.37f
private const val WAVE_TIME_WRAP = 3600f
private const val SWELL_MS = 600
private const val MAX_WAVE_FRAME_NANOS = 100_000_000L
private val THUMB_RADIUS = 5.dp
private val THUMB_CORE = 3.dp
private val THUMB_HALO = 11.dp

private const val MIN_SECONDARY_SATURATION = 0.18f
private const val MIN_SECONDARY_HUE_GAP = 40f
private const val FALLBACK_HUE_TURN = 150f

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private data class Hsl(val h: Float, val s: Float, val l: Float) {
    /** Bright and saturated enough to glow over the player's backdrop. */
    fun vivid(): Color = Color.hsl(h, s.coerceAtLeast(0.6f), l.coerceIn(0.56f, 0.68f))
}

private fun hueDistance(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return if (d > 180f) 360f - d else d
}

private fun Color.toHsl(): Hsl {
    val r = red
    val g = green
    val b = blue
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    val d = mx - mn
    if (d == 0f) return Hsl(0f, 0f, l)
    val s = d / (1f - abs(2f * l - 1f))
    val h = when (mx) {
        r -> 60f * (((g - b) / d) % 6f)
        g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return Hsl((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
}
