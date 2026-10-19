package com.music.bitchord.ui.screens

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext

/**
 * A dial tile that opens a page (an album, an artist, a playlist), as it was
 * when it was let go of: where it sat on screen ([bounds], in root
 * coordinates, at full size), how far it was pressed in, and which page of
 * which dial it was on, so Home can come back to that page for it to close into.
 */
class DialOrigin(
    /** The page it opens: its browse id, which is also the page's key. */
    val key: String,
    val bounds: Rect,
    val pressedScale: Float,
    val thumbnailUrl: String?,
    val shelfTitle: String,
    val page: Int,
    /** The tile as drawn, unpressed: what the page grows out of and lands back as. */
    val snapshot: ImageBitmap? = null,
)

/**
 * The phone-home-screen launch: a page opened from a dial tile grows out of
 * the tile, and closing it shrinks it back in. The tile is hidden for as long
 * as its page is open, as an app's icon is.
 */
object DialLaunch {
    private var current: DialOrigin? by mutableStateOf(null)
    var origin: DialOrigin?
        get() = current
        set(value) {
            current = value
            landed = false
        }

    /**
     * Whether a closing page has all but reached its tile. The tile comes back
     * then, underneath the page's last frames (which show the tile's own
     * picture by that point), so the page is gone over it with no gap between.
     */
    internal var landed by mutableStateOf(false)

    /** The dial page Home should open on: the one a launched tile is on. */
    internal fun pageOf(shelfTitle: String): Int = origin?.takeIf { it.shelfTitle == shelfTitle }?.page ?: 0

    /** Whether the tile for [id] is out on its page, so not on the dial. */
    internal fun isOut(id: String): Boolean = !landed && origin?.key == id

    /** Whether going from page [from] to page [to] opens or closes the launched tile's page from [home]. */
    fun zooms(from: String, to: String, home: String): Boolean {
        val key = origin?.key ?: return false
        return (from == home && to == key) || (from == key && to == home)
    }

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

/** The tile's corners, as drawn on the dial. */
private val TILE_CORNER = 10.dp

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
    content: @Composable BoxScope.() -> Unit,
) {
    val closing = visibility.targetState == EnterExitState.PostExit
    val live = DialLaunch.origin?.takeIf { zooming && it.key == key }
    // Held through a close to its very last frame. The page change is over a
    // moment before the closing page is taken away, and in that moment
    // [zooming] is already false: let go of, the page drew once at full size,
    // right over the tile it had just shrunk into.
    val held = remember { arrayOfNulls<DialOrigin>(1) }
    if (live != null) held[0] = live
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
            DialLaunch.landed = true
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
                            val radius = lerp(TILE_CORNER.toPx() * tileScale, 0f, p.coerceAtLeast(0f)) *
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
        content()
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
