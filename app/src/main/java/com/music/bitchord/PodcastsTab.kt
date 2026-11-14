@file:OptIn(ExperimentalMaterial3Api::class)

package com.music.bitchord

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.podcasts.PodcastsDial
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.data.settings.speedDialId
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.components.HomeStickyLogo
import com.music.bitchord.ui.components.PodcastsDialMenu
import com.music.bitchord.ui.screens.HomeScreen
import com.music.bitchord.ui.screens.SPEED_DIAL_PAGE_KEYS
import com.music.bitchord.ui.theme.Metropolis
import dev.chrisbanes.haze.HazeState

/**
 * What the Podcasts page shares with what is drawn round it (the top bar's
 * "…" button, the mark over the pages, the tab bar), kept here and not as
 * locals of the app's main composable: every local one of that composable's
 * lambdas reads is one more argument to it, and those are counted (see
 * MainActivityClassSizeTest).
 */
internal object PodcastsTab {
    /** The page's list. Kept between visits, so the page comes back where it was left. */
    val listState = LazyListState()

    /** The page's pull to refresh while the page is up, for its mark to fill with. */
    var pull: PullToRefreshState? by mutableStateOf(null)

    /** Whether the page's "…" menu is up. */
    var menuOpen by mutableStateOf(false)

    private val onDial = derivedStateOf {
        val layout = listState.layoutInfo
        val middle = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
        val dial = layout.visibleItemsInfo.firstOrNull { it.key in SPEED_DIAL_PAGE_KEYS }
        if (dial != null) dial.offset + dial.size > middle else listState.firstVisibleItemIndex <= 1
    }

    /**
     * Whether the dial holds the middle of the screen. While it does the tab
     * bar stays in full view, as it does over Home's dials.
     */
    val dialShowing: Boolean get() = onDial.value
}

/**
 * The Podcasts page: a dial of shows (the ones pinned to it, then the ones
 * YouTube Music recommends), then shelves of shows under it. It is Home's
 * page drawn from another feed, so the dial, its pins and the shelves look
 * and act as Home's do.
 *
 * The feed is asked for when the page is first opened, not at launch. Until
 * it is in, the page shows the dial it showed last time, or placeholders the
 * first time, and the tiles change once at most.
 */
@Composable
internal fun PodcastsTabPage(
    viewModel: MainViewModel,
    currentSong: Song?,
    isPlaying: Boolean,
    contentPadding: PaddingValues,
    onItemLongPress: (ShelfItem) -> Unit,
    onDialItemLongPress: (ShelfItem, Boolean) -> Unit,
) {
    val feed by viewModel.podcasts.collectAsStateWithLifecycle()
    val refreshing by viewModel.podcastsRefreshing.collectAsStateWithLifecycle()
    // Another account's recommendations are another feed.
    val account by viewModel.account.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    LaunchedEffect(account, signedIn) { viewModel.openPodcasts() }

    val pins by CustomizationSettings.podcastDialPins.collectAsStateWithLifecycle()
    val hidden by CustomizationSettings.hiddenFromDial.collectAsStateWithLifecycle()
    val lastDial by CustomizationSettings.lastPodcastDial.collectAsStateWithLifecycle()
    val rows by CustomizationSettings.speedDialRows.collectAsStateWithLifecycle()
    val labels by CustomizationSettings.dialLabels.collectAsStateWithLifecycle()
    val labelOverrides by CustomizationSettings.dialHiddenLabels.collectAsStateWithLifecycle()
    val pinIcon by CustomizationSettings.dialPinIcon.collectAsStateWithLifecycle()
    val pinnedIds = remember(pins) { pins.mapNotNullTo(HashSet()) { it.speedDialId } }

    val state: UiState<List<HomeShelf>> = remember(feed, pins, hidden, lastDial) {
        // Nothing from the network (yet): the dial as it last was, when there is one.
        val saved = PodcastsDial.tiles(pins, lastDial, hidden)
            .takeIf { it.isNotEmpty() }
            ?.let { UiState.Success(PodcastsDial.page(it, emptyList())) }
        when (val current = feed) {
            is UiState.Success ->
                UiState.Success(PodcastsDial.page(PodcastsDial.tiles(pins, current.data.picks, hidden), current.data.shelves))
            is UiState.Loading -> saved ?: UiState.Loading
            is UiState.Error -> saved ?: current
        }
    }
    // Saved once the fresh dial is what is showing, for the next time the page opens.
    LaunchedEffect(feed, pins, hidden) {
        val fresh = (feed as? UiState.Success)?.data ?: return@LaunchedEffect
        CustomizationSettings.setLastPodcastDial(PodcastsDial.tiles(pins, fresh.picks, hidden))
    }

    val pullState = rememberPullToRefreshState()
    DisposableEffect(pullState) {
        PodcastsTab.pull = pullState
        onDispose { if (PodcastsTab.pull === pullState) PodcastsTab.pull = null }
    }

    HomeScreen(
        state = state,
        listState = PodcastsTab.listState,
        onItemClick = { item, _ ->
            // Hoisted because ShelfItem lives in :shared, and Kotlin will not
            // smart-cast a public nullable property declared in another module.
            val browseId = item.browseId
            if (browseId != null) {
                viewModel.openDetail(
                    browseId = browseId,
                    title = item.title,
                    subtitle = item.subtitle,
                    thumbnailUrl = item.thumbnailUrl,
                )
            }
        },
        onRetry = viewModel::loadPodcasts,
        refreshing = refreshing,
        onRefresh = viewModel::refreshPodcasts,
        pullState = pullState,
        contentPadding = contentPadding,
        // Not drawn by the page: it says that the page has a mark over it, to leave room for.
        title = stringResource(R.string.podcasts),
        onItemLongPress = onItemLongPress,
        onDialItemLongPress = onDialItemLongPress,
        currentSong = currentSong,
        isPlaying = isPlaying,
        speedDialPinned = pinnedIds,
        speedDialRows = rows,
        onSpeedDialReorder = CustomizationSettings::setPodcastDialPins,
        dialLabels = labels,
        dialLabelOverrides = labelOverrides,
        dialPinIcon = pinIcon,
        skeletonSpeedDialFirst = true,
        skeletonShowRecents = false,
    )
}

/**
 * The Podcasts page's mark, drawn over the pages as Home's is: the logo with
 * "Podcasts" beside it, which fills with the page's pull and spins while the
 * page reads its feed again. [visible] while the page is up with nothing
 * opened over it.
 */
@Composable
internal fun BoxScope.PodcastsMarkLayer(
    visible: Boolean,
    viewModel: MainViewModel,
    topPadding: Dp,
) {
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                alpha = PodcastsMark.alpha
                scaleX = PodcastsMark.scale
                scaleY = PodcastsMark.scale
            },
    ) {
        AnimatedVisibility(
            visible = visible || PodcastsMark.leaving,
            // No fade either way, as for Home's mark: between tabs the layer
            // above fades it with the page.
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            val refreshing by viewModel.podcastsRefreshing.collectAsStateWithLifecycle()
            HomeStickyLogo(
                listState = PodcastsTab.listState,
                topPadding = topPadding,
                refreshing = refreshing,
                pullFraction = { PodcastsTab.pull?.distanceFraction ?: 0f },
                trailing = {
                    Text(
                        text = stringResource(R.string.podcasts),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = Metropolis,
                        fontWeight = FontWeight.W800,
                        fontSize = 30.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 14.dp)
                            .weight(1f, fill = false),
                    )
                },
            )
        }
    }
}

/** The Podcasts page's "…" menu, and the back button closing it. */
@Composable
internal fun PodcastsDialMenuHost(hazeState: HazeState) {
    BackHandler(enabled = PodcastsTab.menuOpen) { PodcastsTab.menuOpen = false }
    PodcastsDialMenu(
        visible = PodcastsTab.menuOpen,
        hazeState = hazeState,
        onDismiss = { PodcastsTab.menuOpen = false },
    )
}
