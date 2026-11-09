package com.music.bitchord.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.ui.graphics.forPixelAccess
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/*
 * "Let Dial Generate Playlist Image": a playlist still wearing YouTube Music's
 * default cover (the 2x2 grid of four of its sleeves) gets a moving collage on
 * its page instead, one cover in front and others floating round it, made of
 * its most recently added songs. A cover the owner chose is left alone.
 */

private fun DetailPage.isLiked(): Boolean =
    browseId == YtMusicRepository.LIKED_MUSIC || browseId == "LM" || OfflinePlaylists.isLikedPage(browseId)

/**
 * The covers to draw for this page, or null to show its own artwork.
 *
 * Settled once and then held: worked out only from a fully loaded list (no
 * continuation still to follow), kept while the page reloads, and replaced
 * only if the reloaded list gives a different set — so a refresh or the
 * later pages of a long playlist arriving never make the header flicker.
 * Kept by page for the life of the process, so reopening one starts on it.
 */
@Composable
internal fun rememberGeneratedCollage(page: DetailPage, songs: List<Song>): List<String>? {
    val enabled by CustomizationSettings.generatePlaylistImages.collectAsStateWithLifecycle()
    val liked = page.isLiked()
    val eligible = enabled && page.type == BrowseType.PLAYLIST && (liked || page.browseId.startsWith("VL"))
    // The first batch of rows is enough: a long playlist's later pages can
    // take a while, and the picture shouldn't wait for them.
    val settled = page.songs is UiState.Success && songs.isNotEmpty()
    val context = LocalContext.current.applicationContext
    val thumb = page.thumbnailUrl
    // The rows that could be the grid's four: YouTube builds it from the
    // playlist's own sleeves, the first ones in its running order.
    val gridSources = remember(songs) { songs.take(GRID_SOURCE_ROWS).mapNotNull { it.thumbnailUrl } }
    val isDefault by produceState<Boolean?>(
        initialValue = if (liked) true else DefaultGridCheck.cached(thumb),
        eligible && settled, thumb, gridSources,
    ) {
        if (!eligible || !settled || liked) return@produceState
        // A cover that wouldn't load is tried again a few times before the
        // page is left on its own artwork.
        repeat(GRID_CHECK_TRIES) { attempt ->
            val verdict = DefaultGridCheck.check(context, thumb, gridSources)
            if (verdict != null) {
                value = verdict
                return@produceState
            }
            if (attempt < GRID_CHECK_TRIES - 1) kotlinx.coroutines.delay(GRID_CHECK_RETRY_MS)
        }
        value = false
    }
    var shown by remember(page.browseId) { mutableStateOf(heldCollages[page.browseId]) }
    LaunchedEffect(page.browseId, eligible, settled, isDefault) {
        // Decided once, the first time the page is opened: later pages of the
        // list arriving and pulls to refresh keep the picture it opened with.
        if (!eligible || !settled || page.browseId in decidedCollages) return@LaunchedEffect
        val next = when (isDefault) {
            true -> collageCovers(songs, newestFirst = liked).takeIf { it.isNotEmpty() }
            false -> null
            null -> return@LaunchedEffect
        }
        decidedCollages += page.browseId
        shown = next
        if (next == null) heldCollages.remove(page.browseId) else heldCollages[page.browseId] = next
    }
    return shown.takeIf { eligible }
}

/** The collage each playlist page settled on, by page. */
private val heldCollages = HashMap<String, List<String>>()

/** Pages whose collage has been decided, so it isn't decided again. */
private val decidedCollages = HashSet<String>()

private const val GRID_SOURCE_ROWS = 16
private const val GRID_CHECK_TRIES = 3
private const val GRID_CHECK_RETRY_MS = 2_000L

/**
 * Whether a playlist's cover is YouTube Music's default grid: its four
 * quarters are each one of the playlist's own sleeves. Compared at a few
 * pixels a side, which is plenty to tell a sleeve from a photo and cheap
 * enough to run on every playlist opened. Answers are kept for the process.
 */
private object DefaultGridCheck {
    private val verdicts = HashMap<String, Boolean>()

    fun cached(url: String?): Boolean? = url?.let { synchronized(verdicts) { verdicts[it] } }

    /**
     * True for the grid, false for a cover of the owner's, null when the
     * cover couldn't be fetched: the page then decides nothing yet, rather
     * than settling on its plain artwork for good over one dropped request.
     */
    suspend fun check(context: Context, url: String?, sources: List<String>): Boolean? {
        if (url == null || !url.startsWith("http")) return false
        cached(url)?.let { return it }
        var complete = true
        val verdict = withContext(Dispatchers.Default) {
            val cover = load(context, url.artworkAt(GRID_PX) ?: url, GRID_PX) ?: return@withContext null
            // The grid fills the middle square of whatever shape the cover
            // comes in (an old playlist's is a 4:3 video still).
            val side = min(cover.width, cover.height)
            val x0 = (cover.width - side) / 2
            val y0 = (cover.height - side) / 2
            val half = side / 2
            val quarters = (0 until 4).map { q -> signature(cover, x0 + (q % 2) * half, y0 + (q / 2) * half, half) }
            val distinct = sources.distinct()
            val sleeves = coroutineScope {
                distinct.map { src ->
                    async { load(context, src.artworkAt(GRID_PX) ?: src, GRID_PX)?.let(::squareSignature) }
                }.awaitAll().filterNotNull()
            }
            if (sleeves.size < distinct.size) complete = false
            val matched = quarters.count { q -> sleeves.any { distance(q, it) < MATCH_DISTANCE } }
            // The seams decide it on their own too: the grid's four can be
            // sleeves from further down the list than the rows compared, or
            // ones the app doesn't list (an unavailable track's).
            matched >= 2 || hasGridSeams(cover, x0, y0, side)
        } ?: return null
        // A "no" with sleeves missing may be one a retry would turn round.
        if (verdict || complete) synchronized(verdicts) { verdicts[url] = verdict }
        return verdict
    }

    private suspend fun load(context: Context, url: String, px: Int): Bitmap? = loadSoftwareBitmap(context, url, px)

    /** A sleeve's middle square, as a video thumbnail's bars would otherwise skew it. */
    private fun squareSignature(b: Bitmap): IntArray {
        val side = min(b.width, b.height)
        return signature(b, (b.width - side) / 2, (b.height - side) / 2, side)
    }

    /** The region averaged down to [SIG] x [SIG] RGB cells. */
    private fun signature(b: Bitmap, x0: Int, y0: Int, side: Int): IntArray {
        val out = IntArray(SIG * SIG * 3)
        val cell = (side / SIG).coerceAtLeast(1)
        for (cy in 0 until SIG) for (cx in 0 until SIG) {
            var r = 0L; var g = 0L; var bl = 0L; var n = 0
            for (y in y0 + cy * cell until min(y0 + (cy + 1) * cell, b.height)) {
                for (x in x0 + cx * cell until min(x0 + (cx + 1) * cell, b.width)) {
                    val p = b.getPixel(x, y)
                    r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF; n++
                }
            }
            val i = (cy * SIG + cx) * 3
            if (n > 0) {
                out[i] = (r / n).toInt(); out[i + 1] = (g / n).toInt(); out[i + 2] = (bl / n).toInt()
            }
        }
        return out
    }

    private fun distance(a: IntArray, b: IntArray): Float {
        var sum = 0L
        for (i in a.indices) sum += abs(a[i] - b[i])
        return sum.toFloat() / a.size
    }

    /**
     * With no sleeves to compare against: a grid has hard edges down its
     * middle and across it, far sharper than the picture either side.
     */
    private fun hasGridSeams(b: Bitmap, x0: Int, y0: Int, side: Int): Boolean {
        val w = side
        val h = side
        if (w < 8 || h < 8) return false
        fun diff(p: Int, q: Int): Int =
            abs(((p shr 16) and 0xFF) - ((q shr 16) and 0xFF)) +
                abs(((p shr 8) and 0xFF) - ((q shr 8) and 0xFF)) +
                abs((p and 0xFF) - (q and 0xFF))
        fun column(x: Int) = (0 until h).sumOf { diff(b.getPixel(x0 + x - 1, y0 + it), b.getPixel(x0 + x, y0 + it)) }.toFloat() / h
        fun row(y: Int) = (0 until w).sumOf { diff(b.getPixel(x0 + it, y0 + y - 1), b.getPixel(x0 + it, y0 + y)) }.toFloat() / w
        val usualCol = listOf(w / 4, w / 3, 2 * w / 3, 3 * w / 4).map(::column).average().toFloat()
        val usualRow = listOf(h / 4, h / 3, 2 * h / 3, 3 * h / 4).map(::row).average().toFloat()
        return column(w / 2) > usualCol * 3f + 12f && row(h / 2) > usualRow * 3f + 12f
    }

    private const val GRID_PX = 96
    private const val SIG = 8
    private const val MATCH_DISTANCE = 26f
}

// ---------------------------------------------------------------------------
// The collage itself: one canvas on one clock, read only while drawing, so a
// frame is a redraw of one Spacer with no recomposition and no new bitmaps.
// ---------------------------------------------------------------------------

private const val THUMB_PX = 360
private const val MAIN_PX = 600
private const val MAIN_SHAPE = 4

/**
 * How far down the picture the front cover's centre sits: the middle of
 * what shows between the top bar and the title laid over the picture's foot.
 */
private const val MAIN_CENTRE_Y = 0.40f

/** Corner radii (top-left, top-right, bottom-right, bottom-left) as shares of a side, per shape. */
private val RADII = arrayOf(
    floatArrayOf(0.22f, 0.22f, 0.22f, 0.22f),
    floatArrayOf(0.50f, 0.50f, 0.50f, 0.50f),
    floatArrayOf(0.45f, 0.08f, 0.45f, 0.08f),
    floatArrayOf(0.08f, 0.42f, 0.08f, 0.42f),
    floatArrayOf(0.30f, 0.10f, 0.30f, 0.12f),
)

/**
 * One floating cover: its centre as shares of the width and height (it may sit
 * partly off the edge), its side as a share of the width, and its depth, near
 * at 1, which scales how far it drifts and how dim and soft it is.
 */
private class Slot(
    val x: Float, val y: Float, val size: Float, val rot: Float, val depth: Float, val shape: Int,
    val period: Float, val phase: Float, val ampX: Float, val ampY: Float, val ampR: Float,
)

private val SLOTS = listOf(
    Slot(-0.02f, 0.20f, 0.46f, -9f, 0.85f, 2, 31f, 0.4f, 0.030f, 0.022f, 2.2f),
    Slot(0.50f, 0.02f, 0.44f, 0f, 0.60f, 1, 27f, 2.1f, 0.024f, 0.030f, 3.0f),
    Slot(1.00f, 0.06f, 0.36f, 14f, 0.70f, 0, 34f, 4.0f, 0.026f, 0.020f, 2.5f),
    Slot(0.98f, 0.42f, 0.38f, 9f, 1.00f, 3, 23f, 5.2f, 0.030f, 0.026f, 3.0f),
    Slot(0.02f, 0.62f, 0.40f, -5f, 0.90f, 0, 29f, 1.3f, 0.028f, 0.030f, 2.5f),
    Slot(0.90f, 0.76f, 0.30f, 7f, 0.55f, 1, 37f, 3.3f, 0.022f, 0.024f, 3.0f),
    Slot(0.16f, 0.86f, 0.26f, -12f, 0.50f, 0, 33f, 0.9f, 0.020f, 0.020f, 3.0f),
    Slot(0.60f, 0.92f, 0.24f, 6f, 0.45f, 2, 39f, 2.7f, 0.020f, 0.020f, 3.0f),
)

/** Furthest first, so nearer covers overlap them. */
private val DRAW_ORDER = SLOTS.indices.sortedBy { SLOTS[it].depth }

@Stable
private class CollageState {
    var seconds by mutableDoubleStateOf(0.0)
    var visible by mutableStateOf(true)
    var main by mutableStateOf<ImageBitmap?>(null)
    var tiles by mutableStateOf<List<ImageBitmap?>>(emptyList())
}

/** Paths are rebuilt only when the size changes, not each frame. */
private class CollageGeometry {
    private var w = -1f
    private var h = -1f
    val sides = FloatArray(SLOTS.size)
    val paths = Array(SLOTS.size) { Path() }
    var mainSide = 0f
    var mainPath = Path()

    fun ensure(width: Float, height: Float) {
        if (width == w && height == h) return
        w = width
        h = height
        for (i in SLOTS.indices) {
            sides[i] = evenPx(width * SLOTS[i].size)
            paths[i] = shapePath(SLOTS[i].shape, sides[i])
        }
        mainSide = evenPx(min(width * 0.60f, height * 0.56f))
        mainPath = shapePath(MAIN_SHAPE, mainSide)
    }
}

private fun evenPx(v: Float): Float = ((v / 2f).roundToInt() * 2).coerceAtLeast(2).toFloat()

private fun shapePath(kind: Int, side: Float): Path {
    val h = side / 2f
    val r = RADII[kind]
    return Path().apply {
        addRoundRect(
            RoundRect(
                -h, -h, h, h,
                CornerRadius(side * r[0]), CornerRadius(side * r[1]),
                CornerRadius(side * r[2]), CornerRadius(side * r[3]),
            ),
        )
    }
}

/**
 * The generated playlist image: [covers]'s first in front, the rest floating
 * round it. Fills its bounds; the page's colour is expected to be under it.
 */
@Composable
internal fun PlaylistCollageArt(covers: List<String>, modifier: Modifier = Modifier) {
    val state = remember { CollageState() }
    val geometry = remember { CollageGeometry() }
    val borderPx = with(LocalDensity.current) { 1.dp.toPx() }
    val enter = remember { Animatable(0f) }
    val context = LocalContext.current

    LaunchedEffect(covers.firstOrNull()) {
        val url = covers.firstOrNull() ?: return@LaunchedEffect
        state.main = withContext(Dispatchers.Default) { loadCover(context, url, MAIN_PX) }
        enter.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(covers.drop(1)) {
        val urls = covers.drop(1).take(SLOTS.size)
        state.tiles = withContext(Dispatchers.Default) {
            coroutineScope {
                urls.map { url ->
                    // Sharp at every depth: distance is shown by size and
                    // dimming alone, never by a smeared low-res copy.
                    async { loadCover(context, url, THUMB_PX) }
                }.awaitAll()
            }
        }
    }
    CollageClock(state)

    Spacer(
        modifier
            .onGloballyPositioned { c ->
                val v = c.isAttached && c.boundsInWindow().let { !it.isEmpty && it.bottom > 0f }
                if (v != state.visible) state.visible = v
            }
            .drawBehind { drawCollage(state, geometry, enter.value, borderPx) },
    )
}

private suspend fun loadCover(context: Context, url: String, px: Int): ImageBitmap? =
    loadSoftwareBitmap(context, url.artworkAt(px) ?: url, px)?.asImageBitmap()

/** A decoded picture the CPU can read, or null if it wouldn't load. */
private suspend fun loadSoftwareBitmap(context: Context, url: String, px: Int): Bitmap? = runCatching {
    val request = ImageRequest.Builder(context).data(url).size(px).forPixelAccess().build()
    val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    if (bitmap?.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
}.getOrNull()

/**
 * The one frame loop for every cover. Stops altogether while the page is off
 * screen, the app is in the background, or system animations are off.
 */
@Composable
private fun CollageClock(state: CollageState) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val motionAllowed = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    LaunchedEffect(lifecycle, state, motionAllowed) {
        if (!motionAllowed) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { state.visible }.collectLatest { running ->
                if (!running) return@collectLatest
                var last = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    state.seconds += ((now - last) / 1_000_000_000.0).coerceAtMost(0.1)
                    last = now
                }
            }
        }
    }
}

private fun DrawScope.drawCollage(
    state: CollageState,
    geo: CollageGeometry,
    enter: Float,
    borderPx: Float,
    centreY: Float = MAIN_CENTRE_Y,
) {
    val w = size.width
    val h = size.height.coerceAtLeast(1f)
    geo.ensure(w, h)
    if (enter <= 0.01f) return
    val t = state.seconds
    val tiles = state.tiles

    for (i in DRAW_ORDER) {
        val img = tiles.getOrNull(i) ?: continue
        val s = SLOTS[i]
        val omega = 2.0 * PI / s.period
        val dx = (s.ampX * w * s.depth * sin(omega * t + s.phase)).toFloat()
        val dy = (s.ampY * w * s.depth * sin(omega * 0.83 * t + s.phase * 1.7 + 1.1)).toFloat()
        val rot = s.rot + (s.ampR * sin(omega * 0.61 * t + s.phase * 0.9)).toFloat()
        val sc = 1f + 0.03f * sin(omega * 1.2 * t + s.phase * 2.3).toFloat()
        drawTile(img, geo.paths[i], geo.sides[i], s.x * w + dx, s.y * h + dy, rot, sc, enter, (1f - s.depth) * 0.45f, borderPx)
    }

    val main = state.main ?: return
    val om = 2.0 * PI / 26.0
    val rot = -7f + (1.6 * sin(om * t)).toFloat()
    val cx = w / 2f + (0.012 * w * sin(om * 0.7 * t + 1.0)).toFloat()
    val cy = h * centreY + (0.012 * w * sin(om * 0.9 * t + 2.0)).toFloat()
    val sc = (0.94f + 0.06f * enter) * (1f + 0.012f * sin(om * 1.3 * t).toFloat())
    // A soft shadow under the front cover.
    withTransform({
        translate(cx, cy + 0.02f * w)
        rotate(rot, Offset.Zero)
        scale(sc * 1.02f, sc * 1.02f, Offset.Zero)
    }) {
        drawPath(geo.mainPath, Color.Black.copy(alpha = 0.22f * enter))
    }
    drawTile(main, geo.mainPath, geo.mainSide, cx, cy, rot, sc, enter, 0f, borderPx)
}

private fun DrawScope.drawTile(
    img: ImageBitmap,
    path: Path,
    side: Float,
    cx: Float,
    cy: Float,
    rot: Float,
    sc: Float,
    alpha: Float,
    dim: Float,
    borderPx: Float,
) {
    val m = min(img.width, img.height)
    val sideI = side.toInt().coerceAtLeast(2)
    withTransform({
        translate(cx, cy)
        rotate(rot, Offset.Zero)
        scale(sc, sc, Offset.Zero)
        clipPath(path)
    }) {
        // The middle square: a video thumbnail is 16:9.
        drawImage(
            image = img,
            srcOffset = IntOffset((img.width - m) / 2, (img.height - m) / 2),
            srcSize = IntSize(m, m),
            dstOffset = IntOffset(-sideI / 2, -sideI / 2),
            dstSize = IntSize(sideI, sideI),
            alpha = alpha,
            filterQuality = FilterQuality.Medium,
        )
        if (dim > 0f) drawPath(path, Color.Black.copy(alpha = dim * alpha))
        if (borderPx > 0f) drawPath(path, Color.White.copy(alpha = 0.22f * alpha), style = Stroke(borderPx * 2f))
    }
}

/**
 * A cover for [browseId] made the way its page's backdrop is: one of its
 * newest songs' sleeves in front, others round it, on a wash of the front
 * one's colour. Saved as the playlist's custom cover (see
 * [com.music.bitchord.data.CustomCovers]). False when the playlist has no
 * songs to make one from or the sleeves couldn't be fetched.
 */
internal suspend fun generateCollageCover(context: Context, browseId: String): Boolean = runCatching {
    val liked = browseId == YtMusicRepository.LIKED_MUSIC || browseId == "LM"
    val id = when {
        liked -> YtMusicRepository.LIKED_MUSIC
        browseId.startsWith("VL") -> browseId
        else -> "VL$browseId"
    }
    val songs = YtMusicRepository.browseSongs(id).getOrNull()?.songs.orEmpty()
    val covers = collageCovers(songs, newestFirst = liked)
    if (covers.isEmpty()) return@runCatching false
    withContext(Dispatchers.Default) {
        val main = loadSoftwareBitmap(context, covers.first().artworkAt(MAIN_PX) ?: covers.first(), MAIN_PX)
            ?: return@withContext false
        val tiles = coroutineScope {
            covers.drop(1).take(SLOTS.size).map { url ->
                async { loadSoftwareBitmap(context, url.artworkAt(THUMB_PX) ?: url, THUMB_PX)?.asImageBitmap() }
            }.awaitAll()
        }
        val state = CollageState().apply {
            this.main = main.asImageBitmap()
            this.tiles = tiles
        }
        val swatch = androidx.palette.graphics.Palette.from(main).generate()
        val base = Color(swatch.getDominantColor(0xFF303030.toInt()))
        val top = androidx.compose.ui.graphics.lerp(base, Color.Black, 0.30f)
        val bottom = androidx.compose.ui.graphics.lerp(top, Color.Black, 0.15f)
        val out = Bitmap.createBitmap(GENERATED_COVER_PX, GENERATED_COVER_PX, Bitmap.Config.ARGB_8888)
        val image = out.asImageBitmap()
        val size = androidx.compose.ui.geometry.Size(GENERATED_COVER_PX.toFloat(), GENERATED_COVER_PX.toFloat())
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
            androidx.compose.ui.unit.Density(1f),
            androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(image),
            size,
        ) {
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(top, bottom)))
            drawCollage(state, CollageGeometry(), enter = 1f, borderPx = GENERATED_COVER_PX / 400f, centreY = 0.5f)
        }
        com.music.bitchord.data.CustomCovers.setBitmap(context, browseId, out)
    }
}.getOrDefault(false)

private const val GENERATED_COVER_PX = 1080
