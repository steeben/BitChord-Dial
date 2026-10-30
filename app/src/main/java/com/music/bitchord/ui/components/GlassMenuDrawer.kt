package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.player.AppPlayerGlass
import com.music.bitchord.ui.player.LocalPlayerGlass
import com.music.bitchord.ui.player.PlayerDrawer
import dev.chrisbanes.haze.HazeState

/**
 * A page's long-press menu in the player's drawer: the same liquid glass, the
 * same slide up and away, the same predictive back swipe and the same rim kept
 * off the bottom edge as the Audio output drawer — see [PlayerDrawer].
 *
 * The glass bends the app's pages ([backdrop], null when nothing records them:
 * glass off, or "Reduce dynamic blur" on), and is washed in the theme's own
 * background so the menu's rows keep the theme's colours in light and dark.
 */
@Composable
fun GlassMenuDrawer(
    backdrop: LayerBackdrop?,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = remember(backdrop) { backdrop?.let(::AppPlayerGlass) }
    val ground = MaterialTheme.colorScheme.background
    CompositionLocalProvider(LocalPlayerGlass provides glass) {
        PlayerDrawer(
            hazeState = hazeState,
            title = null,
            onDismiss = onDismiss,
            handlesBack = true,
            glassTint = ground.copy(alpha = 0.6f),
            surfaceColor = ground,
            contentColor = MaterialTheme.colorScheme.onBackground,
            // The menu's rows carry their own inset, edge to edge.
            horizontalPadding = 0.dp,
            content = content,
        )
    }
}
