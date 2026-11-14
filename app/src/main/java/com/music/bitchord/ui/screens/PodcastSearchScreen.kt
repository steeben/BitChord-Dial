package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.podcasts.PodcastSearch
import com.music.bitchord.data.podcasts.PodcastSearchSource
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.SearchPlayingBars
import com.music.bitchord.ui.components.longPressMenuClickable
import com.music.bitchord.ui.components.songListSkeleton
import com.music.bitchord.ui.components.thumbnailBorder

/**
 * The search the Podcasts tab opens: podcast shows and episodes, never songs.
 * The field is the app's search field in the top bar; this is the source
 * selector under it (YouTube, Spotify, Library) and what was found.
 *
 * Shows first, as square covers, then episodes with their widescreen
 * pictures. A show opens its page; an episode plays, and Autoplay goes on
 * with episodes. See [PodcastSearch].
 */
@Composable
internal fun PodcastSearchScreen(
    query: String,
    currentSong: Song?,
    isPlaying: Boolean,
    onOpenShow: (ShelfItem) -> Unit,
    onPlayEpisode: (Song) -> Unit,
    onEpisodeLongPress: (Song) -> Unit,
    contentPadding: PaddingValues,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val source by PodcastSearch.source.collectAsStateWithLifecycle()
    val results by PodcastSearch.results.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().padding(top = topPadding)) {
        SegmentedControl(
            options = listOf(
                stringResource(R.string.search_source_youtube),
                stringResource(R.string.spotify),
                stringResource(R.string.library),
            ),
            selectedIndex = source.ordinal,
            onSelect = { index -> PodcastSearch.setSource(scope, PodcastSearchSource.entries[index]) },
            glass = true,
            modifier = Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        ) {
            when (val state = results) {
                null -> item(key = "podcast-search:hint") {
                    MessageState(
                        stringResource(
                            if (source == PodcastSearchSource.LIBRARY) {
                                R.string.podcast_search_library_hint
                            } else {
                                R.string.podcast_search_hint
                            },
                        ),
                    )
                }
                UiState.Loading -> songListSkeleton(count = 6, keyPrefix = "skeleton:podcast-search")
                is UiState.Error -> item(key = "podcast-search:error") {
                    MessageState(
                        if (state.message == PodcastSearch.SPOTIFY_SIGNED_OUT) {
                            stringResource(R.string.search_spotify_signed_out)
                        } else {
                            stringResource(R.string.podcast_search_failed)
                        },
                    )
                }
                is UiState.Success -> if (state.data.isEmpty) {
                    item(key = "podcast-search:none") {
                        MessageState(stringResource(R.string.podcast_search_none, query.trim()))
                    }
                } else {
                    hits(
                        shows = state.data.shows,
                        episodes = state.data.episodes,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onOpenShow = onOpenShow,
                        onPlayEpisode = onPlayEpisode,
                        onEpisodeLongPress = onEpisodeLongPress,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.hits(
    shows: List<ShelfItem>,
    episodes: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onOpenShow: (ShelfItem) -> Unit,
    onPlayEpisode: (Song) -> Unit,
    onEpisodeLongPress: (Song) -> Unit,
) {
    if (shows.isNotEmpty()) {
        item(key = "podcast-search:shows") { SectionTitle(stringResource(R.string.podcast_search_shows)) }
        itemsIndexed(shows, key = { index, show -> "show:${show.browseId}:$index" }) { _, show ->
            ShowRow(show) { onOpenShow(show) }
        }
    }
    if (episodes.isNotEmpty()) {
        item(key = "podcast-search:episodes") { SectionTitle(stringResource(R.string.podcast_search_episodes)) }
        itemsIndexed(episodes, key = { index, song -> "episode:${song.videoId}:$index" }) { _, song ->
            val current = currentSong?.videoId == song.videoId
            EpisodeHitRow(
                song = song,
                isCurrent = current,
                isPlaying = current && isPlaying,
                onClick = { onPlayEpisode(song) },
                onLongPress = { onEpisodeLongPress(song) },
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun ShowRow(show: ShelfItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = null)
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
    ) {
        AsyncImage(
            model = show.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .thumbnailBorder(shape),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = show.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (show.subtitle.isNotBlank()) {
                Text(
                    text = show.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EpisodeHitRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(96.dp)
                    .height(54.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .thumbnailBorder(shape),
            )
            if (isPlaying) SearchPlayingBars()
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val line = listOfNotNull(
                song.artist.takeIf { it.isNotBlank() },
                song.durationText,
            ).joinToString(" • ")
            if (line.isNotEmpty()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
