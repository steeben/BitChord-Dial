package com.music.bitchord.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import org.jetbrains.compose.resources.painterResource
import com.music.bitchord.sharedui.resources.spotify_logo
import com.music.bitchord.sharedui.resources.Res
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.spotify.SPOTIFY_PAGE_PREFIX
import com.music.bitchord.data.spotify.SpotifyLibrary
import com.music.bitchord.data.spotify.SpotifyPlaylist
import com.music.bitchord.data.spotify.SpotifyTrack

/**
 * The signed-in Spotify account's playlists. A tap opens one as an ordinary
 * playlist page ([onOpenPlaylist]) — this screen is only the way in. Holding
 * a row, or its overflow, offers pinning it to the speed dial
 * ([onToggleSpeedDial]), with [isInSpeedDial] saying which way the row reads.
 */
@Composable
fun SpotifyLibraryScreen(
    onOpenPlaylist: (SpotifyPlaylist) -> Unit,
    onToggleSpeedDial: (SpotifyPlaylist) -> Unit,
    isInSpeedDial: (SpotifyPlaylist) -> Boolean,
    onOpenLogin: () -> Unit,
    onOpenTokenSetup: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Kept by whoever shows this page, so it is where it was left when a playlist's page closes back onto its row. */
    listState: LazyListState = rememberLazyListState(),
) {
    val cookie by AppSettings.spotifySpdcToken.collectAsStateWithLifecycle()
    // Starts as the list last read for this account: back from a playlist's
    // page, its row is there at once for the page to shrink into.
    var playlists by remember {
        mutableStateOf(lastPlaylists?.takeIf { it.first == cookie }?.second ?: emptyList())
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cookie) {
        if (cookie.isBlank()) {
            playlists = emptyList()
            error = null
            return@LaunchedEffect
        }
        loading = playlists.isEmpty()
        error = null
        runCatching { SpotifyLibrary.playlists() }
            .onSuccess {
                playlists = it
                lastPlaylists = cookie to it
            }
            .onFailure { error = it.message }
        loading = false
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.spotify_logo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(38.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.spotify),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        if (cookie.isBlank()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = stringResource(R.string.spotify_connect_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onOpenLogin) {
                        Text(stringResource(R.string.spotify_sign_in))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenTokenSetup) {
                        Text(stringResource(R.string.spotify_use_spdc_token))
                    }
                }
            }
        } else {
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            error?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            items(playlists, key = { it.id }) { item ->
                LibraryRow(
                    title = item.name,
                    subtitle = item.owner.orEmpty(),
                    imageUrl = item.imageUrl,
                    onClick = { onOpenPlaylist(item) },
                    launchKey = SPOTIFY_PAGE_PREFIX + item.id,
                    inSpeedDial = isInSpeedDial(item),
                    onToggleSpeedDial = { onToggleSpeedDial(item) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    inSpeedDial: Boolean,
    onToggleSpeedDial: () -> Unit,
    /** The page a tap opens, by key, for it to grow out of the row's picture. */
    launchKey: String? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val launch = rememberCardLaunch(launchKey, imageUrl, 8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = launching(launch, onClick),
                onLongClick = { menuOpen = true },
            )
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onState = { launch?.shows(it) },
            modifier = Modifier.size(52.dp).launchArt(launch).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (inSpeedDial) {
            Icon(
                imageVector = BitChordIcons.Pin,
                contentDescription = stringResource(R.string.remove_from_speed_dial),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (inSpeedDial) R.string.remove_from_speed_dial else R.string.add_to_speed_dial,
                            ),
                        )
                    },
                    leadingIcon = { Icon(BitChordIcons.Pin, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onToggleSpeedDial()
                    },
                )
            }
        }
    }
}

/** The playlists last read, and the sign-in they were read for. */
private var lastPlaylists: Pair<String, List<SpotifyPlaylist>>? = null
