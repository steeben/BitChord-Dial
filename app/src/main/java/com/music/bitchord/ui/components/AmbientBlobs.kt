package com.music.bitchord.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.CustomizationSettings
import androidx.compose.runtime.compositionLocalOf
import com.music.bitchord.ui.theme.LocalNowPlayingTint
import com.music.bitchord.ui.theme.accentOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import com.music.bitchord.data.settings.AmbientStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** The app icon's red, the ambient background's colour unless something says otherwise. */
val AmbientAppColor = Color(0xFFF9263E)
val AmbientSpotifyColor = Color(0xFF1DB954)
val AmbientYouTubeColor = Color(0xFFFF0033)

/**
 * The colour [target] is drawn in behind the pages: held between a floor and a
 * ceiling of brightness. The ceiling keeps text on top of it legible whatever
 * colour it starts as — a near-white cover comes out as a dim grey, not a white
 * wash — and the floor keeps a dark cover from vanishing into the black behind
 * it, so there is always some colour in the background.
 */
private fun ambientTone(target: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(target.toArgb(), hsv)
    hsv[2] = hsv[2].coerceIn(AMBIENT_MIN_VALUE, AMBIENT_MAX_VALUE)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * The ambient background behind the app's pages, in [style]:
 *
 * - [AmbientStyle.BLOBS]: three soft colour blobs that drift through each
 *   other — [primary], a dimmer [primary], and [secondary].
 * - [AmbientStyle.RIPPLE_DIAL]: rings that pulse out from a dial's centre.
 * - [AmbientStyle.BIT_GRID]: a wall of pixels a lit diagonal sweeps across.
 * - [AmbientStyle.WAVEFORM_SILK]: sine threads drifting sideways at
 *   different speeds over a soft fill.
 * - [AmbientStyle.VINYL_ORBIT]: an oversized record turning, which stops
 *   while [playing] is false.
 *
 * All plain shape and gradient draws onto the page's background, no blur pass.
 * Colours crossfade when they change. With [animate] off (reduce animation),
 * the background holds still.
 */
@Composable
fun rememberAmbientBlobs(
    primary: Color,
    secondary: Color,
    animate: Boolean,
    style: AmbientStyle = AmbientStyle.BLOBS,
    playing: Boolean = false,
): AmbientBlobs {
    val first by animateColorAsState(ambientTone(primary), tween(AMBIENT_COLOR_FADE_MS), label = "ambientFirst")
    val second by animateColorAsState(ambientTone(secondary), tween(AMBIENT_COLOR_FADE_MS), label = "ambientSecond")
    // The silk's threads swell while something plays and settle when it stops.
    val energy by animateFloatAsState(
        if (playing) 1f else 0.6f,
        if (animate) tween(1_200) else snap(),
        label = "ambientEnergy",
    )
    // Stepped a few times a second rather than every frame. The background
    // sits behind every page, inside the layer the frosted bars and glass
    // sample, so each step redraws those blurs too; at 60 a second that was a
    // steady load on every scroll. Everything moves slowly enough that the
    // steps don't show.
    val clock = remember { mutableLongStateOf(AMBIENT_START_MS) }
    // The record only turns while the music does.
    val running = animate && (style != AmbientStyle.VINYL_ORBIT || playing)
    if (running) {
        LaunchedEffect(Unit) {
            var last = withFrameNanos { it }
            while (true) {
                delay(AMBIENT_STEP_MS)
                val now = withFrameNanos { it }
                val elapsedMs = (now - last) / 1_000_000L
                clock.longValue = (clock.longValue + elapsedMs) % AMBIENT_CLOCK_WRAP_MS
                // Keeps the leftover part of a millisecond for the next step.
                last += elapsedMs * 1_000_000L
            }
        }
    }
    return AmbientBlobs({ first }, { second }, clock, style, { playing }, { energy })
}

/**
 * The accent the page's chrome takes while the ambient background is on: the
 * blobs' main colour, lifted to an accent. Null while the background is off,
 * so the chrome goes back to the playing song's colour.
 *
 * Without it a page could sit on a red wash while its progress bar and
 * buttons wore the blue of some small detail in the cover — two colours that
 * had nothing to do with each other on one screen.
 */
val LocalAmbientAccent = compositionLocalOf<Color?> { null }

/**
 * [primary] as the accent for [LocalAmbientAccent], crossfading with the
 * blobs themselves; null when the ambient background is [on] is false.
 */
@Composable
fun rememberAmbientAccent(primary: Color, on: Boolean): Color? {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent by animateColorAsState(
        accentOf(ambientTone(primary), dark),
        tween(AMBIENT_COLOR_FADE_MS),
        label = "ambientAccent",
    )
    return if (on) accent else null
}

/**
 * The accent for chrome that sits on the page — the mini player's progress,
 * the selected tab, the library's buttons: the ambient background's colour
 * while that is on, else the playing song's, else [fallback].
 */
@Composable
fun pageAccent(fallback: Color = MaterialTheme.colorScheme.primary): Color =
    LocalAmbientAccent.current ?: LocalNowPlayingTint.current ?: fallback

/** A second colour for a single brand colour: the same hue turned a little and darkened. */
fun ambientCompanion(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[0] = (hsv[0] + 28f) % 360f
    hsv[2] = hsv[2] * 0.8f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

class AmbientBlobs internal constructor(
    internal val primary: () -> Color,
    internal val secondary: () -> Color,
    internal val clock: LongState,
    internal val style: AmbientStyle,
    internal val playing: () -> Boolean,
    internal val energy: () -> Float,
)

/** Draws [blobs] behind this; a null [blobs] draws nothing. */
fun Modifier.ambientBlobs(blobs: AmbientBlobs?): Modifier =
    if (blobs == null) {
        this
    } else {
        drawBehind {
            drawAmbient(
                style = blobs.style,
                main = blobs.primary(),
                second = blobs.secondary(),
                clockMs = blobs.clock.longValue,
                playing = blobs.playing(),
                energy = blobs.energy(),
            )
        }
    }

/**
 * Draws one frame of [style] over this whole scope, at [clockMs] into the
 * background's clock. Also used, held still, for the style picker's previews.
 */
fun DrawScope.drawAmbient(
    style: AmbientStyle,
    main: Color,
    second: Color,
    clockMs: Long,
    playing: Boolean,
    energy: Float = 1f,
) {
    when (style) {
        AmbientStyle.BLOBS -> drawBlobs(main, second, loopPhase(clockMs, AMBIENT_CYCLE_MS))
        AmbientStyle.RIPPLE_DIAL -> drawRippleDial(main, second, loopPhase(clockMs, RIPPLE_CYCLE_MS))
        AmbientStyle.BIT_GRID -> drawBitGrid(main, clockMs, playing)
        AmbientStyle.WAVEFORM_SILK -> drawWaveformSilk(main, second, loopPhase(clockMs, SILK_CYCLE_MS), energy)
        AmbientStyle.VINYL_ORBIT -> drawVinylOrbit(main, loopPhase(clockMs, VINYL_CYCLE_MS))
    }
}

private fun loopPhase(clockMs: Long, cycleMs: Int): Float = (clockMs % cycleMs) / cycleMs.toFloat()

/**
 * The designs' unit: a 240-wide phone card, scaled to the page's shorter side
 * so a landscape window gets the same size of picture rather than a huge one.
 */
private fun DrawScope.ambientUnit(): Float = min(size.width, size.height) / 240f

/** [colour] taken [amount] of the way to white, for the bright lines in a design. */
private fun lighten(colour: Color, amount: Float): Color = lerp(colour, Color.White, amount)

private fun DrawScope.drawBlobs(main: Color, second: Color, phase: Float) {
    // One full turn of [phase] is one loop; each blob runs its own Lissajous
    // path at a different whole-number speed so the three keep crossing and
    // overlapping but the loop still joins up.
    val t = phase * 2f * PI.toFloat()
    val w = size.width
    val h = size.height
    val radius = max(w, h) * AMBIENT_RADIUS_FRACTION
    val blobs3 = listOf(
        Triple(
            Offset(w * (0.5f + 0.38f * sin(t)), h * (0.35f + 0.28f * sin(2f * t + 0.6f))),
            main,
            AMBIENT_ALPHA,
        ),
        Triple(
            Offset(w * (0.5f + 0.38f * cos(2f * t + 1.3f)), h * (0.6f + 0.3f * sin(t + 2.2f))),
            main.copy(red = main.red * 0.7f, green = main.green * 0.7f, blue = main.blue * 0.7f),
            AMBIENT_ALPHA * 0.9f,
        ),
        Triple(
            Offset(w * (0.5f + 0.4f * sin(3f * t + 2.4f)), h * (0.5f + 0.34f * cos(t + 0.9f))),
            second,
            AMBIENT_ALPHA * 0.95f,
        ),
    )
    blobs3.forEach { (centre, colour, strength) ->
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colour.copy(alpha = strength),
                    colour.copy(alpha = strength * 0.45f),
                    Color.Transparent,
                ),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
        )
    }
}

/**
 * Ripple Dial: rings born at the dial's centre that grow outward, thinning
 * and fading as they go, over a soft glow. One ring's worth of growth per
 * loop, so the loop joins up.
 */
private fun DrawScope.drawRippleDial(main: Color, second: Color, phase: Float) {
    val u = ambientUnit()
    val centre = Offset(size.width / 2f, size.height * 0.6f)
    val reach = max(size.width, size.height) * 0.75f
    val pulse = 1f - phase
    // The glow under the rings, breathing a little with each pulse.
    val glow = 140f * u * (1f + 0.06f * pulse)
    drawCircle(
        brush = Brush.radialGradient(
            0f to main.copy(alpha = 0.34f + 0.06f * pulse),
            0.45f to second.copy(alpha = 0.14f),
            1f to Color.Transparent,
            center = centre,
            radius = glow,
        ),
        radius = glow,
        center = centre,
    )
    val ring = lighten(main, 0.35f)
    val spacing = RIPPLE_SPACING * u
    val first = 15f * u
    var k = 0
    while (true) {
        val radius = first + (k + phase) * spacing
        if (radius > reach) break
        val travelled = (radius / reach).coerceIn(0f, 1f)
        // Fades in over its first stretch so a new ring doesn't pop in.
        val birth = ((radius - first) / spacing).coerceIn(0f, 1f)
        val alpha = 0.8f * (1f - travelled).pow(1.4f) * birth
        if (alpha > 0.01f) {
            drawCircle(
                color = ring.copy(alpha = alpha),
                radius = radius,
                center = centre,
                style = Stroke(width = (3f - 2f * travelled).coerceAtLeast(1f) * u),
            )
        }
        k++
    }
    drawCircle(color = ring.copy(alpha = 0.9f), radius = 7f * u, center = centre)
}

/**
 * Bit Grid: a wall of rounded pixels, dim, with a lit diagonal band that
 * sweeps across it, and while something plays a few cells that flash on
 * each beat and fade.
 */
private fun DrawScope.drawBitGrid(main: Color, clockMs: Long, playing: Boolean) {
    val u = ambientUnit()
    val pitch = 40f * u
    val cell = 32f * u
    val corner = CornerRadius(6f * u)
    val cols = ceil(size.width / pitch).toInt()
    val rows = ceil(size.height / pitch).toInt()
    // Centred, so whatever is cut off is cut off evenly on both sides.
    val left = (size.width - (cols * pitch - (pitch - cell))) / 2f
    val top = (size.height - (rows * pitch - (pitch - cell))) / 2f
    val lit = lighten(main, 0.3f)
    val phase = loopPhase(clockMs, GRID_CYCLE_MS)
    // The band runs along row - col, from off one corner to off the other.
    val span = rows + cols + 2f * GRID_BAND
    val band = -cols - GRID_BAND + phase * span
    val beat = clockMs / GRID_BEAT_MS
    val beatFade = 1f - (clockMs % GRID_BEAT_MS) / GRID_BEAT_MS.toFloat()
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val d = abs((row - col) - band)
            var glow = (1f - d / GRID_BAND).coerceIn(0f, 1f)
            glow *= glow
            if (playing && cellHash(row, col, beat) % GRID_FLASH_ODDS == 0) {
                glow = max(glow, 0.7f * beatFade)
            }
            drawRoundRect(
                color = lerp(main.copy(alpha = 0.10f), lit.copy(alpha = 0.78f), glow),
                topLeft = Offset(left + col * pitch, top + row * pitch),
                size = Size(cell, cell),
                cornerRadius = corner,
            )
        }
    }
}

private fun cellHash(row: Int, col: Int, beat: Long): Int {
    var x = row * 73_856_093 xor col * 19_349_663 xor (beat * 83_492_791L).toInt()
    x = x xor (x ushr 13)
    x *= 0x5bd1e995
    x = x xor (x ushr 15)
    return x and Int.MAX_VALUE
}

/**
 * Waveform Silk: sine threads across the middle of the page, each scrolling
 * sideways at its own speed, the brightest with a soft fill under it. Their
 * height follows [energy]: fuller while something plays.
 */
private fun DrawScope.drawWaveformSilk(main: Color, second: Color, phase: Float, energy: Float) {
    val u = ambientUnit()
    val midY = size.height * 0.55f
    val t = phase * 2f * PI.toFloat()
    val breathe = energy * (1f + 0.08f * sin(3f * t))
    val bright = lighten(main, 0.35f)
    // amplitude, wavelength, whole wavelengths scrolled per loop, phase offset, alpha, stroke
    val threads = listOf(
        SilkThread(120f, 300f, -1, 1.9f, 0.16f, 1f),
        SilkThread(80f, 260f, 2, 0.4f, 0.26f, 1f),
        SilkThread(55f, 240f, 3, 2.8f, 0.42f, 1.2f),
    )
    val step = max(4f, 6f * u)
    threads.forEach { thread ->
        val path = silkPath(thread.amplitude * u * breathe, thread.wavelength * u, t * thread.speed + thread.offset, midY, step)
        drawPath(path, color = lerp(second, bright, 0.5f).copy(alpha = thread.alpha), style = Stroke(width = thread.stroke * u))
    }
    // The leading thread, with the fill that hangs from it.
    val mainPath = silkPath(40f * u * breathe, 320f * u, t, midY, step)
    val fill = silkPath(40f * u * breathe, 320f * u, t, midY, step).apply {
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(
        fill,
        brush = Brush.verticalGradient(
            0f to main.copy(alpha = 0.34f),
            1f to Color.Transparent,
            startY = midY - 40f * u,
            endY = size.height,
        ),
    )
    drawPath(mainPath, color = bright.copy(alpha = 0.9f), style = Stroke(width = 3f * u, cap = StrokeCap.Round))
}

private class SilkThread(
    val amplitude: Float,
    val wavelength: Float,
    val speed: Int,
    val offset: Float,
    val alpha: Float,
    val stroke: Float,
)

private fun DrawScope.silkPath(amplitude: Float, wavelength: Float, shift: Float, midY: Float, step: Float): Path {
    val path = Path()
    var x = 0f
    path.moveTo(0f, midY - amplitude * sin(shift))
    while (x < size.width) {
        x = min(x + step, size.width)
        path.lineTo(x, midY - amplitude * sin(x / wavelength * 2f * PI.toFloat() + shift))
    }
    return path
}

/**
 * Vinyl Orbit: a record wider than the page, its grooves catching two
 * opposite sheens that turn with it, a ringed label and a glint on it.
 * One turn per loop.
 */
private fun DrawScope.drawVinylOrbit(main: Color, phase: Float) {
    val u = ambientUnit()
    val centre = Offset(size.width / 2f, size.height * 0.63f)
    val radius = 240f * u
    val label = 55f * u
    val angle = phase * 360f
    drawCircle(color = main.copy(alpha = 0.32f), radius = radius, center = centre)
    val groove = lighten(main, 0.4f).copy(alpha = 0.12f)
    var r = radius - 18f * u
    while (r > label + 10f * u) {
        drawCircle(color = groove, radius = r, center = centre, style = Stroke(width = 1f * u))
        r -= 22f * u
    }
    rotate(angle, pivot = centre) {
        drawCircle(
            brush = Brush.sweepGradient(
                0f to Color.Transparent,
                0.06f to Color.White.copy(alpha = 0.07f),
                0.12f to Color.Transparent,
                0.5f to Color.Transparent,
                0.56f to Color.White.copy(alpha = 0.07f),
                0.62f to Color.Transparent,
                1f to Color.Transparent,
                center = centre,
            ),
            radius = radius,
            center = centre,
        )
        drawCircle(color = main.copy(alpha = 0.3f), radius = label, center = centre)
        drawCircle(
            color = lighten(main, 0.15f).copy(alpha = 0.9f),
            radius = label,
            center = centre,
            style = Stroke(width = 1.5f * u),
        )
        // The glint on the label, which is what shows it turning.
        drawArc(
            color = Color.White.copy(alpha = 0.4f),
            startAngle = -25f,
            sweepAngle = 50f,
            useCenter = false,
            topLeft = Offset(centre.x - 38f * u, centre.y - 38f * u),
            size = Size(76f * u, 76f * u),
            style = Stroke(width = 2f * u, cap = StrokeCap.Round),
        )
    }
    drawCircle(color = Color.Black.copy(alpha = 0.85f), radius = 5f * u, center = centre)
}

/** The brightest the blobs' colour may be (HSV value), before its alpha. */
private const val AMBIENT_MAX_VALUE = 0.85f

/** The darkest it may be: a dark cover is lifted to this, keeping its hue. */
private const val AMBIENT_MIN_VALUE = 0.6f

/** How strongly the nearest blob tints the page at its centre. */
private const val AMBIENT_ALPHA = 0.30f

/** Each blob's reach, as a share of the page's longer side. */
private const val AMBIENT_RADIUS_FRACTION = 0.48f

/** One full loop of the blobs' paths. */
private const val AMBIENT_CYCLE_MS = 36_000

private const val AMBIENT_COLOR_FADE_MS = 900

/** How often the background moves, in ms. */
private const val AMBIENT_STEP_MS = 80L

/** Where the clock starts, so the blobs open where they always have. */
private const val AMBIENT_START_MS = 5_400L

/** The clock wraps here, a whole number of every style's loop. */
private const val AMBIENT_CLOCK_WRAP_MS = 180_000L

private const val RIPPLE_CYCLE_MS = 4_000

/** The gap between rings, in the designs' units. */
private const val RIPPLE_SPACING = 30f

private const val GRID_CYCLE_MS = 6_000

/** Half the lit band's width, in cells. */
private const val GRID_BAND = 3f

/** A beat at 120 bpm, for the cells that flash. */
private const val GRID_BEAT_MS = 500L

/** About one cell in this many flashes on a beat. */
private const val GRID_FLASH_ODDS = 23

private const val SILK_CYCLE_MS = 12_000

/** 1 turn every 20 s: a slow 33⅓ feel. */
private const val VINYL_CYCLE_MS = 20_000

/**
 * A surface colour for cards, fields and bars that sit on the page: a thin,
 * see-through frost while the ambient background is on, so the colour behind
 * reads through it, and [fallback] (the usual solid grey) when it is off.
 */
@Composable
fun frostedSurfaceColor(fallback: Color): Color {
    val ambient by CustomizationSettings.ambientBackground.collectAsStateWithLifecycle()
    if (!ambient) return fallback
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) Color.White.copy(alpha = 0.075f) else Color.Black.copy(alpha = 0.05f)
}
