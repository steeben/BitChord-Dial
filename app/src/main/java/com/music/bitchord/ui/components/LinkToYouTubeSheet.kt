package com.music.bitchord.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.LocalLinks
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.theme.LocalNowPlayingTint
import kotlinx.coroutines.launch

/**
 * Linking a file on the device to its track on YouTube Music by hand: the
 * songs YouTube Music finds for the file's title and artist, to pick the one
 * it is. The pick is what the file's Open artist and Open album lead to, and
 * holding its cover in the player. See [LocalLinks].
 */
@Composable
fun LinkToYouTubeSheet(
    file: Song,
    onDone: () -> Unit,
) {
    val links by LocalLinks.links.collectAsStateWithLifecycle()
    val linked = LocalLinks.keyOf(file)?.let { links[it] }
    val accent = LocalNowPlayingTint.current ?: MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()
    // Null while the search runs.
    var found by remember(file.videoId) { mutableStateOf<Result<List<Song>>?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(file.videoId) { found = LocalLinks.candidates(file) }

    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            text = stringResource(R.string.link_to_youtube_music),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = PAGE_GUTTER),
        )
        Text(
            text = stringResource(
                R.string.link_to_youtube_music_about,
                listOfNotNull(file.title, file.artist, file.durationText).joinToString(" · "),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 4.dp, bottom = 12.dp),
        )
        val result = found
        when {
            result == null || saving -> Box(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
            }
            result.isFailure || result.getOrNull().isNullOrEmpty() -> Text(
                text = stringResource(
                    if (result.isFailure) R.string.link_to_youtube_music_failed else R.string.link_to_youtube_music_none,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
            )
            else -> Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                result.getOrThrow().forEach { track ->
                    SongRow(
                        song = track,
                        onClick = {
                            saving = true
                            scope.launch {
                                LocalLinks.link(file, LocalLinks.withIds(track))
                                onDone()
                            }
                        },
                        isCurrent = track.videoId == linked?.videoId,
                        activeTint = accent,
                        showMore = false,
                        rowBackground = Color.Transparent,
                    )
                }
            }
        }
        if (linked != null && !saving) {
            Text(
                text = stringResource(R.string.remove_youtube_music_link),
                style = MaterialTheme.typography.bodyLarge,
                color = accent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable {
                        LocalLinks.unlink(file)
                        onDone()
                    }
                    .padding(vertical = 14.dp),
            )
        }
    }
}
