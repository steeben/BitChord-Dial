package com.music.bitchord.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A button that gives under the finger: at rest it is lit like a dome, light
 * on top and shaded underneath; pressed, it sinks a little and the light
 * turns over, shade at the top and light at the foot, so it reads as a dish
 * pushed in. A tap too quick to see still sinks all the way before it springs
 * back.
 */
@Stable
internal class PressDent(val interactions: MutableInteractionSource) {
    internal val depth = Animatable(0f)
}

@Composable
internal fun rememberPressDent(): PressDent {
    val dent = remember { PressDent(MutableInteractionSource()) }
    LaunchedEffect(dent) {
        var sinking: Job? = null
        dent.interactions.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> sinking = launch {
                    dent.depth.animateTo(1f, tween(PRESS_IN_MS))
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val inward = sinking
                    launch {
                        inward?.join()
                        dent.depth.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
                    }
                }
            }
        }
    }
    return dent
}

/** The sink: put it outside the button's surface, so the whole button shrinks. */
internal fun Modifier.pressSink(dent: PressDent): Modifier = graphicsLayer {
    val s = 1f - PRESS_SCALE * dent.depth.value
    scaleX = s
    scaleY = s
}

/** The light turning over: put it inside the button's clip, over its surface and under its glyph. */
internal fun Modifier.pressShade(dent: PressDent): Modifier = drawBehind {
    val p = dent.depth.value.coerceIn(0f, 1f)
    val top = lerp(Color.White.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.26f), p)
    val foot = lerp(Color.Black.copy(alpha = 0.10f), Color.White.copy(alpha = 0.14f), p)
    drawRect(Brush.verticalGradient(listOf(top, Color.Transparent, foot)))
}

private const val PRESS_IN_MS = 90
private const val PRESS_SCALE = 0.06f
