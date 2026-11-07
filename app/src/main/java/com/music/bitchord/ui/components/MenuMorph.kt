package com.music.bitchord.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings

/**
 * A menu that opens by turning into it the button (or the pill of buttons) it
 * was opened from, rather than appearing beside it.
 *
 * The card is laid out at its full size, glass and all, at a place that covers
 * its anchor, and is shown through a rounded window that starts as exactly the
 * anchor's bounds and springs out to the whole card. The window's corners
 * keep the card's radius, clamped to half the window's height, so a pill's
 * round ends turn into the card's corners as it grows. The anchor fades away
 * under it (see [MenuMorph] in :sharedUi), and the card's rows fade in once it
 * has room for them; closing plays it all back into the anchor.
 */
internal class MenuMorphGeometry {
    /** The anchor, in the card's own coordinates, or null to open from its top end. */
    var source: Rect? = null
}

/** The rounded window at [rect] within the card. */
private data class MorphWindow(val rect: Rect, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}

/** How far the window has grown, for a card of [size], at [progress] (overshoot clamped). */
internal fun morphWindow(source: Rect?, size: Size, progress: Float): Rect {
    val full = Rect(Offset.Zero, size)
    // With no anchor, a pill-sized nub at the top end of the card.
    val from = (source ?: Rect(size.width - size.height.coerceAtMost(48f), 0f, size.width, size.height.coerceAtMost(48f)))
        .let {
            // Kept within the card: the glass is only drawn there.
            Rect(
                it.left.coerceIn(0f, size.width),
                it.top.coerceIn(0f, size.height),
                it.right.coerceIn(0f, size.width),
                it.bottom.coerceIn(0f, size.height),
            )
        }
    val p = progress.coerceIn(0f, 1f)
    return Rect(
        lerp(from.left, full.left, p),
        lerp(from.top, full.top, p),
        lerp(from.right, full.right, p),
        lerp(from.bottom, full.bottom, p),
    )
}

/**
 * The card's layer at [progress]: the growing window, the overshoot of the
 * spring as a slight swell about the anchor, and a quick fade at either end so
 * a small anchor's glass dot doesn't pop. [reduceMotion] leaves only a fade.
 */
internal fun GraphicsLayerScope.applyMenuMorph(
    source: Rect?,
    cardSize: Size,
    progress: Float,
    cornerRadius: Float,
    reduceMotion: Boolean,
    shadowElevationPx: Float = 0f,
) {
    if (reduceMotion) {
        alpha = progress.coerceIn(0f, 1f)
        return
    }
    val window = morphWindow(source, cardSize, progress)
    shape = MorphWindow(window, cornerRadius.coerceAtMost(window.minDimension / 2f))
    clip = true
    alpha = (progress / MORPH_FADE).coerceIn(0f, 1f)
    if (shadowElevationPx > 0f) {
        shadowElevation = shadowElevationPx * progress.coerceIn(0f, 1f)
        ambientShadowColor = Color.Black.copy(alpha = 0.30f)
        spotShadowColor = Color.Black.copy(alpha = 0.30f)
    }
    val swell = (progress - 1f).coerceAtLeast(0f) * MORPH_SWELL
    scaleX = 1f + swell
    scaleY = 1f + swell
    if (source != null && cardSize.width > 0f && cardSize.height > 0f) {
        transformOrigin = TransformOrigin(
            (source.center.x / cardSize.width).coerceIn(0f, 1f),
            (source.center.y / cardSize.height).coerceIn(0f, 1f),
        )
    }
}

/**
 * The glass rim along the growing window, so it reads as the anchor's pill
 * (which wears one) until it has opened, then gives way to the card's own.
 */
internal fun Modifier.menuMorphRim(
    geometry: MenuMorphGeometry,
    progress: () -> Float,
    cornerRadius: Float,
    reduceMotion: Boolean,
): Modifier = if (reduceMotion) this else drawWithContent {
    drawContent()
    val p = progress()
    if (p < 1f) {
        val window = morphWindow(geometry.source, size, p)
        val radius = cornerRadius.coerceAtMost(window.minDimension / 2f)
        val stroke = GLASS_EDGE_WIDTH.toPx()
        drawRoundRect(
            color = GLASS_EDGE_COLOR.copy(alpha = GLASS_EDGE_COLOR.alpha * (1f - p.coerceIn(0f, 1f))),
            topLeft = window.topLeft,
            size = window.size,
            cornerRadius = CornerRadius(radius),
            style = Stroke(stroke),
        )
    }
}

/** The card's rows: in once the window has room for them, out first on the way back. */
internal fun Modifier.menuMorphContent(progress: () -> Float, reduceMotion: Boolean): Modifier =
    if (reduceMotion) this else graphicsLayer {
        alpha = ((progress() - CONTENT_FROM) / (1f - CONTENT_FROM - CONTENT_SETTLED)).coerceIn(0f, 1f)
    }

/** Opening: a spring with a little give, as the app's other glass grows. */
internal fun menuMorphOpenSpec(reduceMotion: Boolean): AnimationSpec<Float> =
    if (reduceMotion) tween(durationMillis = 120, easing = LinearEasing)
    else spring(dampingRatio = 0.78f, stiffness = 420f, visibilityThreshold = 0.001f)

/** Closing: critically damped and quicker, back into the anchor. */
internal fun menuMorphCloseSpec(reduceMotion: Boolean): AnimationSpec<Float> =
    if (reduceMotion) tween(durationMillis = 100, easing = LinearEasing)
    else spring(dampingRatio = 1f, stiffness = 700f, visibilityThreshold = 0.002f)

/**
 * Runs [progress] to [open] and publishes each frame to [MenuMorph] for the
 * anchor at [anchor] (window coordinates) to fade by.
 */
internal suspend fun Animatable<Float, AnimationVector1D>.runMenuMorph(open: Boolean, anchor: Rect?, reduceMotion: Boolean) {
    animateTo(if (open) 1f else 0f, if (open) menuMorphOpenSpec(reduceMotion) else menuMorphCloseSpec(reduceMotion)) {
        if (anchor != null) MenuMorph.publish(anchor, value)
    }
    if (!open) MenuMorph.clear(anchor)
}

/** The app's "Reduce animation", or the system's animator scale at zero. */
@Composable
internal fun menuReduceMotion(): Boolean {
    val context = LocalContext.current
    val appReduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val systemAnimationsOff = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    return appReduceAnimation || systemAnimationsOff
}

/** The share of the opening over which the card itself fades in. */
private const val MORPH_FADE = 0.12f

/** How much of the spring's overshoot shows as swell. */
private const val MORPH_SWELL = 0.6f

/** The rows start to show once the window is this far open… */
private const val CONTENT_FROM = 0.35f

/** …and are fully in this far short of the end. */
private const val CONTENT_SETTLED = 0.1f
