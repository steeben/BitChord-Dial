package com.music.bitchord.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.EpisodeInfo
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.podcasts.EpisodeDate
import com.music.bitchord.data.podcasts.EpisodeProgress
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.RowMoreButton
import com.music.bitchord.ui.components.SearchPlayingBars
import com.music.bitchord.ui.components.SelectionTick
import com.music.bitchord.ui.components.SwipeToQueueBox
import com.music.bitchord.ui.components.longPressMenuClickable
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.theme.ArtworkPalette

/** How wide an episode's still is drawn on its row; it is as tall as 16:9 makes it. */
private val EPISODE_STILL_WIDTH = 112.dp

/** How much of an episode's notes is laid out while they are closed: two lines never hold more. */
private const val CLOSED_NOTES_CHARS = 320

private val WHITESPACE = Regex("""\s+""")

/**
 * One podcast episode on its show's page. Taller than a track's row: the
 * episode's still, its title, when it came out and how long it is, then two
 * lines of its notes, which "Read more" opens in full. A thin bar along the
 * foot of the row, in the page's own colour, is as long as the part of the
 * episode that was played; an episode that was not started has none.
 *
 * It is tapped, held, swiped and selected as a track's row is.
 */
@Composable
internal fun EpisodeRow(
    song: Song,
    info: EpisodeInfo,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onMore: () -> Unit,
    onSwipeToQueue: (playNext: Boolean) -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
) {
    val titleColor by animateColorAsState(
        targetValue = if (isCurrent) palette.accent else palette.onBackground,
        label = "episode row title",
    )
    val activeBackground by animateColorAsState(
        targetValue = if (isCurrent || selected) palette.accent.copy(alpha = 0.14f) else Color.Transparent,
        label = "episode row background",
    )
    // Read here, not by the page: a percent that moves redraws this row alone.
    val playedInApp by EpisodeProgress.played.collectAsStateWithLifecycle()
    val played = EpisodeProgress.shown(info.playedPercent, playedInApp[song.videoId])
    val released = remember(info.released) { EpisodeDate.label(info.released) }
    val meta = listOfNotNull(released, song.durationText).joinToString(" • ")

    val notes = info.description
    var open by rememberSaveable { mutableStateOf(false) }
    // Closed, only the start of the notes is laid out, as running text: they
    // can be thousands of characters long, and open with a line of links.
    val closedNotes = remember(notes) {
        notes?.replace(WHITESPACE, " ")?.take(CLOSED_NOTES_CHARS)
    }
    var clipped by remember(notes) { mutableStateOf(false) }
    val canOpen = clipped || open || (notes != null && closedNotes != null && notes.length > closedNotes.length)

    SwipeToQueueBox(
        onSwipeToQueue = onSwipeToQueue,
        enabled = !selectionMode,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(activeBackground)
                .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 12.dp),
            ) {
                SelectionTick(
                    visible = selectionMode,
                    selected = selected,
                    tint = palette.accent,
                    idleTint = palette.onBackgroundVariant,
                )
                Box(Modifier.width(EPISODE_STILL_WIDTH).aspectRatio(16f / 9f)) {
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .thumbnailBorder(RoundedCornerShape(8.dp))
                            .background(palette.elevated),
                    )
                    if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (meta.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onBackgroundVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!selectionMode) RowMoreButton(onClick = onMore, tint = palette.onBackgroundVariant)
            }
            if (!notes.isNullOrBlank()) {
                Text(
                    text = if (open) notes else closedNotes.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onBackgroundVariant,
                    maxLines = if (open) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result -> if (!open) clipped = result.hasVisualOverflow },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 8.dp),
                )
                if (canOpen) {
                    Text(
                        text = stringResource(if (open) R.string.less else R.string.read_more),
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.accent,
                        modifier = Modifier
                            .padding(start = PAGE_GUTTER - 6.dp, top = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { open = !open }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // How much of it was played, traced along the foot of the row.
            Box(Modifier.fillMaxWidth().height(2.dp)) {
                if (played > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth(played / 100f)
                            .fillMaxHeight()
                            .background(palette.accent),
                    )
                }
            }
        }
    }
}
