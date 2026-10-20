package com.music.bitchord.data.settings

import android.content.SharedPreferences
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.ui.components.DialTileStyle
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A page the bottom bar can lead to. [id] is the tab index MainActivity keys its pages by. */
enum class NavPage(val id: Int) {
    HOME(0),
    EXPLORE(1),
    LIBRARY(2),
    SEARCH(3),
    PLAYLISTS(4),
    ;

    companion object {
        fun fromId(id: Int): NavPage? = entries.firstOrNull { it.id == id }

        /** Pages that can sit in the bar's main pill. Search always has its own button at the end. */
        val pillPages = listOf(HOME, EXPLORE, LIBRARY, PLAYLISTS)
        val defaultPill = listOf(HOME, EXPLORE, LIBRARY)
    }
}

enum class MiniPlayerShape { CIRCULAR, SQUARED }

/**
 * Which library the Library tab shows: the YouTube Music account's, the
 * device's (local files and downloads together), or both at once.
 */
enum class LibrarySource { YOUTUBE_MUSIC, LOCAL, ALL }

/** One entry in an ordered list where each entry can also be switched off. */
data class OrderedToggle<T>(val item: T, val enabled: Boolean)

/**
 * Layout and navigation preferences: the bottom bar, the start page, the mini
 * player's shape, the Home page's sections and the speed dial pins.
 *
 * Stored in the same preference file as [AppSettings], so backups and restores
 * carry them along with everything else.
 */
object CustomizationSettings {
    private var prefs: SharedPreferences? = null

    /** The pages in the bar's main pill, in order, each either shown or hidden. */
    val navTabs = MutableStateFlow(NavPage.pillPages.map { OrderedToggle(it, it in NavPage.defaultPill) })
    val startPage = MutableStateFlow(NavPage.HOME)
    val keepNavBarExpanded = MutableStateFlow(false)

    /** The skip button on the miniplayer. Off: swiping it sideways still skips either way. */
    val miniPlayerNextButton = MutableStateFlow(false)

    /** Fill thumbnails that aren't square, trimming any bars baked into them, instead of fitting them. */
    val cropThumbnails = MutableStateFlow(true)

    /**
     * A second dial under the speed dial, of artists: the ones pinned to it,
     * then the most played. Off, artists are pinned to the speed dial itself.
     */
    val artistDial = MutableStateFlow(true)

    /** A die on the speed dial that starts a radio off a random personal pick. */
    val feelingLucky = MutableStateFlow(true)

    /** Artists pinned to [artistDial]'s page, in their order there. */
    val artistDialPins = MutableStateFlow<List<ShelfItem>>(emptyList())

    /** Ids taken off the dials with "Remove from dial", kept off until pinned again. */
    val hiddenFromDial = MutableStateFlow<Set<String>>(emptySet())

    /** Whether dial tiles show their titles, unless a tile says otherwise. */
    val dialLabels = MutableStateFlow(true)

    /** Dial tiles that go against [dialLabels]: hidden while it is on, shown while it is off. */
    val dialHiddenLabels = MutableStateFlow<Set<String>>(emptySet())

    /** Whether pinned tiles on the dials carry the little pin badge. */
    val dialPinIcon = MutableStateFlow(true)

    /** How the dials' tiles are edged: the plain hairline, a contoured rim, or a glass rim. */
    val dialTileStyle = MutableStateFlow(DialTileStyle.NONE)
    val miniPlayerShape = MutableStateFlow(MiniPlayerShape.CIRCULAR)

    /** How many rows of three tiles the speed dial shows per page: 3 or 4. */
    val speedDialRows = MutableStateFlow(DEFAULT_SPEED_DIAL_ROWS)

    /**
     * Over a Spotify Canvas, shrink the controls to the foot of the screen
     * instead of hiding them: title, live lyrics and transport stay.
     */
    val compactCanvasControls = MutableStateFlow(true)

    /** One UI 9's wave progress on the player's seek bar. Off, the plain thin bar. */
    val wavyProgressBar = MutableStateFlow(true)

    private val DEFAULT_TAB_VIEWS = listOf(LibraryViewType.LIST, LibraryViewType.LIST, LibraryViewType.GRID)

    /** List or grid for each folder tab — songs, artists, albums — on its own. */
    val libraryTabViews = MutableStateFlow(DEFAULT_TAB_VIEWS)

    /** Slow colour blobs behind the app's pages. */
    val ambientBackground = MutableStateFlow(true)

    /** Whether those blobs take the playing song's artwork colour. */
    val ambientFollowsArtwork = MutableStateFlow(false)

    /** The library last picked from the Library page's logo; remembered across launches. */
    val librarySource = MutableStateFlow(LibrarySource.ALL)

    /** Items pinned to the speed dial, in the order they were pinned. */
    val speedDialPins = MutableStateFlow<List<ShelfItem>>(emptyList())

    /**
     * The speed dial as Home last showed it, saved so the next launch opens on
     * the same tiles straight away instead of building them up as the feed,
     * Recently played and the rest come in.
     */
    val lastSpeedDial = MutableStateFlow<List<ShelfItem>>(emptyList())

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        readAll()
    }

    fun readAll() {
        val p = prefs ?: return
        navTabs.value = readOrdered(p.getString(KEY_NAV_TABS, null), NavPage.pillPages, NavPage.defaultPill.toSet()) {
            NavPage.entries.firstOrNull { page -> page.name == it }
        }.let(::ensureOneTab)
        startPage.value = NavPage.entries.firstOrNull { it.name == p.getString(KEY_START_PAGE, null) } ?: NavPage.HOME
        keepNavBarExpanded.value = p.getBoolean(KEY_KEEP_NAV_EXPANDED, false)
        miniPlayerNextButton.value = p.getBoolean(KEY_MINI_PLAYER_NEXT, false)
        cropThumbnails.value = p.getBoolean(KEY_CROP_THUMBNAILS, true)
        artistDial.value = p.getBoolean(KEY_ARTIST_DIAL, true)
        feelingLucky.value = p.getBoolean(KEY_FEELING_LUCKY, true)
        speedDialRows.value = p.getInt(KEY_SPEED_DIAL_ROWS, DEFAULT_SPEED_DIAL_ROWS).takeIf { it in 3..4 }
            ?: DEFAULT_SPEED_DIAL_ROWS
        miniPlayerShape.value = MiniPlayerShape.entries
            .firstOrNull { it.name == p.getString(KEY_MINI_PLAYER_SHAPE, null) } ?: MiniPlayerShape.CIRCULAR
        compactCanvasControls.value = p.getBoolean(KEY_COMPACT_CANVAS_CONTROLS, true)
        wavyProgressBar.value = p.getBoolean(KEY_WAVY_PROGRESS_BAR, true)
        libraryTabViews.value = p.getString(KEY_LIBRARY_TAB_VIEWS, null)
            ?.split(",")
            ?.mapNotNull { name -> LibraryViewType.entries.firstOrNull { it.name == name } }
            ?.takeIf { it.size == DEFAULT_TAB_VIEWS.size }
            ?: DEFAULT_TAB_VIEWS
        ambientBackground.value = p.getBoolean(KEY_AMBIENT_BACKGROUND, true)
        ambientFollowsArtwork.value = p.getBoolean(KEY_AMBIENT_FOLLOWS_ARTWORK, false)
        librarySource.value = LibrarySource.entries
            .firstOrNull { it.name == p.getString(KEY_LIBRARY_SOURCE, null) } ?: LibrarySource.ALL
        speedDialPins.value = readPins(p.getString(KEY_SPEED_DIAL_PINS, null))
        lastSpeedDial.value = readPins(p.getString(KEY_LAST_SPEED_DIAL, null))
        artistDialPins.value = readPins(p.getString(KEY_ARTIST_DIAL_PINS, null))
        hiddenFromDial.value = p.getStringSet(KEY_HIDDEN_FROM_DIAL, emptySet())?.toSet().orEmpty()
        dialHiddenLabels.value = p.getStringSet(KEY_DIAL_HIDDEN_LABELS, emptySet())?.toSet().orEmpty()
        dialPinIcon.value = p.getBoolean(KEY_DIAL_PIN_ICON, true)
        dialLabels.value = p.getBoolean(KEY_DIAL_LABELS, true)
        dialTileStyle.value = p.getString(KEY_DIAL_TILE_STYLE, null)
            ?.let { name -> DialTileStyle.entries.firstOrNull { it.name == name } }
            // The style's first form was one switch, for the contoured rim.
            ?: if (p.getBoolean(KEY_DIAL_CONTOURED_EDGE, false)) DialTileStyle.EDGE else DialTileStyle.NONE
    }

    // ── Speed dial ──────────────────────────────────────────────────────

    fun isPinnedToSpeedDial(id: String?): Boolean =
        id != null && (speedDialPins.value.any { it.speedDialId == id } || artistDialPins.value.any { it.speedDialId == id })

    /** Whether [item] is an artist (a YouTube channel), which may have a dial of its own. */
    fun isArtist(item: ShelfItem): Boolean = item.videoId == null && item.browseId?.startsWith("UC") == true

    /**
     * Pins [item], or unpins it if it is already pinned. Returns whether it is
     * pinned now. An artist goes to the artists' own dial while that is on.
     */
    fun toggleSpeedDial(item: ShelfItem): Boolean {
        val id = item.speedDialId ?: return false
        // Pinning something taken off the dial brings it back.
        if (id in hiddenFromDial.value) setHiddenFromDial(hiddenFromDial.value - id)
        if (isArtist(item) && (artistDial.value || artistDialPins.value.any { it.speedDialId == id })) {
            val current = artistDialPins.value
            val pinned = current.none { it.speedDialId == id }
            setArtistDialPins(if (pinned) (current + item).takeLast(SPEED_DIAL_SIZE) else current.filterNot { it.speedDialId == id })
            // Not on both dials at once.
            if (pinned) setSpeedDialPins(speedDialPins.value.filterNot { it.speedDialId == id })
            return pinned
        }
        val current = speedDialPins.value
        val pinned = current.none { it.speedDialId == id }
        val updated = if (pinned) {
            (current + item).takeLast(SPEED_DIAL_SIZE)
        } else {
            current.filterNot { it.speedDialId == id }
        }
        setSpeedDialPins(updated)
        return pinned
    }

    /** Whether this launch has refreshed the pins' art yet: once per fresh start. */
    var pinArtRefreshed = false

    /**
     * Gives pinned tiles the art [items] carry for them now. A pin is saved
     * with the art it had when it was pinned, but YouTube redraws the cover of
     * a mix it rebuilds (Discover Mix, Supermix, New Release Mix) with each
     * rebuild, so the pinned tile would otherwise keep last week's.
     */
    fun refreshPinArt(items: List<ShelfItem>) {
        val art = HashMap<String, String>()
        items.forEach { item ->
            val id = item.speedDialId ?: return@forEach
            item.thumbnailUrl?.let { art.putIfAbsent(id, it) }
        }
        refreshPinArt(art)
    }

    /** [refreshPinArt] from art already keyed by the pin's id. */
    fun refreshPinArt(art: Map<String, String>) {
        if (art.isEmpty()) return
        fun fresh(pins: List<ShelfItem>) = pins.map { pin ->
            val url = art[pin.speedDialId]
            if (url == null || url == pin.thumbnailUrl) pin else pin.copy(thumbnailUrl = url)
        }
        val speed = fresh(speedDialPins.value)
        if (speed != speedDialPins.value) setSpeedDialPins(speed)
        val artists = fresh(artistDialPins.value)
        if (artists != artistDialPins.value) setArtistDialPins(artists)
    }

    /**
     * The pinned mixes YouTube builds for the listener and rebuilds over time,
     * whose `RDTMAK` ids are the same from one rebuild to the next.
     */
    fun pinnedDynamicMixes(): List<String> =
        speedDialPins.value.mapNotNull { pin -> pin.browseId?.takeIf { pin.videoId == null && isDynamicMix(it) } }

    fun isDynamicMix(browseId: String): Boolean = browseId.removePrefix("VL").startsWith("RDTMAK")

    /** Replaces the pins, in the order given. */
    fun setSpeedDialPins(value: List<ShelfItem>) {
        val updated = value.distinctBy { it.speedDialId }.take(SPEED_DIAL_SIZE)
        speedDialPins.value = updated
        prefs?.edit()?.putString(KEY_SPEED_DIAL_PINS, writeItems(updated))?.apply()
    }

    /**
     * Takes [item] off whichever dial it is on: unpinned, and kept off even
     * where YouTube's own speed dial, Recently played or the play counts would
     * put it back — until it is pinned again.
     */
    fun removeFromDial(item: ShelfItem) {
        val id = item.speedDialId ?: return
        setSpeedDialPins(speedDialPins.value.filterNot { it.speedDialId == id })
        setArtistDialPins(artistDialPins.value.filterNot { it.speedDialId == id })
        setHiddenFromDial(hiddenFromDial.value + id)
    }

    /** Whether a dial tile's title shows: the default, unless the tile overrides it. */
    fun dialLabelShown(id: String?): Boolean = dialLabels.value != (id != null && id in dialHiddenLabels.value)

    /** Shows or hides one dial tile's title, against the default. */
    fun toggleDialLabel(item: ShelfItem) {
        val id = item.speedDialId ?: return
        val value = if (id in dialHiddenLabels.value) dialHiddenLabels.value - id else dialHiddenLabels.value + id
        setDialLabelOverrides(value)
    }

    /** Shows or hides every tile's title; the tiles' own choices start over from it. */
    fun setDialLabels(value: Boolean) {
        dialLabels.value = value
        prefs?.edit()?.putBoolean(KEY_DIAL_LABELS, value)?.apply()
        setDialLabelOverrides(emptySet())
    }

    private fun setDialLabelOverrides(value: Set<String>) {
        dialHiddenLabels.value = value
        prefs?.edit()?.putStringSet(KEY_DIAL_HIDDEN_LABELS, value)?.apply()
    }

    fun setDialPinIcon(value: Boolean) {
        dialPinIcon.value = value
        prefs?.edit()?.putBoolean(KEY_DIAL_PIN_ICON, value)?.apply()
    }

    fun setDialTileStyle(value: DialTileStyle) {
        dialTileStyle.value = value
        prefs?.edit()?.putString(KEY_DIAL_TILE_STYLE, value.name)?.remove(KEY_DIAL_CONTOURED_EDGE)?.apply()
    }

    private fun setHiddenFromDial(value: Set<String>) {
        hiddenFromDial.value = value
        prefs?.edit()?.putStringSet(KEY_HIDDEN_FROM_DIAL, value)?.apply()
    }

    /** Replaces the artists' dial's pins, in the order given. */
    fun setArtistDialPins(value: List<ShelfItem>) {
        val updated = value.distinctBy { it.speedDialId }.take(SPEED_DIAL_SIZE)
        artistDialPins.value = updated
        prefs?.edit()?.putString(KEY_ARTIST_DIAL_PINS, writeItems(updated))?.apply()
    }

    fun setFeelingLucky(value: Boolean) {
        feelingLucky.value = value
        prefs?.edit()?.putBoolean(KEY_FEELING_LUCKY, value)?.apply()
    }

    /** Turning it on moves pinned artists onto their dial; off, back onto the speed dial. */
    fun setArtistDial(value: Boolean) {
        artistDial.value = value
        prefs?.edit()?.putBoolean(KEY_ARTIST_DIAL, value)?.apply()
        if (value) {
            val artists = speedDialPins.value.filter { isArtist(it) }
            if (artists.isNotEmpty()) {
                setArtistDialPins(artistDialPins.value + artists)
                setSpeedDialPins(speedDialPins.value.filterNot { isArtist(it) })
            }
        } else if (artistDialPins.value.isNotEmpty()) {
            setSpeedDialPins(speedDialPins.value + artistDialPins.value)
            setArtistDialPins(emptyList())
        }
    }

    /** Saves the dial Home is showing, for the next launch to open on. */
    fun setLastSpeedDial(value: List<ShelfItem>) {
        if (value == lastSpeedDial.value) return
        lastSpeedDial.value = value
        prefs?.edit()?.putString(KEY_LAST_SPEED_DIAL, writeItems(value))?.apply()
    }

    private fun writeItems(items: List<ShelfItem>): String {
        val json = JSONArray()
        items.forEach { pin ->
            json.put(
                JSONObject()
                    .put("title", pin.title)
                    .put("subtitle", pin.subtitle)
                    .put("thumb", pin.thumbnailUrl ?: JSONObject.NULL)
                    .put("video", pin.videoId ?: JSONObject.NULL)
                    .put("browse", pin.browseId ?: JSONObject.NULL),
            )
        }
        return json.toString()
    }

    /**
     * Up to [SPEED_DIAL_SIZE] items: pins first, then YouTube's own speed dial
     * when the feed has one, then recently played, then the rest of the feed.
     * A song played from an album or playlist stands for that album or
     * playlist, once, where its latest play would be (see [PlayedCollections]).
     */
    fun buildSpeedDial(pins: List<ShelfItem>, shelves: List<HomeShelf>): List<ShelfItem> {
        fun shelf(title: String) = shelves.firstOrNull { it.title.equals(title, ignoreCase = true) }?.items.orEmpty()
        fun asPlayed(items: List<ShelfItem>) = items.map { PlayedCollections.collectionFor(it.videoId) ?: it }
        val official = asPlayed(shelf(SPEED_DIAL_TITLE))
        // Tracks from the library and other files never reach YouTube's history.
        val recents = asPlayed(DevicePlays.withDevicePlays(shelf(RECENTS_TITLE)))
        val rest = shelves
            .filterNot { it.title.equals(SPEED_DIAL_TITLE, true) || it.title.equals(RECENTS_TITLE, true) }
            .flatMap { it.items }
        val hidden = hiddenFromDial.value
        return (pins + official + recents + rest)
            .filter { it.speedDialId != null && it.speedDialId !in hidden }
            .distinctBy { it.speedDialId }
            .take(SPEED_DIAL_SIZE)
    }

    private fun readPins(stored: String?): List<ShelfItem> {
        if (stored.isNullOrBlank()) return emptyList()
        return runCatching {
            val json = JSONArray(stored)
            (0 until json.length()).map { i ->
                val o = json.getJSONObject(i)
                ShelfItem(
                    title = o.optString("title"),
                    subtitle = o.optString("subtitle"),
                    thumbnailUrl = o.optStringOrNull("thumb"),
                    videoId = o.optStringOrNull("video"),
                    browseId = o.optStringOrNull("browse"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    /** The pages the bar shows, Search last. */
    fun visibleNavPages(tabs: List<OrderedToggle<NavPage>> = navTabs.value): List<NavPage> =
        tabs.filter { it.enabled }.map { it.item } + NavPage.SEARCH

    fun setNavTabs(value: List<OrderedToggle<NavPage>>) {
        val fixed = ensureOneTab(value)
        navTabs.value = fixed
        prefs?.edit()?.putString(KEY_NAV_TABS, writeOrdered(fixed) { it.name })?.apply()
    }

    fun setStartPage(value: NavPage) {
        startPage.value = value
        prefs?.edit()?.putString(KEY_START_PAGE, value.name)?.apply()
    }

    fun setMiniPlayerNextButton(value: Boolean) {
        miniPlayerNextButton.value = value
        prefs?.edit()?.putBoolean(KEY_MINI_PLAYER_NEXT, value)?.apply()
    }

    fun setCropThumbnails(value: Boolean) {
        cropThumbnails.value = value
        prefs?.edit()?.putBoolean(KEY_CROP_THUMBNAILS, value)?.apply()
    }

    fun setKeepNavBarExpanded(value: Boolean) {
        keepNavBarExpanded.value = value
        prefs?.edit()?.putBoolean(KEY_KEEP_NAV_EXPANDED, value)?.apply()
    }

    fun setLibrarySource(value: LibrarySource) {
        librarySource.value = value
        prefs?.edit()?.putString(KEY_LIBRARY_SOURCE, value.name)?.apply()
    }

    fun setLibraryTabView(tab: Int, value: LibraryViewType) {
        val updated = libraryTabViews.value.toMutableList()
        if (tab !in updated.indices) return
        updated[tab] = value
        libraryTabViews.value = updated
        prefs?.edit()?.putString(KEY_LIBRARY_TAB_VIEWS, updated.joinToString(",") { it.name })?.apply()
    }

    fun setAmbientBackground(value: Boolean) {
        ambientBackground.value = value
        prefs?.edit()?.putBoolean(KEY_AMBIENT_BACKGROUND, value)?.apply()
    }

    fun setAmbientFollowsArtwork(value: Boolean) {
        ambientFollowsArtwork.value = value
        prefs?.edit()?.putBoolean(KEY_AMBIENT_FOLLOWS_ARTWORK, value)?.apply()
    }

    fun setWavyProgressBar(value: Boolean) {
        wavyProgressBar.value = value
        prefs?.edit()?.putBoolean(KEY_WAVY_PROGRESS_BAR, value)?.apply()
    }

    fun setCompactCanvasControls(value: Boolean) {
        compactCanvasControls.value = value
        prefs?.edit()?.putBoolean(KEY_COMPACT_CANVAS_CONTROLS, value)?.apply()
    }

    fun setMiniPlayerShape(value: MiniPlayerShape) {
        miniPlayerShape.value = value
        prefs?.edit()?.putString(KEY_MINI_PLAYER_SHAPE, value.name)?.apply()
    }

    fun setSpeedDialRows(value: Int) {
        val rows = value.coerceIn(3, 4)
        speedDialRows.value = rows
        prefs?.edit()?.putInt(KEY_SPEED_DIAL_ROWS, rows)?.apply()
    }

    private fun ensureOneTab(tabs: List<OrderedToggle<NavPage>>): List<OrderedToggle<NavPage>> =
        if (tabs.any { it.enabled }) {
            tabs
        } else {
            tabs.map { if (it.item == NavPage.HOME) it.copy(enabled = true) else it }
        }

    /**
     * Reads "NAME:1,NAME:0,…". Unknown names are dropped, and any [all] entry the
     * stored list doesn't mention is appended, enabled when it is in [defaultOn],
     * so entries added in later builds still show up.
     */
    internal fun <T> readOrdered(
        stored: String?,
        all: List<T>,
        defaultOn: Set<T>,
        parse: (String) -> T?,
    ): List<OrderedToggle<T>> {
        if (stored.isNullOrBlank()) return all.map { OrderedToggle(it, it in defaultOn) }
        val saved: List<OrderedToggle<T>> = stored.split(",").mapNotNull { entry ->
            val item: T = parse(entry.substringBefore(':')) ?: return@mapNotNull null
            OrderedToggle<T>(item, entry.substringAfter(':', "1") != "0")
        }.distinctBy { it.item }
        val missing: List<OrderedToggle<T>> = all.filter { item -> saved.none { it.item == item } }
            .map { OrderedToggle<T>(it, it in defaultOn) }
        return saved.filter { it.item in all } + missing
    }

    internal fun <T> writeOrdered(value: List<OrderedToggle<T>>, name: (T) -> String): String =
        value.joinToString(",") { "${name(it.item)}:${if (it.enabled) 1 else 0}" }

    /** The title the speed dial goes by, both YouTube's and the one built here. */
    const val SPEED_DIAL_TITLE = "Speed dial"
    private const val RECENTS_TITLE = "Recents"
    const val SPEED_DIAL_SIZE = 36
    const val DEFAULT_SPEED_DIAL_ROWS = 4
    const val DEFAULT_HOME_TITLE = "BitChord Dial"

    private const val KEY_NAV_TABS = "custom_nav_tabs"
    private const val KEY_COMPACT_CANVAS_CONTROLS = "custom_compact_canvas_controls"
    private const val KEY_WAVY_PROGRESS_BAR = "custom_wavy_progress_bar"
    private const val KEY_AMBIENT_BACKGROUND = "custom_ambient_background"
    private const val KEY_LIBRARY_TAB_VIEWS = "custom_library_tab_views"
    private const val KEY_AMBIENT_FOLLOWS_ARTWORK = "custom_ambient_follows_artwork"
    private const val KEY_LIBRARY_SOURCE = "custom_library_source"
    private const val KEY_SPEED_DIAL_PINS = "custom_speed_dial_pins"
    private const val KEY_LAST_SPEED_DIAL = "custom_last_speed_dial"
    private const val KEY_MINI_PLAYER_NEXT = "custom_mini_player_next"
    private const val KEY_CROP_THUMBNAILS = "custom_crop_thumbnails"
    private const val KEY_ARTIST_DIAL = "custom_artist_dial"
    private const val KEY_FEELING_LUCKY = "custom_feeling_lucky"
    private const val KEY_ARTIST_DIAL_PINS = "custom_artist_dial_pins"
    private const val KEY_HIDDEN_FROM_DIAL = "custom_hidden_from_dial"
    private const val KEY_DIAL_HIDDEN_LABELS = "custom_dial_hidden_labels"
    private const val KEY_DIAL_PIN_ICON = "custom_dial_pin_icon"
    private const val KEY_DIAL_LABELS = "custom_dial_labels"
    private const val KEY_DIAL_CONTOURED_EDGE = "custom_dial_contoured_edge"
    private const val KEY_DIAL_TILE_STYLE = "custom_dial_tile_style"
    private const val KEY_START_PAGE = "custom_start_page"
    private const val KEY_KEEP_NAV_EXPANDED = "custom_keep_nav_expanded"
    private const val KEY_MINI_PLAYER_SHAPE = "custom_mini_player_shape"
    private const val KEY_SPEED_DIAL_ROWS = "custom_speed_dial_rows"
}

/** What identifies an item on the speed dial: its track, or the page it opens. */
val ShelfItem.speedDialId: String? get() = videoId ?: browseId

/**
 * When each track was last started, by video id, for the folders' "Last played"
 * sort. Kept on this device only; YouTube's own history is per account and
 * not something a folder of local files could be matched against.
 */
object LastPlayed {
    private var prefs: SharedPreferences? = null
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun init(context: android.content.Context) {
        val p = context.getSharedPreferences("bitchord_last_played", android.content.Context.MODE_PRIVATE)
        prefs = p
        cache.clear()
        p.all.forEach { (key, value) -> (value as? Long)?.let { cache[key] = it } }
    }

    fun record(videoId: String) {
        if (videoId.isBlank()) return
        val now = System.currentTimeMillis()
        cache[videoId] = now
        prefs?.edit()?.putLong(videoId, now)?.apply()
    }

    /** Milliseconds since the epoch, or 0 for never. */
    fun at(videoId: String): Long = cache[videoId] ?: 0L
}
