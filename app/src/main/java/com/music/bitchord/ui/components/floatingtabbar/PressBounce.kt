package com.music.bitchord.ui.components.floatingtabbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A tab glyph pushing in under the finger and springing back past its size on
 * release, once for every press [interactionSource] reports.
 *
 * Driven by the press events rather than by a pressed state: a quick tap is a
 * press and a release a frame or two apart, and a scale animated towards
 * `isPressed` would barely have moved before turning round. Here the push-in
 * always plays out in full and the bounce back waits for it.
 */
@Composable
internal fun Modifier.pressBounce(
    interactionSource: InteractionSource,
    pressedScale: Float = PRESSED_SCALE,
): Modifier {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        coroutineScope {
            var pushIn: Job? = null
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pushIn = launch {
                            scale.animateTo(pressedScale, tween(PUSH_IN_MS, easing = FastOutSlowInEasing))
                        }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        val pressing = pushIn
                        launch {
                            pressing?.join()
                            scale.animateTo(
                                1f,
                                spring(dampingRatio = BOUNCE_DAMPING, stiffness = BOUNCE_STIFFNESS),
                            )
                        }
                    }
                }
            }
        }
    }
    return graphicsLayer {
        val s = scale.value
        scaleX = s
        scaleY = s
    }
}

// Deep and unhurried: the glyph sinks to two thirds of its size, then springs
// back slowly enough that the overshoot past full size is plainly visible.
private const val PRESSED_SCALE = 0.66f
private const val PUSH_IN_MS = 170
private const val BOUNCE_DAMPING = 0.3f
private const val BOUNCE_STIFFNESS = Spring.StiffnessMediumLow
