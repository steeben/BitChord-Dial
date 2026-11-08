package com.music.bitchord.ui.screens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.ui.components.SearchField
import kotlin.math.roundToInt

/**
 * What Settings' one search field needs to know to sit in the page at rest
 * and dock beside the back button as the page scrolls.
 *
 * The field itself lives in the top bar, so there is only ever one of it.
 * The page keeps an empty space of the field's size under its title and
 * reports where that space is; the bar reports where the field docks.
 */
@Stable
class SettingsSearchDockState {
    /** The page's space for the field, in root coordinates, as at scroll 0. */
    var restBounds by mutableStateOf<Rect?>(null)

    /** Where the field docks in the bar, in root coordinates. */
    var slotBounds by mutableStateOf<Rect?>(null)

    /** How far the page has scrolled. Read only while laying out, so a scroll relays out the field without recomposing anything. */
    var scroll: () -> Float by mutableStateOf({ 0f })

    /** The scroll at which the field reaches the bar's row, or null before both have been laid out. */
    val dockScroll: Float?
        get() {
            val rest = restBounds ?: return null
            val slot = slotBounds ?: return null
            return (rest.center.y - slot.center.y).coerceAtLeast(0f)
        }
}

/**
 * Settings' search field, composed in the bar's slot but drawn wherever the
 * page's space for it is. It rises with the page; as it does, only its left
 * edge draws in, in step with the scroll, so it is exactly the slot's width
 * beside the back button when it reaches the bar's row, and it stays there.
 * One field the whole way, laid out rather than scaled so the text stays sharp.
 *
 * [surface] is the field's glass. It is applied inside the resize, so the
 * glass follows the field's width.
 *
 * [shown] scales the field about its own centre: 1 in place, 0 gone. It is
 * how the field shrinks away as a Settings page slides in over the main page,
 * and grows back on the way out, rather than blinking out of the bar.
 */
@Composable
fun SettingsDockingSearchField(
    state: SettingsSearchDockState,
    query: String,
    onQueryChange: (String) -> Unit,
    surface: Modifier,
    modifier: Modifier = Modifier,
    shown: () -> Float = { 1f },
) {
    SearchField(
        query = query,
        onQueryChange = onQueryChange,
        onSubmit = {},
        placeholder = stringResource(R.string.settings_search_hint),
        containerColor = Color.Transparent,
        shape = CircleShape,
        height = 44.dp,
        modifier = modifier
            .layout { measurable, constraints ->
                val slotWidth = constraints.maxWidth
                val rest = state.restBounds
                val slot = state.slotBounds
                val dockScroll = state.dockScroll
                if (rest == null || slot == null || dockScroll == null) {
                    // Nothing to place it against yet: keep the slot's room, show nothing.
                    val placeable = measurable.measure(constraints)
                    return@layout layout(slotWidth, placeable.height) {}
                }
                val scroll = state.scroll()
                val progress = settingsSearchDockProgress(scroll, dockScroll)
                val left = settingsSearchDockEdge(rest.left, slot.left, progress)
                val right = settingsSearchDockEdge(rest.right, slot.right, progress)
                val width = (right - left).roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = width,
                        maxWidth = width,
                        minHeight = constraints.minHeight,
                        maxHeight = constraints.maxHeight,
                    ),
                )
                // Below the bar's row by however far the page still has to
                // scroll to bring it up; pinned there once it arrives.
                val drop = (dockScroll - scroll).coerceAtLeast(0f)
                layout(slotWidth, placeable.height) {
                    placeable.place((left - slot.left).roundToInt(), drop.roundToInt())
                }
            }
            // After the layout, so the layer is the field itself and it
            // shrinks toward its own middle wherever it is standing.
            .graphicsLayer {
                val s = shown().coerceIn(0f, 1f)
                scaleX = s
                scaleY = s
                alpha = s
            }
            .then(surface),
    )
}
