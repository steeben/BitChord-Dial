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
import androidx.compose.runtime.rememberUpdatedState
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
import com.music.bitchord.data.CustomCovers
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/*
 * "Let Dial Generate Playlist Image": every playlist page gets a moving
 * collage behind it, one cover in front and others floating round it, made of
 * songs picked at random from the first batch of the list to load. A playlist
 * with a picture of its own (one its owner chose, a mix's artwork, a cover
 * set on this device) has that picture in front; one wearing the default 2x2
 * grid of its sleeves has a song's. A playlist's ⋯ menu can turn it off for
 * that one.
 */

private fun DetailPage.isLiked(): Boolean =
    browseId == YtMusicRepository.LIKED_MUSIC || browseId == "LM" || OfflinePlaylists.isLikedPage(browseId)

/**
 * The covers to draw for this page, or null to show its own artwork.
 *
 * Every playlist gets one while the setting is on, unless its "..." menu has
 * turned it off for that playlist. The front cover is the playlist's own
 * picture when it has one (see [ownCoverOf]), and the rest are picked
 * at random from the first batch of rows the first time the page is opened,
 * and then held: later pages of the list arriving and pulls to
 * refresh keep the picture it opened with. Kept by page for the life of the
 * process, so reopening one starts on it; a cover set or removed on this
 * device makes it again.
 */
@Composable
internal fun rememberGeneratedCollage(page: DetailPage, songs: List<Song>): List<String>? {
    val enabled by CustomizationSettings.generatePlaylistImages.collectAsStateWithLifecycle()
    val turnedOff by CustomizationSettings.playlistBackdropsOff.collectAsStateWithLifecycle()
    val customCovers by CustomCovers.covers.collectAsStateWithLifecycle()
    val custom = CustomCovers.coverFor(page.browseId, customCovers)
    val context = LocalContext.current.applicationContext
    val liked = page.isLiked()
    val eligible = enabled && page.type == BrowseType.PLAYLIST &&
        (liked || playlistTakesGeneratedImage(page.browseId)) &&
        CustomizationSettings.playlistBackdropKey(page.browseId) !in turnedOff
    // The first batch of rows is enough: a long playlist's later pages can
    // take a while, and the picture shouldn't wait for them.
    val settled = page.songs is UiState.Success && songs.isNotEmpty()
    val held = page.browseId + "|" + custom.orEmpty()
    var shown by remember(held) { mutableStateOf(heldCollages[held]) }
    LaunchedEffect(held, eligible, settled) {
        if (!eligible || !settled || held in heldCollages) return@LaunchedEffect
        val own = ownCoverOf(context, custom, page.thumbnailUrl, liked, songs)
        if (own != null) ownFronts += own
        val picked = collageCovers(
            songs,
            newestFirst = liked,
            max = if (own == null) COLLAGE_MAX_COVERS else COLLAGE_MAX_COVERS - 1,
            shuffle = kotlin.random.Random(System.nanoTime()),
        ).takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val next = listOfNotNull(own) + picked
        shown = next
        heldCollages[held] = next
    }
    return shown.takeIf { eligible }
}

/** The front covers that are a playlist's own picture, which make a different entrance. */
private val ownFronts = HashSet<String>()

/** Whether [covers]' front one is the playlist's own picture rather than a song's sleeve. */
internal fun collageFrontIsOwn(covers: List<String>?): Boolean =
    covers?.firstOrNull()?.let { it in ownFronts } == true

/**
 * The playlist's own picture, for the front of its generated image: the
 * cover set for it on this device ([custom]) unless the app generated that
 * one, else the cover it came with ([thumbnail]) unless that is made of its
 * songs' sleeves. Liked music's ([liked]) is never a grid: its thumbs-up or
 * heart is its own. Null when it has none, or the cover couldn't be read in time.
 */
private suspend fun ownCoverOf(
    context: Context,
    custom: String?,
    thumbnail: String?,
    liked: Boolean,
    songs: List<Song>,
): String? {
    if (custom != null && playlistCoverIsOwnByAddress(custom) == true) return custom
    if (thumbnail.isNullOrBlank()) return null
    val sleeves = songs.take(GRID_SOURCE_ROWS).mapNotNull { it.thumbnailUrl }.distinct()
    val key = artworkKey(thumbnail)
    if (songs.any { artworkKey(it.thumbnailUrl) == key }) return null
    if (liked) return thumbnail
    val own = playlistCoverIsOwnByAddress(thumbnail) ?: coverIsOwnByPicture(context, thumbnail, sleeves)
    return thumbnail.takeIf { own }
}

/** [url] judged by its pixels against [sleeves]. Answers are kept for the process. */
private suspend fun coverIsOwnByPicture(context: Context, url: String, sleeves: List<String>): Boolean {
    ownCoverVerdicts[url]?.let { return it }
    val verdict = withTimeoutOrNull(OWN_COVER_WAIT_MS) {
        withContext<Boolean?>(Dispatchers.Default) {
            val cover = loadPixels(context, url) ?: return@withContext null
            val others = coroutineScope { sleeves.map { async { loadPixels(context, it) } }.awaitAll() }
            coverIsOwnPicture(cover, others.filterNotNull())
        }
    // Not read in time: the page gets the picture it always had, a song in front.
    } ?: return false
    ownCoverVerdicts[url] = verdict
    return verdict
}

private suspend fun loadPixels(context: Context, url: String): CoverPixels? {
    val bitmap = loadSoftwareBitmap(context, url.artworkAt(GRID_PX) ?: url, GRID_PX) ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return CoverPixels(bitmap.width, bitmap.height, pixels)
}

private val ownCoverVerdicts = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

/** The rows whose sleeves a cover is compared with: the default grid is made of the first ones. */
private const val GRID_SOURCE_ROWS = 16
private const val GRID_PX = 96

/** How long the page's picture waits to learn whether the playlist's cover is its own. */
private const val OWN_COVER_WAIT_MS = 4_000L

/** The collage each playlist page settled on, by page. */
private val heldCollages = HashMap<String, List<String>>()

// ---------------------------------------------------------------------------
// The collage itself: one canvas on one clock, read only while drawing, so a
// frame is a redraw of one Spacer with no recomposition and no new bitmaps.
// ---------------------------------------------------------------------------

private const val THUMB_PX = 360
private const val MAIN_PX = 600
private const val MAIN_SHAPE = 4

/** How long the covers take to come in. */
private const val ENTER_MS = 700

/** How long the playlist's own picture takes to shrink from the whole banner into its place. */
private const val OWN_ENTER_MS = 800

/** The playlist's own picture is decoded larger: it starts out filling the banner. */
private const val OWN_MAIN_PX = 1000

/** How long the front cover waits for the floating ones before coming in without them. */
private const val TILES_WAIT_MS = 2_500L

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

/**
 * How much of the strip under the status bar and the top controls each of the
 * three covers floating up there fills ([SLOTS]' first three: left, middle,
 * right), and how much the front cover's top edge does. The rest is the
 * page's own colour showing between them.
 */
private val TOP_STRIP_SHARES = floatArrayOf(0.15f, 0.25f, 0.15f)
private const val TOP_STRIP_FRONT_SHARE = 0.20f

/**
 * How bright a generated collage is under the status bar and the top
 * controls, for [com.music.bitchord.ui.components.topChromeOverDark]. That strip is
 * mostly the page's colour and the dimmed covers floating at the top; the
 * front cover only reaches into it with its top edge. Read off the front
 * cover alone, a bright sleeve there (a white burst on red) turned the
 * glyphs black over a strip that was dark almost everywhere.
 *
 * [frontTop] is the front cover's top band, [floating] the whole-sleeve
 * luminance of the covers after it in the collage's order (null for one not
 * read yet or not there, which counts as the page showing through) and [wash]
 * the page colour's. Null until the front cover has been read.
 */
internal fun collageTopLuminance(frontTop: Float?, floating: List<Float?>, wash: Float): Float? {
    if (frontTop == null) return null
    var sum = frontTop * TOP_STRIP_FRONT_SHARE
    var covered = TOP_STRIP_FRONT_SHARE
    for (i in TOP_STRIP_SHARES.indices) {
        val tile = floating.getOrNull(i) ?: continue
        // As drawn: a far cover has black laid over it, see drawCollage.
        sum += tile * (1f - (1f - SLOTS[i].depth) * FAR_DIM) * TOP_STRIP_SHARES[i]
        covered += TOP_STRIP_SHARES[i]
    }
    return sum + wash * (1f - covered)
}

/** How many of a collage's floating covers [collageTopLuminance] reads. */
internal const val COLLAGE_TOP_COVERS = 3

/** How much black is laid over the furthest floating cover; nearer ones get less. */
private const val FAR_DIM = 0.45f

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
    private val settling = Path()

    /** The front cover's outline [placed] of the way from square-cornered to its own shape. */
    fun mainPathAt(placed: Float): Path {
        if (placed >= 1f) return mainPath
        val h = mainSide / 2f
        val r = RADII[MAIN_SHAPE]
        settling.reset()
        settling.addRoundRect(
            RoundRect(
                -h, -h, h, h,
                CornerRadius(mainSide * r[0] * placed), CornerRadius(mainSide * r[1] * placed),
                CornerRadius(mainSide * r[2] * placed), CornerRadius(mainSide * r[3] * placed),
            ),
        )
        return settling
    }

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
 *
 * With [ownFront] the front cover is the playlist's own picture, the one the
 * banner was showing whole: it starts out filling these bounds as the banner
 * did and shrinks back into its place in one movement, the others coming in
 * round it. [onEntrance] is called as that starts, for the banner to step
 * aside.
 */
@Composable
internal fun PlaylistCollageArt(
    covers: List<String>,
    modifier: Modifier = Modifier,
    ownFront: Boolean = false,
    onEntrance: () -> Unit = {},
) {
    val entranceStarted by rememberUpdatedState(onEntrance)
    val enterMs = if (ownFront) OWN_ENTER_MS else ENTER_MS
    val state = remember { CollageState() }
    val geometry = remember { CollageGeometry() }
    val borderPx = with(LocalDensity.current) { 1.dp.toPx() }
    val enter = remember { Animatable(0f) }
    val tilesEnter = remember { Animatable(0f) }
    val context = LocalContext.current

    // Nothing is drawn until every cover is decoded, so the picture arrives
    // as one movement. Loaded apart, the floating covers used to land after
    // the front cover's entrance had already run, and so appeared at full
    // strength with no entrance of their own.
    LaunchedEffect(covers) {
        val url = covers.firstOrNull() ?: return@LaunchedEffect
        enter.snapTo(0f)
        tilesEnter.snapTo(0f)
        coroutineScope {
            val main = async(Dispatchers.Default) { loadCover(context, url, if (ownFront) OWN_MAIN_PX else MAIN_PX) }
            val tiles = async(Dispatchers.Default) {
                // Sharp at every depth: distance is shown by size and
                // dimming alone, never by a smeared low-res copy.
                covers.drop(1).take(SLOTS.size).map { async { loadCover(context, it, THUMB_PX) } }.awaitAll()
            }
            state.main = main.await()
            // A cover that is slow to arrive doesn't hold the front one back
            // for long: the rest then make their own entrance when they come.
            val ready = withTimeoutOrNull(TILES_WAIT_MS) { tiles.await() }
            if (ready != null) {
                state.tiles = ready
                entranceStarted()
                launch { tilesEnter.animateTo(1f, tween(enterMs, easing = FastOutSlowInEasing)) }
                enter.animateTo(1f, tween(enterMs, easing = FastOutSlowInEasing))
            } else {
                entranceStarted()
                launch { enter.animateTo(1f, tween(enterMs, easing = FastOutSlowInEasing)) }
                state.tiles = tiles.await()
                tilesEnter.animateTo(1f, tween(enterMs, easing = FastOutSlowInEasing))
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
            .drawBehind { drawCollage(state, geometry, enter.value, tilesEnter.value, borderPx, fromBanner = ownFront) },
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
    tilesEnter: Float,
    borderPx: Float,
    centreY: Float = MAIN_CENTRE_Y,
    /** The front cover starts out filling the bounds and shrinks into place, instead of fading in. */
    fromBanner: Boolean = false,
) {
    val w = size.width
    val h = size.height.coerceAtLeast(1f)
    geo.ensure(w, h)
    if (!fromBanner && enter <= 0.01f && tilesEnter <= 0.01f) return
    val t = state.seconds
    val tiles = if (tilesEnter > 0.01f) state.tiles else emptyList()

    for (i in DRAW_ORDER) {
        val img = tiles.getOrNull(i) ?: continue
        val s = SLOTS[i]
        val omega = 2.0 * PI / s.period
        val dx = (s.ampX * w * s.depth * sin(omega * t + s.phase)).toFloat()
        val dy = (s.ampY * w * s.depth * sin(omega * 0.83 * t + s.phase * 1.7 + 1.1)).toFloat()
        val rot = s.rot + (s.ampR * sin(omega * 0.61 * t + s.phase * 0.9)).toFloat()
        val sc = 1f + 0.03f * sin(omega * 1.2 * t + s.phase * 2.3).toFloat()
        drawTile(img, geo.paths[i], geo.sides[i], s.x * w + dx, s.y * h + dy, rot, sc * (0.94f + 0.06f * tilesEnter), tilesEnter, (1f - s.depth) * FAR_DIM, borderPx)
    }

    val main = state.main?.takeIf { fromBanner || enter > 0.01f } ?: return
    // How far the front cover is into its place, and how much of it shows.
    // Fading in, it is in place from the start; out of the banner, it is
    // whole from the start and travels there.
    val placed = if (fromBanner) enter else 1f
    val alpha = if (fromBanner) 1f else enter
    val om = 2.0 * PI / 26.0
    val rot = (-7f + (1.6 * sin(om * t)).toFloat()) * placed
    val restX = w / 2f + (0.012 * w * sin(om * 0.7 * t + 1.0)).toFloat()
    val restY = h * centreY + (0.012 * w * sin(om * 0.9 * t + 2.0)).toFloat()
    val cx = w / 2f + (restX - w / 2f) * placed
    val cy = h / 2f + (restY - h / 2f) * placed
    val restScale = (if (fromBanner) 1f else 0.94f + 0.06f * enter) * (1f + 0.012f * sin(om * 1.3 * t).toFloat())
    // Filling the bounds as the banner's picture does: its middle square, over the longer side.
    val fillScale = max(w, h) / geo.mainSide
    val sc = fillScale + (restScale - fillScale) * placed
    val path = geo.mainPathAt(placed)
    // A soft shadow under the front cover.
    withTransform({
        translate(cx, cy + 0.02f * w * placed)
        rotate(rot, Offset.Zero)
        scale(sc * 1.02f, sc * 1.02f, Offset.Zero)
    }) {
        drawPath(path, Color.Black.copy(alpha = 0.22f * alpha * placed))
    }
    drawTile(main, path, geo.mainSide, cx, cy, rot, sc, alpha, 0f, borderPx * placed)
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
 * What a generated cover for a playlist is made of, fetched once so the
 * dialog can redraw its preview in each background colour without asking
 * again: the sleeves, and the colours offered for the ground under them.
 */
internal class CollageCoverDraft(
    val browseId: String,
    internal val main: Bitmap,
    internal val tiles: List<ImageBitmap?>,
    /** The colour the page's own backdrop would be: the first choice. */
    val automatic: Color,
    /** Other colours out of the sleeves, then neutrals. */
    val choices: List<Color>,
)

/**
 * Fetches [browseId]'s newest songs and their sleeves for a generated cover.
 * With [shuffle], a random pick of its songs instead, from the list already
 * fetched for it. Null when the playlist has no songs to make one from, or
 * the front sleeve couldn't be fetched.
 */
internal suspend fun prepareCollageCover(
    context: Context,
    browseId: String,
    dark: Boolean,
    shuffle: kotlin.random.Random? = null,
): CollageCoverDraft? = runCatching {
    val liked = browseId == YtMusicRepository.LIKED_MUSIC || browseId == "LM"
    val id = when {
        liked -> YtMusicRepository.LIKED_MUSIC
        browseId.startsWith("VL") -> browseId
        else -> "VL$browseId"
    }
    val songs = coverSongs[id]?.takeIf { shuffle != null }
        ?: YtMusicRepository.browseSongs(id).getOrNull()?.songs.orEmpty().also { coverSongs[id] = it }
    val covers = collageCovers(songs, newestFirst = liked, shuffle = shuffle)
    if (covers.isEmpty()) return@runCatching null
    withContext(Dispatchers.Default) {
        val main = loadSoftwareBitmap(context, covers.first().artworkAt(MAIN_PX) ?: covers.first(), MAIN_PX)
            ?: return@withContext null
        val tiles = coroutineScope {
            covers.drop(1).take(SLOTS.size).map { url ->
                async { loadSoftwareBitmap(context, url.artworkAt(THUMB_PX) ?: url, THUMB_PX)?.asImageBitmap() }
            }.awaitAll()
        }
        // As the page does it: the front sleeve's palette, re-based on its
        // accent (see DetailScreen's accentWashed).
        val automatic = com.music.bitchord.ui.theme.artworkPaletteOf(main.asImageBitmap(), dark)
            ?.collageWashed()?.wash ?: Color(0xFF303030)
        val swatches = androidx.palette.graphics.Palette.from(main).generate()
        val fromSleeve = listOfNotNull(
            swatches.vibrantSwatch, swatches.darkVibrantSwatch, swatches.mutedSwatch,
            swatches.darkMutedSwatch, swatches.lightVibrantSwatch,
        ).map { Color(it.rgb) }
        val neutrals = listOf(Color(0xFF121212), Color(0xFF5A5A5A), Color(0xFFEDEDED))
        val choices = (fromSleeve + neutrals)
            .distinctBy { it.value }
            .filterNot { it.closeTo(automatic) }
            .take(MAX_COVER_CHOICES)
        CollageCoverDraft(browseId, main, tiles, automatic, choices)
    }
}.getOrNull()

/** The songs each playlist's cover generator last fetched, so picking again doesn't fetch again. */
private val coverSongs = java.util.concurrent.ConcurrentHashMap<String, List<Song>>()

private fun Color.closeTo(other: Color): Boolean =
    abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue) < 0.06f

private const val MAX_COVER_CHOICES = 6

/** The generated cover on [background], fading 15% darker towards the foot as the page does. */
internal fun renderCollageCover(draft: CollageCoverDraft, background: Color, px: Int = GENERATED_COVER_PX): Bitmap {
    val state = CollageState().apply {
        main = draft.main.asImageBitmap()
        tiles = draft.tiles
    }
    val bottom = Color(background.red * 0.85f, background.green * 0.85f, background.blue * 0.85f, 1f)
    val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val image = out.asImageBitmap()
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        androidx.compose.ui.unit.Density(1f),
        androidx.compose.ui.unit.LayoutDirection.Ltr,
        androidx.compose.ui.graphics.Canvas(image),
        androidx.compose.ui.geometry.Size(px.toFloat(), px.toFloat()),
    ) {
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(background, bottom)))
        drawCollage(state, CollageGeometry(), enter = 1f, tilesEnter = 1f, borderPx = px / 400f, centreY = 0.5f)
    }
    return out
}

/** Renders [draft] full size on [background] and saves it as the playlist's custom cover. */
internal suspend fun saveCollageCover(context: Context, draft: CollageCoverDraft, background: Color): Boolean {
    val bitmap = withContext(Dispatchers.Default) { renderCollageCover(draft, background) }
    return com.music.bitchord.data.CustomCovers.setBitmap(context, draft.browseId, bitmap)
}

private const val GENERATED_COVER_PX = 1080
