package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.model.Song

/** One bulk action a page adds to the selection menu, on top of the shared ones. */
class SelectionAction(
    val icon: ImageVector,
    val label: String,
    /** Drawn in the error colour: it deletes something. */
    val destructive: Boolean = false,
    val run: (List<Song>) -> Unit,
)

/**
 * The rows held into a multi-selection on whichever list is showing.
 *
 * A single slot, like [LongPressOrigin]: only one list can be selecting at a
 * time, and the two things that act on it, the top bar's ⋯ (which stands in
 * for the account photo while anything is selected) and the bulk actions
 * sheet the app hosts, sit far from the list that owns it. The list
 * publishes through [PublishListSelection] and takes its selection back when
 * it leaves.
 */
object ListSelection {
    private var owner: Any? = null
    private var clearOwner: (() -> Unit)? = null

    /** What is selected, in list order. Empty when nothing is. */
    var songs by mutableStateOf<List<Song>>(emptyList())
        private set

    /** The publishing page's own actions: Delete on Downloads, and so on. */
    var extras by mutableStateOf<List<SelectionAction>>(emptyList())
        private set

    /** Selects every row the page is showing. Null hides the action. */
    var selectAll by mutableStateOf<(() -> Unit)?>(null)
        private set

    /** False where the page offers its own download (or everything is already one). */
    var offersDownload by mutableStateOf(true)
        private set

    var menuOpen by mutableStateOf(false)

    val active: Boolean get() = songs.isNotEmpty()

    fun openMenu() {
        if (active) menuOpen = true
    }

    /** Ends the selection: the page unticks everything and the bar gets its photo back. */
    fun clear() {
        menuOpen = false
        clearOwner?.invoke()
    }

    internal fun publish(
        key: Any,
        selected: List<Song>,
        pageActions: List<SelectionAction>,
        onSelectAll: (() -> Unit)?,
        download: Boolean,
        onClear: () -> Unit,
    ) {
        owner = key
        clearOwner = onClear
        songs = selected
        extras = pageActions
        selectAll = onSelectAll
        offersDownload = download
    }

    internal fun withdraw(key: Any) {
        if (owner !== key) return
        owner = null
        clearOwner = null
        songs = emptyList()
        extras = emptyList()
        selectAll = null
        menuOpen = false
    }
}

/**
 * Hands a list's selection to [ListSelection] while it has one. A list with
 * nothing selected publishes nothing, so a page further down the stack can't
 * wipe out the selection of the one on top.
 */
@Composable
fun PublishListSelection(
    selected: List<Song>,
    onClear: () -> Unit,
    onSelectAll: (() -> Unit)? = null,
    extras: List<SelectionAction> = emptyList(),
    offersDownload: Boolean = true,
) {
    val key = remember { Any() }
    val clear by rememberUpdatedState(onClear)
    SideEffect {
        if (selected.isEmpty()) {
            ListSelection.withdraw(key)
        } else {
            ListSelection.publish(key, selected, extras, onSelectAll, offersDownload) { clear() }
        }
    }
    DisposableEffect(key) { onDispose { ListSelection.withdraw(key) } }
}

/**
 * The bulk actions sheet: what the ⋯ opens while rows are selected. Every
 * action ends the selection, the same way the Downloads bar always did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListSelectionSheetHost(
    onPlay: (List<Song>) -> Unit,
    onPlayNext: (List<Song>) -> Unit,
    onAddToQueue: (List<Song>) -> Unit,
    onDownload: (List<Song>) -> Unit,
) {
    if (!ListSelection.menuOpen) return
    val songs = ListSelection.songs
    LaunchedEffect(songs.isEmpty()) { if (songs.isEmpty()) ListSelection.menuOpen = false }
    if (songs.isEmpty()) return
    val finish: ((List<Song>) -> Unit) -> () -> Unit = { action ->
        {
            val chosen = songs
            ListSelection.clear()
            action(chosen)
        }
    }
    SlideDownBottomSheet(
        onDismissRequest = { ListSelection.menuOpen = false },
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                text = "${songs.size} ${stringResource(R.string.selected)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
            ActionRow(Icons.Rounded.PlayArrow, stringResource(R.string.play), onClick = finish(onPlay))
            ActionRow(
                Icons.AutoMirrored.Rounded.PlaylistPlay,
                stringResource(R.string.play_next),
                onClick = finish(onPlayNext),
            )
            ActionRow(
                Icons.AutoMirrored.Rounded.QueueMusic,
                stringResource(R.string.add_to_queue),
                onClick = finish(onAddToQueue),
            )
            if (ListSelection.offersDownload) {
                ActionRow(Icons.Rounded.Download, stringResource(R.string.download), onClick = finish(onDownload))
            }
            ListSelection.extras.forEach { extra ->
                ActionRow(
                    extra.icon,
                    extra.label,
                    tint = if (extra.destructive) MaterialTheme.colorScheme.error else null,
                    onClick = finish(extra.run),
                )
            }
            ListSelection.selectAll?.let { all ->
                ActionRow(Icons.Rounded.DoneAll, stringResource(R.string.select_all)) {
                    // Stays open: picking everything is a step towards an action, not one.
                    all()
                }
            }
            ActionRow(Icons.Rounded.Close, stringResource(R.string.cancel), tint = null) { ListSelection.clear() }
        }
    }
}
