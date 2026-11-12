package com.music.bitchord.ui.player

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/*
 * The lyrics and the queue running on under the control deck.
 *
 * The deck used to end the list: the panel was laid out in what the deck left
 * over, so a line reaching the controls was simply cut off there, as if the
 * controls stood on something opaque. Here the panel is laid out down to the
 * foot of the player instead, and what of it lies under the deck is drawn out
 * of focus and under a thin wash, so the list is seen passing behind the
 * controls without being read through them.
 *
 * Glass and Frost only. Solid draws nothing blurred anywhere, and keeps the
 * list ending at the deck.
 */

/** How strongly a panel is softened under the deck. */
internal class UnderDeckStyle(val blur: Dp, val wash: Float)

/** Glass: the list is still made out as rows. */
internal val GlassUnderDeck = UnderDeckStyle(blur = 12.dp, wash = 0.18f)

/** Frost: the app's own 24dp frost, and a denser wash. */
internal val FrostUnderDeck = UnderDeckStyle(blur = 24.dp, wash = 0.34f)

/** The run above the deck over which the list goes out of focus. */
internal val UNDER_DECK_FADE = 28.dp

/** Where a panel of [height] goes soft above a deck [deck] tall at its foot. */
internal class UnderDeckBand(val fadeTop: Float, val deckTop: Float)

/** Null with no deck up to go under. */
internal fun underDeckBand(height: Float, deck: Float, fade: Float): UnderDeckBand? {
    if (deck < 1f || height <= 0f) return null
    val deckTop = (height - deck).coerceAtLeast(0f)
    return UnderDeckBand(fadeTop = (deckTop - fade).coerceAtLeast(0f), deckTop = deckTop)
}

/**
 * Lays the panel out [under] pixels taller than the room it was given, and
 * reports the room: the extra hangs below, under the deck. The deck takes its
 * height from the same column, so the sum does not change as the deck slides
 * and the panel is not measured again for it.
 */
internal fun Modifier.runsUnderDeck(under: () -> Int): Modifier = layout { measurable, constraints ->
    val extra = if (constraints.hasBoundedHeight) under().coerceAtLeast(0) else 0
    val placeable = measurable.measure(
        constraints.copy(
            minHeight = constraints.minHeight + extra,
            maxHeight = constraints.maxHeight + extra,
        ),
    )
    layout(placeable.width, (placeable.height - extra).coerceAtLeast(0)) {
        placeable.place(0, 0)
    }
}

/**
 * Bottom padding for a list that runs under the deck, read when the list is
 * measured: its last rows still come to rest above the deck, and a row held
 * for reordering turns back there, exactly as when the list ended at it.
 */
internal fun underDeckPadding(horizontal: Dp, under: () -> Dp): PaddingValues = object : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = horizontal
    override fun calculateRightPadding(layoutDirection: LayoutDirection) = horizontal
    override fun calculateTopPadding() = 0.dp
    override fun calculateBottomPadding() = under().coerceAtLeast(0.dp)
}

/**
 * Draws what of this panel lies under the deck out of focus. [deckHeight] is
 * the deck's height in pixels, at the panel's foot; [bleed] is how far the
 * panel's lists reach past its sides.
 *
 * Only the panel is blurred: the artwork behind it is a blur already. The
 * strip is recorded once and drawn twice, sharp down to the deck and soft from
 * just above it, the two dissolving into each other over [UNDER_DECK_FADE].
 */
@Composable
internal fun Modifier.softUnderDeck(
    style: UnderDeckStyle?,
    deckHeight: () -> Int,
    bleed: Dp,
): Modifier {
    if (style == null) return this
    val content = rememberGraphicsLayer()
    val soft = rememberGraphicsLayer()
    val blurPx = with(LocalDensity.current) { style.blur.toPx() }
    val effect = remember(blurPx) { BlurEffect(blurPx, blurPx, TileMode.Decal) }
    val paint = remember { Paint() }
    return drawWithContent {
        val band = underDeckBand(size.height, deckHeight().toFloat(), UNDER_DECK_FADE.toPx())
        if (band == null) {
            drawContent()
            return@drawWithContent
        }
        val side = bleed.toPx()
        val left = -side
        val right = size.width + side
        content.record { this@drawWithContent.drawContent() }

        // In focus down to the band, and through it on the way out.
        // The playing line swells past its box and glows past that: nothing
        // above the band is clipped at the sides.
        clipRect(left = -size.width, top = -size.height, right = size.width * 2f, bottom = band.fadeTop) {
            drawLayer(content)
        }
        dissolve(paint, left, band.fadeTop, right, band.deckTop, rising = false) {
            drawLayer(content)
        }

        // Out of focus from the band down. Recorded with room above it, so the
        // blur at the band's top edge has the rows above to draw on.
        val softTop = band.fadeTop - blurPx * 2f
        soft.renderEffect = effect
        soft.record(
            size = IntSize(ceil(right - left).toInt(), ceil(size.height - softTop).toInt()),
        ) {
            translate(left = side, top = -softTop) { drawLayer(content) }
        }
        dissolve(paint, left, band.fadeTop, right, band.deckTop, rising = true) {
            translate(left = left, top = softTop) { drawLayer(soft) }
        }
        clipRect(left = left, top = band.deckTop, right = right, bottom = size.height) {
            translate(left = left, top = softTop) { drawLayer(soft) }
        }
    }
}

/** [block] within the given rows, its opacity running from none to full down them, or back. */
private inline fun DrawScope.dissolve(
    paint: Paint,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    rising: Boolean,
    block: DrawScope.() -> Unit,
) {
    if (bottom - top < 1f) return
    drawIntoCanvas { it.saveLayer(Rect(left, top, right, bottom), paint) }
    clipRect(left = left, top = top, right = right, bottom = bottom) { block() }
    drawRect(
        brush = Brush.verticalGradient(
            colors = if (rising) listOf(Color.Transparent, Color.Black) else listOf(Color.Black, Color.Transparent),
            startY = top,
            endY = bottom,
        ),
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        blendMode = BlendMode.DstIn,
    )
    drawIntoCanvas { it.restore() }
}

/**
 * The wash the deck stands on over a panel: [color] at [alpha], coming in over
 * [UNDER_DECK_FADE] above the deck and carried past its sides and its foot, so
 * it has no edge on screen but the soft one at its top. Drawn by the deck and
 * not the panel, which is clipped to its own box while it fades.
 */
internal fun Modifier.underDeckWash(color: Color, alpha: () -> Float): Modifier = drawBehind {
    val a = alpha().coerceIn(0f, 1f)
    if (a <= 0f || size.height < 1f) return@drawBehind
    val fade = UNDER_DECK_FADE.toPx()
    val reach = UNDER_DECK_REACH.toPx()
    val top = -fade
    val bottom = size.height + reach
    val full = color.copy(alpha = color.alpha * a)
    drawRect(
        brush = Brush.verticalGradient(
            0f to color.copy(alpha = 0f),
            (fade / (bottom - top)).coerceIn(0f, 1f) to full,
            1f to full,
            startY = top,
            endY = bottom,
        ),
        topLeft = Offset(-reach, top),
        size = Size(size.width + reach * 2f, bottom - top),
    )
}

/** Past the deck's sides and foot: the gutters, a tablet's margins, the navigation bar. */
private val UNDER_DECK_REACH = 480.dp
