package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.spotify.SpotifyPlayedList

/**
 * What the "Played in Dial" playlist is, in full (the settings row has room
 * for a line of it), and how many songs it holds before the oldest make way.
 */
@Composable
internal fun PlayedInDialDialog(onDismiss: () -> Unit) {
    val keep by AppSettings.spotifyPlayedKeep.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spotify_played_playlist)) },
        text = {
            Column {
                Text(stringResource(R.string.spotify_played_playlist_subtitle))
                Text(
                    text = stringResource(R.string.spotify_played_keep),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                )
                SegmentedControl(
                    options = SpotifyPlayedList.KEEP_OPTIONS.map { it.toString() },
                    selectedIndex = SpotifyPlayedList.KEEP_OPTIONS.indexOf(keep),
                    onSelect = { index -> AppSettings.setSpotifyPlayedKeep(SpotifyPlayedList.KEEP_OPTIONS[index]) },
                )
                Text(
                    text = stringResource(R.string.spotify_played_keep_note),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}
