package com.music.bitchord.ui.screens

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext

/**
 * A dial tile or a card that opens a page (an album, an artist, a playlist, a
 * mood's page), as it was when it was let go of: where it sat on screen
 * ([bounds], in root coordinates, at full size), how far it was pressed in,
 * and where on its page it was (which page of which dial, or how far along
 * which shelf), so that page can come back to it for this one to close into.
 */
class DialOrigin(
    /** The page it opens: its browse id, which is also the page's key. */
    override val key: String,
    val bounds: Rect,
    val pressedScale: Float,
    val thumbnailUrl: String?,
    val shelfTitle: String,
    val page: Int,
    /** The tile as drawn, unpressed: what the page grows out of and lands back as. */
    val snapshot: ImageBitmap? = null,
    /** The page it was on, by key: the one its page shrinks back onto. */
    override val parent: String = DialLaunch.page,
    /** Its corners, as drawn. */
    val corner: Dp = 10.dp,
    /** How far along its shelf's row was scrolled, for a card in one. */
    val rowIndex: Int = 0,
    val rowOffset: Int = 0,
    /** Whether it is a card on a shelf ([shelfTitle]) rather than a dial tile. */
    val card: Boolean = false,
    /**
     * The picture it was showing, as loaded: what its page's banner starts as,
     * held here so it is on screen from the page's first frame whatever has
     * become of the image cache since.
     */
    val art: Painter? = null,
    /**
     * What of it was in view: less than [bounds] for a card part of the way
     * off the edge of its row. Its page closes onto [bounds] all the same,
     * showing only as much of itself as this.
     */
    val visible: Rect = bounds,
) : Launched {
    /**
     * Whether the tapped tile's page has come up in its place. The tile hides
     * only from then: hidden as soon as it was tapped, it was gone for a frame
     * or two before the page had drawn anything to stand in for it.
     */
    internal var launched by mutableStateOf(false)

    /**
     * Whether a closing page has all but reached its tile. The tile comes back
     * then, underneath the page's last frames (which show the tile's own
     * picture by that point), so the page is gone over it with no gap between.
     */
    internal var landed by mutableStateOf(false)
}

/**
 * The phone-home-screen launch: a page opened from a dial tile, or from a card
 * on a page that asks for it (see [LocalCardLaunch]), grows out of the tile,
 * and closing it shrinks it back in. The tile is hidden for as long as its
 * page is open, as an app's icon is.
 *
 * A page opened this way can open another the same way (a mood's page, then a
 * playlist on it), so the launches are kept one on top of the other, each to
 * close back onto the page under it.
 */
object DialLaunch {
    private var stack: List<DialOrigin> by mutableStateOf(emptyList())

    /** The page on screen, by key, told by whoever lays the pages out: what a tile tapped now opens from. */
    var page: String = ""

    /** Every launch still open, the first one opened first. */
    val origins: List<DialOrigin> get() = stack

    /** The last launch: the page on top, when it was opened from a tile. */
    var origin: DialOrigin?
        get() = stack.lastOrNull()
        set(value) {
            stack = stackWith(stack, value)
        }

    /** Lets go of [origin], and of whatever was launched from its page. */
    fun drop(origin: DialOrigin) {
        stack = stackWithout(stack, origin)
    }

    /** Lets go of every launch that closes onto [page], now that [page] is back on screen. */
    fun settledOn(page: String) {
        stack = stackSettledOn(stack, page)
    }

    /** The dial page Home should open on: the one a launched tile is on. */
    internal fun pageOf(shelfTitle: String): Int = stack.lastOrNull { it.shelfTitle == shelfTitle }?.page ?: 0

    /** The launch from the shelf called [shelfTitle], for its row to come back to where it was. */
    internal fun rowOf(shelfTitle: String): DialOrigin? =
        stack.lastOrNull { it.card && it.shelfTitle == shelfTitle }

    /**
     * Whether the tile for [id] is out on its page, so not where it was. A
     * card is asked for by its shelf ([cardShelf]) as well: the same album can
     * be on the dial and on a shelf under it, and only the one tapped is out.
     */
    internal fun isOut(id: String, cardShelf: String? = null): Boolean =
        stack.any {
            it.key == id && it.launched && !it.landed &&
                if (cardShelf == null) !it.card else it.card && it.shelfTitle == cardShelf
        }

    /** The launch that going from page [from] to page [to] opens or closes, if it is one. */
    fun between(from: String, to: String): DialOrigin? = launchBetween(stack, from, to)

    /** Whether going from page [from] to page [to] opens or closes a launched tile's page. */
    fun zooms(from: String, to: String): Boolean = between(from, to) != null

    /** Whether going from page [from] to page [to] opens a launched tile's page, rather than closes it. */
    fun opens(from: String, to: String): Boolean = between(from, to)?.parent == from

    /**
     * Where in a close's run to seek for a back swipe [progress] (0 to 1) of
     * the way across. The close's own curve is quick off the mark, so seeking
     * straight by the swipe would shrink most of the page in its first inch;
     * this undoes the curve, so the page shrinks in step with the finger, as
     * far as [BACK_REACH] of the way to its tile. Letting go runs the rest.
     */
    fun backFraction(progress: Float): Float {
        val shrunk = progress.coerceIn(0f, 1f) * BACK_REACH
        var lo = 0f
        var hi = 1f
        repeat(24) {
            val mid = (lo + hi) / 2f
            if (CloseEasing.transform(mid) < shrunk) lo = mid else hi = mid
        }
        return (lo + hi) / 2f
    }

    /** How much of the way to its tile a page shrinks under a full back swipe. */
    private const val BACK_REACH = 0.6f

    const val OPEN_MS = 420
    const val CLOSE_MS = 600

    /**
     * How long the page under a closing one takes to come forward again: back
     * in place by the time the closing page is down to its tile (see
     * [closeCurve]), so the tile it lands on is where the page lands.
     */
    const val CLOSE_UNDER_MS = 270
}

/** Quick off the mark and long to settle, like a phone's own app launch. */
private val LaunchEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The close's curve, a spring's (see [closeCurve]): a gentle start, down to
 * the tile, a touch past it (about 5% smaller) and a settle back out to it,
 * so the page lands with a small bounce rather than stopping dead.
 */
private val CloseEasing = Easing { closeCurve(it) }

/**
 * One page of the app's content, grown out of / shrunk back into its dial
 * tile while [zooming]. [visibility] is the page's own enter/exit transition
 * (an AnimatedContent slot's), which carries the animation, so the page stays
 * up for as long as it lasts. Otherwise the page is drawn as it always is.
 */
@Composable
fun DialLaunchPage(
    key: String,
    zooming: Boolean,
    visibility: Transition<EnterExitState>,
    /** Whether this page's own cards open their pages the same way: see [LocalCardLaunch]. */
    cardLaunch: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val closing = visibility.targetState == EnterExitState.PostExit
    val live = if (zooming) DialLaunch.origins.lastOrNull { it.key == key } else null
    // Held through a close to its very last frame. The page change is over a
    // moment before the closing page is taken away, and in that moment
    // [zooming] is already false: let go of, the page drew once at full size,
    // right over the tile it had just shrunk into.
    val held = remember { arrayOfNulls<DialOrigin>(1) }
    if (live != null) {
        held[0] = live
        // In the same frame this page first draws over the tile.
        SideEffect { live.launched = true }
    }
    val origin = live ?: held[0]?.takeIf { closing }
    val progress by visibility.animateFloat(
        transitionSpec = {
            when {
                origin == null -> snap()
                closing -> tween(DialLaunch.CLOSE_MS, easing = CloseEasing)
                else -> tween(DialLaunch.OPEN_MS, easing = LaunchEasing)
            }
        },
        label = "dialLaunch",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    // Where this page sits on screen, to turn the tile's root position into one on the page.
    val pageAt = remember { mutableStateOf(Offset.Zero) }
    val ground = MaterialTheme.colorScheme.background
    // Read where it is drawn, not here: a frame of the close redraws the
    // picture and recomposes nothing.
    // Not only while closing: a back swipe let go of early runs the page back
    // out as an opening, and the picture has to follow it out, not jump.
    val zoomed = origin != null
    val closeProgress = remember { LaunchClose() }
    val readProgress = remember(visibility) { { progress } }
    SideEffect { closeProgress.read = if (zoomed) readProgress else null }
    if (origin != null && closing) {
        LaunchedEffect(origin) {
            snapshotFlow { progress }.first { it <= LAND_AT }
            // Past the tile and back: not until the bounce has settled, or the
            // tile would show round the page while it is smaller than it.
            // Deep into the bounce first: a frame just under zero is already
            // within [SETTLED], so waiting only for "below zero" let the tile
            // back the moment the bounce began.
            snapshotFlow { progress }.first { it <= -BOUNCED }
            snapshotFlow { progress }.first { it >= -SETTLED }
            origin.landed = true
        }
    }
    Box(
        Modifier
            .onPlaced { pageAt.value = it.positionInRoot() }
            .then(
                if (origin == null) {
                    Modifier
                } else {
                    Modifier
                        .graphicsLayer {
                            val p = progress
                            // Out from the tile as it was let go of, pressed in;
                            // back into it at full size, as it will be drawn.
                            val from = if (closing) origin.bounds else origin.bounds.scaled(origin.pressedScale)
                            val tile = from.translate(-pageAt.value)
                            // As much of the tile as was in view: all of it, but
                            // for a card part of the way off the edge of its row.
                            val seen = from.intersect(origin.visible).translate(-pageAt.value)
                            val w = size.width
                            val h = size.height
                            if (w <= 0f || h <= 0f) return@graphicsLayer
                            // Where the page is drawn: from the tile to the whole
                            // page, or, below zero (the close's bounce), the tile
                            // itself a little smaller, about its own centre.
                            val at = tile.launched(w, h, tile.center, p)
                            val scale = at.width / w
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = scale
                            scaleY = scale
                            translationX = at.left
                            translationY = at.top
                            // The window: the tile's shape growing to the page's,
                            // in the page's own (unscaled) units.
                            // Cut where the tile itself is cut off, so the page
                            // comes down onto the part of it in view at the
                            // tile's own proportions, not squeezed into that part.
                            val shown = seen.launched(w, h, tile.center, p)
                            val window = Rect(
                                (shown.left - at.left) / scale,
                                (shown.top - at.top) / scale,
                                (shown.right - at.left) / scale,
                                (shown.bottom - at.top) / scale,
                            )
                            val tileScale = if (closing) 1f else origin.pressedScale
                            // No rounder than a circle: a round picture (an
                            // artist's) gives a corner as big as it likes.
                            val corner = (origin.corner.toPx() * tileScale)
                                .coerceAtMost(minOf(tile.width, tile.height) / 2f)
                            val radius = lerp(corner, 0f, p.coerceAtLeast(0f)) *
                                (1f + p.coerceAtMost(0f)) / scale
                            shape = WindowShape(
                                window,
                                radius,
                                cutLeft = seen.left - tile.left > CUT_PX,
                                cutTop = seen.top - tile.top > CUT_PX,
                                cutRight = tile.right - seen.right > CUT_PX,
                                cutBottom = tile.bottom - seen.bottom > CUT_PX,
                            )
                            clip = true
                        }
                        // The page is see-through (the colour behind it belongs to
                        // the app), so while it is small it stands on solid ground,
                        // dissolving once it has filled the screen.
                        .drawBehind {
                            val solid = ((1f - progress) / 0.3f).coerceIn(0f, 1f)
                            if (solid > 0f) drawRect(ground.copy(alpha = solid))
                        }
                },
            ),
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(
            LocalCardLaunch provides cardLaunch,
            LocalLaunchClose provides closeProgress,
        ) { content() }
        if (origin != null) {
            // The tile itself over the top of the page, fading into the
            // page's banner as it opens, and back in as it closes: wholly the
            // tile again for the last stretch, so it lands as exactly that.
            Box(
                Modifier
                    // Free of the page's full-size minimum, to be only as tall as the tile.
                    .wrapContentSize(Alignment.TopStart)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = (1f - (progress - LAND_AT) / 0.4f).coerceIn(0f, 1f) }
                    // As tall, against the page's width, as the tile is against its own.
                    .aspectRatio(origin.bounds.width / origin.bounds.height.coerceAtLeast(1f)),
            ) {
                val snapshot = origin.snapshot
                if (snapshot != null) {
                    Image(
                        bitmap = snapshot,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    val context = LocalPlatformContext.current
                    val request = remember(origin.thumbnailUrl, context) {
                        speedDialArtRequest(context, origin.thumbnailUrl)
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
    }
}

/**
 * A launched page's way back to its tile, for what is on the page to take
 * part in: [placed] is 1 on the open page and falls to 0 as it shrinks (under
 * a back swipe included), see [closePlaced]. Meant to be read while drawing.
 */
@Stable
class LaunchClose {
    internal var read: (() -> Float)? by mutableStateOf(null)

    val placed: Float get() = read?.let { closePlaced(it()) } ?: 1f
}

/** The close of the launched page this is on: never closing, for a page that wasn't launched from a tile. */
val LocalLaunchClose = staticCompositionLocalOf { LaunchClose() }

/** How close to its tile a closing page is (in progress) when the tile itself comes back. */
private const val LAND_AT = 0.04f

/** How near its tile's own size a bouncing page has to be back to count as landed. */
private const val SETTLED = 0.004f

/** How far past its tile a closing page has to have gone for the bounce to be under way (it peaks at [CLOSE_DIP], near 0.05). */
private const val BOUNCED = 0.02f

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun Rect.scaled(factor: Float): Rect {
    val c = center
    val half = Size(width * factor / 2f, height * factor / 2f)
    return Rect(c.x - half.width, c.y - half.height, c.x + half.width, c.y + half.height)
}

/** This tile's rect on its way to a page [w] by [h], at launch [progress]: see [launchEdge]. */
private fun Rect.launched(w: Float, h: Float, centre: Offset, progress: Float) = Rect(
    launchEdge(left, 0f, centre.x, progress),
    launchEdge(top, 0f, centre.y, progress),
    launchEdge(right, w, centre.x, progress),
    launchEdge(bottom, h, centre.y, progress),
)

/** How much of a tile's side has to be out of view (in pixels) for it to count as cut off there. */
private const val CUT_PX = 0.5f

/**
 * What of the page shows while it grows: its [window], with [radius] corners.
 * A side the tile is cut off at (by the edge of its row, say) is cut square,
 * as the tile is there.
 */
private class WindowShape(
    val window: Rect,
    val radius: Float,
    val cutLeft: Boolean = false,
    val cutTop: Boolean = false,
    val cutRight: Boolean = false,
    val cutBottom: Boolean = false,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rect = Rect(
            window.left.coerceAtLeast(0f),
            window.top.coerceAtLeast(0f),
            window.right.coerceAtMost(size.width),
            window.bottom.coerceAtMost(size.height),
        )
        val round = CornerRadius(radius)
        if (!cutLeft && !cutTop && !cutRight && !cutBottom) return Outline.Rounded(RoundRect(rect, round))
        return Outline.Rounded(
            RoundRect(
                rect,
                topLeft = if (cutLeft || cutTop) CornerRadius.Zero else round,
                topRight = if (cutRight || cutTop) CornerRadius.Zero else round,
                bottomRight = if (cutRight || cutBottom) CornerRadius.Zero else round,
                bottomLeft = if (cutLeft || cutBottom) CornerRadius.Zero else round,
            ),
        )
    }
}

/**
 * Whether the cards under this open their pages as dial tiles do, growing out
 * of their artwork (see [rememberCardLaunch]). Off unless a page asks: Home,
 * Explore and the Library's pages do.
 */
val LocalCardLaunch = staticCompositionLocalOf { false }

/** The corners to give [rememberCardLaunch] for a round picture, an artist's: cut down to a circle whatever its size. */
val ROUND_CARD_CORNER = 1000.dp

/** The shelf a card sits in and its row's scroll, for the row to come back to when the card's page closes. */
class LaunchRow(val title: String, val state: LazyListState)

val LocalLaunchRow = compositionLocalOf<LaunchRow?> { null }

/**
 * The scroll of the shelf called [title]'s row: where it was when a card on
 * it was opened, while that card's page is open, so the page closes onto the
 * card rather than onto wherever a row starting over would have put it.
 */
@Composable
fun rememberLaunchRowState(title: String): LazyListState {
    val from = DialLaunch.rowOf(title)
    return rememberLazyListState(from?.rowIndex ?: 0, from?.rowOffset ?: 0)
}

/**
 * A card's artwork as what its page grows out of: where it is, and every frame
 * of it kept to hand, as a dial tile's are. Put on the artwork with
 * [launchArt], and told of a tap with [open].
 */
class CardLaunch internal constructor(
    internal val layer: GraphicsLayer,
    private val scope: CoroutineScope,
) {
    internal var key: String = ""
    internal var thumbnailUrl: String? = null
    internal var corner: Dp = 0.dp
    internal var row: LaunchRow? = null
    internal var coordinates: LayoutCoordinates? = null
    internal var art: Painter? = null

    /** Told what the card's artwork is showing, to keep the picture once it has loaded. */
    fun shows(state: AsyncImagePainter.State) {
        if (state is AsyncImagePainter.State.Success) art = state.painter
    }

    /** Opens the card's page with [action], out of the artwork as it is drawn now. */
    fun open(action: () -> Unit) {
        scope.launch {
            val placed = coordinates?.takeIf { it.isAttached }
            // What of it is in view, and the whole of it, in view or not: a
            // card part of the way off the edge of its row has less in view
            // than its picture is, and its page still closes to the picture's
            // own proportions.
            val visible = placed?.boundsInRoot()
            val bounds = placed?.let { it.findRootCoordinates().localBoundingBoxOf(it, clipBounds = false) }
            if (bounds != null && visible != null && !visible.isEmpty) {
                val snapshot = runCatching { layer.toImageBitmap() }.getOrNull()
                DialLaunch.origin = DialOrigin(
                    key = key,
                    bounds = bounds,
                    pressedScale = 1f,
                    thumbnailUrl = thumbnailUrl,
                    shelfTitle = row?.title.orEmpty(),
                    page = 0,
                    snapshot = snapshot,
                    corner = corner,
                    rowIndex = row?.state?.firstVisibleItemIndex ?: 0,
                    rowOffset = row?.state?.firstVisibleItemScrollOffset ?: 0,
                    card = true,
                    art = art,
                    visible = visible,
                )
            }
            action()
        }
    }
}

/**
 * The launch for a card that opens the page [opens] (its key), or null where
 * cards don't launch (see [LocalCardLaunch]) or this one opens no page.
 * [corner] is its artwork's corners.
 */
@Composable
fun rememberCardLaunch(opens: String?, thumbnailUrl: String?, corner: Dp): CardLaunch? {
    if (opens == null || !LocalCardLaunch.current) return null
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val launch = remember(layer, scope) { CardLaunch(layer, scope) }
    launch.key = opens
    launch.thumbnailUrl = thumbnailUrl
    launch.corner = corner
    launch.row = LocalLaunchRow.current
    return launch
}

/** A tap's action for a card: through its [launch] when it has one. */
fun launching(launch: CardLaunch?, action: () -> Unit): () -> Unit =
    if (launch == null) action else ({ launch.open(action) })

/**
 * Marks a card's artwork as what its page grows out of. Goes ahead of the
 * artwork's clip, so the picture taken of it has its corners. The artwork is
 * hidden while its page is open, as a dial tile is.
 */
fun Modifier.launchArt(launch: CardLaunch?): Modifier =
    if (launch == null) {
        this
    } else {
        this
            .onPlaced { launch.coordinates = it }
            .graphicsLayer { alpha = if (DialLaunch.isOut(launch.key, launch.row?.title.orEmpty())) 0f else 1f }
            .drawWithContent {
                launch.layer.record { this@drawWithContent.drawContent() }
                drawLayer(launch.layer)
            }
    }
