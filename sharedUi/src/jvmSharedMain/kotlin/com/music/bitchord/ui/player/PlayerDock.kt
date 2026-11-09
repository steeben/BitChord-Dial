package com.music.bitchord.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Dp
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

    internal fun hasMiniArt(): Boolean = frozenArt != null || miniArt?.isAttached == true

    /**
     * Both corners mapped through every transform above the cover, not its
     * position plus its laid-out size: the glass bar draws its player scaled,
     * and the artwork landed on a cover a fifth bigger than the one on screen.
     */
    internal fun miniArtOnScreen(): Rect? {
        val art = frozenArt ?: return liveArt()
        // Carried along with the panel's top-left corner: the pill's row rides
        // there as the panel grows and shrinks, and the cover lands in that
        // row, not where the pill was left behind. Back on the pill, exactly
        // where it was, once the panel is.
        val pill = miniPillInWindow() ?: return art
        val window = panelWindow.takeIf { it.width > 0f && it.height > 0f } ?: return art
        val bounds = panelBounds(window)
        return art.translate(bounds.left - pill.left, bounds.top - pill.top)
    }

    private fun liveArt(): Rect? {
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

    // ---- The one panel: the mini player's pill and the full player ----
    // The pill's rect (at rest) is where the player starts and ends: the
    // player's panel grows from that pill to the whole window as it opens and
    // shrinks back into it as it closes — see PlayerPanel in the app.
    private var miniPill: LayoutCoordinates? = null
    private var miniPillCorner: Dp? = null

    /** Where the panel's window starts on screen, and its size. */
    private var windowOrigin: Offset = Offset.Zero
    private var panelWindow: Size = Size.Zero

    /**
     * The pill and the cover where they were as the panel came up. Held for
     * as long as the panel is: the bar the pill sits in slides away under
     * the opening player, and both ends of the movement have to stay put.
     */
    private var frozenPill: Rect? = null
    private var frozenArt: Rect? = null

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

    private fun livePill(): Rect? {
        val c = miniPill?.takeIf { it.isAttached } ?: return null
        val size = c.size.toSize()
        if (size.width <= 0f || size.height <= 0f) return null
        return Rect(c.localToScreen(Offset.Zero), c.localToScreen(Offset(size.width, size.height)))
    }

    /** The pill in the panel window's own pixels, or null with no pill on screen. */
    fun miniPillInWindow(): Rect? = (frozenPill ?: livePill())?.translate(-windowOrigin)

    /** The pill's corner radius in pixels, at [density]. */
    fun miniPillCornerPx(density: Float): Float? = miniPillCorner?.let { it.value * density }

    /**
     * How far the player travels between fully open and docked on the pill:
     * down to the pill's top edge, where the panel has shrunk to the pill.
     * The whole window's height with no pill to grow from.
     */
    fun travel(): Float {
        val top = miniPillInWindow()?.top
        return if (top != null && top > 1f) top else sheetTravel
    }

    /**
     * True while the panel stands where the mini player's pill is, so the pill
     * itself stands aside instead of showing as a second panel.
     */
    var panelShown by mutableStateOf(false)
        private set

    /** From the panel's root, once placed: where its window starts on screen. */
    fun panelPlaced(coordinates: LayoutCoordinates) {
        windowOrigin = coordinates.localToScreen(Offset.Zero)
        panelWindow = coordinates.size.toSize()
        if (!panelShown) {
            freezeEnds()
            panelShown = true
        }
    }

    /**
     * Holds the pill and its cover where they are now, as the two ends of the
     * movement that is about to start. Called by whoever starts it, before
     * anything has moved: a pull lifts the bar, and a pill measured a frame
     * later was measured lifted. A no-op if they are already held.
     */
    /**
     * Measures the cover again, as the player starts back down to it: a tap
     * on the pill swells it for a moment, and a cover measured during that
     * swell was a few pixels off where the pill's own comes back.
     */
    fun refreshArt() {
        val held = frozenPill ?: return
        val pill = livePill() ?: return
        val art = liveArt() ?: return
        // The bar may have slid away since; the pill moves with it, so the
        // cover is put back by however far the pill has gone.
        frozenArt = art.translate(held.left - pill.left, held.top - pill.top)
    }

    fun freezeEnds() {
        if (frozenPill == null) frozenPill = livePill()
        if (frozenArt == null) frozenArt = liveArt()
    }

    /** The pill comes back. Called as the panel goes, not after it has. */
    fun releasePanel() {
        panelShown = false
        frozenPill = null
        frozenArt = null
    }

    /**
     * The panel's rect this frame, in the window's own pixels. At the bottom
     * of its travel it is the pill's rect; fully open, the whole window. In
     * between its left and right close in on the pill a little ahead of the
     * rise, its top follows the player's offset, and its bottom drops to the
     * screen's well ahead of that: one shape changing size. With no pill to
     * grow from it is the whole window, sliding as one.
     */
    fun panelBounds(window: Size): Rect {
        val offset = offset() ?: 0f
        val pill = miniPillInWindow()
        if (pill == null || pill.top <= 1f) {
            return Rect(0f, offset, window.width, window.height + offset)
        }
        val progress = (1f - offset / pill.top).coerceIn(0f, 1f)
        val wide = panelWideEase(progress)
        val left = pill.left * (1f - wide)
        val right = window.width - (window.width - pill.right) * (1f - wide)
        // The artwork grows out of the pill faster than the panel climbs, and
        // a bottom that only kept pace with the climb cut the cover's foot off.
        val deep = 1f - (1f - (progress / PANEL_DEEP_AT).coerceIn(0f, 1f)).let { it * it }
        val bottom = pill.bottom + (window.height - pill.bottom) * deep
        return Rect(left, offset, right.coerceAtLeast(left + 1f), bottom.coerceAtLeast(offset + 1f))
    }

    /** 0 on the pill, 1 once the player's plain fill has fully covered the pill's material. */
    fun panelFill(until: Float): Float = (openFraction() / until).coerceIn(0f, 1f)

    /**
     * The panel's corner radius this frame: the pill's, rounding into a
     * sheet's top corners on the way up, squaring off only as it covers the
     * whole screen.
     */
    fun panelCornerPx(sheetCornerPx: Float, density: Float): Float {
        val pill = miniPillInWindow() ?: return 0f
        if (pill.top <= 1f) return 0f
        val progress = (1f - (offset() ?: 0f) / pill.top).coerceIn(0f, 1f)
        val pillCornerPx = miniPillCornerPx(density) ?: (pill.height / 2f)
        val pillCorner = pillCornerPx * (1f - progress)
        val sheetCorner = sheetCornerPx *
            (progress / PANEL_WIDE_AT).coerceIn(0f, 1f) *
            (1f - ((progress - 0.9f) / 0.1f).coerceIn(0f, 1f))
        return maxOf(pillCorner, sheetCorner)
    }

    /**
     * How far down the bar under the mini player has slid out of the way, 0
     * to 1. Only once the panel has frozen the pill where it was: the bar
     * moving under a pill still being measured would move the pill with it.
     */
    fun chromeAway(): Float = if (panelShown && frozenPill != null) openFraction() else 0f

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
    private val coversMiniArtState = derivedStateOf { attached && (openFraction() > 0f || panelShown) }
}

/** How far the panel is open when its width has reached the screen's: gradual, not at once. */
private const val PANEL_WIDE_AT = 0.8f

/** How far the panel is open when its bottom has reached the screen's. */
private const val PANEL_DEEP_AT = 0.4f

/** The panel's width curve: 0 on the pill, 1 once it is as wide as the screen, for open fraction [t]. */
internal fun panelWideEase(t: Float): Float {
    val x = (t / PANEL_WIDE_AT).coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x)
}

/** The [PlayerDock] around the player, if its host has a mini player to dock into. */
val LocalPlayerDock = staticCompositionLocalOf<PlayerDock?> { null }
