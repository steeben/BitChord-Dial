package com.music.bitchord.ui.screens

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.music.bitchord.ui.components.IosSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.music.bitchord.R
import com.music.bitchord.data.settings.NavPage
import com.music.bitchord.data.settings.OrderedToggle

@Composable
internal fun NavPage.label(): String = stringResource(
    when (this) {
        NavPage.HOME -> R.string.home
        NavPage.EXPLORE -> R.string.explore
        NavPage.LIBRARY -> R.string.library
        NavPage.SEARCH -> R.string.search
        NavPage.PLAYLISTS -> R.string.playlists
    },
)

/**
 * A dialog holding a [ReorderableToggleList], for settings that order and
 * switch off a short list of things. Every change is handed to [onChange] as it
 * happens, so there is nothing to save on the way out.
 */
@Composable
internal fun <T : Any> ReorderDialog(
    title: String,
    note: String?,
    items: List<OrderedToggle<T>>,
    label: @Composable (T) -> String,
    onChange: (List<OrderedToggle<T>>) -> Unit,
    onDismiss: () -> Unit,
    /** Whether [item] may be switched off, given the list as it stands. */
    canDisable: (List<OrderedToggle<T>>, T) -> Boolean = { _, _ -> true },
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                ReorderableToggleList(items, label, onChange, canDisable)
            }
        },
    )
}

/** Rows that can be dragged by their handle into a new order, each with a switch. */
@Composable
internal fun <T : Any> ReorderableToggleList(
    items: List<OrderedToggle<T>>,
    label: @Composable (T) -> String,
    onChange: (List<OrderedToggle<T>>) -> Unit,
    canDisable: (List<OrderedToggle<T>>, T) -> Boolean = { _, _ -> true },
) {
    var working by remember(items) { mutableStateOf(items) }
    var dragging by remember { mutableStateOf<T?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { REORDER_ROW_HEIGHT.toPx() }
    val haptics = LocalHapticFeedback.current
    val currentOnChange by rememberUpdatedState(onChange)

    Column(Modifier.fillMaxWidth()) {
        working.forEach { entry ->
            key(entry.item) {
                val isDragging = dragging == entry.item
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(REORDER_ROW_HEIGHT)
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else 0f
                            val lift = if (isDragging) 1.03f else 1f
                            scaleX = lift
                            scaleY = lift
                        },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = stringResource(R.string.drag_to_reorder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp)
                            .pointerInput(entry.item) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = entry.item
                                        dragOffset = 0f
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val list = working
                                        val from = list.indexOfFirst { it.item == entry.item }
                                        val to = when {
                                            dragOffset > rowHeightPx / 2 && from < list.lastIndex -> from + 1
                                            dragOffset < -rowHeightPx / 2 && from > 0 -> from - 1
                                            else -> from
                                        }
                                        if (to != from) {
                                            working = list.toMutableList().apply { add(to, removeAt(from)) }
                                            dragOffset += if (to > from) -rowHeightPx else rowHeightPx
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onDragEnd = {
                                        dragging = null
                                        dragOffset = 0f
                                        currentOnChange(working)
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        dragOffset = 0f
                                        currentOnChange(working)
                                    },
                                )
                            },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label(entry.item),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    val allowed = !entry.enabled || canDisable(working, entry.item)
                    IosSwitch(
                        checked = entry.enabled,
                        enabled = allowed,
                        onCheckedChange = { on ->
                            working = working.map { if (it.item == entry.item) it.copy(enabled = on) else it }
                            currentOnChange(working)
                        },
                    )
                }
            }
        }
    }
}

private val REORDER_ROW_HEIGHT = 56.dp
