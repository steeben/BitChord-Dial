package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.VideoComment
import com.music.bitchord.data.VideoComments
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.icons.BitChordIcons
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** What the drawer has of a video's comments so far. */
private data class CommentsState(
    val comments: List<VideoComment> = emptyList(),
    /** What asks for the next page; null when there is none. */
    val continuation: String? = null,
    val total: String? = null,
    val loading: Boolean = true,
    /** The video takes no comments. */
    val off: Boolean = false,
    /** The last request did not come back. */
    val failed: Boolean = false,
)

/** A comment longer than this many lines is cut, and opens in full when it is tapped. */
private const val COMMENT_CLOSED_LINES = 6

/**
 * The comments under the podcast episode that is playing, in the player's
 * drawer: what the lyrics button opens for an episode, which has no lyrics.
 * YouTube's "Top comments", a page of twenty at a time; "More comments" at
 * the foot asks for the next page. Read only: nothing here writes, likes or
 * replies.
 *
 * Asked for when the drawer opens and not before, and not kept after it shuts.
 */
@Composable
internal fun EpisodeCommentsSheet(
    hazeState: HazeState,
    videoId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember(videoId) { mutableStateOf(CommentsState()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(videoId) {
        val first = VideoComments.first(videoId).getOrNull()
        state = if (first == null) {
            CommentsState(loading = false, failed = true)
        } else {
            CommentsState(first.comments, first.continuation, first.total, loading = false, off = first.off)
        }
    }
    val loadMore: () -> Unit = {
        val token = state.continuation
        if (token != null && !state.loading) {
            state = state.copy(loading = true, failed = false)
            scope.launch {
                val next = VideoComments.more(token).getOrNull()
                state = if (next == null) {
                    state.copy(loading = false, failed = true)
                } else {
                    state.copy(
                        comments = (state.comments + next.comments).distinctBy { it.id },
                        continuation = next.continuation,
                        loading = false,
                    )
                }
            }
        }
    }

    val title = stringResource(Res.string.comments)
    PlayerDrawer(
        hazeState = hazeState,
        title = state.total?.let { "$title  $it" } ?: title,
        onDismiss = onDismiss,
        modifier = modifier,
        handlesBack = true,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.comments.forEach { comment -> CommentRow(comment) }
        }
        when {
            state.loading -> Box(
                Modifier.fillMaxWidth().padding(vertical = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.85f),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(26.dp),
                )
            }
            state.failed && state.comments.isEmpty() -> CommentsNote(stringResource(Res.string.comments_unavailable))
            state.off -> CommentsNote(stringResource(Res.string.comments_off))
            state.comments.isEmpty() -> CommentsNote(stringResource(Res.string.no_comments))
            state.continuation != null -> Text(
                text = stringResource(Res.string.more_comments),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = loadMore)
                    .padding(vertical = 13.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CommentsNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
    )
}

@Composable
private fun CommentRow(comment: VideoComment) {
    var open by remember(comment.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AsyncImage(
            model = comment.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (comment.byCreator) FontWeight.Bold else FontWeight.Medium,
                    color = Color.White.copy(alpha = if (comment.byCreator) 1f else 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                comment.published?.let { published ->
                    Text(
                        text = "  •  $published",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = if (open) Int.MAX_VALUE else COMMENT_CLOSED_LINES,
                overflow = TextOverflow.Ellipsis,
                // A long comment opens and shuts on a tap; a short one has nothing to open.
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { open = !open },
            )
            if (comment.likes != null || comment.replies != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    comment.likes?.let { CommentCount(Icons.Rounded.ThumbUp, it) }
                    if (comment.likes != null && comment.replies != null) Spacer(Modifier.width(16.dp))
                    comment.replies?.let { CommentCount(BitChordIcons.Comments, it) }
                }
            }
        }
    }
}

@Composable
private fun CommentCount(icon: ImageVector, count: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = count,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}
