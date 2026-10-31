package com.music.bitchord.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.CustomizationSettings
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
 * whatever the theme's accent, which is what makes it read as a switch.
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

    // The glass transparency option, measured from where it ships: denser
    // glass gives a denser switch, clearer glass a clearer one.
    val transparency by CustomizationSettings.glassTransparency.collectAsStateWithLifecycle()
    val density = if (LocalLiquidGlassEnabled.current && LocalGlassFollowsOptions.current) {
        ((1f - transparency) / (1f - CustomizationSettings.DEFAULT_GLASS_TRANSPARENCY)).coerceIn(0.55f, 1.3f)
    } else {
        1f
    }

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
            checked -> (if (dark) Color(0xFF30D158) else Color(0xFF34C759))
                .copy(alpha = (ON_TRACK_ALPHA * density).coerceIn(0f, 1f))
            dark -> Color.White.copy(alpha = (0.16f * density).coerceIn(0f, 1f))
            else -> Color.Black.copy(alpha = (0.09f * density).coerceIn(0f, 1f))
        },
        label = "iosSwitchTrack",
    )
    val trackRim = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.55f)
    val thumbFill = Color.White.copy(alpha = (if (dark) 0.84f else 0.9f) * thumbClarity)
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

/** How opaque the green is at the shipped glass transparency: enough to read as on, not solid. */
private const val ON_TRACK_ALPHA = 0.78f

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
