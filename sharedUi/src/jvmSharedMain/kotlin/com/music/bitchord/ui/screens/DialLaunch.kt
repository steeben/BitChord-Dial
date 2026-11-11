package com.music.bitchord.ui.screens

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
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
import coil3.compose.AsyncImage
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
    const val CLOSE_MS = 420
}

/** Quick off the mark and long to settle, like a phone's own app launch. */
private val LaunchEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The close's curve: the launch's quick start, then a touch past the tile
 * (about 7% smaller than it) and a settle back out to it, so the page lands
 * with a small bounce rather than stopping dead.
 */
private val CloseEasing = CubicBezierEasing(0.2f, 0f, 0.1f, 1.4f)

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
                            val w = size.width
                            val h = size.height
                            if (w <= 0f || h <= 0f) return@graphicsLayer
                            // Where the page is drawn: from the tile to the whole
                            // page, or, below zero (the close's bounce), the tile
                            // itself a little smaller, about its own centre.
                            val at = if (p >= 0f) {
                                Rect(lerp(tile.left, 0f, p), lerp(tile.top, 0f, p), lerp(tile.right, w, p), lerp(tile.bottom, h, p))
                            } else {
                                tile.scaled(1f + p)
                            }
                            val scale = at.width / w
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = scale
                            scaleY = scale
                            translationX = at.left
                            translationY = at.top
                            // The window: the tile's shape growing to the page's,
                            // in the page's own (unscaled) units.
                            val shownHeight = at.height / scale
                            val tileScale = if (closing) 1f else origin.pressedScale
                            val radius = lerp(origin.corner.toPx() * tileScale, 0f, p.coerceAtLeast(0f)) *
                                (1f + p.coerceAtMost(0f)) / scale
                            shape = WindowShape(shownHeight, radius)
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
        CompositionLocalProvider(LocalCardLaunch provides cardLaunch) { content() }
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

/** How close to its tile a closing page is (in progress) when the tile itself comes back. */
private const val LAND_AT = 0.04f

/** How near its tile's own size a bouncing page has to be back to count as landed. */
private const val SETTLED = 0.004f

/** How far past its tile a closing page has to have gone for the bounce to be under way (it peaks near 0.07). */
private const val BOUNCED = 0.02f

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun Rect.scaled(factor: Float): Rect {
    val c = center
    val half = Size(width * factor / 2f, height * factor / 2f)
    return Rect(c.x - half.width, c.y - half.height, c.x + half.width, c.y + half.height)
}

/** The page's top [height], with [radius] corners: what of it shows while it grows. */
private class WindowShape(val height: Float, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(0f, 0f, size.width, height.coerceAtMost(size.height), CornerRadius(radius)),
        )
}

/**
 * Whether the cards under this open their pages as dial tiles do, growing out
 * of their artwork (see [rememberCardLaunch]). Off unless a page asks: where
 * pages slide in sideways (the Library), they still do.
 */
val LocalCardLaunch = staticCompositionLocalOf { false }

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

    /** Opens the card's page with [action], out of the artwork as it is drawn now. */
    fun open(action: () -> Unit) {
        scope.launch {
            val bounds = coordinates?.takeIf { it.isAttached }?.boundsInRoot()
            if (bounds != null && !bounds.isEmpty) {
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
