package com.music.bitchord.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * A menu or card opened from a top bar button, which opens by turning the
 * bar's pill of buttons into itself: the card settles over the pill (its top
 * and trailing edges on the pill's), the pill fades away under it, and the
 * card is revealed through a window that springs out of the pill's bounds —
 * see [applyMenuMorph]. Its rows fade in as it opens and out as it folds back.
 * With no pill to grow from (opened some other way) it takes its place under
 * the bar at the trailing end and grows out of its top corner.
 *
 * [surface] is the card's material, sized to the whole card; [fullWidth]
 * spans the card across the page, with as much room either side as the pill
 * has at its end. Composes nothing while hidden.
 */
@Composable
fun PopupLayer(
    visible: Boolean,
    onDismiss: () -> Unit,
    shape: CornerBasedShape,
    contentColor: Color,
    surface: @Composable () -> Modifier,
    fullWidth: Boolean = false,
    scrim: Color = MaterialTheme.colorScheme.scrim.copy(alpha = .48f),
    card: @Composable () -> Unit,
) {
    // The pill this opening was a tap on, kept through the close so the card
    // can fold back into it.
    val anchor = remember { PopupAnchor() }
    remember(visible) {
        if (visible) anchor.bounds = MenuAnchor.consume()
        visible
    }
    var closed by remember { mutableStateOf(!visible) }
    if (closed && !visible) return

    val reduceMotion = menuReduceMotion()
    val progress = remember { Animatable(0f) }
    val menuAnchor = anchor.bounds
    LaunchedEffect(visible) {
        if (visible) closed = false
        progress.runMenuMorph(visible, menuAnchor, reduceMotion)
        if (!visible) closed = true
    }
    BackHandler(enabled = visible, onBack = onDismiss)

    val geometry = remember { MenuMorphGeometry() }
    val insets = WindowInsets.safeDrawing
    val statusBar = WindowInsets.statusBars
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }

    Layout(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOffset = it.positionInWindow() },
        content = {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                    .background(scrim)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        enabled = visible,
                        onClick = onDismiss,
                    ),
            )
            // The scrim is not in the backdrop the card's glass samples.
            CompositionLocalProvider(LocalGlassUnderlay provides scrim) {
                Surface(
                    color = Color.Transparent,
                    contentColor = contentColor,
                    shape = shape,
                    modifier = Modifier
                        .then(if (fullWidth) Modifier else Modifier.width(IntrinsicSize.Max))
                        .then(surface())
                        .menuMorphRim(
                            geometry,
                            { progress.value },
                            with(LocalDensity.current) { POPUP_CORNER_HINT.toPx() },
                            reduceMotion,
                        ),
                ) {
                    Box(Modifier.menuMorphContent({ progress.value }, reduceMotion)) { card() }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = PAGE_GUTTER.roundToPx()
        val top = insets.getTop(this)
        val bottom = height - insets.getBottom(this) - 12.dp.roundToPx()
        val pill = menuAnchor?.translate(-overlayOffset)

        val scrimPlaceable = measurables[0].measure(Constraints.fixed(width, height))
        // Full width keeps the pill's own distance from the edge on both sides.
        val side = pill?.let { (width - it.right.roundToInt()).coerceIn(0, width / 4) } ?: margin
        val maxHeight = (bottom - top).coerceAtLeast(0)
        val card = measurables[1].measure(
            if (fullWidth) {
                Constraints(minWidth = width - side * 2, maxWidth = width - side * 2, maxHeight = maxHeight)
            } else {
                Constraints(maxWidth = (width - margin * 2).coerceAtLeast(0), maxHeight = maxHeight)
            },
        )
        val right = pill?.right?.roundToInt() ?: (width - side)
        val cardX = if (fullWidth) {
            side
        } else {
            (right - card.width).coerceIn(margin.coerceAtMost(width - card.width), (width - card.width).coerceAtLeast(0))
        }
        val lowest = (bottom - card.height).coerceAtLeast(top)
        val cardY = pill?.top?.roundToInt()?.coerceIn(top, lowest)
            ?: (statusBar.getTop(this) + TopBarContentHeight.roundToPx()).coerceAtMost(lowest)
        val cardSize = Size(card.width.toFloat(), card.height.toFloat())
        val radius = shape.topStart.toPx(cardSize, this)
        // With no pill, a button-sized nub at the card's top end.
        val nub = 44.dp.toPx()
        geometry.source = pill?.translate(-cardX.toFloat(), -cardY.toFloat())
            ?: Rect(cardSize.width - nub, 0f, cardSize.width, nub)

        layout(width, height) {
            scrimPlaceable.place(0, 0)
            card.placeWithLayer(cardX, cardY) {
                applyMenuMorph(geometry.source, cardSize, progress.value, radius, reduceMotion)
            }
        }
    }
}

private class PopupAnchor {
    var bounds: Rect? = null
}

/** The rim's corner while the card opens; a top bar card's is the theme's extra large. */
private val POPUP_CORNER_HINT = 28.dp

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

