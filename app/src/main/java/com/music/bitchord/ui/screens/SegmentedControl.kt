package com.music.bitchord.ui.screens

import android.os.Build
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AppStyle
import com.music.bitchord.ui.components.GLASS_EDGE_WIDTH
import com.music.bitchord.ui.components.LocalLiquidGlassEnabled
import com.music.bitchord.ui.components.THUMB_RIM
import com.music.bitchord.ui.components.THUMB_SHEEN
import com.music.bitchord.ui.components.appStyle
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.music.bitchord.ui.components.backdrop.drawBackdrop
import com.music.bitchord.ui.components.backdrop.effects.lens
import com.music.bitchord.ui.components.backdrop.highlight.Highlight
import com.music.bitchord.ui.components.backdrop.internal.ShapeProvider
import com.music.bitchord.ui.components.backdrop.shadow.Shadow
import com.music.bitchord.ui.components.backdrop.shadow.ShadowElement
import com.music.bitchord.ui.components.floatingtabbar.GlassPillMotion
import com.music.bitchord.ui.components.floatingtabbar.pillPlacement
import com.music.bitchord.ui.components.frostedGlassFill
import com.music.bitchord.ui.components.frostedTrackTint
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.components.lightweightLiquidGlass
import com.music.bitchord.ui.components.rememberFrostedGlassDensity
import com.music.bitchord.ui.components.thumbAlpha
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The app's selector for a handful of choices: the search sources, and the
 * settings with two to four states.
 *
 * It moves as the navigation bar's selection does, on the same physics
 * ([GlassPillMotion]). One thumb marks the choice. A tap sends it to the
 * option tapped: it lifts, travels on a spring, stretches as it sets off and
 * gathers itself as it brakes, and comes down where it lands. A finger can
 * also pick it up and drag it along the track; it settles on the option it
 * is let go over.
 *
 * In the Glass style the lifted thumb is a bead of clear glass: its milky
 * fill thins, it takes a rim and a shadow, and the labels under it are
 * magnified and bent ([SelectorLens]). The Frost and Solid styles bend
 * nothing, so the thumb carries the motion as it is. With Reduce animations
 * on (and so in Performance mode) the thumb is on the new option at once.
 *
 * The thumb is one shape that moves. Each option used to fade a thumb of its
 * own in and out, with a shadow under a see-through fill: mid-fade the
 * shadow showed through as a grey box around the label, and the press ripple
 * flashed over it.
 */
@Composable
internal fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** A glass track (liquid glass when that is on, a frosted film otherwise) for over the page's backdrop. */
    glass: Boolean = false,
) {
    val haptics = rememberHaptics()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glassDensity = rememberFrostedGlassDensity()
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    // The lens is liquid glass's own, and its shader needs Android 13.
    val lens = LocalLiquidGlassEnabled.current && isGlassSupported() && appStyle() == AppStyle.GLASS &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val count = options.size
    // Nothing is marked when the choice is not one of the options.
    val shown = if (selectedIndex in 0 until count) selectedIndex else -1
    val currentShown by rememberUpdatedState(shown)
    val currentOnSelect by rememberUpdatedState(onSelect)

    val scope = rememberCoroutineScope()
    val pill = remember { GlassPillMotion(scope, shown.coerceAtLeast(0)) }
    pill.reduceMotion = reduceAnimation

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    var lastShown by remember { mutableIntStateOf(shown) }
    var lastTick by remember { mutableIntStateOf(shown) }
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val gapPx = with(density) { SEGMENT_GAP.toPx() }
    val padPx = with(density) { TRACK_PADDING.toPx() }
    val cellWidthPx = if (rowSize.width > 0 && count > 0) (rowSize.width - gapPx * (count - 1)) / count else 0f
    val stepPx = if (rowSize.width > 0 && count > 0) (rowSize.width + gapPx) / count else 0f
    pill.stepDp = stepPx / density.density

    // The thumb rests in its option's cell, and lifts to stand a little past
    // the track on every side.
    val rowHeightPx = rowSize.height.toFloat()
    val restSize = Size(cellWidthPx, rowHeightPx)
    val growPx = padPx * 2 + with(density) { THUMB_GROW.toPx() }
    val liftedSize = Size(cellWidthPx + growPx, rowHeightPx + growPx)
    // Where the thumb's centre is among the labels, for a position in option steps.
    val centerInRow: (Float) -> Offset = { position ->
        val x = position * stepPx + cellWidthPx / 2f
        Offset(if (isRtl) rowSize.width - x else x, rowHeightPx / 2f)
    }
    val showThumb = shown >= 0 && cellWidthPx > 0f
    val lensUp = lens && showThumb && !pill.isFlat

    LaunchedEffect(shown) {
        if (shown >= 0) {
            // From nothing marked there is no option to travel from.
            if (lastShown < 0) pill.snapTo(shown) else pill.animateTo(shown)
        }
        lastShown = shown
        lastTick = shown
    }
    // A different set of options has different places: no travel between them.
    LaunchedEffect(count) {
        if (shown >= 0) pill.snapTo(shown)
    }

    // How much of the white thumb is under option [index]: its label is dark
    // on the thumb and the theme's own colour off it. A lifted glass thumb is
    // clear, so the label under it keeps the theme's colour until it lands.
    val cover: (Int) -> Float = { index ->
        if (!showThumb) {
            0f
        } else {
            (1f - abs(pill.position - index)).coerceIn(0f, 1f) * (if (lens) 1f - pill.material else 1f)
        }
    }

    val labels = rememberLayerBackdrop()
    val cutout = remember { Path() }
    val thumbShadow = remember(lens) {
        ShadowElement(ShapeProvider { ThumbShape }) {
            // The lifted glass has a shadow of its own.
            val alpha = if (lens) 1f - pill.material else 1f
            if (alpha > 0f) THUMB_SHADOW.copy(alpha = alpha) else null
        }
    }
    val labelStyle = MaterialTheme.typography.labelMedium
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Not clipped: the lifted thumb stands past the track's edges.
    Box(modifier.fillMaxWidth()) {
        // Frosted like the settings' switches: a see-through track with a
        // sheen and a light rim.
        Box(
            Modifier
                .matchParentSize()
                .then(
                    if (glass) {
                        Modifier.lightweightLiquidGlass(
                            shape = TrackShape,
                            fallbackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        )
                    } else {
                        Modifier.frostedGlassFill(frostedTrackTint(dark, glassDensity), TrackShape, dark)
                    },
                ),
        )

        // The thumb: made like the switches' white thumb, under the labels.
        if (showThumb) {
            Box(
                Modifier
                    .matchParentSize()
                    .pillPlacement(
                        center = { centerInRow(pill.position) + Offset(padPx, padPx) },
                        size = { pill.liveSize(restSize, liftedSize) },
                    )
                    .then(thumbShadow)
                    .drawBehind {
                        val lifted = if (lens) pill.material else 0f
                        val rest = thumbAlpha(dark)
                        val radius = CornerRadius(THUMB_CORNER.toPx())
                        drawRoundRect(
                            color = Color.White.copy(alpha = rest + (liftedThumbAlpha(dark) - rest) * lifted),
                            cornerRadius = radius,
                        )
                        drawRoundRect(brush = THUMB_SHEEN, cornerRadius = radius, alpha = 1f - 0.5f * lifted)
                        val edge = GLASS_EDGE_WIDTH.toPx()
                        drawRoundRect(
                            color = THUMB_RIM,
                            topLeft = Offset(edge / 2f, edge / 2f),
                            size = Size(size.width - edge, size.height - edge),
                            cornerRadius = radius,
                            style = Stroke(edge),
                        )
                    },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Enough of a margin for the track to read as a track. At 2dp
                // the thumb sat all but flush against the track's own edge, so
                // the two rounded rectangles merged into one shape.
                .padding(TRACK_PADDING)
                .onSizeChanged { rowSize = it }
                .drawWithContent {
                    if (lensUp) {
                        // The lens draws the labels it is over, magnified. Here
                        // they are left out, or they would show twice.
                        val centre = centerInRow(pill.position)
                        val live = pill.liveSize(restSize, liftedSize)
                        cutout.reset()
                        cutout.addRoundRect(
                            RoundRect(
                                Rect(
                                    offset = Offset(centre.x - live.width / 2f, centre.y - live.height / 2f),
                                    size = live,
                                ),
                                CornerRadius(THUMB_CORNER.toPx()),
                            ),
                        )
                        clipPath(cutout, clipOp = ClipOp.Difference) { this@drawWithContent.drawContent() }
                    } else {
                        drawContent()
                    }
                }
                .then(if (lens) Modifier.layerBackdrop(labels) else Modifier)
                .pointerInput(count, stepPx, isRtl, enabled, shown >= 0) {
                    if (!enabled || currentShown < 0 || stepPx <= 0f) return@pointerInput

                    // In option order, whichever way the row runs.
                    val direction = if (isRtl) -1f else 1f
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            totalDrag = 0f
                            pill.startDrag()
                        },
                        onDragCancel = { pill.release(currentShown.coerceAtLeast(0)) },
                        onDragEnd = {
                            val from = currentShown.coerceAtLeast(0)
                            val ratio = totalDrag / stepPx
                            val shift = when {
                                ratio > 0.35f -> maxOf(1, ratio.roundToInt())
                                ratio < -0.35f -> minOf(-1, ratio.roundToInt())
                                else -> 0
                            }
                            val next = (from + shift).coerceIn(0, count - 1)
                            pill.release(next)
                            if (next != from) currentOnSelect(next)
                        },
                        onHorizontalDrag = { _, delta ->
                            val from = currentShown.coerceAtLeast(0)
                            totalDrag += delta * direction
                            // Past either end the thumb gives a little and no more.
                            val offset = when {
                                totalDrag > 0 && from == count - 1 -> totalDrag * 0.25f
                                totalDrag < 0 && from == 0 -> totalDrag * 0.25f
                                else -> totalDrag
                            }
                            pill.dragTo(from + offset / stepPx)

                            val over = (from + offset / stepPx).coerceIn(0f, (count - 1).toFloat()).roundToInt()
                            if (over != lastTick) {
                                haptics.play(Haptic.Tick)
                                lastTick = over
                            }
                        },
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            options.forEachIndexed { index, label ->
                val chosen = index == shown
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // No ripple: the thumb's own travel answers the tap.
                        .selectable(
                            selected = chosen,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = enabled,
                            role = Role.Tab,
                        ) {
                            if (!chosen) {
                                haptics.play(Haptic.Select)
                                onSelect(index)
                            }
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // The label twice, one colour over the other, so its change
                    // of colour under the thumb is a redraw and nothing more.
                    Text(
                        text = label,
                        style = labelStyle,
                        color = idleColor,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer {
                            alpha = 1f - cover(index)
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                    )
                    Text(
                        text = label,
                        style = labelStyle,
                        // Dark on the white thumb, as the switch's thumb would carry it.
                        color = Color.Black.copy(alpha = 0.87f),
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer {
                            alpha = cover(index)
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                    )
                }
            }
        }

        if (lensUp) {
            SelectorLens(
                motion = pill,
                labels = labels,
                modifier = Modifier
                    .matchParentSize()
                    .pillPlacement(
                        center = { centerInRow(pill.position) + Offset(padPx, padPx) },
                        size = { pill.liveSize(restSize, liftedSize) },
                    ),
            )
        }
    }
}

/**
 * The lifted thumb's glass, drawn over the labels: a lens that shows the
 * labels under it magnified and bent at its edge, inside a rim and over a
 * shadow. The rim and the shadow are the first things shed on landing and the
 * bend eases out behind them, so at the end it draws the labels as they are,
 * which is what the row under it draws when the lens is gone.
 */
@Composable
private fun SelectorLens(
    motion: GlassPillMotion,
    labels: LayerBackdrop,
    modifier: Modifier,
) {
    val backdrop = remember(labels, motion) {
        MagnifiedBackdrop(labels) { 1f + LENS_MAGNIFY * motion.material }
    }
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { ThumbShape },
            effects = {
                lens(
                    refractionHeight = LENS_HEIGHT.toPx() * motion.glassPresence,
                    refractionAmount = LENS_AMOUNT.toPx() * motion.material,
                    chromaticAberration = true,
                )
            },
            highlight = {
                val alpha = motion.material * rimOf(motion.glassPresence)
                if (alpha > 0f) Highlight.Default.copy(alpha = alpha) else null
            },
            shadow = {
                val alpha = motion.material * rimOf(motion.glassPresence)
                if (alpha > 0f) LENS_SHADOW.copy(alpha = alpha) else null
            },
        ),
    )
}

/** The rim and its shadow are gone by the time the landing is 55% through. */
private fun rimOf(presence: Float): Float = ((presence - 0.45f) / 0.55f).coerceIn(0f, 1f)

/** [content] drawn scaled up about the centre of what draws it, by [magnify]. */
private class MagnifiedBackdrop(
    private val content: Backdrop,
    private val magnify: () -> Float,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val factor = magnify()
        if (factor != 1f && coordinates != null) {
            // The canvas origin here is the lens's own top left.
            val pivot = Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
            scale(factor, pivot) { with(content) { drawBackdrop(density, coordinates, layerBlock) } }
        } else {
            with(content) { drawBackdrop(density, coordinates, layerBlock) }
        }
    }
}

/** How milky the thumb's fill is while it is lifted glass: a trace, so the bead still reads as a thing. */
private fun liftedThumbAlpha(dark: Boolean): Float = if (dark) 0.12f else 0.35f

private val TRACK_PADDING = 4.dp
private val SEGMENT_GAP = 2.dp

/** How far past the track the lifted thumb stands, top and bottom together. */
private val THUMB_GROW = 6.dp

private val THUMB_CORNER = 8.dp
private val TrackShape = RoundedCornerShape(10.dp)
private val ThumbShape = RoundedCornerShape(THUMB_CORNER)

/** The resting thumb's shadow: drawn around it only, never under its see-through fill. */
private val THUMB_SHADOW = Shadow(
    radius = 2.dp,
    offset = DpOffset(0.dp, 1.dp),
    color = Color.Black.copy(alpha = 0.22f),
)

/** How much bigger the labels look through the lifted lens than beside it. */
private const val LENS_MAGNIFY = 0.14f

private val LENS_HEIGHT = 8.dp
private val LENS_AMOUNT = 10.dp

private val LENS_SHADOW = Shadow(
    radius = 10.dp,
    offset = DpOffset(0.dp, 2.dp),
    color = Color.Black.copy(alpha = 0.28f),
)
