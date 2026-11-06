package com.music.bitchord.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.canvas.AnimatedArtKind
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.IosSwitch

/**
 * The animated cover, Spotify Canvas and music video switches under
 * Appearance › Animated cover art, listed in priority order: the first one a
 * song has is what the player shows. The arrows move a kind up or down.
 */
@Composable
internal fun AnimatedArtPriorityRows() {
    val prefs by AppSettings.animatedArt.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.animated_art_priority_caption),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = ROW_INSET, end = ROW_INSET, bottom = 4.dp),
        )
        prefs.order.forEachIndexed { index, kind ->
            val on = prefs.allows(kind)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { AppSettings.setAnimatedArt(prefs.withEnabled(kind, !on)) }
                    .padding(start = ROW_INSET, end = ROW_INSET, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(kind.label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (on) 1f else 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(kind.detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { AppSettings.setAnimatedArt(prefs.move(kind, -1)) },
                    enabled = index > 0,
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.animated_art_move_up),
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(
                    onClick = { AppSettings.setAnimatedArt(prefs.move(kind, 1)) },
                    enabled = index < prefs.order.lastIndex,
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.animated_art_move_down),
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                IosSwitch(
                    checked = on,
                    onCheckedChange = { AppSettings.setAnimatedArt(prefs.withEnabled(kind, it)) },
                )
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

private val AnimatedArtKind.label: Int
    get() = when (this) {
        AnimatedArtKind.ANIMATED_COVER -> R.string.animated_art_cover
        AnimatedArtKind.CANVAS -> R.string.animated_art_canvas
        AnimatedArtKind.MUSIC_VIDEO -> R.string.animated_art_music_video
    }

private val AnimatedArtKind.detail: Int
    get() = when (this) {
        AnimatedArtKind.ANIMATED_COVER -> R.string.animated_art_cover_detail
        AnimatedArtKind.CANVAS -> R.string.animated_art_canvas_detail
        AnimatedArtKind.MUSIC_VIDEO -> R.string.animated_art_music_video_detail
    }
