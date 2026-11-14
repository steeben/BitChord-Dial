package com.music.bitchord.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.podcasts.EpisodeFilter
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import dev.chrisbanes.haze.HazeState

/**
 * The menu of a show page's episode filter: All, Unplayed, In progress,
 * Played. It opens out of the filter button as the top bar's menus open out
 * of their pill (see [PopupLayer]); the button marks itself with [menuAnchor].
 *
 * One slot, like the Podcasts page's dial menu: the page that is up opens it
 * with [show], and the app composes it once, with the other menus, where the
 * haze can see the page ([EpisodeFilterMenuHost]).
 */
object EpisodeFilterMenu {
    var open by mutableStateOf(false)
        private set

    /** The filter that is on, which the menu ticks. */
    var selected by mutableStateOf(EpisodeFilter.ALL)
        private set

    private var onSelect: ((EpisodeFilter) -> Unit)? = null

    /** Opens the menu over a page whose filter is [current]; [select] takes the choice. */
    fun show(current: EpisodeFilter, select: (EpisodeFilter) -> Unit) {
        selected = current
        onSelect = select
        open = true
    }

    fun dismiss() {
        open = false
    }

    internal fun choose(filter: EpisodeFilter) {
        selected = filter
        onSelect?.invoke(filter)
        open = false
    }
}

/** The name of [filter], as the button and the menu show it. */
fun episodeFilterLabel(filter: EpisodeFilter): Int = when (filter) {
    EpisodeFilter.ALL -> R.string.episode_filter_all
    EpisodeFilter.UNPLAYED -> R.string.episode_filter_unplayed
    EpisodeFilter.IN_PROGRESS -> R.string.episode_filter_in_progress
    EpisodeFilter.PLAYED -> R.string.episode_filter_played
}

/** [EpisodeFilterMenu], composed with the app's other menus. */
@Composable
fun EpisodeFilterMenuHost(hazeState: HazeState) {
    BackHandler(enabled = EpisodeFilterMenu.open) { EpisodeFilterMenu.dismiss() }
    val shape = MaterialTheme.shapes.extraLarge
    val haptics = rememberHaptics()
    PopupLayer(
        visible = EpisodeFilterMenu.open,
        onDismiss = EpisodeFilterMenu::dismiss,
        shape = shape,
        contentColor = popupContentColor(),
        surface = { Modifier.popupSurface(shape, hazeState) },
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            EpisodeFilter.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Button) {
                            haptics.play(Haptic.Select)
                            EpisodeFilterMenu.choose(option)
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(episodeFilterLabel(option)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(end = 16.dp),
                    )
                    if (option == EpisodeFilterMenu.selected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
