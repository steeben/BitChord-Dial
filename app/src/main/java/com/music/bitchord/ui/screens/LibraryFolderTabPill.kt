package com.music.bitchord.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.LocalLiquidGlassEnabled
import com.music.bitchord.ui.components.artworkPageSurface
import com.music.bitchord.ui.components.glassContentColor
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.components.pageAccent
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import dev.chrisbanes.haze.HazeState

/** How much wider the picked segment grows than the others. */
private const val SELECTED_WEIGHT = 2.4f

/** About a 1% overshoot: a hint of bounce, not a stretch. Was 0.72 (about 4%). */
private const val WEIGHT_DAMPING = 0.82f
private const val WEIGHT_STIFFNESS = 480f

private class PillTab(val index: Int, val icon: ImageVector, val label: String)

/**
 * A library folder's Songs / Artists / Albums switch, as one glass pill of
 * icons up in the top bar beside the back button — where the folder's search
 * field used to sit.
 *
 * The picked tab's segment swells and the others give it the room, on a
 * spring, so a tap reads as the selection sliding over, and the picked tab
 * spells out its name beside its icon. Tapping the tab that is already picked
 * opens its options ([onReopenTab]): list or grid, and the sort order.
 * Playlists and Folders (the whole-device page's extra two) only ever list,
 * so a second tap on them does nothing.
 *
 * Drawn in the top bar, which sits outside the page's recorded backdrop, so
 * the glass bends the list scrolling under it rather than itself.
 */
@Composable
fun LibraryFolderTabPill(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    /** A tap on the tab that is already picked: open its view and sort options. */
    onReopenTab: (Int) -> Unit,
    /** The whole-device page's Playlists and Folders tabs as well. */
    deviceTabs: Boolean,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val useLiquidGlass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val idleColor = if (useLiquidGlass && !reduceDynamicBlur) {
        glassContentColor()
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val accent = pageAccent()

    val songs = stringResource(R.string.songs)
    val artists = stringResource(R.string.artists)
    val albums = stringResource(R.string.albums)
    val playlists = stringResource(R.string.playlists)
    val folders = stringResource(R.string.folders)
    val tabs = remember(deviceTabs, songs, artists, albums, playlists, folders) {
        buildList {
            add(PillTab(LOCAL_TAB_SONGS, Icons.Rounded.MusicNote, songs))
            add(PillTab(LOCAL_TAB_ARTISTS, Icons.Rounded.Person, artists))
            add(PillTab(LOCAL_TAB_ALBUMS, Icons.Rounded.Album, albums))
            if (deviceTabs) {
                add(PillTab(LOCAL_TAB_PLAYLISTS, Icons.AutoMirrored.Rounded.QueueMusic, playlists))
                add(PillTab(LOCAL_TAB_FOLDERS, Icons.Rounded.Folder, folders))
            }
        }
    }
    val shown = selectedTab.coerceIn(0, tabs.lastIndex)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .then(artworkPageSurface(shape = CircleShape, hazeState = hazeState))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = tab.index == shown
            val hasOptions = tab.index <= LOCAL_TAB_ALBUMS
            // A soft give at the end, so the segment settles into its width
            // rather than stopping dead at it. Kept well damped: every segment
            // springs at once, so a looser spring makes the whole pill wobble.
            val weight by animateFloatAsState(
                targetValue = if (selected) SELECTED_WEIGHT else 1f,
                animationSpec = spring(
                    dampingRatio = WEIGHT_DAMPING,
                    stiffness = WEIGHT_STIFFNESS,
                ),
                label = "libraryPillWeight",
            )
            val fill by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = tween(220),
                label = "libraryPillFill",
            )
            val tint by animateColorAsState(
                targetValue = if (selected) accent else idleColor,
                animationSpec = tween(220),
                label = "libraryPillTint",
            )
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .drawBehind {
                        if (fill > 0f) {
                            drawRoundRect(
                                color = accent.copy(alpha = 0.2f * fill),
                                cornerRadius = CornerRadius(size.height / 2f),
                            )
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                    ) {
                        when {
                            !selected -> {
                                haptics.play(Haptic.Select)
                                onSelectTab(tab.index)
                            }
                            hasOptions -> {
                                haptics.play(Haptic.Select)
                                onReopenTab(tab.index)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = if (selected) null else tab.label,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                    AnimatedVisibility(
                        visible = selected,
                        enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(tween(180, delayMillis = 60)),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(tween(120)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = tab.label,
                                color = tint,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
