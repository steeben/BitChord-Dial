package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.spotify.SpotifyPlayedList
import com.music.bitchord.ui.components.GlassAlert

/**
 * What the "Played in Dial" playlist is, in full (the settings row has room
 * for a line of it), and how many songs it holds before the oldest make way.
 */
@Composable
internal fun PlayedInDialDialog(onDismiss: () -> Unit) {
    val keep by AppSettings.spotifyPlayedKeep.collectAsStateWithLifecycle()
    GlassAlert(title = stringResource(R.string.spotify_played_playlist), onDismiss = onDismiss) {
        val contentColor = LocalContentColor.current
        Text(
            text = stringResource(R.string.spotify_played_playlist_subtitle),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
            color = contentColor,
        )
        Text(
            text = stringResource(R.string.spotify_played_keep),
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
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
            color = contentColor.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
