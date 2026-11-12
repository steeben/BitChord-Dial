package com.music.bitchord.ui.screens

import androidx.compose.animation.AnimatedContent
import com.music.bitchord.ui.components.pageAccent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.AlbumArtChoices
import com.music.bitchord.data.LibraryArtists
import com.music.bitchord.data.LibraryDuplicates
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.canvas.AppleArtistArtRepository
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LibrarySource
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.spotify_logo
import com.music.bitchord.ui.components.LIBRARY_GRID_SPACING
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PopupLayer
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.SQUARE_LOGO_ASPECT
import com.music.bitchord.ui.components.LogoPullHaptics
import com.music.bitchord.ui.components.SquareLogo
import com.music.bitchord.ui.components.TopBarContentHeight
import com.music.bitchord.ui.components.glassContentColor
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.components.glassMenu
import com.music.bitchord.ui.components.longPressMenuClickable
import com.music.bitchord.ui.components.popupSurface
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.icons.BitChordIcons
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

/**
 * The Library tab, laid out after Apple Music's: "Library" with the picked
 * library's mark beside it, a 3×2 grid of things to go back to, the menu —
 * Playlists, Artists, Albums, Songs, Spotify, and whichever extra folders
 * ([extraLinks]) are set up — then rows of songs and cards ([feed]'s rows).
 *
 * Which library everything here reads from is [source]: YouTube Music, the
 * device, or both, picked from the top bar's "…" menu ([LibraryMenu]).
 * [feed] is dealt by [dealLibraryFeed] and only dealt again on a pull.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHomeScreen(
    source: LibrarySource,
    signedIn: Boolean,
    /** Null while it is being dealt. */
    feed: LibraryFeed?,
    accent: Color,
    listState: LazyListState,
    currentSong: Song?,
    onSuggestionClick: (LibrarySuggestion, String) -> Unit,
    onSuggestionLongPress: (LibrarySuggestion) -> Unit,
    onSongClick: (List<Song>, Int, String) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenSpotify: () -> Unit,
    extraLinks: List<LibraryLink>,
    onLinkClick: (ShelfItem) -> Unit,
    onSignIn: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // The heading sits level with the top bar's buttons, the way Apple Music's
    // does, rather than under them: the bar has nothing at its leading end on
    // this page, so the space is the heading's.
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = statusBar, bottom = contentPadding.calculateBottomPadding()),
        ) {
            item(key = "heading") {
                LibraryHeading(
                    source = source,
                    accent = accent,
                    refreshing = refreshing,
                    pullFraction = { pullState.distanceFraction },
                )
            }
            item(key = "suggestions") {
                SuggestionGrid(
                    suggestions = feed?.suggestions,
                    onClick = { onSuggestionClick(it, "") },
                    onLongPress = onSuggestionLongPress,
                )
            }
            item(key = "menu") {
                val rows = buildList<MenuRow> {
                    add(MenuRow(stringResource(R.string.playlists), Icons.AutoMirrored.Rounded.QueueMusic, onClick = onOpenPlaylists))
                    add(MenuRow(stringResource(R.string.artists), Icons.Rounded.Mic, onClick = onOpenArtists))
                    add(MenuRow(stringResource(R.string.albums), Icons.Rounded.Album, onClick = onOpenAlbums))
                    add(MenuRow(stringResource(R.string.songs), Icons.Rounded.MusicNote, onClick = onOpenSongs))
                    add(MenuRow(stringResource(R.string.spotify), icon = null, spotify = true, onClick = onOpenSpotify))
                    extraLinks.forEach { link ->
                        add(MenuRow(link.item.title, link.icon, onClick = { onLinkClick(link.item) }))
                    }
                }
                LibraryMenuRows(rows = rows, accent = accent)
            }
            if (!signedIn && source == LibrarySource.YOUTUBE_MUSIC) {
                item(key = "sign_in") {
                    MessageState(
                        message = stringResource(R.string.library_sign_in_description),
                        actionLabel = stringResource(R.string.sign_in),
                        onAction = onSignIn,
                    )
                }
            }
            feed?.rows?.forEachIndexed { index, row ->
                item(key = "row:$index:${row.title}") {
                    FeedRow(
                        row = row,
                        currentSong = currentSong,
                        onSongClick = { songs, at -> onSongClick(songs, at, row.title) },
                        onSongLongPress = onSongLongPress,
                        onItemClick = { onSuggestionClick(it, row.title) },
                        onItemLongPress = onSuggestionLongPress,
                    )
                }
            }
        }
    }
}

/**
 * "Library", larger than a section title, with the picked library's mark
 * before it. Pulled down, the mark gives way to the app's own square logo
 * lighting up row by row, and spinning while the refresh runs — the same
 * thing Home's mark does.
 */
@Composable
private fun LibraryHeading(
    source: LibrarySource,
    accent: Color,
    refreshing: Boolean,
    pullFraction: () -> Float,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .height(TopBarContentHeight)
            // Clear of the "…" button and account photo at the other end.
            .padding(start = PAGE_GUTTER, end = 120.dp),
    ) {
        LogoPullHaptics(refreshing = refreshing, pullFraction = pullFraction)
        val pulling = refreshing || pullFraction() > 0.02f
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp)) {
            AnimatedContent(
                targetState = pulling || source == LibrarySource.ALL,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
                label = "libraryMark",
            ) { dial ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp)) {
                    if (dial) {
                        SquareLogo(
                            color = accent,
                            refreshing = refreshing,
                            pullFraction = pullFraction,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(SQUARE_LOGO_ASPECT),
                        )
                    } else {
                        LibrarySourceIcon(source = source, tint = accent, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.library),
            style = MaterialTheme.typography.headlineLarge,
            fontSize = (MaterialTheme.typography.headlineLarge.fontSize.value + 2f).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── The grid ────────────────────────────────────────────────────────────────

/**
 * One card the Library deals: an album, playlist or artist page, one of the
 * device's albums or artists ([songs] with [groupType]), or a station — a
 * YouTube Music radio off [seed], or a mix played from [songs].
 */
data class LibrarySuggestion(
    val item: ShelfItem,
    val round: Boolean = false,
    val radio: Boolean = false,
    val seed: Song? = null,
    val songs: List<Song>? = null,
    val groupType: BrowseType? = null,
) {
    val key: String get() = item.browseId ?: item.videoId ?: (item.title + "\u0000" + item.subtitle)
}

private const val GRID_COLUMNS = 3
private const val GRID_ROWS = 2
private const val GRID_SIZE = GRID_COLUMNS * GRID_ROWS

@Composable
private fun SuggestionGrid(
    suggestions: List<LibrarySuggestion>?,
    onClick: (LibrarySuggestion) -> Unit,
    onLongPress: (LibrarySuggestion) -> Unit,
) {
    // Nothing played yet and nothing saved: no grid at all, rather than six
    // grey squares that never fill in.
    if (suggestions != null && suggestions.isEmpty()) return
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER)
            .padding(top = 4.dp, bottom = 10.dp),
    ) {
        val cell = (maxWidth - LIBRARY_GRID_SPACING * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (row in 0 until GRID_ROWS) {
                Row(horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING)) {
                    for (column in 0 until GRID_COLUMNS) {
                        val index = row * GRID_COLUMNS + column
                        if (suggestions == null) {
                            CardPlaceholder(cell)
                        } else {
                            val suggestion = suggestions.getOrNull(index)
                            if (suggestion != null) {
                                SuggestionCard(suggestion, cell, centered = true, onClick, onLongPress)
                            } else {
                                Spacer(Modifier.width(cell))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: LibrarySuggestion,
    width: Dp,
    centered: Boolean,
    onClick: (LibrarySuggestion) -> Unit,
    onLongPress: (LibrarySuggestion) -> Unit,
) {
    val shape = if (suggestion.round) CircleShape else RoundedCornerShape(10.dp)
    // Its page grows out of its picture, as a dial tile's does: not a
    // station's, which plays where it is.
    val launch = rememberCardLaunch(
        opens = cardLaunchKey(suggestion.item.browseId, suggestion.item.videoId).takeUnless { suggestion.radio },
        thumbnailUrl = suggestion.item.thumbnailUrl,
        corner = if (suggestion.round) ROUND_CARD_CORNER else 10.dp,
    )
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier
            .width(width)
            .longPressMenuClickable(
                onClick = launching(launch) { onClick(suggestion) },
                onLongClick = { onLongPress(suggestion) },
            ),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .launchArt(launch)
                    .clip(shape)
                    .thumbnailBorder(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = suggestion.item.thumbnailUrl.artworkAt(CARD_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onState = { launch?.shows(it) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (suggestion.radio) RadioBadge(Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = suggestion.item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!centered && suggestion.item.subtitle.isNotBlank()) {
            Text(
                text = suggestion.item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The mark on a station's card, in its bottom corner: it plays, it doesn't open. */
@Composable
private fun RadioBadge(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(5.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = .55f)),
    ) {
        Icon(
            Icons.Rounded.Radio,
            contentDescription = stringResource(R.string.library_radio),
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun CardPlaceholder(width: Dp) {
    Column(Modifier.width(width)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)),
        )
        Spacer(Modifier.height(26.dp))
    }
}

// ── The menu ────────────────────────────────────────────────────────────────

private class MenuRow(
    val title: String,
    val icon: ImageVector?,
    val spotify: Boolean = false,
    val onClick: () -> Unit,
)

private val MENU_ICON_SIZE = 26.dp
private val MENU_ICON_GAP = 16.dp

/** Icon, name, chevron, hairlines between — the rows under the grid. */
@Composable
private fun LibraryMenuRows(rows: List<MenuRow>, accent: Color) {
    Column(Modifier.padding(top = 4.dp, bottom = 18.dp)) {
        rows.forEachIndexed { index, row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clickable(onClick = row.onClick)
                    .padding(horizontal = PAGE_GUTTER),
            ) {
                if (row.spotify) {
                    Icon(
                        painter = org.jetbrains.compose.resources.painterResource(Res.drawable.spotify_logo),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(MENU_ICON_SIZE),
                    )
                } else if (row.icon != null) {
                    Icon(
                        imageVector = row.icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(MENU_ICON_SIZE),
                    )
                }
                Spacer(Modifier.width(MENU_ICON_GAP))
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = BitChordIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (index < rows.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = PAGE_GUTTER + MENU_ICON_SIZE + MENU_ICON_GAP),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

// ── The rows ────────────────────────────────────────────────────────────────

/** One row under the menu: songs to play ([songs]), or cards to open or play ([items]). */
class LibraryFeedRow(
    val title: String,
    val songs: List<Song> = emptyList(),
    val items: List<LibrarySuggestion> = emptyList(),
)

/** Everything [LibraryHomeScreen] deals: the grid's six, and the rows under the menu. */
class LibraryFeed(
    val source: LibrarySource,
    val suggestions: List<LibrarySuggestion>,
    val rows: List<LibraryFeedRow>,
)

private val FEED_CARD_WIDTH = 140.dp

@Composable
private fun FeedRow(
    row: LibraryFeedRow,
    currentSong: Song?,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onItemClick: (LibrarySuggestion) -> Unit,
    onItemLongPress: (LibrarySuggestion) -> Unit,
) {
    Column(Modifier.padding(bottom = 22.dp)) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = PAGE_GUTTER).padding(bottom = 10.dp),
        )
        // Back where it was scrolled to when one of its cards' pages closes,
        // for the page to land on the card.
        val rowState = rememberLaunchRowState(row.title)
        val launchRow = remember(row.title, rowState) { LaunchRow(row.title, rowState) }
        CompositionLocalProvider(LocalLaunchRow provides launchRow) {
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
        ) {
            if (row.songs.isNotEmpty()) {
                itemsIndexed(row.songs, key = { index, song -> "${song.videoId}:$index" }) { index, song ->
                    SongCard(
                        song = song,
                        current = currentSong?.videoId == song.videoId,
                        onClick = { onSongClick(row.songs, index) },
                        onLongPress = { onSongLongPress(song) },
                    )
                }
            } else {
                itemsIndexed(row.items, key = { index, item -> "${item.key}:$index" }) { _, item ->
                    SuggestionCard(item, FEED_CARD_WIDTH, centered = false, onItemClick, onItemLongPress)
                }
            }
        }
        }
    }
}

@Composable
private fun SongCard(song: Song, current: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .width(FEED_CARD_WIDTH)
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .thumbnailBorder(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = song.thumbnailUrl.artworkAt(CARD_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── The "…" menu ────────────────────────────────────────────────────────────

/**
 * What the Library's "…" button opens, on a liquid-glass panel that grows out
 * of it: which library the page reads — YouTube Music, Local (the device's
 * files and the downloads) or All — and the listening history. The pick is
 * remembered for next time.
 */
@Composable
fun LibraryMenu(
    visible: Boolean,
    selected: LibrarySource,
    signedIn: Boolean,
    hazeState: HazeState,
    accent: Color,
    onSelect: (LibrarySource) -> Unit,
    onOpenHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassMenuPanel(visible = visible, hazeState = hazeState, onDismiss = onDismiss) {
        MenuHeading(stringResource(R.string.choose_library))
        LibrarySource.entries.forEach { source ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable(role = Role.Button) { onSelect(source) }
                    .padding(start = 18.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            ) {
                LibrarySourceIcon(
                    source = source,
                    tint = if (source == selected) accent else LocalContentColor.current,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f, fill = false).padding(end = 16.dp)) {
                    Text(source.label(), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(
                            when (source) {
                                LibrarySource.YOUTUBE_MUSIC -> if (signedIn) {
                                    R.string.library_source_youtube_music_subtitle
                                } else {
                                    R.string.library_source_youtube_music_signed_out
                                }
                                LibrarySource.LOCAL -> R.string.library_source_local_subtitle
                                LibrarySource.ALL -> R.string.library_source_all_subtitle
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = .7f),
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(22.dp)) {
                    if (source == selected) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = accent)
                    }
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = LocalContentColor.current.copy(alpha = .2f),
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onOpenHistory)
                .padding(start = 18.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.view_song_history), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * A short list to pick one of — a sort order — on the same glass as
 * [LibraryMenu], the pick marked in the playing song's colour.
 */
@Composable
fun LibraryChoiceMenu(
    visible: Boolean,
    title: String?,
    options: List<String>,
    selected: Int,
    hazeState: HazeState,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = pageAccent()
    GlassMenuPanel(visible = visible, hazeState = hazeState, onDismiss = onDismiss) {
        if (title != null) MenuHeading(title)
        options.forEachIndexed { index, label ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .clickable(role = Role.Button) { onSelect(index) }
                    .padding(start = 20.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (index == selected) accent else LocalContentColor.current,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 20.dp),
                )
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(22.dp)) {
                    if (index == selected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.selected),
                            tint = accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MenuHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalContentColor.current.copy(alpha = .6f),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp),
    )
}

/**
 * The card the Library's menus open on: liquid glass where the device draws
 * it, the app's frosted popup otherwise, grown out of the top bar's right end
 * where the buttons that open them sit.
 */
@Composable
internal fun GlassMenuPanel(
    visible: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glass = isGlassSupported() && !reduceDynamicBlur
    // The bar's pill of buttons turns into the card; see [PopupLayer].
    PopupLayer(
        visible = visible,
        onDismiss = onDismiss,
        shape = shape,
        contentColor = if (glass) glassContentColor() else MaterialTheme.colorScheme.onSurface,
        surface = {
            if (isGlassSupported()) {
                Modifier.clip(shape).glassMenu(shape)
            } else {
                Modifier.popupSurface(shape, hazeState)
            }
        },
    ) {
        Column(Modifier.padding(vertical = 8.dp), content = content)
    }
}

// ── Dealing ─────────────────────────────────────────────────────────────────

/** The row and card names [dealLibraryFeed] writes, resolved where strings are. */
class LibraryFeedLabels(
    val recentlyAdded: String,
    val recentlyPlayed: String,
    val mostPlayed: String,
    val albums: String,
    val artists: String,
    val rediscover: String,
    /** "%s Radio" */
    val radio: String,
    /** "%s Mix" */
    val mix: String,
)

@Composable
fun rememberLibraryFeedLabels(): LibraryFeedLabels {
    val recentlyAdded = stringResource(R.string.library_recently_added)
    val recentlyPlayed = stringResource(R.string.library_recently_played)
    val mostPlayed = stringResource(R.string.library_most_played)
    val albums = stringResource(R.string.library_albums_for_you)
    val artists = stringResource(R.string.artists)
    val rediscover = stringResource(R.string.library_rediscover)
    val radio = stringResource(R.string.library_radio_named)
    val mix = stringResource(R.string.library_mix_named)
    return remember(recentlyAdded, recentlyPlayed, mostPlayed, albums, artists, rediscover, radio, mix) {
        LibraryFeedLabels(recentlyAdded, recentlyPlayed, mostPlayed, albums, artists, rediscover, radio, mix)
    }
}

/** A YouTube Music page id, rather than a device grouping or nothing. */
private fun String?.isRemoteBrowseId(): Boolean =
    !isNullOrBlank() && !startsWith("local:") && !contains("://")

/** A YouTube video id, which a station can be started off. */
private fun String.isVideoId(): Boolean = length == 11 && none { it == ':' || it == '/' }

/** A track that is a file on this device, rather than a stream. */
private fun Song.onDevice(): Boolean =
    localUri != null || localPath != null || localDateAddedSeconds != null

private fun String.folded(): String = lowercase(Locale.ROOT).trim()

private fun Song.matchKey(): String = LibraryDuplicates.titleKey(title) + "\u0000" + LibraryDuplicates.artistKey(artist)

private const val RECENT_TRACKS = 120
private const val TOP_ENTRIES = 12
private const val ROW_LENGTH = 15
private const val MIX_LENGTH = 40
private const val MAX_ROWS = 5

/** The page id a device album or artist opens under; see [LIBRARY_GROUP_PREFIX]. */
fun libraryGroupId(type: BrowseType, title: String, artist: String = ""): String =
    LIBRARY_GROUP_PREFIX + type.name.lowercase(Locale.ROOT) + ":" + title + "|" + artist

/**
 * Deals the Library page for [source]: six cards for the grid and the rows
 * under the menu.
 *
 * The grid mixes what was played lately, what is played most, a playlist and
 * a station, from YouTube Music's side (listening history, [ytShelves] — the
 * account's library) and the device's ([songs] that are files, [deviceItems] —
 * the releases downloaded whole and the app's own playlists) as [source]
 * allows. The rows start with Recently added, then up to five more: rows off
 * YouTube Music's home feed ([homeShelves]) for a library with YouTube Music
 * in it, rows made from the device's songs for Local.
 *
 * Random, off [random]: the caller deals once and keeps the result until a
 * pull asks for a new hand.
 */
suspend fun dealLibraryFeed(
    source: LibrarySource,
    songs: List<Song>,
    ytShelves: List<HomeShelf>,
    deviceItems: List<ShelfItem>,
    homeShelves: List<HomeShelf>,
    labels: LibraryFeedLabels,
    random: Random,
): LibraryFeed = withContext(Dispatchers.Default) {
    val youTube = source != LibrarySource.LOCAL
    val device = source != LibrarySource.YOUTUBE_MUSIC
    val summary = runCatching { ListeningStats.summary(ReplayPeriod.ALL_TIME) }.getOrNull()
    val recent = runCatching { ListeningStats.recentTracks(RECENT_TRACKS) }.getOrDefault(emptyList())

    // ── YouTube Music's side ──
    val artistsById = summary?.artists.orEmpty()
        .filter { it.browseId.isRemoteBrowseId() }
        .associateBy { it.browseId!! }
    val ytRecent = mutableListOf<LibrarySuggestion>()
    val ytOften = mutableListOf<LibrarySuggestion>()
    val ytRadios = mutableListOf<LibrarySuggestion>()
    if (youTube) {
        recent.filter { it.albumId.isRemoteBrowseId() && !it.album.isNullOrBlank() }
            .distinctBy { it.albumId }
            .take(TOP_ENTRIES)
            .forEach { ytRecent += LibrarySuggestion(ShelfItem(it.album!!, it.artist, it.art, null, it.albumId)) }
        // Only artists with a picture of their own: a round crop of a track's
        // sleeve would pass for the artist and not be them.
        recent.mapNotNull { track -> track.artistId?.let { artistsById[it] } }
            .distinctBy { it.browseId }
            .filter { !it.artworkUrl.isNullOrBlank() }
            .take(TOP_ENTRIES / 2)
            .forEach {
                ytRecent += LibrarySuggestion(
                    ShelfItem(it.title, it.subtitle.orEmpty(), it.artworkUrl, null, it.browseId),
                    round = true,
                )
            }
        summary?.albums.orEmpty().filter { it.browseId.isRemoteBrowseId() }.take(TOP_ENTRIES).forEach {
            ytOften += LibrarySuggestion(ShelfItem(it.title, it.subtitle.orEmpty(), it.artworkUrl, null, it.browseId))
        }
        artistsById.values.filter { !it.artworkUrl.isNullOrBlank() }
            .sortedByDescending { it.ms }
            .take(TOP_ENTRIES)
            .forEach {
                ytOften += LibrarySuggestion(
                    ShelfItem(it.title, it.subtitle.orEmpty(), it.artworkUrl, null, it.browseId),
                    round = true,
                )
            }
        // Stations: off a song played lately or often, and off a favourite
        // artist's most played song.
        val streamed = recent.filter { it.id.isVideoId() }
        streamed.take(TOP_ENTRIES).forEach { track ->
            val seed = Song(videoId = track.id, title = track.title, artist = track.artist, thumbnailUrl = track.art)
            ytRadios += LibrarySuggestion(
                ShelfItem(labels.radio.format(track.title), track.artist, track.art, track.id, null),
                radio = true,
                seed = seed,
            )
        }
        artistsById.values.filter { !it.artworkUrl.isNullOrBlank() }
            .sortedByDescending { it.ms }
            .take(TOP_ENTRIES / 2)
            .forEach { artist ->
                val lead = streamed.firstOrNull { it.artistId == artist.browseId } ?: return@forEach
                ytRadios += LibrarySuggestion(
                    ShelfItem(labels.radio.format(artist.title), "", artist.artworkUrl, lead.id, null),
                    round = true,
                    radio = true,
                    seed = Song(videoId = lead.id, title = lead.title, artist = lead.artist, thumbnailUrl = lead.art),
                )
            }
    }
    fun shelf(title: String) = ytShelves.firstOrNull { it.title == title }?.items.orEmpty()
    val ytPlaylists = if (youTube) shelf(YtMusicRepository.PLAYLISTS_SHELF).map { LibrarySuggestion(it) } else emptyList()
    val ytSaved = if (youTube) {
        shelf("Albums").map { LibrarySuggestion(it) } +
            (shelf("Artists") + shelf("Subscriptions")).map { LibrarySuggestion(it, round = true) }
    } else {
        emptyList()
    }

    // ── The device's side ──
    val local = if (device) songs.filter { it.onDevice() } else emptyList()
    val knownArtists = LibraryArtists.knownArtists(local.map { it.artist })
    val albumArt = AlbumArtChoices.choices.value
    val localAlbums = local
        .filter { !it.albumName.isNullOrBlank() }
        .groupBy { it.albumName!!.folded() }
        .values
        .filter { it.size >= 2 }
        .map { tracks ->
            val first = tracks.first()
            val artist = LibraryArtists.albumArtist(tracks, knownArtists)
            LibrarySuggestion(
                ShelfItem(
                    title = first.albumName!!,
                    subtitle = artist,
                    thumbnailUrl = AlbumArtChoices.chosen(null, first.albumName!!, albumArt)
                        ?: tracks.firstNotNullOfOrNull { it.thumbnailUrl },
                    videoId = null,
                    browseId = libraryGroupId(BrowseType.ALBUM, first.albumName!!, artist),
                ),
                songs = tracks,
                groupType = BrowseType.ALBUM,
            )
        }
    // A collaboration counts toward each of its artists — see LibraryArtists.
    val localArtists = LibraryArtists.group(local)
        .filter { (_, tracks) -> tracks.size >= 2 }
        .map { (name, tracks) ->
            LibrarySuggestion(
                ShelfItem(
                    title = name,
                    subtitle = "",
                    thumbnailUrl = AppleArtistArtRepository.cached(name)?.heroUrl
                        ?: tracks.firstNotNullOfOrNull { it.thumbnailUrl },
                    videoId = null,
                    browseId = libraryGroupId(BrowseType.ARTIST, name),
                ),
                round = true,
                songs = tracks,
                groupType = BrowseType.ARTIST,
            )
        }
    val recentNames = recent.mapNotNull { it.album?.folded() }.toSet()
    val topNames = summary?.albums.orEmpty().take(TOP_ENTRIES * 2).map { it.title.folded() }.toSet()
    val recentArtistKeys = recent.map { LibraryDuplicates.artistKey(it.artist) }.toSet()
    val topArtistKeys = summary?.artists.orEmpty().take(TOP_ENTRIES * 2).map { LibraryDuplicates.artistKey(it.title) }.toSet()
    val localByKey = local.associateBy { it.matchKey() }
    val localById = local.associateBy { it.videoId }
    fun localMatch(id: String, title: String, artist: String): Song? =
        localById[id] ?: localByKey[LibraryDuplicates.titleKey(title) + "\u0000" + LibraryDuplicates.artistKey(artist)]
    val recentlyPlayedLocal = recent.mapNotNull { localMatch(it.id, it.title, it.artist) }.distinctBy { it.videoId }
    val mostPlayedLocal = summary?.songs.orEmpty()
        .mapNotNull { ranked -> localMatch(ranked.song.videoId, ranked.song.title, ranked.song.artist) }
        .distinctBy { it.videoId }
    val deviceCards = if (device) deviceItems.map { LibrarySuggestion(it) } else emptyList()

    // Mixes: the device's answer to a station, played from what is on it.
    val localMixes = buildList<LibrarySuggestion> {
        localArtists.forEach { artist ->
            val tracks = artist.songs.orEmpty()
            if (tracks.size >= 3) {
                add(
                    artist.copy(
                        item = artist.item.copy(title = labels.mix.format(artist.item.title), browseId = null),
                        radio = true,
                        songs = tracks.shuffled(random).take(MIX_LENGTH),
                        groupType = null,
                    ),
                )
            }
        }
        (recentlyPlayedLocal.take(TOP_ENTRIES) + mostPlayedLocal.take(TOP_ENTRIES)).distinctBy { it.videoId }.forEach { seed ->
            val kin = local.filter {
                it.videoId != seed.videoId &&
                    (LibraryDuplicates.artistKey(it.artist) == LibraryDuplicates.artistKey(seed.artist) ||
                        (it.albumName != null && it.albumName == seed.albumName))
            }.shuffled(random)
            val kinIds = kin.mapTo(HashSet()) { it.videoId }
            val rest = local.filter { it.videoId != seed.videoId && it.videoId !in kinIds }.shuffled(random)
            add(
                LibrarySuggestion(
                    ShelfItem(labels.mix.format(seed.title), seed.artist, seed.thumbnailUrl, seed.videoId, null),
                    radio = true,
                    songs = (listOf(seed) + kin + rest).take(MIX_LENGTH),
                ),
            )
        }
    }

    val recentPool = ytRecent +
        localAlbums.filter { it.item.title.folded() in recentNames } +
        localArtists.filter { LibraryDuplicates.artistKey(it.item.title) in recentArtistKeys } +
        deviceCards.filter { it.item.title.folded() in recentNames }
    val oftenPool = ytOften +
        localAlbums.filter { it.item.title.folded() in topNames } +
        localArtists.filter { LibraryDuplicates.artistKey(it.item.title) in topArtistKeys } +
        deviceCards.filter { it.item.title.folded() in topNames }
    val playlistPool = ytPlaylists + deviceCards
    val radioPool = ytRadios + localMixes
    val restPool = ytSaved + localAlbums + localArtists

    val picked = LinkedHashMap<String, LibrarySuggestion>()
    fun take(pool: List<LibrarySuggestion>, count: Int) {
        var added = 0
        for (candidate in pool.shuffled(random)) {
            if (added >= count || picked.size >= GRID_SIZE) return
            if (candidate.key in picked) continue
            picked[candidate.key] = candidate
            added++
        }
    }
    take(recentPool, 2)
    take(oftenPool, 2)
    take(playlistPool, 1)
    take(radioPool, 1)
    take(recentPool + oftenPool + playlistPool + radioPool + restPool, GRID_SIZE)
    val suggestions = picked.values.toList().shuffled(random)

    // ── Rows ──
    val rows = mutableListOf<LibraryFeedRow>()
    val recentlyAdded = when (source) {
        LibrarySource.YOUTUBE_MUSIC -> songs.take(ROW_LENGTH)
        LibrarySource.LOCAL -> local.sortedByDescending { it.localDateAddedSeconds ?: Long.MIN_VALUE }.take(ROW_LENGTH)
        LibrarySource.ALL -> {
            // Nothing dates both sides alike, so the newest of each take turns.
            val newestLocal = songs.filter { it.onDevice() }
                .sortedByDescending { it.localDateAddedSeconds ?: Long.MIN_VALUE }
            val newestStreamed = songs.filterNot { it.onDevice() }
            buildList<Song> {
                var i = 0
                while (size < ROW_LENGTH && (i < newestLocal.size || i < newestStreamed.size)) {
                    newestStreamed.getOrNull(i)?.let { add(it) }
                    newestLocal.getOrNull(i)?.let { add(it) }
                    i++
                }
            }.take(ROW_LENGTH)
        }
    }
    if (recentlyAdded.isNotEmpty()) rows += LibraryFeedRow(labels.recentlyAdded, songs = recentlyAdded)

    if (youTube) {
        homeShelves
            .filter { shelf -> shelf.items.size >= 3 && shelf.title.isNotBlank() }
            .shuffled(random)
            .take(MAX_ROWS)
            .forEach { shelf ->
                rows += LibraryFeedRow(
                    title = shelf.title,
                    items = shelf.items.take(ROW_LENGTH).map { item ->
                        LibrarySuggestion(item, round = item.browseId?.startsWith("UC") == true)
                    },
                )
            }
    } else {
        val localRows = buildList<LibraryFeedRow> {
            if (recentlyPlayedLocal.isNotEmpty()) {
                add(LibraryFeedRow(labels.recentlyPlayed, songs = recentlyPlayedLocal.take(ROW_LENGTH)))
            }
            if (mostPlayedLocal.isNotEmpty()) {
                add(LibraryFeedRow(labels.mostPlayed, songs = mostPlayedLocal.take(ROW_LENGTH)))
            }
            if (localAlbums.isNotEmpty()) {
                add(LibraryFeedRow(labels.albums, items = localAlbums.shuffled(random).take(ROW_LENGTH)))
            }
            if (localArtists.isNotEmpty()) {
                add(LibraryFeedRow(labels.artists, items = localArtists.shuffled(random).take(ROW_LENGTH)))
            }
            // What hasn't been played in a while, or ever.
            val heard = recentlyPlayedLocal.map { it.videoId }.toSet()
            val forgotten = local.filter { it.videoId !in heard }.shuffled(random).take(ROW_LENGTH)
            if (forgotten.isNotEmpty()) add(LibraryFeedRow(labels.rediscover, songs = forgotten))
        }
        rows += localRows.take(MAX_ROWS)
    }
    LibraryFeed(source, suggestions, rows)
}
