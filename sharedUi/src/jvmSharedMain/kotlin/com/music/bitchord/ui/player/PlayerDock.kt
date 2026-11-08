package com.music.bitchord.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/**
 * The mini player the expanded player docks into, for a host that has one.
 *
 * Opening and closing the player is one movement on screen: the sheet's
 * artwork travels between its own place and the mini player's cover while the
 * rest of the player fades — see the sleeve in [NowPlayingScreen]. The two are
 * in different windows on a phone (the player is a modal sheet over the page),
 * so nothing here is shared layout; it is the two ends of the movement, each
 * reported by the side that owns it, and met in screen coordinates.
 *
 * Read in layout and draw only. Nothing in it is worth a recomposition: a
 * frame of the sheet's travel moves the artwork and re-tints a layer, and that
 * is all it does.
 */
@Stable
class PlayerDock {
    /**
     * The mini player's cover, as last laid out. Held as coordinates rather than
     * as a rect, and only turned into one when the player asks: the bar moves
     * on every scroll that folds it, and working out a screen position for each
     * of those frames would be paid for by a player that isn't open.
     */
    private var miniArt: LayoutCoordinates? = null
    private var miniArtCorner: Dp = 8.dp

    /**
     * How far the player sheet sits below fully open, in pixels. Set by the
     * host while the sheet is up, null while it is not. `NaN` before the sheet
     * has been laid out.
     */
    var sheetOffset: (() -> Float)? by mutableStateOf(null)

    /** How far the sheet travels from fully open to gone, in pixels. */
    var sheetTravel by mutableFloatStateOf(0f)

    /**
     * Whether the player on screen takes part. Only its portrait shape does —
     * a landscape player has no sleeve that could become a cover, and keeps
     * the plain slide.
     */
    var attached by mutableStateOf(false)
        internal set

    /** Reported by the mini player's cover whenever it is placed. */
    fun reportMiniArt(coordinates: LayoutCoordinates, corner: Dp) {
        miniArt = coordinates
        miniArtCorner = corner
    }

    /** From the cover leaving composition, so a stale rect is never flown to. */
    fun releaseMiniArt(coordinates: LayoutCoordinates) {
        if (miniArt === coordinates) miniArt = null
    }

    internal fun hasMiniArt(): Boolean = miniArt?.isAttached == true

    /**
     * Both corners mapped through every transform above the cover, not its
     * position plus its laid-out size: the glass bar draws its player scaled,
     * and the artwork landed on a cover a fifth bigger than the one on screen.
     */
    internal fun miniArtOnScreen(): Rect? {
        val coordinates = miniArt?.takeIf { it.isAttached } ?: return null
        val size = coordinates.size.toSize()
        return Rect(
            coordinates.localToScreen(Offset.Zero),
            coordinates.localToScreen(Offset(size.width, size.height)),
        )
    }

    internal fun miniArtCorner(): Dp = miniArtCorner

    /** The sheet's offset this frame, or null with no sheet laid out. */
    internal fun offset(): Float? = sheetOffset?.invoke()?.takeUnless { it.isNaN() }

    // ---- Morph: the mini player's pill and the full player are one surface ----
    // The pill's rect (at rest, without the pull lift) is where the player
    // starts and ends: the player is clipped to a rounded rect that grows from
    // that pill to the whole window as it opens — see [playerMorph].
    private var miniPill: LayoutCoordinates? = null
    private var miniPillCorner: Dp? = null
    private var windowW = -1
    private var windowH = -1

    /** Where the player's window starts on screen. Measured once the sheet is laid out. */
    private var windowOrigin: Offset = Offset.Zero

    /**
     * Reported by the mini player's whole pill whenever it is placed. [corner]
     * is its corner radius, or null for a pill rounded to half its height.
     */
    fun reportMiniPill(coordinates: LayoutCoordinates, corner: Dp?) {
        miniPill = coordinates
        miniPillCorner = corner
    }

    fun releaseMiniPill(coordinates: LayoutCoordinates) {
        if (miniPill === coordinates) miniPill = null
    }

    /** The pill in the player window's own pixels, or null with no pill on screen. */
    internal fun miniPillInWindow(): Rect? {
        val c = miniPill?.takeIf { it.isAttached } ?: return null
        val size = c.size.toSize()
        if (size.width <= 0f || size.height <= 0f) return null
        return Rect(
            c.localToScreen(Offset.Zero) - windowOrigin,
            c.localToScreen(Offset(size.width, size.height)) - windowOrigin,
        )
    }

    /**
     * How far the player travels between fully open and docked on the pill:
     * down to the pill's top edge, where the morph has shrunk it to the pill.
     * The whole sheet's height with no pill to grow from.
     */
    fun travel(): Float {
        val top = miniPillInWindow()?.top
        return if (top != null && top > 1f) top else sheetTravel
    }

    internal fun measureWindow(coordinates: LayoutCoordinates) {
        if (windowW == coordinates.size.width && windowH == coordinates.size.height) return
        val offset = offset() ?: return
        windowW = coordinates.size.width
        windowH = coordinates.size.height
        windowOrigin = coordinates.localToScreen(Offset.Zero) - Offset(0f, offset.toInt().toFloat())
    }

    /** From the host as the sheet goes: the next one measures its window afresh. */
    fun forgetWindow() {
        windowW = -1
        windowH = -1
    }

    /**
     * The player's visible rect this frame, in the player box's own pixels, or
     * null when it is fully open (no clip at all) or there is no pill to grow
     * from. Left and right close in on the pill, a little ahead of the rise so
     * the screen's edges are covered before halfway; the bottom rises to meet
     * the pill; and the corners round off from the pill's into a sheet's and
     * then square up as it lands full screen. One shape changing size.
     */
    internal fun morph(size: Size, density: Float): PlayerMorph? {
        val pill = miniPillInWindow() ?: return null
        val offset = offset() ?: return null
        val travel = pill.top
        if (travel <= 1f) return null
        val progress = (1f - offset / travel).coerceIn(0f, 1f)
        if (progress >= 1f) return null
        val wide = panelWideEase(progress)
        val left = pill.left * (1f - wide)
        val right = size.width - (size.width - pill.right) * (1f - wide)
        // The bottom drops to the screen's well ahead of the rise: the artwork
        // grows out of the pill faster than the sheet climbs, and a bottom
        // that only kept pace with the sheet cut the cover's foot off.
        val deep = 1f - (1f - (progress / PANEL_DEEP_AT).coerceIn(0f, 1f)).let { it * it }
        val bottom = (pill.bottom + (size.height - pill.bottom) * deep - offset).coerceAtLeast(1f)
        val pillCornerPx = miniPillCorner?.let { it.value * density } ?: (pill.height / 2f)
        val sheetCornerPx = SHEET_CORNER.value * density
        val pillCorner = pillCornerPx * (1f - progress)
        val sheetCorner = sheetCornerPx *
            (progress / PANEL_WIDE_AT).coerceIn(0f, 1f) *
            (1f - ((progress - 0.9f) / 0.1f).coerceIn(0f, 1f))
        return PlayerMorph(
            rect = Rect(left, 0f, right.coerceAtLeast(left + 1f), bottom),
            corner = maxOf(pillCorner, sheetCorner),
            progress = progress,
        )
    }

    /**
     * 1 with the player fully open, 0 down on the mini player. 0 too while
     * there is no sheet yet, which is where an opening player starts.
     */
    fun openFraction(): Float {
        val travel = travel()
        val offset = offset() ?: return 0f
        if (travel <= 0f) return 0f
        return (1f - offset / travel).coerceIn(0f, 1f)
    }

    /**
     * Whether the mini player's own cover should stand aside, because the
     * player's is on its way to or from it. Not before the sheet has moved at
     * all: the sheet's window can land a frame after the page's, and a cover
     * hidden in that frame is a hole in the bar.
     */
    val coversMiniArt: Boolean
        get() = coversMiniArtState.value

    // Derived, so a reader hears about it twice a transition — when it flips
    // — rather than on every frame of the sheet's travel. Read straight off
    // the fraction, the cover's layer re-ran every frame of an open or a close,
    // and every one of those invalidated the page's whole window behind the
    // player: a full redraw, liquid glass and all, for a cover that hadn't
    // changed.
    private val coversMiniArtState = derivedStateOf { attached && openFraction() > 0f }
}

/** The [PlayerDock] around the player, if its host has a mini player to dock into. */
val LocalPlayerDock = staticCompositionLocalOf<PlayerDock?> { null }

/** How far the panel is open when its width has reached the screen's: gradual, not at once. */
private const val PANEL_WIDE_AT = 0.8f

/** How far the panel is open when its bottom has reached the screen's. */
private const val PANEL_DEEP_AT = 0.4f

/** The corner the panel rounds to on its way up, as a sheet's top corners do. */
private val SHEET_CORNER = 22.dp

/** What the panel is filled with while it grows out of the pill. */
private val MorphFill = Color(0xFF151517)

/** The panel's width curve: 0 on the pill, 1 once it is as wide as the screen, for open fraction [t]. */
internal fun panelWideEase(t: Float): Float {
    val x = (t / PANEL_WIDE_AT).coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x)
}

internal class PlayerMorph(val rect: Rect, val corner: Float, val progress: Float)

private class MorphShape(private val rect: Rect, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(corner, corner)))
}

/**
 * Clips the player to a rounded rect that grows from the mini player's pill to
 * the whole window as the sheet opens (and shrinks back into it as it closes),
 * with a dark fill that comes up quickly so the pill reads as turning into the
 * player. Read in the graphics layer and draw only: no recomposition per frame.
 * No-op without a dock, and once the player is fully open.
 */
fun Modifier.playerMorph(dock: PlayerDock?): Modifier {
    if (dock == null) return this
    return this
        .onGloballyPositioned { dock.measureWindow(it) }
        .graphicsLayer {
            val morph = dock.morph(size, density)
            if (morph != null) {
                shape = MorphShape(morph.rect, morph.corner)
                clip = true
                // Until the player has placed itself, its first frame would
                // show full-size UI inside the pill.
                alpha = if (!dock.attached && morph.progress < 0.02f) 0f else 1f
            } else {
                clip = false
                alpha = 1f
            }
        }
        .drawBehind {
            val morph = dock.morph(size, density) ?: return@drawBehind
            drawRect(MorphFill.copy(alpha = (morph.progress * 4f).coerceIn(0f, 1f)))
        }
}
