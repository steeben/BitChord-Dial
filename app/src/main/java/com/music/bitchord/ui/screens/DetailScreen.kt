package com.music.bitchord.ui.screens

import com.music.bitchord.ui.components.trackArtSize
import android.os.Build
import com.music.bitchord.R

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import com.music.bitchord.ui.components.longPressMenuClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.graphics.luminance
import com.music.bitchord.data.model.LibraryState
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Radio
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.components.LocalLiquidGlassEnabled
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.transformations
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.music.bitchord.data.canvas.AppleArtistArt
import com.music.bitchord.data.canvas.AppleArtistArtRepository
import com.music.bitchord.data.canvas.keyColors
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.ArtistOnDevice
import com.music.bitchord.data.LibraryDuplicates
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.settings.SongSort
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.SubscriptionState
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.model.isSameTrackAs
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.canvas.ArtistSignature
import com.music.bitchord.data.canvas.ArtistSignatureRepository
import com.music.bitchord.data.canvas.SignatureInkTransformation
import com.music.bitchord.ui.components.DownloadedBadge
import com.music.bitchord.ui.components.ExplicitSongTitle
import com.music.bitchord.ui.components.LIBRARY_GRID_SPACING
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.NUMBERED_ROW_DIVIDER_INSET
import com.music.bitchord.ui.components.RowMoreButton
import com.music.bitchord.ui.components.ROW_DIVIDER_INSET
import com.music.bitchord.ui.components.SHELF_CARD_WIDTH
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.SearchPlayingBars
import com.music.bitchord.ui.components.libraryGrid
import com.music.bitchord.ui.components.lightweightLiquidGlass
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.components.detailSkeleton
import com.music.bitchord.ui.components.ShimmerBox
import com.music.bitchord.ui.components.topBarContentPadding
import com.music.bitchord.ui.components.trackColumnWidth
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.CanvasArtworkPlayer
import com.music.bitchord.ui.theme.ArtworkPalette
import com.music.bitchord.ui.theme.rememberArtworkPalette
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.SideEffect
import com.music.bitchord.ui.components.glassSheen
import kotlin.math.roundToInt
import java.util.Locale

private const val MAX_ARTIST_SONGS = 20
private const val SONGS_PER_COLUMN = 4
private const val ARTIST_ROW_MAX_ITEMS = 5

/** The artist photo, very slightly taller than it is wide. */
private const val ARTIST_PHOTO_RATIO = 0.95f

/** A release's sleeve, given a little more height than the artist photo. */
private const val SLEEVE_RATIO = 0.92f

/** The sleeve on a release page, as a fraction of the page width. */
private const val SLEEVE_FRACTION = 0.80f

private val SLEEVE_SHAPE = RoundedCornerShape(12.dp)
private val PILL_SHAPE = RoundedCornerShape(12.dp)

/**
 * Where the search field sits once it is open — directly under the header,
 * which is item zero. The one place that has to know, so that opening the
 * search can carry the page up to it.
 */
private const val SEARCH_ITEM_INDEX = 1

/**
 * The held header's sizes by page — controls offset, controls height, title
 * lead, artist header height — kept for the life of the process so a page
 * returned to while scrolled past its header holds it in the right place.
 */
private val heldHeaderSizes = HashMap<String, IntArray>()

/** The artist page's action row: the item after the header. */
private const val ARTIST_ACTIONS_INDEX = 1

/** The gap under a release's controls row, at the foot of its header. */
private val RELEASE_HEADER_FOOT = 14.dp

/** The band under the pinned controls in which rows fade out on their way up. */
private val PIN_FADE_BAND = 28.dp

/** The inset the header text and the action pills share. */
private val HEADER_GUTTER = PAGE_GUTTER + 8.dp

/** Extra breathing room for the editorial copy on album and artist pages. */
private val ABOUT_GUTTER = PAGE_GUTTER + 6.dp

/** The artist bio and everything below it: the page edge every page shares. */
private val ARTIST_CONTENT_GUTTER = PAGE_GUTTER

/**
 * How far past the foot of the artwork the title block is allowed to hang.
 *
 * Sat flush to the bottom of the picture it lands wherever the picture happens
 * to be busy, and on a sleeve with anything going on down there the title reads
 * as part of the artwork rather than as a caption to it. Dropped clear, it sits
 * on the blurred colour instead, which has nothing in it to compete.
 */
private val HEADER_DROP = 44.dp

/** How long the header's buttons take to fade in over their placeholders. */
private const val HEADER_FADE_MS = 260

/** The first rows of a list that has just loaded come in one after another, this far apart. */
private const val ROW_REVEAL_STEP_MS = 35L
private const val ROW_REVEAL_MS = 260
private const val ROW_REVEAL_COUNT = 14
private const val ROW_REVEAL_WINDOW_MS = 800L

/** How far the artist name/logo and everything under it sit above the usual [HEADER_DROP]. */
private val ARTIST_HEADER_LIFT = 64.dp

/** The artist action row: a large Play flanked by smaller glass circles. */
private val ARTIST_PLAY_BUTTON = 70.dp

/** A larger share of a smaller circle than the release headers' 0.44. */
private const val ARTIST_PLAY_ICON_SCALE = 0.56f

/** Radio and Shuffle, then the star and the pin: five across. */
private val ARTIST_SIDE_BUTTON_CROWDED = 46.dp
private val ARTIST_ACTION_GAP_CROWDED = 12.dp

/** The title logo's widest share of the page, and tallest share of the photo. */
private const val ARTIST_LOGO_WIDTH = 0.82f
private const val ARTIST_LOGO_MAX_HEIGHT = 0.30f

/** The signature closing the About section. */
private val ABOUT_LOGO_HEIGHT = 56.dp

/** A signature is drawn at this share of a title logo's size. */
private const val SIGNATURE_SCALE = 0.7f

/** Vector signatures decode at this width so small files stay crisp laid out large. */
private const val SVG_DECODE_WIDTH = 1600

private const val LOGO_RETRIES = 2
private const val LOGO_RETRY_DELAY_MS = 800L

private val CREDIT_SEPARATOR = Regex(""",\s|\s&\s|\s(?:x|and|feat\.?|ft\.?|with)\s""", RegexOption.IGNORE_CASE)

/** Whether this reads as several artists credited together rather than one name. */
private fun String.looksLikeCredit() = CREDIT_SEPARATOR.containsMatchIn(this)

/**
 * Album / artist / playlist page. Rendered inside the main content area
 * rather than as a sheet, so the tab bar and mini player stay visible.
 *
 * The page paints itself in the artwork's own colours — one solid tint behind
 * everything, the artwork itself across the top of it, and an accent taken off
 * the sleeve for the credit line and the Play/Shuffle pair. See
 * [rememberArtworkPalette] for how those are derived and kept legible.
 *
 * Two layers: [PageBackground] — the artwork, dissolving into the tint, and
 * nothing you can read — and the list of titles, buttons and rows over it.
 * Nothing in either is re-blurred while the page scrolls.
 */
@Composable
fun DetailScreen(
    page: DetailPage,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song, Boolean) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSectionItemClick: (ShelfItem) -> Unit,
    onArtistClick: (String, String) -> Unit,
    onAddSuggested: (Song) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    activeShelf: HomeShelf? = null,
    onActiveShelfChange: (HomeShelf?) -> Unit = {},
    /**
     * Holding one of the album cards on an artist page — the same menu the
     * shelves on every other tab open, so a release can be queued from
     * wherever it is seen rather than only from its own page.
     */
    onSectionItemLongPress: ((ShelfItem) -> Unit)? = null,
    /**
     * The header's overflow: everything this page can do to its whole track
     * list that isn't already one of the buttons beside it — downloading it
     * among them, see [com.music.bitchord.ui.components.BrowseActionsSheet].
     * Null on the pages with no list to act on.
     */
    onMore: ((List<Song>) -> Unit)? = null,
    /**
     * Saves this release to the account's library, or takes it out —
     * [DetailPage.library] says which way round. Null hides the control
     * entirely, which is the answer for a guest and for the pages YouTube never
     * offers to save; there is nothing to show a signed-out user here that
     * wouldn't just be refused.
     */
    onToggleLibrary: (() -> Unit)? = null,
    /**
     * Subscribes to this artist's channel, or unsubscribes —
     * [DetailPage.subscription] says which way round. The artist page's answer
     * to [onToggleLibrary], and null in the same circumstances: a guest, or a
     * page whose header never offered the button.
     */
    onToggleSubscription: (() -> Unit)? = null,
    /** Starts a radio off the artist's top song; null leaves the button off. */
    onStartRadio: ((Song) -> Unit)? = null,
    /**
     * Whether an artist page's Local toggle starts on, narrowing the page to
     * what is on this device. Read once, when the page opens; after that the
     * toggle is the listener's.
     */
    localOnlyByDefault: Boolean = false,
    /** Whether a track is on this device — a device file or a download. */
    isLocalSong: (Song) -> Boolean = { it.localUri != null || it.localPath != null },
    /** Whether a release card on an artist page is on this device. */
    isLocalRelease: (ShelfItem) -> Boolean = { false },
    /**
     * The artist's songs and albums among the device's files and downloads,
     * which the Local toggle lists beside what it keeps of the page: a file
     * is never one of the tracks YouTube Music's page lists. Null until
     * [onLoadDeviceArtist] has looked.
     */
    deviceArtist: ArtistOnDevice? = null,
    /** Looks the artist up on the device, by the page's browse id and the artist's name. */
    onLoadDeviceArtist: ((String, String) -> Unit)? = null,
    /** Library state of releases shown as cards on this page, by browse id. */
    releaseLibrary: Map<String, LibraryState> = emptyMap(),
    onLoadReleaseLibrary: ((String) -> Unit)? = null,
    /** Null where saving isn't offered — a guest. */
    onToggleReleaseLibrary: ((String) -> Unit)? = null,
    /**
     * How the track list is ordered — the release's own running order by
     * default, or alphabetical. Owned by the caller rather than this page
     * because the control for it lives in the top bar, alongside the account
     * photo, not in this header.
     */
    songSort: SongSort = SongSort.DEFAULT,
) {
    val rawSongs = (page.songs as? UiState.Success)?.data.orEmpty()
    val songs = remember(rawSongs, songSort) { rawSongs.sortedForDetail(songSort) }
    // What a numbered row shows regardless of [songSort] — an album's track
    // numbers are the sleeve's own and must not relabel themselves to match
    // wherever a sort put the row.
    val originalTrackNumbers = remember(rawSongs) {
        rawSongs.withIndex().associate { (i, s) -> s.videoId to i + 1 }
    }
    val isArtist = page.type == BrowseType.ARTIST
    // An artist page's Local toggle: only what is on this device, both the top
    // songs and the release shelves.
    var localOnly by rememberSaveable(page.browseId) { mutableStateOf(isArtist && localOnlyByDefault) }
    val albumsTitle = stringResource(R.string.albums)
    val shownSections = remember(page.sections, localOnly, isLocalRelease, deviceArtist, albumsTitle) {
        if (!localOnly) {
            page.sections
        } else {
            page.sections
                .map { shelf -> shelf.copy(items = shelf.items.filter(isLocalRelease)) }
                .filter { it.items.isNotEmpty() }
                .withDeviceAlbums(deviceArtist?.albums.orEmpty(), albumsTitle)
        }
    }
    // What the artist page plays and lists: everything, or with Local on, only
    // the tracks on this device, the page's own and the artist's other files.
    val artistSongs = remember(songs, localOnly, isLocalSong, deviceArtist) {
        if (localOnly) {
            val kept = songs.filter(isLocalSong)
            val ids = kept.mapTo(HashSet()) { it.videoId }
            kept + deviceArtist?.songs.orEmpty().filter { file ->
                file.videoId !in ids && kept.none { LibraryDuplicates.sameSong(it, file) }
            }
        } else {
            songs
        }
    }
    // Whether the top release card's album is already saved isn't on the shelf
    // item; it is read off the album once, as soon as the card is known.
    val topReleaseId = if (isArtist) shownSections.topRelease()?.browseId else null
    LaunchedEffect(topReleaseId) { topReleaseId?.let { onLoadReleaseLibrary?.invoke(it) } }
    // Apple Music's hero photo and title logo for this artist, found by name.
    // Null until (and unless) it arrives; the YouTube header stands in meanwhile.
    var appleArt by remember(page.browseId) {
        mutableStateOf(if (isArtist) AppleArtistArtRepository.cached(page.title) else null)
    }
    // Opened from a track, the title is the whole credit ("A, B & C") until the
    // page loads and swaps in the one artist's name. Searching a credit finds
    // nobody, so a title that reads as several artists waits for that swap; any
    // other name starts the moment the page opens.
    val nameSettled = !page.title.looksLikeCredit() || page.songs !is UiState.Loading
    // The device is looked up by the artist's name too, so it waits the same way.
    LaunchedEffect(localOnly, page.browseId, page.title, nameSettled) {
        if (isArtist && localOnly && nameSettled) onLoadDeviceArtist?.invoke(page.browseId, page.title)
    }
    LaunchedEffect(page.browseId, page.title, nameSettled) {
        if (isArtist && nameSettled) appleArt = AppleArtistArtRepository.artFor(page.title) ?: appleArt
    }
    // Secondary provider: a handwritten signature from the artist-signature
    // API in Settings. In the header when Apple has no title logo, otherwise
    // at the foot of the About section.
    val signatureEnabled by AppSettings.signatureApiEnabled.collectAsStateWithLifecycle()
    val signatureBaseUrl by AppSettings.signatureApiUrl.collectAsStateWithLifecycle()
    val signatureUpdates by ArtistSignatureRepository.updates.collectAsStateWithLifecycle()
    var signature by remember(page.browseId) { mutableStateOf<ArtistSignature?>(null) }
    LaunchedEffect(page.browseId, page.title, nameSettled, signatureEnabled, signatureBaseUrl, signatureUpdates) {
        if (!isArtist || !nameSettled) return@LaunchedEffect
        val base = ArtistSignatureRepository.effectiveBaseUrl(signatureEnabled, signatureBaseUrl)
        if (base == null) {
            signature = null
            return@LaunchedEffect
        }
        // Bounded retries with backoff: a sleeping self-hosted API can take
        // ~30-60s to wake, and a single miss would otherwise stand until the
        // page is reopened. Misses aren't cached, so retrying is cheap.
        val backoff = listOf(0L, 5_000L, 15_000L, 30_000L)
        for (wait in backoff) {
            if (wait > 0) delay(wait)
            signature = ArtistSignatureRepository.cached(page.title, base)
                ?: ArtistSignatureRepository.signatureFor(page.title, base)
                ?: signature
            if (signature != null) return@LaunchedEffect
        }
    }
    val palette = rememberArtworkPalette(
        imageUrl = appleArt?.heroUrl ?: page.thumbnailUrl,
        keyColors = appleArt?.keyColors(),
    )

    // Narrowing the running order in place — the release equivalent of the
    // filter box on the Local Music tab, and the one thing a long track list
    // needs that scrolling can't give it. Off by default and reset with the
    // page: a filter left on an album that was closed and reopened would be a
    // page that appears to have lost most of its tracks.
    var searching by rememberSaveable(page.browseId) { mutableStateOf(false) }
    var query by rememberSaveable(page.browseId) { mutableStateOf("") }
    // Whether the field still owes the keyboard an appearance. Held here rather
    // than in the field, which is a row in a lazy list: scrolled out of sight it
    // is disposed, and a field that asks for focus every time it is composed
    // would throw the keyboard back up each time it scrolled into view.
    var focusSearch by remember(page.browseId) { mutableStateOf(false) }
    val closeSearch = {
        searching = false
        query = ""
    }
    // Back closes the search first — this handler is registered after the one
    // that pops the page, so it is the one that answers while it's enabled.
    BackHandler(enabled = searching) { closeSearch() }
    val toggleSearch: () -> Unit = {
        if (searching) {
            closeSearch()
        } else {
            searching = true
            focusSearch = true
        }
    }

    // Each surviving row still knows where it sat in the full running order, so
    // an album's track numbers stay the album's rather than becoming positions
    // in the filtered list.
    val matches = remember(songs, query) { songs.matching(query) }
    // What a tap plays: the filtered set of tracks currently standing in the
    // list. When no filter is active, matches contains the full running order
    // and plays the complete release/playlist from the tapped position.
    val queue = remember(matches) { matches.map { it.value } }
    val suggested = remember(page.suggestedSongs, query) {
        page.suggestedSongs.matching(query).map { it.value }
    }

    // What marks a row as already downloaded, tinted from the sleeve like the
    // rest of the page. Null on any page that is itself a reading of this
    // device — the Downloads folder, one downloaded playlist — where every row
    // qualifies and the badge would be decoration rather than information.
    val downloadedTint = palette.accent.takeUnless { page.browseId.startsWith("local:") }

    // Animated cover art on the header, the same feature the player has.
    // Albums only: a playlist's artwork is a collage and an artist page's is a
    // photograph, and neither is something a label publishes a canvas for.
    val canvasEnabled by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    val prioritizeSpotifyCanvas by AppSettings.prioritizeSpotifyCanvas.collectAsStateWithLifecycle()
    // The credit line the header shows is the artist as far as the catalogue
    // services are concerned. A browse card's subtitle sometimes omits it, in
    // which case the tracks themselves know who it is.
    val credit = page.headerLines(songs.size).first.ifBlank { songs.firstOrNull()?.artist.orEmpty() }
    var canvas by remember(page.browseId) { mutableStateOf<CanvasArtwork?>(null) }
    LaunchedEffect(page.browseId, page.title, credit, canvasEnabled, prioritizeSpotifyCanvas) {
        // An artist's clip is set below, by the lookup that finds it.
        if (isArtist) return@LaunchedEffect
        if (!canvasEnabled || page.type != BrowseType.ALBUM) {
            canvas = null
            return@LaunchedEffect
        }
        // As on the player: the credit fills in once the tracks load, so this
        // can run twice. Keep a clip that is already playing if the second
        // pass comes back empty.
        canvas = CanvasRepository.canvasForAlbum(page.title, credit) ?: canvas
    }
    // An artist's clip comes with the same Apple lookup as its photograph.
    val artistVideo = appleArt?.videoUrl
    LaunchedEffect(artistVideo, canvasEnabled) {
        if (isArtist) canvas = artistVideo?.takeIf { canvasEnabled }?.let { CanvasArtwork(url = it) }
    }

    // Opening the search carries the page up to it, so the field lands just
    // clear of the frosted bar with the tracks under it rather than at the foot
    // of a screen still filled with artwork. Done as an effect rather than in
    // the tap, so the row it scrolls to is already in the list by the time it
    // runs.
    val searchStop = with(LocalDensity.current) { topBarContentPadding().roundToPx() }
    val releaseActionsFoot = with(LocalDensity.current) { RELEASE_HEADER_FOOT.roundToPx() }
    val pinBandPx = with(LocalDensity.current) { PIN_FADE_BAND.toPx() }

    // The controls row — the release's circles, or the artist's Play row —
    // stops under the top bar once a scroll carries it up there, and the list
    // runs on beneath it. Where the row sits inside its list item and how tall
    // it is are reported by the row; the rest is read off the list.
    //
    // Seeded from the last time this page was laid out: coming back to a page
    // left scrolled past its header, the header isn't in the list to measure,
    // and a held header placed off guessed sizes lands in the wrong place.
    val measured = remember(page.browseId) { heldHeaderSizes[page.browseId] ?: IntArray(4) }
    var controlsOffset by remember(page.browseId) { mutableIntStateOf(measured[0]) }
    var controlsHeight by remember(page.browseId) { mutableIntStateOf(measured[1]) }
    // How far above the row the page's title starts. The row stops that far
    // below the top bar, so at the stop the title sits just under the bar with
    // the credit and meta lines between it and the row — and from there on the
    // title is what scrolls up and fades, not the row.
    var controlsLead by remember(page.browseId) { mutableIntStateOf(measured[2]) }
    // The artist page's header is an item of its own above the row.
    var artistHeaderHeight by remember(page.browseId) { mutableIntStateOf(measured[3]) }
    SideEffect {
        heldHeaderSizes[page.browseId] = intArrayOf(controlsOffset, controlsHeight, controlsLead, artistHeaderHeight)
    }
    val controlsIndex = if (isArtist) ARTIST_ACTIONS_INDEX else 0
    val hasControls = if (isArtist) songs.isNotEmpty() else songs.isNotEmpty() || page.songs is UiState.Loading
    val showsControls by rememberUpdatedState(hasControls)
    val pinTop by rememberUpdatedState(searchStop + controlsLead)
    // How far past the stop the row would have scrolled, in px; negative while
    // it is still below it, null where there is no row to pin.
    val controlsOvershoot: () -> Float? = remember(listState, controlsIndex, page.browseId) {
        {
            // Nothing is held until every size it is placed by is known.
            if (!showsControls || controlsHeight <= 0 || controlsLead <= 0 || (isArtist && artistHeaderHeight <= 0)) {
                null
            } else {
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == controlsIndex }
                when {
                    item != null -> (pinTop - (item.offset + controlsOffset)).toFloat()
                    listState.firstVisibleItemIndex > controlsIndex -> Float.MAX_VALUE
                    else -> null
                }
            }
        }
    }
    val controlsPinned by remember(controlsOvershoot) {
        derivedStateOf { (controlsOvershoot() ?: -1f) >= 0f }
    }
    // Where the top of the page — artwork, title and all — sits while the
    // controls are at their stop. The whole header holds there with them, and
    // only the list goes on scrolling, under it.
    val headerStopTop by rememberUpdatedState(
        pinTop - controlsOffset - if (isArtist) artistHeaderHeight else 0,
    )

    // The artist page's row, in the list and again in the held header.
    val artistActions: @Composable (Modifier, Dp) -> Unit = { rowModifier, bottomSpace ->
        ActionRow(
            palette = palette,
            onPlay = { if (artistSongs.isNotEmpty()) onSongClick(artistSongs, 0) },
            onShuffle = { if (artistSongs.isNotEmpty()) onShuffle(artistSongs) },
            subscription = page.subscription?.takeIf { onToggleSubscription != null },
            onToggleSubscription = onToggleSubscription,
            onStartRadio = onStartRadio?.let { radio -> artistSongs.firstOrNull()?.let { first -> { radio(first) } } },
            localOnly = localOnly,
            onToggleLocal = { localOnly = !localOnly },
            // A library artist is a grouping of tracks rather than a channel:
            // its row is laid out like an album's.
            compact = page.browseId.startsWith("local:"),
            bottomSpace = bottomSpace,
            modifier = rowModifier,
        )
    }

    LaunchedEffect(searching) {
        // Under the pinned controls rather than behind them: the field is the
        // next thing down from the row, so the row is carried exactly to its
        // stop and the field lands just below it.
        if (searching) listState.animateScrollToItem(SEARCH_ITEM_INDEX, -(pinTop + controlsHeight + releaseActionsFoot))
    }

    // When the track list lands while the page is up (rather than being in
    // hand when it opened), the moment it did — its first rows fade in from
    // then, one after another.
    val openedLoading = remember(page.browseId) { page.songs is UiState.Loading }
    val songsLoaded = page.songs is UiState.Success
    val rowsRevealAt = remember(page.browseId, songsLoaded) {
        if (songsLoaded && openedLoading) System.currentTimeMillis() else null
    }

    AnimatedContent(
        targetState = activeShelf,
        transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
        },
        label = "artist_shelf_transition",
        modifier = modifier.fillMaxSize(),
    ) { targetShelf ->
        if (targetShelf == null) {
            BoxWithConstraints(Modifier.fillMaxSize().background(palette.wash)) {
                // The artwork is drawn behind the list rather than in it, so both need
                // to agree on its height without being able to ask each other. The
                // width is the page's, so the ratio decides it and both can work it out
                // alone.
                //
                // Measured rather than read off the window. The player runs
                // full-screen at every window size, so this page's own width in
                // landscape is the *whole* window. Straight off that width, the ratio hands back
                // a hero taller than the window itself — the artwork and track list
                // end up scrolled out of sight beneath what reads as a blank page.
                // Capping against the window's own height is what keeps the ratio's
                // math honest once the width it's fed is no longer guaranteed to be
                // the narrow one it was written for.
                val artHeight = (maxWidth / if (isArtist) ARTIST_PHOTO_RATIO else SLEEVE_RATIO)
                    .coerceAtMost(maxHeight * 0.6f)

                PageBackground(
                    page = page,
                    palette = palette,
                    canvas = canvas,
                    artHeight = artHeight,
                    listState = listState,
                    heroUrl = appleArt?.heroUrl,
                    floorTop = { if (controlsPinned) headerStopTop.toFloat() else Float.NEGATIVE_INFINITY },
                    modifier = Modifier.matchParentSize(),
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        // Once the header is held, the list scrolls on under it:
                        // rows fade out in the band just under the controls and
                        // are gone by the time they reach them, so nothing of the
                        // list shows behind the held header or the top bar.
                        // Offscreen only then, so the cut is out of the list
                        // alone and not the artwork painted behind it.
                        .graphicsLayer {
                            compositingStrategy = if (controlsPinned) {
                                CompositingStrategy.Offscreen
                            } else {
                                CompositingStrategy.Auto
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            if (!controlsPinned) return@drawWithContent
                            val cut = pinTop + controlsHeight.toFloat()
                            drawRect(
                                color = Color.Transparent,
                                topLeft = Offset.Zero,
                                size = Size(size.width, cut),
                                blendMode = BlendMode.DstIn,
                            )
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to Color.Black,
                                    startY = cut,
                                    endY = cut + pinBandPx,
                                ),
                                topLeft = Offset(0f, cut),
                                size = Size(size.width, pinBandPx),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                    // Both artist photos and release artwork run edge-to-edge up under
                    // the glass bar — the image is the top of the page, not a card on it.
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                ) {
            item(key = "header") {
                if (isArtist) {
                    Box(Modifier.onSizeChanged { artistHeaderHeight = it.height }) {
                        ArtistHeader(
                            page = page,
                            palette = palette,
                            artHeight = artHeight,
                            appleArt = appleArt,
                            signature = signature,
                            onNamePlaced = { controlsLead = it },
                        )
                    }
                } else {
                    ReleaseHeader(
                        page = page,
                        palette = palette,
                        artHeight = artHeight,
                        trackCount = songs.size,
                        songs = songs,
                        onPlay = { onSongClick(songs, 0) },
                        onShuffle = { onShuffle(songs) },
                        searching = searching,
                        onSearch = toggleSearch,
                        onMore = onMore,
                        onArtistClick = onArtistClick,
                        onToggleLibrary = onToggleLibrary,
                        onControlsPlaced = { top, height, lead ->
                            controlsOffset = top
                            controlsHeight = height
                            controlsLead = lead
                        },
                        controlsPinned = { controlsPinned },
                    )
                }
            }

            if (searching) {
                item(key = "search") {
                    DetailSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        onClose = closeSearch,
                        autoFocus = focusSearch,
                        onFocused = { focusSearch = false },
                        palette = palette,
                        type = page.type,
                    )
                }
            }

            val description = page.description

            if (songs.isNotEmpty() && isArtist) {
                item(key = "actions") {
                    artistActions(
                        Modifier
                            .onSizeChanged {
                                controlsOffset = 0
                                controlsHeight = it.height
                            }
                            .graphicsLayer { alpha = if (controlsPinned) 0f else 1f },
                        22.dp,
                    )
                }
            }

            // The first release on the shelves below, pulled out as a card of its own.
            val topRelease = if (isArtist) shownSections.topRelease() else null
            if (topRelease != null) {
                item(key = "top-release") {
                    TopReleaseCard(
                        item = topRelease,
                        palette = palette,
                        onClick = { onSectionItemClick(topRelease) },
                        onLongPress = onSectionItemLongPress?.let { { it(topRelease) } },
                        saved = topRelease.browseId?.let { releaseLibrary[it]?.saved },
                        onToggleSaved = onToggleReleaseLibrary?.let { toggle ->
                            topRelease.browseId?.let { id -> { toggle(id) } }
                        },
                    )
                }
            }

            // YouTube's own editorial blurb — an album or an artist only, per
            // [DetailPage.description]. A playlist never carries one, and the
            // section is skipped for it even on the rare response that does.
            //
            // On an artist page it closes the page, after the shelves, rather
            // than sitting between the action row and the songs.
            // The artist's counts close it, under the text, so a page whose blurb is
            // empty still gets the section for them.
            val hasStats = isArtist &&
                (page.subscriberCountText != null || page.monthlyListenerCount != null)
            // The header holds Apple's logo here, so the ink goes at the foot of the page instead.
            val aboutSignature = if (isArtist && appleArt?.logoUrl != null) signature else null
            val showAbout = (!description.isNullOrBlank() &&
                (page.type == BrowseType.ALBUM || isArtist)) || hasStats || aboutSignature != null
            fun LazyListScope.aboutItem() {
                if (!showAbout) return
                item(key = "about") {
                    // Clear of the last shelf above it.
                    Box(Modifier.padding(top = if (isArtist) 28.dp else 0.dp)) {
                    AboutSection(
                        title = stringResource(
                            if (isArtist) R.string.about_artist else R.string.about_album,
                        ),
                        text = description?.takeIf { it.isNotBlank() },
                        palette = palette,
                        horizontalPadding = if (isArtist) ARTIST_CONTENT_GUTTER else ABOUT_GUTTER,
                        stats = if (hasStats) {
                            {
                                ArtistStatsRow(
                                    subscriberCountText = page.subscriberCountText,
                                    monthlyListenerCount = page.monthlyListenerCount,
                                    palette = palette,
                                )
                            }
                        } else {
                            null
                        },
                        footer = aboutSignature?.let { sig ->
                            {
                                AboutSignature(
                                    signature = sig,
                                    name = page.title,
                                    palette = palette,
                                    horizontalPadding = ARTIST_CONTENT_GUTTER,
                                )
                            }
                        },
                    )
                    }
                }
            }
            if (!isArtist) aboutItem()

            when (val state = page.songs) {
                is UiState.Loading -> detailSkeleton(isArtist)
                is UiState.Error -> item { MessageState(state.message) }
                is UiState.Success -> if (isArtist) {
                    // Said only once the device has been looked through, not while it is.
                    val deviceLooked = deviceArtist != null || onLoadDeviceArtist == null
                    if (localOnly && deviceLooked && artistSongs.isEmpty() && shownSections.isEmpty()) {
                        item(key = "nothing-local") { MessageState(stringResource(R.string.nothing_on_device_for_artist)) }
                    }
                    // An artist's full song list would bury the album shelves, so
                    // it pages sideways four at a time and stops at twenty. What is
                    // on the device is listed whole: it is the listener's own.
                    if (artistSongs.isNotEmpty()) item {
                        val top = if (localOnly) artistSongs else artistSongs.take(MAX_ARTIST_SONGS)
                        SectionHeading(
                            title = stringResource(if (localOnly) R.string.songs else R.string.top_songs),
                            palette = palette,
                            horizontalPadding = ARTIST_CONTENT_GUTTER,
                        )
                        BoxWithConstraints {
                            val columnWidth = trackColumnWidth(maxWidth)
                            val topSongsState = rememberLazyListState()
                            LazyRow(
                                state = topSongsState,
                                contentPadding = PaddingValues(horizontal = ARTIST_CONTENT_GUTTER),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                flingBehavior = rememberSnapFlingBehavior(lazyListState = topSongsState),
                            ) {
                                items(top.chunked(SONGS_PER_COLUMN)) { column ->
                                    Column(Modifier.width(columnWidth)) {
                                        column.forEach { song ->
                                            CompactSongRow(
                                                song = song,
                                                palette = palette,
                                                onClick = { onSongClick(top, top.indexOf(song)) },
                                                onLongPress = { onSongLongPress(song) },
                                                downloadedTint = downloadedTint,
                                                isCurrent = song.isSameTrackAs(currentSong),
                                                isPlaying = song.isSameTrackAs(currentSong) && isPlaying,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Every row on an album carries the same sleeve, which is
                    // already the largest thing on the page — Apple Music
                    // numbers those rows instead, and so does this.
                    val numbered = page.type == BrowseType.ALBUM
                    if (matches.isEmpty() && state.data.isNotEmpty()) {
                        item(key = "no-matches") {
                            MessageState(stringResource(R.string.nothing_matches, query))
                        }
                    }
                    itemsIndexed(
                        items = matches,
                        key = { _, entry -> "${entry.index}_${entry.value.videoId}" },
                    ) { position, entry ->
                        val song = entry.value
                        val isCurrent = song.isSameTrackAs(currentSong)
                        // Just landed: the first screenful fades in a row at a
                        // time, top to bottom. Anything after (scrolling, a
                        // filter) is simply there.
                        val reveal = remember {
                            val fresh = rowsRevealAt != null && position < ROW_REVEAL_COUNT &&
                                System.currentTimeMillis() - rowsRevealAt < ROW_REVEAL_WINDOW_MS
                            Animatable(if (fresh) 0f else 1f)
                        }
                        LaunchedEffect(reveal) {
                            if (reveal.value < 1f) {
                                delay(position * ROW_REVEAL_STEP_MS)
                                reveal.animateTo(1f, tween(ROW_REVEAL_MS))
                            }
                        }
                        Column(Modifier.graphicsLayer { alpha = reveal.value }) {
                        SongRow(
                            song = if (numbered) {
                                song
                            } else {
                                song.copy(thumbnailUrl = song.thumbnailUrl ?: page.thumbnailUrl)
                            },
                            onClick = {
                                onSongClick(queue, position)
                            },
                            onLongPress = { onSongLongPress(song) },
                            onSwipeToQueue = { onSongSwipe(song, it) },
                            rowBackground = Color.Transparent,
                            // The track's place on the release, not its place in
                            // what the filter — or a sort — left standing.
                            trackNumber = originalTrackNumbers[song.videoId].takeIf { numbered },
                            subtitleColor = palette.onBackgroundVariant,
                            downloadedTint = downloadedTint,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            activeTint = palette.accent,
                            searchPlayingStyle = true,
                        )
                        if (position < matches.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(
                                    start = if (numbered) NUMBERED_ROW_DIVIDER_INSET else ROW_DIVIDER_INSET,
                                ),
                                thickness = 0.5.dp,
                                color = palette.divider,
                            )
                        }
                        }
                    }
                }
            }

            // Tracks YouTube offers to round the playlist out, never folded
            // into the list above — see [DetailPage.suggestedSongs].
            if (suggested.isNotEmpty()) {
                item(key = "suggested-heading") {
                    SectionHeading(stringResource(R.string.suggested), palette)
                }
                itemsIndexed(
                    suggested,
                    key = { _, song -> "suggested-${song.videoId}" },
                ) { index, song ->
                    SuggestedSongRow(
                        song = song,
                        palette = palette,
                        onClick = { onSongClick(suggested, index) },
                        onLongPress = { onSongLongPress(song) },
                        onAdd = { onAddSuggested(song) },
                        downloadedTint = downloadedTint,
                        isCurrent = song.isSameTrackAs(currentSong),
                        isPlaying = song.isSameTrackAs(currentSong) && isPlaying,
                    )
                    if (index < suggested.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                            thickness = 0.5.dp,
                            color = palette.divider,
                        )
                    }
                }
            }

            // Albums / Singles & EPs carousels (artist pages).
            items(shownSections) { shelf ->
                val canShowAll = shelf.items.size > ARTIST_ROW_MAX_ITEMS
                val displayItems = remember(shelf.items) {
                    if (canShowAll) shelf.items.take(ARTIST_ROW_MAX_ITEMS) else shelf.items
                }
                Column(Modifier.padding(top = 22.dp)) {
                    SectionHeading(
                        title = shelf.title,
                        palette = palette,
                        onShowAll = if (canShowAll) { { onActiveShelfChange(shelf) } } else null,
                        horizontalPadding = ARTIST_CONTENT_GUTTER,
                    )
                    val carouselState = rememberLazyListState()
                    LazyRow(
                        state = carouselState,
                        contentPadding = PaddingValues(horizontal = ARTIST_CONTENT_GUTTER),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        flingBehavior = rememberSnapFlingBehavior(lazyListState = carouselState),
                    ) {
                        items(displayItems) { item ->
                            SectionCard(
                                item = item,
                                palette = palette,
                                onClick = { onSectionItemClick(item) },
                                onLongPress = onSectionItemLongPress?.let { { it(item) } },
                                modifier = Modifier.width(if (item.isVideo) VIDEO_CARD_WIDTH else SHELF_CARD_WIDTH),
                            )
                        }
                    }
                }
            }
            if (isArtist) aboutItem()
        }

                // The held header: a copy of the page's top — title, credit
                // and controls — at the place it stopped. The artwork behind it
                // holds at the same place (see [PageBackground]'s floor), and
                // the list's own copy is cut away while this one is up, so only
                // one is ever seen or pressed. Nothing in it but the buttons
                // takes a touch, so a drag on the title still scrolls the list.
                if (controlsPinned) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, headerStopTop) },
                    ) {
                        if (isArtist) {
                            Column {
                                ArtistHeader(
                                    page = page,
                                    palette = palette,
                                    artHeight = artHeight,
                                    appleArt = appleArt,
                                    signature = signature,
                                )
                                artistActions(Modifier, 0.dp)
                            }
                        } else {
                            ReleaseHeader(
                                page = page,
                                palette = palette,
                                artHeight = artHeight,
                                trackCount = songs.size,
                                songs = songs,
                                onPlay = { onSongClick(songs, 0) },
                                onShuffle = { onShuffle(songs) },
                                searching = searching,
                                onSearch = toggleSearch,
                                onMore = onMore,
                                onArtistClick = onArtistClick,
                                onToggleLibrary = onToggleLibrary,
                            )
                        }
                    }
                }
    }
} else {
    ArtistShelfGridPage(
        shelf = targetShelf,
        palette = palette,
        onItemClick = onSectionItemClick,
        onItemLongPress = onSectionItemLongPress,
        contentPadding = contentPadding,
    )
}
}
}

/**
 * An album or playlist: the title, credit, meta and action buttons that sit
 * over the foot of the artwork.
 *
 * The artwork itself is not here — [PageBackground] draws it, pinned behind
 * the list while this scrolls over it. What this item holds in
 * its place is a spacer of exactly the picture's height, which is what keeps
 * the two in step: the list reserves the room, the background fills it.
 */
@Composable
private fun ReleaseHeader(
    page: DetailPage,
    palette: ArtworkPalette,
    artHeight: Dp,
    trackCount: Int,
    songs: List<Song>,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    searching: Boolean,
    onSearch: () -> Unit,
    onMore: ((List<Song>) -> Unit)?,
    onArtistClick: (String, String) -> Unit,
    onToggleLibrary: (() -> Unit)?,
    /**
     * Where the controls row sits inside this header, how tall it is, and how
     * far above it the title starts, in px.
     */
    onControlsPlaced: (top: Int, height: Int, lead: Int) -> Unit = { _, _, _ -> },
    /** True while the pinned copy above the list stands in for the row here. */
    controlsPinned: () -> Boolean = { false },
) {
    val (credit, meta) = page.headerLines(trackCount, songs.playtime())
    // Held outside state: only read when the row reports where it landed.
    val headerCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val titleTop = remember { intArrayOf(0) }
    // Every row on a release carries the same credit — see [pageCredit] — so
    // the first one speaks for the whole page, the same source the rows'
    // own long-press "Open artist" already reads from.
    val artist = songs.firstOrNull()

    // The outer Box just needs to be as tall as its content — we don't force
    // an aspect ratio here so the action buttons can extend below the artwork.
    Box(Modifier.fillMaxWidth().onPlaced { headerCoords[0] = it }) {

        Spacer(Modifier.fillMaxWidth().height(artHeight + HEADER_DROP))

        // Text + action row stacked, pinned to the bottom of the Box.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = RELEASE_HEADER_FOOT),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = HEADER_GUTTER)
                    .onGloballyPositioned { title ->
                        val header = headerCoords[0]?.takeIf { it.isAttached } ?: return@onGloballyPositioned
                        titleTop[0] = header.localPositionOf(title, Offset.Zero).y.roundToInt()
                    },
            )
            // Artist / credit line
            if (credit.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = credit,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.accent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = HEADER_GUTTER)
                        .let { m ->
                            val id = artist?.artistId
                            if (id == null) {
                                m
                            } else {
                                m.clip(RoundedCornerShape(6.dp))
                                    .clickable { onArtistClick(id, artist.artist) }
                            }
                        },
                )
            }
            // Metadata (kind • year • count)
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                    color = palette.onBackgroundVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = HEADER_GUTTER),
                )
            }

            // Action buttons — live inside the header so there is zero gap
            // between the cover zone and the first song row.
            if (songs.isNotEmpty() || page.songs is UiState.Loading) {
                Spacer(Modifier.height(14.dp))
                ReleaseActions(
                    page = page,
                    palette = palette,
                    songs = songs,
                    onPlay = onPlay,
                    onShuffle = onShuffle,
                    searching = searching,
                    onSearch = onSearch,
                    onMore = onMore,
                    onToggleLibrary = onToggleLibrary,
                    modifier = Modifier
                        .onGloballyPositioned { row ->
                            val header = headerCoords[0]?.takeIf { it.isAttached } ?: return@onGloballyPositioned
                            val top = header.localPositionOf(row, Offset.Zero).y.roundToInt()
                            onControlsPlaced(top, row.size.height, (top - titleTop[0]).coerceAtLeast(0))
                        }
                        .graphicsLayer { alpha = if (controlsPinned()) 0f else 1f },
                )
            }
        }
    }
}

/**
 * A release's controls row — save, shuffle, play, search, more. Drawn in the
 * header, and again pinned under the top bar once the header has scrolled
 * it up there (see [DetailScreen]), so both copies have to be the same thing.
 * While the tracks load, placeholders of the same size hold their place, so
 * nothing above or below moves when the real ones fade in over them.
 */
@Composable
private fun ReleaseActions(
    page: DetailPage,
    palette: ArtworkPalette,
    songs: List<Song>,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    searching: Boolean,
    onSearch: () -> Unit,
    onMore: ((List<Song>) -> Unit)?,
    onToggleLibrary: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Only where YouTube said the release can be saved and the
    // caller is willing to take the write — see [onToggleLibrary].
    val library = page.library?.takeIf { onToggleLibrary != null }
    // Always five slots — save, shuffle, play, search, more — so the
    // loading placeholders match and the row doesn't reflow between
    // releases. A slot this release can't use stays, greyed out. 46dp
    // keeps five circles inside the shared header gutter on 360dp.
    val circleSize = 46.dp
    AnimatedContent(
        targetState = songs.isNotEmpty(),
        transitionSpec = {
            fadeIn(tween(HEADER_FADE_MS)) togetherWith fadeOut(tween(HEADER_FADE_MS / 2))
        },
        contentAlignment = Alignment.Center,
        label = "releaseActions",
        modifier = modifier,
    ) { ready ->
    if (!ready) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HEADER_GUTTER)
                .graphicsLayer { alpha = 0.4f },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            repeat(5) { index ->
                ShimmerBox(Modifier.size(if (index == 2) ARTIST_PLAY_BUTTON else circleSize), CircleShape)
            }
        }
        return@AnimatedContent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_GUTTER),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (library == null) {
            CircleIconButton(
                icon = BitChordIcons.Plus,
                contentDescription = stringResource(R.string.add_to_library),
                palette = palette,
                onClick = {},
                size = circleSize,
                enabled = false,
            )
        } else {
            CircleIconButton(
                // A tick, not a filled-in plus: the pair reads as
                // "not yet / done", which is what the state is.
                icon = if (library.saved) BitChordIcons.Check else BitChordIcons.Plus,
                contentDescription = if (library.saved) {
                    stringResource(R.string.remove_from_library)
                } else {
                    stringResource(R.string.add_to_library)
                },
                palette = palette,
                onClick = { onToggleLibrary?.invoke() },
                haptic = if (library.saved) Haptic.ToggleOff else Haptic.ToggleOn,
                size = circleSize,
            )
        }
        CircleIconButton(
            icon = BitChordIcons.Shuffle,
            contentDescription = stringResource(R.string.shuffle),
            palette = palette,
            onClick = onShuffle,
            haptic = Haptic.Resume,
            size = circleSize,
        )
        PlayPill(
            onClick = onPlay,
            iconOnly = true,
            // The artist page's big Play, so the two headers match.
            size = ARTIST_PLAY_BUTTON,
            iconScale = ARTIST_PLAY_ICON_SCALE,
            // The artwork's own colour, as on the artist page.
            containerColor = palette.accent,
        )
        // Where the download circle used to be. Downloading a
        // release is a thing done once and then not thought about;
        // finding a track on a long playlist is a thing done while
        // reading the page, so it is the one that earns a button and
        // the download moved to the overflow beside it.
        CircleIconButton(
            icon = if (searching) Icons.Rounded.Close else BitChordIcons.Search,
            contentDescription = stringResource(
                if (searching) R.string.close_search else R.string.search_this_list,
            ),
            palette = palette,
            onClick = onSearch,
            size = circleSize,
        )
        CircleIconButton(
            icon = Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(R.string.more),
            palette = palette,
            onClick = { onMore?.invoke(songs) },
            size = circleSize,
            enabled = onMore != null,
        )
    }
    }
}

/**
 * The filter box, shown under the header while the search circle is lit.
 *
 * Live rather than submit-on-enter, and for the same reason the Local Music
 * one is (see `LocalSearchField`): the list it narrows is already in memory, so
 * there is nothing for a submit action to wait for.
 *
 * Glass rather than a filled field — it is one of the header's controls that
 * happens to be typed into, and it sits close enough to the circles that a
 * Material text field beside them would read as a different app's furniture.
 */
@Composable
private fun DetailSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    autoFocus: Boolean,
    onFocused: () -> Unit,
    palette: ArtworkPalette,
    type: BrowseType,
) {
    // Opened by a tap on a button, which is as clear a statement of intent as
    // the keyboard is going to get — so it comes up with the field rather than
    // making the tap land twice. Once only: see [autoFocus]'s owner.
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focus.requestFocus()
            onFocused()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_GUTTER)
            .padding(bottom = 10.dp)
            .height(46.dp)
            .clip(PILL_SHAPE)
            .background(com.music.bitchord.ui.components.fieldFill(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), PILL_SHAPE)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = BitChordIcons.Search,
            contentDescription = null,
            tint = palette.onBackgroundVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_this_list),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.onBackgroundVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.onBackground),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                // Clearing and closing are the same gesture: an empty filter
                // showing an unfiltered list is a row of furniture with nothing
                // left to do.
                .clickable { if (query.isEmpty()) onClose() else onQueryChange("") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(
                    if (query.isEmpty()) R.string.close_search else R.string.clear_search,
                ),
                tint = palette.onBackgroundVariant,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

private fun List<Song>.sortedForDetail(sort: SongSort): List<Song> = when (sort) {
    SongSort.DEFAULT -> this
    SongSort.TITLE_ASC -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    SongSort.TITLE_DESC -> sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
    // A catalogue row carries no added date, so its place in the running
    // order stands in for one: a playlist is appended to as songs are added,
    // and reversed that puts the most recent addition on top. Rows that do
    // carry a MediaStore timestamp — device tracks, downloads — are dated
    // properly, with the position order left to break the ties.
    SongSort.DATE_ADDED_ASC -> withIndex()
        .sortedWith(
            compareBy<IndexedValue<Song>> { (_, song) ->
                song.localDateAddedSeconds ?: Long.MAX_VALUE
            }.thenBy { (position, _) -> position },
        )
        .map { it.value }
    SongSort.DATE_ADDED_DESC -> withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<Song>> { (_, song) ->
                song.localDateAddedSeconds ?: Long.MIN_VALUE
            }.thenByDescending { (position, _) -> position },
        )
        .map { it.value }
}

/**
 * The rows a typed query leaves standing, each still carrying its place in the
 * full list — see the track numbers on an album, which are the release's own
 * and not positions in whatever the filter left.
 */
internal fun List<Song>.matching(query: String): List<IndexedValue<Song>> {
    val all = withIndex().toList()
    if (query.isBlank()) return all
    return all.filter { (_, song) ->
        song.title.contains(query, ignoreCase = true) ||
            song.artist.contains(query, ignoreCase = true) ||
            song.albumName?.contains(query, ignoreCase = true) == true
    }
}

/**
 * An artist: their name across the foot of the photo [PageBackground] is
 * drawing behind this. See [ReleaseHeader] for why the picture isn't here.
 */
@Composable
private fun ArtistHeader(
    page: DetailPage,
    palette: ArtworkPalette,
    artHeight: Dp,
    appleArt: AppleArtistArt?,
    /** Drawn in place of the name when Apple has no title logo. */
    signature: ArtistSignature? = null,
    /** How far the name (or logo) starts above the foot of this header, in px. */
    onNamePlaced: (Int) -> Unit = {},
) {
    val headerCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun reportName(name: LayoutCoordinates) {
        val header = headerCoords[0]?.takeIf { it.isAttached } ?: return
        onNamePlaced((header.size.height - header.localPositionOf(name, Offset.Zero).y.roundToInt()).coerceAtLeast(0))
    }
    Box(Modifier.fillMaxWidth().onPlaced { headerCoords[0] = it }) {
        Spacer(Modifier.fillMaxWidth().height(artHeight + HEADER_DROP - ARTIST_HEADER_LIFT))
        val logoUrl = appleArt?.logoUrl
        // Secondary provider: drawn only when Apple has no title logo.
        val isSignature = logoUrl == null && signature != null
        val imageUrl = logoUrl ?: signature?.imageUrl
        // Signature vectors are dark ink on transparency, so they are tinted
        // to the page's text colour. Scans (jpeg/png) go through the ink
        // transformation instead: tinting an opaque scan would flatten it.
        val isSignatureSvg = isSignature && signature?.format == "svg"
        val isSignatureScan = isSignature && !isSignatureSvg
        // Whether the logo has actually been drawn. Until it has — a first fetch
        // of an Apple logo is a few hundred KB the CDN may still be resizing — the
        // name stands in for it, so the header is never left with nothing in it.
        var logoShown by remember(imageUrl) { mutableStateOf(false) }
        // The logo fades in over the name rather than replacing it outright.
        val logoIn by animateFloatAsState(
            targetValue = if (logoShown) 1f else 0f,
            animationSpec = tween(ART_CROSSFADE_MS, easing = FastOutSlowInEasing),
            label = "logoIn",
        )
        if (imageUrl != null) {
            val context = LocalPlatformContext.current
            var attempt by remember(imageUrl) { mutableIntStateOf(0) }
            var failed by remember(imageUrl) { mutableStateOf(false) }
            val aspect = (if (logoUrl != null) appleArt?.logoAspect else signature?.aspect)
                ?.coerceIn(0.4f, 6f) ?: 1f
            // Remembered, and at the size the URL already names: nothing about the
            // load waits on layout to decide how big to decode, and a recomposition
            // can't hand Coil a new request that cancels the one in flight.
            // Vectors decode small and would upscale into a blur, so SVGs render large.
            val request = remember(imageUrl, attempt) {
                ImageRequest.Builder(context).data(imageUrl).apply {
                    if (isSignatureSvg) {
                        size(SVG_DECODE_WIDTH, (SVG_DECODE_WIDTH / aspect).roundToInt())
                    } else {
                        size(coil3.size.Size.ORIGINAL)
                    }
                    if (isSignatureScan) transformations(SignatureInkTransformation)
                }.build()
            }
            // A dropped fetch gets two more tries before the name is left in place.
            LaunchedEffect(failed) {
                if (failed && attempt < LOGO_RETRIES) {
                    delay(LOGO_RETRY_DELAY_MS)
                    failed = false
                    attempt++
                }
            }
            // Apple's own title logo stands in for the name, laid over its photo.
            // Capped in height as well as width: a logo set on two or three
            // lines is nearly square, and by width alone it would swallow the
            // photograph it is meant to sit on. A signature is a panoramic ink
            // strip and reads best smaller than a title logo.
            val sizeFactor = if (isSignature) SIGNATURE_SCALE else 1f
            val maxLogoHeight = artHeight * ARTIST_LOGO_MAX_HEIGHT * sizeFactor
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = logoIn }
                    .onGloballyPositioned { if (logoShown) reportName(it) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = page.title,
                    contentScale = ContentScale.Fit,
                    colorFilter = if (isSignatureSvg) ColorFilter.tint(palette.onBackground) else null,
                    onState = { state ->
                        when (state) {
                            is AsyncImagePainter.State.Success -> logoShown = true
                            is AsyncImagePainter.State.Error -> failed = true
                            else -> Unit
                        }
                    },
                    modifier = Modifier
                        .padding(top = 14.dp, bottom = if (isSignature) 6.dp else 20.dp)
                        // Sized from the incoming width in layout rather than through a
                        // BoxWithConstraints, which would subcompose the image.
                        .layout { measurable, constraints ->
                            val widest = constraints.maxWidth * ARTIST_LOGO_WIDTH * sizeFactor
                            val height = minOf(widest / aspect, maxLogoHeight.toPx()).roundToInt()
                            val width = (height * aspect).roundToInt()
                            val placeable = measurable.measure(Constraints.fixed(width, height))
                            layout(width, height) { placeable.place(0, 0) }
                        },
                )
                if (isSignature) {
                    SignedByCaption(
                        name = page.title,
                        palette = palette,
                        modifier = Modifier.padding(horizontal = HEADER_GUTTER).padding(bottom = 20.dp),
                    )
                }
            }
        }
        if (logoIn < 1f) Text(
            text = page.title,
            style = MaterialTheme.typography.displayLarge,
            color = palette.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = 1f - logoIn }
                .onGloballyPositioned { if (!logoShown) reportName(it) }
                // Bottom is half the top inset — the stats pills (or, absent
                // those, the action row) sit closer under the name than the
                // name sits under the artwork.
                .padding(start = HEADER_GUTTER, end = HEADER_GUTTER, top = 14.dp, bottom = 7.dp),
        )
    }
}

/**
 * Everything on a detail page that is colour rather than words: the artwork,
 * with its foot dissolved into the solid [ArtworkPalette.wash] the page is
 * painted in.
 *
 * A layer of its own so the list can be scrolled over it rather than carry it.
 * It follows the scroll instead of being scrolled: the list owns the gesture
 * and reserves the room, and the picture is offset to follow whatever the list
 * did with item zero. Read in a placement block, so a scroll moves it without
 * recomposing anything.
 *
 * The join used to be hidden by a live blur laid across it, re-run on every
 * frame of every scroll. A blurred copy of the sleeve does the same job here
 * drawn once: nothing about it changes as the page moves, so it is rasterised
 * on the first frame and only composited after that.
 */
@Composable
private fun PageBackground(
    page: DetailPage,
    palette: ArtworkPalette,
    canvas: CanvasArtwork?,
    artHeight: Dp,
    listState: LazyListState,
    heroUrl: String? = null,
    /** The highest the artwork goes: where it holds while the page's header is held. */
    floorTop: () -> Float = { Float.NEGATIVE_INFINITY },
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    // Kept under a clip too: the clip covers it once faded in, and dropping it
    // the moment a clip turned up cut the foot of the banner from soft to sharp.
    val softenFoot = !reduceDynamicBlur &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseArt = page.thumbnailUrl.artworkAt(HEADER_ART_PX)
    // An artist's Apple photo turns up after the page has opened. It fades in
    // over the picture already up rather than cutting to it.
    val heroArt = heroUrl?.takeIf { it != baseArt }
    var heroLoaded by remember(heroArt) { mutableStateOf(false) }
    val heroIn by animateFloatAsState(
        targetValue = if (heroLoaded) 1f else 0f,
        animationSpec = tween(ART_CROSSFADE_MS, easing = FastOutSlowInEasing),
        label = "heroIn",
    )
    // The moving clip too, slower still and only once the page has settled,
    // so the banner eases from still to moving instead of jumping to it.
    val clipIn = remember(canvas) { Animatable(0f) }
    LaunchedEffect(canvas) {
        if (canvas == null) return@LaunchedEffect
        delay(CLIP_FADE_DELAY_MS)
        clipIn.animateTo(1f, tween(CLIP_FADE_MS, easing = FastOutSlowInEasing))
    }

    Box(modifier.clipToBounds()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(artHeight)
                .offset {
                    val top = maxOf(listState.headerTop(artHeight.toPx()), floorTop())
                    IntOffset(0, top.roundToInt())
                },
        ) {
            HeaderArt(
                art = baseArt,
                softenFoot = softenFoot,
                modifier = Modifier.matchParentSize().background(palette.elevated),
            )
            if (heroArt != null) {
                HeaderArt(
                    art = heroArt,
                    softenFoot = softenFoot,
                    onLoaded = { heroLoaded = true },
                    modifier = Modifier.matchParentSize().graphicsLayer { alpha = heroIn },
                )
            }

            // Above the still art but below the scrim, so the scrim that
            // settles the header into the page still sits over it. Always
            // running: unlike the player's sleeve there is no transport here
            // to follow, and the page is only up while it's being read.
            canvas?.let { clip ->
                CanvasArtworkPlayer(
                    canvas = clip,
                    isPlaying = true,
                    presentationAlpha = { clipIn.value },
                    modifier = Modifier.matchParentSize(),
                )
            }

            // Ends on the page colour at full strength, so there is no edge
            // left where the artwork stops. Eased rather than run straight: a
            // gradient that changes slope at a stop shows a line at that stop,
            // however close the colours either side of it are.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            0.65f to palette.wash.copy(alpha = 0.30f),
                            0.82f to palette.wash.copy(alpha = 0.72f),
                            0.94f to palette.wash.copy(alpha = 0.95f),
                            1.00f to palette.wash,
                        ),
                    ),
            )
        }
    }
}

/**
 * Where the top of the artwork currently is.
 *
 * The list is the one being scrolled; the background only has to agree with it.
 * While the header is item zero and on screen, how far it has been scrolled off
 * the top is exactly the offset the picture behind it needs. Once it isn't,
 * there is nothing to agree with, and the picture parks two artwork-heights up
 * — far enough that no part of it comes back down.
 */
private fun LazyListState.headerTop(artHeightPx: Float): Float =
    if (firstVisibleItemIndex == 0) -firstVisibleItemScrollOffset.toFloat() else -artHeightPx * 2f

/**
 * One picture for the header: the artwork, and (when [softenFoot]) the same
 * artwork blurred and faded in over its lower half, so the picture loses its
 * detail before it loses its colour — a merge rather than a fade to a flat
 * tint. The same request as the sharp copy, so it is a memory-cache hit and
 * lands on the same frame.
 */
@Composable
private fun HeaderArt(
    art: String?,
    softenFoot: Boolean,
    modifier: Modifier = Modifier,
    onLoaded: () -> Unit = {},
) {
    Box(modifier) {
        AsyncImage(
            model = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onState = { if (it is AsyncImagePainter.State.Success) onLoaded() },
            modifier = Modifier.matchParentSize(),
        )
        if (softenFoot) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    // Offscreen so the mask cuts the blurred result rather
                    // than each draw beneath it.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(SOFT_FOOT_MASK, blendMode = BlendMode.DstIn)
                    }
                    // Rectangle keeps the edges clamped to the picture's own
                    // colour rather than fading to transparent at the foot.
                    .blur(SOFT_FOOT_BLUR, BlurredEdgeTreatment.Rectangle),
            )
        }
    }
}

/** How long a late-arriving photo, and then a moving clip (after a beat), take to fade in over the banner. */
private const val ART_CROSSFADE_MS = 700
private const val CLIP_FADE_DELAY_MS = 350L
private const val CLIP_FADE_MS = 1100

/** Where the blurred copy starts to show and where it has fully taken over. */
private val SOFT_FOOT_MASK = Brush.verticalGradient(
    0.35f to Color.Transparent,
    0.75f to Color.Black,
)

/**
 * Wide enough that no shapes survive where the blurred copy is at full
 * strength — a blur that leaves them reads as a blurred photograph, and a
 * blurred photograph next to a flat colour is still two surfaces.
 */
private val SOFT_FOOT_BLUR = 48.dp

/**
 * Radio • Shuffle • Play • Star • Local — the artist page's action row, as
 * Apple Music lays it out: a large Play circle with small circles either side.
 *
 * The star is the page's existing subscribe toggle. Any of the four small
 * circles the page can't offer (no radio, a guest's star) stays, greyed out,
 * so every page's row is five across.
 *
 * [compact] is the album pages' look instead — dark glass circles, a solid
 * Play — for an artist that is a grouping of library tracks.
 */
@Composable
private fun ActionRow(
    palette: ArtworkPalette,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    bottomSpace: Dp = 22.dp,
    /** The artist header's subscribe state, or null where it isn't offered. */
    subscription: SubscriptionState? = null,
    onToggleSubscription: (() -> Unit)? = null,
    /** The Play circle's fill; null takes the artwork's own colour. */
    playColor: Color? = null,
    /** Left of Shuffle: a radio built off the artist. */
    onStartRadio: (() -> Unit)? = null,
    /** Right of the star: the Local toggle, lit while on. Null greys it out. */
    localOnly: Boolean = false,
    onToggleLocal: (() -> Unit)? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val playFill = playColor ?: palette.accent
    // Always five slots — radio, shuffle, play, star, local — as on a release
    // page. One this artist can't use stays in place, greyed out.
    val side = if (compact) 46.dp else ARTIST_SIDE_BUTTON_CROWDED
    val lightFill = !compact
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_GUTTER),
        horizontalArrangement = Arrangement.spacedBy(
            if (compact) 8.dp else ARTIST_ACTION_GAP_CROWDED,
            Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = Icons.Rounded.Radio,
            contentDescription = stringResource(R.string.start_radio),
            palette = palette,
            onClick = { onStartRadio?.invoke() },
            haptic = Haptic.Resume,
            size = side,
            lightFill = lightFill,
            enabled = onStartRadio != null,
        )
        CircleIconButton(
            icon = BitChordIcons.Shuffle,
            contentDescription = stringResource(R.string.shuffle),
            palette = palette,
            onClick = onShuffle,
            haptic = Haptic.Resume,
            size = side,
            lightFill = lightFill,
        )
        PlayPill(
            onClick = onPlay,
            iconOnly = true,
            size = ARTIST_PLAY_BUTTON,
            iconScale = ARTIST_PLAY_ICON_SCALE,
            containerColor = playFill,
            cutout = !compact,
        )
        val subscribed = subscription?.subscribed == true
        CircleIconButton(
            icon = if (subscribed) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = stringResource(
                if (subscribed) R.string.unsubscribe else R.string.subscribe,
            ),
            palette = palette,
            onClick = { onToggleSubscription?.invoke() },
            haptic = if (subscribed) Haptic.ToggleOff else Haptic.ToggleOn,
            size = side,
            lightFill = lightFill,
            enabled = subscription != null,
        )
        if (onToggleLocal != null) {
            LocalToggle(localOnly, onToggleLocal, palette, side, lightFill = lightFill)
        } else {
            CircleIconButton(
                icon = Icons.Rounded.PhoneAndroid,
                contentDescription = stringResource(R.string.local_only),
                palette = palette,
                onClick = {},
                size = side,
                lightFill = lightFill,
                enabled = false,
            )
        }
    }
    Spacer(Modifier.height(bottomSpace))
}

/** The artist page's Local circle: lit (a solid white fill) while it narrows the page. */
@Composable
private fun LocalToggle(
    on: Boolean,
    onToggle: () -> Unit,
    palette: ArtworkPalette,
    size: Dp,
    lightFill: Boolean,
) {
    CircleIconButton(
        icon = Icons.Rounded.PhoneAndroid,
        contentDescription = stringResource(R.string.local_only),
        palette = palette,
        onClick = onToggle,
        haptic = if (on) Haptic.ToggleOff else Haptic.ToggleOn,
        size = size,
        lightFill = lightFill,
        lit = on,
    )
}

/**
 * The prominent Play control that anchors the action row. Releases and the
 * artist page both use its icon-only circle; the labeled pill is the default.
 * Both use a fixed white surface with black content so the primary action
 * survives every palette.
 */
@Composable
private fun PlayPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 32.dp,
    iconOnly: Boolean = false,
    size: Dp = 50.dp,
    iconScale: Float = 0.44f,
    containerColor: Color = Color.White,
    /** Punch the triangle out of the circle, so the page shows through it, as Apple's does. */
    cutout: Boolean = false,
) {
    // Black on the light fills Apple picks, white on the rare dark one.
    val contentColor = if (containerColor.luminance() > 0.35f) Color.Black else Color.White
    // Resume rather than a flat tap: this button starts a queue, and the rising
    // pair says so.
    val haptics = rememberHaptics()
    if (iconOnly && cutout) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .clickable {
                    haptics.play(Haptic.Resume)
                    onClick()
                }
                // The glass buttons' light and rim, over the fill and the cut.
                .glassSheen(CircleShape)
                // Offscreen so the clear below cuts the circle drawn under it,
                // not whatever the page has behind.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawCircle(containerColor)
                    val box = this.size.minDimension * iconScale
                    val unit = box / 24f
                    val origin = Offset((this.size.width - box) / 2f, (this.size.height - box) / 2f)
                    val triangle = androidx.compose.ui.graphics.Path().apply {
                        moveTo(origin.x + 6.8f * unit, origin.y + 4.8f * unit)
                        lineTo(origin.x + 19.2f * unit, origin.y + 12f * unit)
                        lineTo(origin.x + 6.8f * unit, origin.y + 19.2f * unit)
                        close()
                    }
                    drawPath(triangle, Color.Black, blendMode = BlendMode.Clear)
                    // The same round-joined pen the icon is drawn with.
                    drawPath(
                        triangle,
                        Color.Black,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2f * unit,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round,
                        ),
                        blendMode = BlendMode.Clear,
                    )
                }
                .semantics { contentDescription = "Play" },
        )
        return
    }
    Row(
        modifier = modifier
            .then(if (iconOnly) Modifier.size(size) else Modifier.height(size))
            .clip(CircleShape)
            .background(containerColor)
            // The glass buttons' light and rim beside it, over its own colour.
            .glassSheen(CircleShape)
            .clickable {
                haptics.play(Haptic.Resume)
                onClick()
            }
            .then(if (iconOnly) Modifier else Modifier.padding(horizontal = horizontalPadding)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = BitChordIcons.Play,
            contentDescription = if (iconOnly) stringResource(R.string.play) else null,
            tint = contentColor,
            modifier = Modifier.size(if (iconOnly) size * iconScale else 18.dp),
        )
        if (!iconOnly) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.play),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
            )
        }
    }
}

/**
 * Small circular icon-only button — used for Shuffle and Download flanking the
 * Play pill. Translucent glassy fill, accent-coloured icon.
 */
@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    haptic: Haptic = Haptic.Tap,
    size: Dp = 50.dp,
    /** Whiter veil in place of the dark glass — the artist page's Apple look. */
    lightFill: Boolean = false,
    /** A toggle that is on: a solid white circle with a dark glyph. */
    lit: Boolean = false,
    /** False keeps the circle in its slot, greyed out and inert. */
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .then(
                if (lit) {
                    Modifier.clip(CircleShape).background(Color.White)
                } else if (lightFill) {
                    Modifier.artistVeil(CircleShape)
                } else {
                    Modifier.lightweightLiquidGlass(
                        shape = CircleShape,
                        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    )
                },
            )
            .clickable(enabled = enabled) {
                haptics.play(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (lit) Color.Black else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size * 0.44f),
        )
    }
}

/**
 * The artist page's buttons and cards: liquid glass when that is on, else the
 * light veil Apple uses there.
 */
@Composable
private fun Modifier.artistVeil(shape: androidx.compose.foundation.shape.CornerBasedShape): Modifier =
    if (LocalLiquidGlassEnabled.current && isGlassSupported()) {
        lightweightLiquidGlass(shape = shape, fallbackColor = Color.White.copy(alpha = LIGHT_FILL_ALPHA))
    } else {
        clip(shape)
            .background(Color.White.copy(alpha = LIGHT_FILL_ALPHA), shape)
            .border(1.dp, Color.White.copy(alpha = TOP_RELEASE_EDGE_ALPHA), shape)
    }

/** "1.2M subscribers" and "3.4M monthly listeners", off the artist header. */
@Composable
private fun ArtistStatsRow(
    subscriberCountText: String?,
    monthlyListenerCount: String?,
    palette: ArtworkPalette,
) {
    // Stacked and left-aligned with the text under it, since this now sits in
    // the About section rather than across the header.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ARTIST_CONTENT_GUTTER, end = ARTIST_CONTENT_GUTTER, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        // YouTube's own count text already reads "1.2M subscribers" in full,
        // so only the number is kept and the label re-said in the app's own
        // words — the one way to fit both stats on one line on a narrow
        // screen without either wrapping into two.
        subscriberCountText?.let {
            StatChip(
                icon = Icons.Rounded.Person,
                text = stringResource(R.string.subscribers, it.substringBefore(' ')),
                palette = palette,
            )
        }
        monthlyListenerCount?.let {
            StatChip(
                icon = Icons.Rounded.GraphicEq,
                text = stringResource(R.string.monthly_listeners, it.substringBefore(' ')),
                palette = palette,
            )
        }
    }
}

@Composable
private fun StatChip(icon: ImageVector, text: String, palette: ArtworkPalette) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .lightweightLiquidGlass(
                shape = CircleShape,
                fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.onBackgroundVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = palette.onBackgroundVariant,
        )
    }
}

/**
 * YouTube's own editorial note for a release or an artist, collapsed to a
 * few lines with a tap to read the rest — the same "About" block Apple
 * Music and YouTube Music itself show under the header.
 *
 * Whether there's anything to expand is only knowable once the text has
 * been laid out at the collapsed line count, so the "More" toggle is held
 * back until that measurement says the clipped text actually lost
 * something — otherwise a two-line bio would show a toggle with nothing
 * behind it to reveal.
 */
@Composable
private fun AboutSection(
    title: String,
    text: String?,
    palette: ArtworkPalette,
    horizontalPadding: Dp = ABOUT_GUTTER,
    /** Drawn under the text. */
    stats: (@Composable () -> Unit)? = null,
    /** Drawn last, under the stats. */
    footer: (@Composable () -> Unit)? = null,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var clipped by remember(text) { mutableStateOf(false) }
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.onBackground,
            modifier = Modifier.padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 2.dp,
                bottom = 6.dp,
            ),
        )
        if (text != null) Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = palette.onBackgroundVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> if (!expanded) clipped = result.hasVisualOverflow },
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = horizontalPadding)
                .let { m -> if (clipped || expanded) m.clickable { expanded = !expanded } else m },
        )
        if (clipped || expanded) {
            Text(
                text = stringResource(if (expanded) R.string.less else R.string.more),
                style = MaterialTheme.typography.labelLarge,
                color = palette.accent,
                modifier = Modifier
                    .padding(horizontal = horizontalPadding, vertical = 4.dp)
                    .clickable { expanded = !expanded },
            )
        }
        if (stats != null) {
            Spacer(Modifier.height(if (text != null) 12.dp else 4.dp))
            stats()
        }
        footer?.invoke()
    }
}

/** "Signed by **Name**" — the artist's name in bold, whatever order the language puts it in. */
@Composable
private fun SignedByCaption(
    name: String,
    palette: ArtworkPalette,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    val signed = stringResource(R.string.signed_by, name)
    val nameAt = signed.indexOf(name)
    Text(
        text = buildAnnotatedString {
            append(signed)
            if (nameAt >= 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), nameAt, nameAt + name.length)
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = palette.onBackground.copy(alpha = 0.8f),
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * The handwritten signature with its signed-by line, closing the About section of an artist whose
 * header already carries Apple's title logo.
 */
@Composable
private fun AboutSignature(
    signature: ArtistSignature,
    name: String,
    palette: ArtworkPalette,
    horizontalPadding: Dp,
) {
    val context = LocalPlatformContext.current
    val isSvg = signature.format == "svg"
    val aspect = signature.aspect.coerceIn(0.4f, 6f)
    val request = remember(signature.imageUrl) {
        ImageRequest.Builder(context).data(signature.imageUrl).apply {
            if (isSvg) {
                size(SVG_DECODE_WIDTH, (SVG_DECODE_WIDTH / aspect).roundToInt())
            } else {
                size(coil3.size.Size.ORIGINAL)
                transformations(SignatureInkTransformation)
            }
        }.build()
    }
    var loaded by remember(signature.imageUrl) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        horizontalAlignment = Alignment.Start,
    ) {
        AsyncImage(
            model = request,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            colorFilter = if (isSvg) ColorFilter.tint(palette.onBackground) else null,
            onState = { if (it is AsyncImagePainter.State.Success) loaded = true },
            modifier = Modifier
                .padding(top = 20.dp, bottom = 6.dp)
                .height(ABOUT_LOGO_HEIGHT)
                .aspectRatio(aspect),
        )
        if (loaded) SignedByCaption(name = name, palette = palette, textAlign = TextAlign.Start)
    }
}

@Composable
private fun SectionHeading(
    title: String,
    palette: ArtworkPalette,
    onShowAll: (() -> Unit)? = null,
    horizontalPadding: Dp = PAGE_GUTTER,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 10.dp,
                bottom = 8.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = palette.onBackground,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onShowAll != null) {
            Text(
                text = stringResource(R.string.show_all),
                style = MaterialTheme.typography.titleSmall,
                color = palette.accent,
                modifier = Modifier
                    .clickable(onClick = onShowAll)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

/** Compact row used inside the artist song grid; no swipe, to keep the
 *  horizontal pager's gestures unambiguous. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactSongRow(
    song: Song,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    downloadedTint: Color? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.trackArtSize(48.dp, song.isVideo)) {
            AsyncImage(
                model = song.artworkAt(ROW_ART_PX),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(7.dp))
                    .thumbnailBorder(RoundedCornerShape(7.dp))
                    .background(palette.elevated),
                    contentScale = com.music.bitchord.ui.components.thumbnailContentScale(),
            )
            if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) palette.accent else palette.onBackground,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (downloadedTint != null) {
            DownloadedBadge(song.videoId, downloadedTint)
        }
        RowMoreButton(onClick = onLongPress, tint = palette.onBackgroundVariant)
    }
}

/**
 * A row under "Suggested" — a track YouTube offers to round the playlist
 * out but that was never added. [onAdd] is the point of the row, so it gets
 * the trailing spot a track already on the playlist spends on "more"; the
 * long-press sheet is still one gesture away for anything else.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestedSongRow(
    song: Song,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onAdd: () -> Unit,
    downloadedTint: Color? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = PAGE_GUTTER, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.trackArtSize(52.dp, song.isVideo)) {
            AsyncImage(
                model = song.artworkAt(ROW_ART_PX),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .thumbnailBorder(RoundedCornerShape(8.dp))
                    .background(palette.elevated),
                    contentScale = com.music.bitchord.ui.components.thumbnailContentScale(),
            )
            if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) palette.accent else palette.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (downloadedTint != null) {
            DownloadedBadge(song.videoId, downloadedTint)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.16f))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.add_to_playlist),
                tint = palette.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * These shelves with the artist's [albums] on the device added, under
 * [title]: on the shelf of that name if one is left, or a shelf of their own
 * first. An album already on a shelf (a release downloaded whole) is left
 * where it is.
 */
private fun List<HomeShelf>.withDeviceAlbums(albums: List<ShelfItem>, title: String): List<HomeShelf> {
    val shown = flatMapTo(HashSet()) { shelf -> shelf.items.map { it.title.lowercase(Locale.ROOT) } }
    val added = albums.filter { it.title.lowercase(Locale.ROOT) !in shown }
    if (added.isEmpty()) return this
    val shelf = indexOfFirst { it.title.equals(title, ignoreCase = true) }
    if (shelf < 0) return listOf(HomeShelf(title, added)) + this
    return toMutableList().also { it[shelf] = it[shelf].copy(items = it[shelf].items + added) }
}

/**
 * The newest release on an artist's shelves — an album, or a single or EP that
 * came out after it. Playlists, videos and related artists share these shelves
 * and are skipped: only an `MPRE…` browse id is a release.
 *
 * Newest by the year in the card's subtitle, which is all a shelf item says
 * about when. Ties and releases with no year keep YouTube's own order, which
 * lists albums first.
 */
private fun List<HomeShelf>.topRelease(): ShelfItem? =
    asSequence()
        .flatMap { it.items.asSequence() }
        .filter { it.browseId?.startsWith("MPRE") == true }
        .withIndex()
        .maxWithOrNull(
            compareBy<IndexedValue<ShelfItem>> { it.value.releaseYear() ?: 0 }
                .thenByDescending { it.index },
        )?.value

private val RELEASE_YEAR = Regex("""\b(19|20)\d{2}\b""")

private fun ShelfItem.releaseYear(): Int? =
    RELEASE_YEAR.find(subtitle)?.value?.toIntOrNull()

/** "Recent Single" and "Recent EP" where the card says so, otherwise "Recent Album". */
@androidx.annotation.StringRes
private fun ShelfItem.recentLabel(): Int {
    val kind = subtitle.lowercase()
    return when {
        kind.contains("single") -> R.string.recent_single
        Regex("""\bep\b""").containsMatchIn(kind) -> R.string.recent_ep
        else -> R.string.recent_album
    }
}

/**
 * The artist page's top-release card: sleeve, what it is and when, and its
 * title, in a rounded glass panel the width of the page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopReleaseCard(
    item: ShelfItem,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    /** Whether the album is in the library; null while that is still being read. */
    saved: Boolean?,
    /** Null hides the button. */
    onToggleSaved: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(28.dp)
    val coverShape = RoundedCornerShape(12.dp)
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ARTIST_CONTENT_GUTTER)
            .padding(bottom = 22.dp)
            // A light veil rather than the dark glass: Apple's containers read
            // slightly white against the page, and a dark panel reads as a hole.
            .artistVeil(shape)
            .clip(shape)
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
            contentDescription = null,
            modifier = Modifier
                .size(88.dp)
                .clip(coverShape)
                .border(1.dp, Color.White.copy(alpha = TOP_RELEASE_COVER_EDGE_ALPHA), coverShape)
                .background(palette.elevated),
                contentScale = com.music.bitchord.ui.components.thumbnailContentScale(),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                color = palette.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(item.recentLabel()),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                maxLines = 1,
            )
        }
        if (onToggleSaved != null && saved != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .artistVeil(CircleShape)
                    .clickable {
                        haptics.play(if (saved) Haptic.ToggleOff else Haptic.ToggleOn)
                        onToggleSaved()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (saved) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = stringResource(
                        if (saved) R.string.remove_from_library else R.string.add_to_library,
                    ),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** The white veil under Apple-style containers and their buttons. */
private const val LIGHT_FILL_ALPHA = 0.07f

/** Brighter than the default 0.15 hairlines, as on Apple Music's own card. */
private const val TOP_RELEASE_EDGE_ALPHA = 0.10f
private const val TOP_RELEASE_COVER_EDGE_ALPHA = 0.215f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionCard(
    item: ShelfItem,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(SHELF_CARD_WIDTH),
    onLongPress: (() -> Unit)? = null,
) {
    // An artist's Videos shelf: widescreen, as on the web player.
    if (item.isVideo) {
        VideoShelfCard(
            item = item,
            onClick = onClick,
            onLongPress = onLongPress,
            modifier = modifier,
            titleColor = palette.onBackground,
            subtitleColor = palette.onBackgroundVariant,
            placeholderColor = palette.elevated,
        )
        return
    }
    Column(
        modifier = modifier
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .thumbnailBorder(RoundedCornerShape(10.dp))
                .background(palette.elevated),
                contentScale = com.music.bitchord.ui.components.thumbnailContentScale(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistShelfGridPage(
    shelf: HomeShelf,
    palette: ArtworkPalette,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    BoxWithConstraints(modifier.fillMaxSize().background(palette.background)) {
        val grid = libraryGrid(maxWidth - PAGE_GUTTER * 2)
        LazyVerticalGrid(
            columns = GridCells.Fixed(grid.columns),
            state = gridState,
            contentPadding = PaddingValues(
                top = topBarContentPadding(),
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = PAGE_GUTTER),
        ) {
            items(shelf.items, key = { it.browseId ?: it.videoId ?: it.title }) { item ->
                SectionCard(
                    item = item,
                    palette = palette,
                    onClick = { onItemClick(item) },
                    onLongPress = onItemLongPress?.let { { it(item) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Splits the one subtitle a browse row hands over — "Album • Travis Scott •
 * 2023", or sometimes just "Travis Scott" — into the credit line and the
 * metadata line the header shows separately.
 *
 * Everything is optional, because every caller supplies a different amount of
 * it: the player knows an album's artist but not its year, search knows both,
 * and a home card frequently knows neither.
 */
@Composable
private fun DetailPage.headerLines(trackCount: Int, playtime: String? = null): Pair<String, String> {
    val parts = subtitle.split("•", "·").map { it.trim() }.filter { it.isNotEmpty() }
    val year = parts.lastOrNull { it.length == 4 && it.all(Char::isDigit) }
    val kind = parts.firstOrNull { it.lowercase(Locale.ROOT) in KIND_WORDS }
    val credit = parts.filter { it != year && it != kind }.joinToString(", ")
    val meta = listOfNotNull(
        kind ?: type.localizedLabel(),
        year,
        trackCount.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.track_count_plural, it, it)
        },
        playtime,
    ).joinToString(" • ").uppercase(Locale.getDefault())
    return credit to meta
}

/** Subtitle words that name what a page *is* rather than who made it. */
private val KIND_WORDS = setOf(
    "album", "single", "ep", "playlist", "artist", "podcast", "episode", "song", "video",
)

@Composable
private fun BrowseType.localizedLabel(): String? = when (this) {
        BrowseType.ALBUM -> stringResource(R.string.album)
        BrowseType.PLAYLIST -> stringResource(R.string.playlist)
        BrowseType.ARTIST -> stringResource(R.string.artist)
        BrowseType.OTHER -> null
    }

/**
 * How long the page plays for — "41 min", "1h 25m" — summed over the rows
 * on it, or null when none of them carry a duration. A playlist still filling
 * in counts up with it, so the figure is never ahead of the list it sits over.
 */
@Composable
private fun List<Song>.playtime(): String? {
    val minutes = sumOf { it.durationMillis() } / 60_000
    return when {
        minutes <= 0 -> null
        minutes < 60 -> stringResource(R.string.minutes_short, minutes.toInt())
        else -> stringResource(R.string.hours_minutes_short, (minutes / 60).toInt(), (minutes % 60).toInt())
    }
}
