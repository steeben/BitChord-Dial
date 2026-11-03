package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * A menu or card that opens from the control that summoned it: the scrim fades
 * in while the card grows out of [origin] (a point on the card itself, so
 * `TransformOrigin(1f, 0f)` for one opened by a button at the top right) and
 * shrinks back into it on the way out. Composes nothing while hidden.
 */
@Composable
fun PopupLayer(
    visible: Boolean,
    onDismiss: () -> Unit,
    contentAlignment: Alignment,
    origin: TransformOrigin,
    scrim: Color = MaterialTheme.colorScheme.scrim.copy(alpha = .48f),
    card: @Composable () -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState && state.isIdle) return
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visibleState = state,
            enter = fadeIn(tween(POPUP_SCRIM_IN_MS)),
            exit = fadeOut(tween(POPUP_OUT_MS)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrim)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = contentAlignment) {
            AnimatedVisibility(
                visibleState = state,
                enter = fadeIn(tween(POPUP_FADE_IN_MS)) + scaleIn(
                    initialScale = 0.82f,
                    transformOrigin = origin,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                ),
                exit = fadeOut(tween(POPUP_OUT_MS)) + scaleOut(
                    targetScale = 0.9f,
                    transformOrigin = origin,
                    animationSpec = tween(POPUP_OUT_MS),
                ),
            ) {
                // The scrim is not in the backdrop the card's glass samples.
                CompositionLocalProvider(LocalGlassUnderlay provides scrim) { card() }
            }
        }
    }
}

/**
 * The material a popup card is made of, matching the rest of the app: liquid
 * glass when that is on, the navbar's frosted blur when it is not, and solid
 * under "reduce dynamic blur".
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.popupSurface(shape: CornerBasedShape, hazeState: HazeState): Modifier {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glass = LocalLiquidGlassEnabled.current && isGlassSupported()
    return clip(shape).then(
        when {
            reduceDynamicBlur -> Modifier.background(MaterialTheme.colorScheme.surface)
            glass -> Modifier.liquidGlass(shape)
            else -> Modifier.optimizedHazeEffect(
                state = hazeState,
                style = HazeMaterials.thin(MaterialTheme.colorScheme.surface),
            )
        },
    )
}

/** What text and icons on a [popupSurface] are drawn in. */
@Composable
fun popupContentColor(): Color {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glass = LocalLiquidGlassEnabled.current && isGlassSupported() && !reduceDynamicBlur
    return if (glass) glassContentColor() else MaterialTheme.colorScheme.onSurface
}

private const val POPUP_SCRIM_IN_MS = 200
private const val POPUP_FADE_IN_MS = 140
private const val POPUP_OUT_MS = 150
