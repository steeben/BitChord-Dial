package com.music.bitchord.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.CustomizationSettings
import androidx.compose.runtime.State
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
 * Three soft, semi-large colour blobs behind the app's pages that drift around
 * and through each other: [primary], a dimmer [primary], and [secondary]. Drawn
 * as plain radial gradients straight onto the page's background — no blur pass
 * — so the motion costs three gradient draws a frame.
 *
 * Colours crossfade when they change. With [animate] off (reduce animation),
 * the blobs hold still.
 */
@Composable
fun rememberAmbientBlobs(primary: Color, secondary: Color, animate: Boolean): AmbientBlobs {
    val first by animateColorAsState(ambientTone(primary), tween(AMBIENT_COLOR_FADE_MS), label = "ambientFirst")
    val second by animateColorAsState(ambientTone(secondary), tween(AMBIENT_COLOR_FADE_MS), label = "ambientSecond")
    // Stepped a few times a second rather than every frame. The blobs sit
    // behind every page, inside the layer the frosted bars and glass sample,
    // so each step redraws those blurs too; at 60 a second that was a steady
    // load on every scroll. The blobs drift so slowly, and are so soft, that
    // the steps don't show.
    val phase = remember { mutableFloatStateOf(0.15f) }
    if (animate) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it } - (phase.floatValue * AMBIENT_CYCLE_MS * 1_000_000L).toLong()
            while (true) {
                delay(AMBIENT_STEP_MS)
                val now = withFrameNanos { it }
                phase.floatValue = ((now - start) / 1_000_000L % AMBIENT_CYCLE_MS) / AMBIENT_CYCLE_MS.toFloat()
            }
        }
    }
    return AmbientBlobs({ first }, { second }, phase)
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
    internal val phase: State<Float>,
)

/** Draws [blobs] behind this; a null [blobs] draws nothing. */
fun Modifier.ambientBlobs(blobs: AmbientBlobs?): Modifier =
    if (blobs == null) {
        this
    } else {
        drawBehind {
            // One full turn of [phase] is one loop; each blob runs its own
            // Lissajous path at a different whole-number speed so the three
            // keep crossing and overlapping but the loop still joins up.
            val t = blobs.phase.value * 2f * PI.toFloat()
            val w = size.width
            val h = size.height
            val radius = max(w, h) * AMBIENT_RADIUS_FRACTION
            val main = blobs.primary()
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
                    blobs.secondary(),
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

/** How often the blobs move, in ms. */
private const val AMBIENT_STEP_MS = 80L

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
