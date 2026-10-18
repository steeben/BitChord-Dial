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
import androidx.compose.ui.graphics.BlendMode
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

/** The three waves' colours: a light tint of the accent, the accent, a darker shade of it. */
data class WaveAccents(val light: Color, val accent: Color, val dark: Color)

/**
 * The accent the rest of the app wears for this song ([LocalNowPlayingTint]), lifted to a
 * bright, saturated tone so it reads over any backdrop, with a lighter tint and a darker shade
 * of the same hue for the other two waves. Without a tint, the artwork's most colourful swatch
 * stands in. Changes crossfade with the track.
 */
@Composable
fun rememberWaveAccents(artworkUrl: String?): WaveAccents {
    val tint = LocalNowPlayingTint.current
    // The artwork is only read when there is no tint to follow.
    val palette = rememberArtworkColors(artworkUrl.takeIf { tint == null })
    val target = remember(palette, tint) {
        val base = tint?.toHsl()
            ?: (palette.art + palette.colors).map { it.toHsl() }.maxByOrNull { it.s }
            ?: Hsl(25f, 0.9f, 0.6f)
        // A grey sleeve's tint has no hue worth saturating; its waves stay grey.
        val s = if (base.s < GREY_SATURATION) 0f else base.s.coerceAtLeast(0.6f)
        WaveAccents(
            light = Color.hsl(base.h, s * 0.75f, 0.8f),
            accent = Color.hsl(base.h, s, base.l.coerceIn(0.56f, 0.66f)),
            dark = Color.hsl(base.h, s, 0.36f),
        )
    }
    val spec = tween<Color>(durationMillis = 900, easing = FastOutSlowInEasing)
    return WaveAccents(
        light = animateColorAsState(target.light, spec, label = "waveLight").value,
        accent = animateColorAsState(target.accent, spec, label = "waveAccent").value,
        dark = animateColorAsState(target.dark, spec, label = "waveDark").value,
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
 * line and drifting toward the playhead — a light tint of the accent, a dark shade of it and the accent,
 * blended additively, so where they cross their colours add up and brighten. The whole set
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
            WaveTone.LIGHT -> accents.light
            WaveTone.DARK -> accents.dark
            WaveTone.ACCENT -> accents.accent
        }
        // Additive: overlaps add up toward white, the way coloured light does, rather than the
        // front wave covering the ones behind it.
        drawPath(path, color.copy(alpha = layer.alpha * alpha), blendMode = BlendMode.Plus)
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

private enum class WaveTone { LIGHT, DARK, ACCENT }

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
    WaveLayer(WaveTone.LIGHT, height = 0.55f, wavelength = 92.dp, speed = 30.dp, phase = 0.35f, alpha = 0.85f, unevenness = 0.35f),
    WaveLayer(WaveTone.DARK, height = 0.72f, wavelength = 128.dp, speed = 40.dp, phase = 0.5f, alpha = 0.72f, unevenness = 0.3f),
    WaveLayer(WaveTone.ACCENT, height = 1f, wavelength = 150.dp, speed = 48.dp, phase = 0f, alpha = 0.72f, unevenness = 0.25f),
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


private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private const val GREY_SATURATION = 0.08f

private data class Hsl(val h: Float, val s: Float, val l: Float)

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
