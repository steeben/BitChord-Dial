package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.AlbumArtChoices
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt

/**
 * Picks which of the covers an album's own files carry stands for the album —
 * see [AlbumArtChoices]. Tapping a cover picks it; the row under
 * the grid goes back to the one Android chose.
 */
@Composable
fun AlbumArtPickerSheet(
    target: BrowseTarget,
    /** The cover picked, or null to go back to the original. */
    onPick: (String?) -> Unit,
    /**
     * Set when the pick is for this one song rather than the whole album
     * [target] stands for; [target]'s songs are still where the covers come from.
     */
    song: Song? = null,
) {
    val context = LocalContext.current
    val choices by AlbumArtChoices.choices.collectAsStateWithLifecycle()
    val songArt by AlbumArtChoices.songArt.collectAsStateWithLifecycle()
    val current = if (song != null) {
        songArt.let { AlbumArtChoices.pickedCandidateForSong(song.localUri) }
    } else {
        choices.let { AlbumArtChoices.pickedCandidate(target.browseId, target.title) }
    }
    val candidates by produceState<List<String>?>(null, target) {
        value = AlbumArtChoices.candidates(context, target.songs)
    }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            text = stringResource(if (song != null) R.string.change_album_art else R.string.choose_album_art),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = song?.title ?: target.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(16.dp))
        val found = candidates
        when {
            found == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
            found.isEmpty() -> Text(
                text = stringResource(R.string.no_album_art_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(found, key = { it }) { url ->
                    val picked = url == current
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .then(
                                if (picked) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier,
                            )
                            .clickable {
                                onPick(url)
                            },
                    ) {
                        AsyncImage(
                            model = url.artworkAt(CARD_ART_PX),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                        if (picked) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(3.dp),
                            )
                        }
                    }
                }
            }
        }
        }
        if (current != null) {
            Spacer(Modifier.height(8.dp))
            ActionRow(Icons.Rounded.Restore, stringResource(R.string.use_original_album_art)) {
                onPick(null)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
