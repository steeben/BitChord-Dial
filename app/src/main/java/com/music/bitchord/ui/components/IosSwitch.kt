package com.music.bitchord.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppStyle
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.ui.theme.LocalNowPlayingTint
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics

/**
 * The iOS 26 switch, as BitChord 1.9.0 has it — a wide capsule and a pill of a
 * thumb that stretches toward where it is going while pressed — in frosted
 * glass: the green track is see-through, both pieces catch a sheen across
 * their top and wear a light rim, and the thumb is milky rather than solid
 * white, clearing further while held, the way iOS's lifts into a lens.
 *
 * Plain drawing only, no backdrop capture or blur. Every settings page has a
 * column of these, and anything heavier per switch is paid that many times
 * over on every scroll frame. How see-through it is follows the Liquid Glass
 * transparency option while Liquid Glass is on, so it matches the menus.
 *
 * It takes Material3's `Switch` call shape so a settings row adopts it by name
 * alone. [thumbContent] and [colors] are accepted and ignored: on is iOS green
 * whatever the theme's accent, or the playing song's accent while one plays.
 *
 * A null [onCheckedChange] makes it a read-out, the way M3's does: the row
 * around it owns the tap. Interactive, it takes up M3's 48dp touch target
 * around the drawn capsule, which the settings rows are spaced around.
 *
 * It buzzes when [checked] flips rather than when it is tapped: most rows also
 * toggle from their own `onClick`, which never passes through here.
 */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: SwitchColors? = null,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val density = rememberFrostedGlassDensity()

    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 700f),
        label = "iosSwitchThumb",
    )
    val stretch by animateFloatAsState(
        targetValue = if (pressed) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "iosSwitchStretch",
    )
    val thumbClarity by animateFloatAsState(
        targetValue = if (pressed) 0.55f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 900f),
        label = "iosSwitchThumbClarity",
    )
    val track by animateColorAsState(
        targetValue = when {
            checked -> frostedOnTint(dark, density, LocalNowPlayingTint.current)
            else -> frostedTrackTint(dark, density)
        },
        label = "iosSwitchTrack",
    )
    val trackRim = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.55f)
    val thumbFill = Color.White.copy(alpha = thumbAlpha(dark) * thumbClarity)
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr

    val haptics = rememberHaptics()
    // Skips the first run, so a switch scrolling into view stays quiet.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        if (settled) haptics.play(if (checked) Haptic.ToggleOn else Haptic.ToggleOff)
        settled = true
    }

    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .then(
                if (onCheckedChange != null) {
                    Modifier.minimumInteractiveComponentSize().toggleable(
                        value = checked,
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            )
            .size(TrackWidth, TrackHeight)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(color = track, cornerRadius = radius)
                drawRoundRect(brush = TRACK_SHEEN, cornerRadius = radius)
            }
            .border(GLASS_EDGE_WIDTH, trackRim, CircleShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    val travel = (TrackWidth - ThumbWidth - ThumbPadding * 2).toPx()
                    val x = ThumbPadding.toPx() + travel * position
                    translationX = if (ltr) x else -x
                    // Grows from the edge it is leaving, so the stretch reads
                    // as reaching toward the other side.
                    transformOrigin = TransformOrigin(if (checked == ltr) 1f else 0f, 0.5f)
                    scaleX = stretch
                }
                .size(ThumbWidth, ThumbHeight)
                .shadow(1.5.dp, CircleShape, clip = false)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(color = thumbFill, cornerRadius = radius)
                    drawRoundRect(brush = THUMB_SHEEN, cornerRadius = radius)
                }
                .border(GLASS_EDGE_WIDTH, THUMB_RIM, CircleShape),
        )
    }
}

/**
 * How dense the frosted controls (this switch, the settings' segmented
 * selectors) are drawn: the glass transparency option, measured from where it
 * ships, while Liquid Glass is on and follows the options; 1 otherwise.
 * Denser glass gives a denser control, clearer glass a clearer one.
 */
@Composable
internal fun rememberFrostedGlassDensity(): Float {
    val dark = MaterialTheme.colorScheme.surface.luminance() <= 0.5f
    val transparency by CustomizationSettings.glassOptions(clear = false, dark = dark).transparency
        .collectAsStateWithLifecycle()
    return if (appStyle() == AppStyle.GLASS && LocalGlassFollowsOptions.current) {
        ((1f - transparency) / (1f - CustomizationSettings.DEFAULT_GLASS_TRANSPARENCY)).coerceIn(0.55f, 1.3f)
    } else {
        1f
    }
}

/**
 * The switch's frosted film on any shape: a see-through [tint], the sheen
 * across the top and a light rim. Plain drawing, no backdrop capture, so a
 * page full of them costs nothing extra per scroll frame.
 */
internal fun Modifier.frostedGlassFill(tint: Color, shape: Shape, dark: Boolean): Modifier =
    this
        .clip(shape)
        .background(tint, shape)
        .background(TRACK_SHEEN, shape)
        .border(GLASS_EDGE_WIDTH, if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.55f), shape)

/** How milky the thumb is at rest. */
private fun thumbAlpha(dark: Boolean): Float = if (dark) 0.84f else 0.9f

/**
 * The switch's thumb on any shape — its milky white fill, the bead-of-glass
 * sheen and the bright rim, with the same small shadow — so a segmented
 * selector's chosen option looks like the white part of a switch beside it.
 * [presence] fades the whole thing in and out (0 draws nothing).
 */
internal fun Modifier.frostedThumbFill(shape: Shape, dark: Boolean, presence: Float = 1f): Modifier {
    if (presence <= 0f) return this.clip(shape)
    return this
        .shadow(1.5.dp * presence, shape, clip = false)
        .clip(shape)
        .background(Color.White.copy(alpha = thumbAlpha(dark) * presence), shape)
        .drawBehind { drawRect(THUMB_SHEEN, alpha = presence) }
        .border(GLASS_EDGE_WIDTH, THUMB_RIM.copy(alpha = THUMB_RIM.alpha * presence), shape)
}

/** The tint of a frosted track that is off, or empty: a faint film either side of the theme. */
internal fun frostedTrackTint(dark: Boolean, density: Float): Color =
    if (dark) Color.White.copy(alpha = (0.16f * density).coerceIn(0f, 1f))
    else Color.Black.copy(alpha = (0.09f * density).coerceIn(0f, 1f))

/**
 * The fill of the switch's track while it is on: the playing song's accent
 * ([nowPlaying]), so the switches take on the song as the Home mark and the
 * selected tab do, and iOS green while nothing plays.
 */
internal fun frostedOnTint(dark: Boolean, density: Float, nowPlaying: Color? = null): Color =
    (nowPlaying ?: if (dark) Color(0xFF30D158) else Color(0xFF34C759))
        .copy(alpha = (ON_TRACK_ALPHA * density).coerceIn(0f, 1f))

/** How opaque a frosted "on" fill is at the shipped glass transparency: enough to read as on, not solid. */
internal const val ON_TRACK_ALPHA = 0.78f

/** The light the track catches across its top, as the glass buttons do. */
private val TRACK_SHEEN = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.20f),
    0.55f to Color.White.copy(alpha = 0.03f),
    1f to Color.Transparent,
)

/** The thumb's sheen: brighter at the top, a little shade at the foot, like a bead of glass. */
private val THUMB_SHEEN = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.55f),
    0.5f to Color.White.copy(alpha = 0.08f),
    1f to Color.Black.copy(alpha = 0.06f),
)

private val THUMB_RIM = Color.White.copy(alpha = 0.7f)

private val TrackWidth = 64.dp
private val TrackHeight = 28.dp
private val ThumbWidth = 40.dp
private val ThumbHeight = 24.dp
private val ThumbPadding = 2.dp
