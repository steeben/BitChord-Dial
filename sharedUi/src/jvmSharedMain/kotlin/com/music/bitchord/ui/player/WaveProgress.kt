package com.music.bitchord.ui.player

import androidx.compose.animation.animateColorAsState
import kotlin.math.roundToInt
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
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
 * The player's white: the colour its text and glyphs are drawn in, leaning very slightly
 * toward the song's accent so the chrome sits with the backdrop rather than on top of it.
 * Plain white outside [ProvidePlayerInk], so the player's pieces read the same elsewhere.
 */
internal val LocalPlayerInk = compositionLocalOf { Color.White }

/** Shorthand for [LocalPlayerInk] at the call site. */
internal val PlayerInk: Color
    @Composable get() = LocalPlayerInk.current

/**
 * What the player around the ink is doing, written by the player as it changes and read by
 * [ProvidePlayerInk]. [dim] is how much darker the backdrop has gone than the plain player
 * (the Canvas deck's dim, the lyrics and queue scrim), 0 to 1. [forcedDark] is the light or
 * dark text the listener picked for this song from its ⋯ menu's Song fixes (over its album
 * art or over its Canvas, whichever is behind it now); null leaves it to the sleeve.
 */
@Stable
class PlayerInkConditions {
    var dim by mutableFloatStateOf(0f)
    var forcedDark by mutableStateOf<Boolean?>(null)

    /**
     * How bright the band behind the credits and controls is (see
     * [com.music.bitchord.ui.theme.controlsBandLuminance]): off the playing Canvas or
     * video's latest frame while one is showing, else off the sleeve. Null while unknown,
     * which leaves the ink to the whole sleeve as before.
     */
    var band by mutableStateOf<Float?>(null)

    /**
     * Lyrics or the queue are up. Their ink is the same light or dark verdict the main
     * player had, so the text doesn't change brightness on the way in; only the scrim
     * behind the main player's controls is left out, since those controls aren't shown.
     */
    var panelOpen by mutableStateOf(false)

    /**
     * A Canvas or music video is the picture behind the credits and controls. A clip
     * moves, so a bright frame now is a dark one a second later, and a frame read off it
     * can be stale or cropped differently from what is on screen: the ink stays light
     * over a clip, on the scrim, and only a Song fixes pick turns it dark.
     */
    var overClip by mutableStateOf(false)
}

/**
 * How dark the scrim behind the player's credits and controls is, 0 to 1 (black at that
 * alpha), as [ProvidePlayerInk] works it out from the band behind them. The player draws
 * it from just above the title to the foot of the screen; see [controlsScrimAlpha].
 */
internal val LocalPlayerControlsScrim = compositionLocalOf { 0f }

/**
 * How far the ink is pushed toward its pole (white for the light ink, black for the dark),
 * and its faded tones toward full strength, to make up for a dimmed backdrop. Follows
 * [PlayerInkConditions.dim].
 */
internal val LocalPlayerInkLift = compositionLocalOf { 0f }

/**
 * Whether [ProvidePlayerInk] settled on the dark ink: one verdict for the main player,
 * the lyrics and the queue. The lyrics and queue backdrop thins its scrim under dark ink.
 */
internal val LocalPlayerInkDark = compositionLocalOf { false }

/**
 * How much white is laid under the player's dark ink, 0 to 1, in the light theme
 * ([lightVeilAlpha]): there the ink goes dark over a band that is bright but short of
 * white, and the veil lifts that band the rest of the way. The main player draws it where
 * the dark scrim would be; lyrics and the queue lay it over their backdrop. Always 0 in
 * the dark theme and under light ink.
 */
internal val LocalPlayerInkVeil = compositionLocalOf { 0f }

/** [LocalPlayerInkVeil] as the main player's controls wear it: none with a panel up. */
internal val LocalPlayerControlsVeil = compositionLocalOf { 0f }

/**
 * What the player's buttons sit on that their glass does not see: the dark scrim under
 * light ink or the white veil under dark ink, as a colour to lay under their own fill
 * (see [PlayerGlass.surface]'s underlay). Without it a button showed the bright sleeve
 * as it was before the scrim and stood out as a white disc on the darkened band.
 */
internal val playerControlsUnderlay: Color
    @Composable get() {
        val scrim = LocalPlayerControlsScrim.current
        val veil = LocalPlayerControlsVeil.current
        return when {
            scrim > 0f -> Color.Black.copy(alpha = scrim)
            veil > 0f -> Color.White.copy(alpha = veil)
            else -> Color.Transparent
        }
    }

/**
 * [PlayerInk] at [alpha], with the faded tone brought up by the ink's lift: as the backdrop
 * dims, a 55% caption moves toward full strength by the same share, so it keeps the
 * contrast it had over the brighter backdrop.
 */
@Composable
internal fun playerInk(alpha: Float): Color {
    val lift = LocalPlayerInkLift.current
    return PlayerInk.copy(alpha = (alpha + (1f - alpha) * lift).coerceIn(0f, 1f))
}

/**
 * Hands the player's [content] its ink, off the same accent [rememberWaveAccents] follows
 * (or the theme's own, with nothing playing). Only [PLAYER_INK_TINT] of the way there, so
 * it reads as white with a cast, not as a colour. A grey accent leaves it plain white.
 *
 * [artLuminance] is how white the sleeve is overall (mean relative luminance, null while
 * unknown). It is an either/or switch, not a ramp: only a sleeve at or past
 * [INK_DARK_FROM] (mostly white) flips the ink to its dark form, leaning further into the
 * accent ([PLAYER_INK_TINT_BRIGHT]) and going [PLAYER_INK_DARKEN] of the way to black.
 * Anything short of that keeps the plain light ink, so a mid-toned backdrop never ends up
 * with dim, low-contrast text. A pick made for the song ([PlayerInkConditions.forcedDark])
 * overrides the sleeve either way.
 *
 * On the main player, once the band behind the credits and controls is known
 * ([PlayerInkConditions.band]), that band decides instead of the whole sleeve: light ink
 * unless it is nearly white ([bandInkIsDark]), stood on a scrim that darkens with it
 * ([LocalPlayerControlsScrim]). Lyrics and the queue take that same verdict
 * ([LocalPlayerInkDark]), so their text is dark exactly when the player's is.
 *
 * A dimmed backdrop ([PlayerInkConditions.dim]) pushes the ink by the same amount: the
 * light ink that share of the way to white, the dark ink that share of the way to black,
 * and (through [playerInk]) either one's faded tones that share of the way to full
 * strength. Every change eases, never snaps.
 */
@Composable
fun ProvidePlayerInk(
    artLuminance: Float? = null,
    conditions: PlayerInkConditions? = null,
    content: @Composable () -> Unit,
) {
    val accent = LocalNowPlayingTint.current ?: MaterialTheme.colorScheme.primary
    // The app's theme, not the player's own: in the light theme a bright band
    // takes dark ink on a white veil instead of light ink on a dark scrim.
    val lightTheme = LocalPlayerLightTheme.current
    // The light or dark verdict reads the main player's band and clip whether or
    // not lyrics or the queue are up, so those screens share it with the player.
    val verdictBand = conditions?.band
    // The band's verdict is held between frames, so a clip hovering around the
    // line doesn't flip the ink back and forth (see [bandInkIsDark]).
    val bandWasDark = remember { mutableStateOf(false) }
    val bandDark = verdictBand?.let { bandInkIsDark(it, bandWasDark.value, lightTheme) }
    SideEffect { if (bandDark != null) bandWasDark.value = bandDark }
    val dark = playerInkDark(
        forcedDark = conditions?.forcedDark,
        overClip = conditions?.overClip == true,
        bandDark = bandDark,
        artLuminance = artLuminance,
    )
    // Told to the app as well, for the ⋯ menu it draws over the player.
    val sleeveTone = LocalPlayerSleeveTone.current
    SideEffect { sleeveTone?.bright = dark }
    // The scrim behind the controls is the main player's alone: with lyrics or
    // the queue up there are no controls on that band to stand it under.
    val panelOpen = conditions?.panelOpen == true
    val band = if (panelOpen) null else verdictBand
    val overClip = conditions?.overClip == true && !panelOpen
    val tint = if (dark) PLAYER_INK_TINT_BRIGHT else PLAYER_INK_TINT
    val darken = if (dark) PLAYER_INK_DARKEN else 0f
    val target = remember(accent, tint, darken) {
        val base = accent.toHsl()
        val cast = if (base.s < GREY_SATURATION) {
            Color.White
        } else {
            lerp(Color.White, Color.hsl(base.h, base.s.coerceAtLeast(0.6f), 0.6f), tint)
        }
        // Toward black in linear proportion: 0.5 leaves half of each channel.
        lerp(cast, Color.Black, darken)
    }
    val ink by animateColorAsState(
        target,
        tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "playerInk",
    )
    val lift by animateFloatAsState(
        targetValue = (conditions?.dim ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "playerInkLift",
    )
    // Where a dimmed backdrop pushes the ink: the light ink toward white, the
    // dark ink toward black, so either keeps its contrast as the backdrop
    // under it goes darker. Crossfades on the ink's own clock.
    val pole by animateColorAsState(
        if (dark) Color.Black else Color.White,
        tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "playerInkPole",
    )
    // Light ink over a bright band gets a darker floor to stand on instead of
    // giving way to dark ink. None under dark ink, which wants the brightness,
    // and less as the player's own dims (which already darken the backdrop) come in.
    // Over a clip whose frame hasn't been read yet, a middling scrim rather than none.
    val scrimBand = band ?: if (overClip) CLIP_UNKNOWN_BAND else null
    val scrimTarget = if (dark || scrimBand == null) {
        0f
    } else {
        controlsScrimAlpha(scrimBand) * (1f - 2f * (conditions?.dim ?: 0f)).coerceIn(0f, 1f)
    }
    val scrim by animateFloatAsState(
        targetValue = scrimTarget,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "playerControlsScrim",
    )
    // Dark ink the band itself called for, in the light theme, gets the white
    // veil. Not a Song fixes pick: that is the listener's call over whatever
    // is there, and it never had a scrim of either kind.
    val veilTarget = if (lightTheme && dark && conditions?.forcedDark == null && verdictBand != null) {
        lightVeilAlpha(verdictBand)
    } else {
        0f
    }
    val veil by animateFloatAsState(
        targetValue = veilTarget,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "playerInkVeil",
    )
    val controlsVeil by animateFloatAsState(
        targetValue = if (panelOpen) 0f else veilTarget * (1f - 2f * (conditions?.dim ?: 0f)).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "playerControlsVeil",
    )
    CompositionLocalProvider(
        LocalPlayerInkVeil provides veil,
        LocalPlayerControlsVeil provides controlsVeil,
        LocalPlayerInk provides lerp(ink, pole.copy(alpha = ink.alpha), lift),
        LocalPlayerInkLift provides lift,
        LocalPlayerInkDark provides dark,
        LocalPlayerControlsScrim provides scrim,
        content = content,
    )
}

/**
 * Whether the player's ink is dark. A Song fixes pick ([forcedDark]) wins. Over a playing
 * clip ([overClip]) it is always light: the clip changes under it faster than a frame read
 * every few seconds can follow, and dark ink caught over a dark scene is unreadable, where
 * light ink on the scrim still reads over a white one. Otherwise the band behind the
 * controls decides ([bandDark]), or the whole sleeve while that is unknown.
 */
internal fun playerInkDark(
    forcedDark: Boolean?,
    overClip: Boolean,
    bandDark: Boolean?,
    artLuminance: Float?,
): Boolean = forcedDark ?: if (overClip) false else bandDark ?: playerInkIsDark(artLuminance)

/** The band brightness the scrim assumes over a clip before a frame of it has been read. */
private const val CLIP_UNKNOWN_BAND = 0.45f

/**
 * Whether a controls band of [band] brightness calls for dark ink, given what it called
 * for last time ([wasDark]). The light ink, stood on [controlsScrimAlpha]'s scrim, reads
 * over everything short of a nearly white band, so only that goes dark: from
 * [BAND_DARK_FROM] up, and it stays dark until the band drops below [BAND_LIGHT_BELOW].
 * The gap between the two is what keeps a moving clip from flickering the ink.
 */
internal fun bandInkIsDark(band: Float, wasDark: Boolean, lightTheme: Boolean = false): Boolean = when {
    lightTheme -> if (wasDark) band >= LIGHT_THEME_BAND_LIGHT_BELOW else band >= LIGHT_THEME_BAND_DARK_FROM
    else -> if (wasDark) band >= BAND_LIGHT_BELOW else band >= BAND_DARK_FROM
}

/**
 * The white veil under dark ink in the light theme, over a band of [band] brightness:
 * none from [BAND_DARK_FROM] up, where the band is white enough on its own, easing up to
 * [LIGHT_VEIL_MAX] at [LIGHT_THEME_BAND_DARK_FROM] and below. Rounded as
 * [controlsScrimAlpha] is.
 */
internal fun lightVeilAlpha(band: Float): Float {
    val t = ((BAND_DARK_FROM - band) / (BAND_DARK_FROM - LIGHT_THEME_BAND_DARK_FROM)).coerceIn(0f, 1f)
    val eased = t * t * (3f - 2f * t)
    return (LIGHT_VEIL_MAX * eased * 50f).roundToInt() / 50f
}

/**
 * The scrim behind light ink over a band of [band] brightness: none over a dark band,
 * easing up from [SCRIM_FROM] to [CONTROLS_SCRIM_MAX] at [SCRIM_FULL_AT]. Rounded to a few
 * steps so a clip's frame-to-frame wobble doesn't re-key the animation.
 */
internal fun controlsScrimAlpha(band: Float): Float {
    val t = ((band - SCRIM_FROM) / (SCRIM_FULL_AT - SCRIM_FROM)).coerceIn(0f, 1f)
    val eased = t * t * (3f - 2f * t)
    return (CONTROLS_SCRIM_MAX * eased * 50f).roundToInt() / 50f
}

/** Band brightness (relative luminance, linear light) where dark ink takes over: near white. */
private const val BAND_DARK_FROM = 0.90f

/** ...and where it hands back to light ink, once it has. */
private const val BAND_LIGHT_BELOW = 0.80f

/**
 * In the light theme, where dark ink takes over: any band on the bright side of the
 * middle (an sRGB grey of about 174), a pastel cover as much as a white one. A light app
 * with a pale sleeve should stay light rather than be darkened until white text reads;
 * the veil ([lightVeilAlpha]) makes up the brightness the band is short of.
 */
private const val LIGHT_THEME_BAND_DARK_FROM = 0.42f

/** ...and where it hands back to light ink there. */
private const val LIGHT_THEME_BAND_LIGHT_BELOW = 0.34f

/**
 * The most white the veil lays down. Over a band at [LIGHT_THEME_BAND_DARK_FROM] it
 * leaves the dark ink at about 4.5:1, the contrast body text is asked for. As strong as
 * the dark scrim is the other way ([CONTROLS_SCRIM_MAX]).
 */
private const val LIGHT_VEIL_MAX = 0.56f

/** Band brightness below which light ink needs no scrim at all (an sRGB grey of about 125). */
private const val SCRIM_FROM = 0.20f

/** Band brightness where the scrim reaches [CONTROLS_SCRIM_MAX]. */
private const val SCRIM_FULL_AT = 0.65f

/**
 * The darkest the scrim gets. Over a band at [BAND_DARK_FROM] it still leaves white text
 * at about 4.5:1, the contrast body text is asked for.
 */
private const val CONTROLS_SCRIM_MAX = 0.56f

/** Whether a sleeve of [artLuminance] is white enough to switch the player to dark ink. */
internal fun playerInkIsDark(artLuminance: Float?): Boolean =
    artLuminance != null && artLuminance >= INK_DARK_FROM

/** How far the ink leans toward the accent: the bright cast on a dark-ink sleeve, else the base. */
internal fun playerInkTint(artLuminance: Float?): Float =
    if (playerInkIsDark(artLuminance)) PLAYER_INK_TINT_BRIGHT else PLAYER_INK_TINT

/** How far toward black the ink goes: [PLAYER_INK_DARKEN] on a dark-ink sleeve, else none. */
internal fun playerInkDarken(artLuminance: Float?): Float =
    if (playerInkIsDark(artLuminance)) PLAYER_INK_DARKEN else 0f

private const val PLAYER_INK_TINT = 0.28f
private const val PLAYER_INK_TINT_BRIGHT = 0.40f

/**
 * How much darker the ink is in its dark form: 57.5% of the way to black, which leaves
 * 85% of what the old 50% left, so dark text sits 15% darker than it used to.
 */
private const val PLAYER_INK_DARKEN = 0.575f

/**
 * Mean relative luminance (linear light) a sleeve must reach before the ink goes dark.
 * 0.7 is roughly an sRGB grey of 218/255 across the whole sleeve, i.e. art that is
 * mostly white, not merely bright: a vivid green or yellow sleeve keeps light ink.
 */
private const val INK_DARK_FROM = 0.70f

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
    // The halo keeps its size under the finger: it already fills the bar's inset at the ends.
    val halo = THUMB_HALO.toPx() * min(scale, 1f)
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
/** Also how far the line is inset from the bar's ends, so the halo is never cut off there. */
internal val THUMB_HALO = 10.dp


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
