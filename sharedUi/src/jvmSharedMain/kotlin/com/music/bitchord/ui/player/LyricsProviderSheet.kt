package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.ui.LyricsProviderState
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import dev.chrisbanes.haze.HazeState

/**
 * Manual provider chooser for the lyrics half-player.
 *
 * It deliberately uses [PlayerDrawer], [ROW_SHAPE], and the same row geometry
 * as [AudioOutputSheet], so it behaves like another player destination rather
 * than a settings dialog. A row that the automatic lookup already completed is
 * entirely local: hits switch immediately and misses cannot be requested twice.
 *
 * A pick holds for the playing song only; the host clears it on the next
 * track. When [onSelectAutomatic] is given, an Automatic row heads the list to
 * forget it sooner.
 *
 * Picking does not close the drawer: it stays up so the rows can be compared,
 * reordered by their handles, or the track marked instrumental.
 */
@Composable
internal fun LyricsProviderSheet(
    hazeState: HazeState,
    currentSource: LyricsSource?,
    states: Map<LyricsSource, LyricsProviderState>,
    onSelect: (LyricsSource) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectAutomatic: (() -> Unit)? = null,
    preferWordSynced: Boolean = false,
    /**
     * Shown under the Automatic row when given: whether Automatic holds out
     * for the best-timed answer anywhere in the order — per word, then per
     * line, then untimed — rather than taking the first timed one.
     */
    onPreferWordSyncedChange: ((Boolean) -> Unit)? = null,
    ignoreShort: Boolean = false,
    /** Shown under the Automatic row when given: pass over answers of two lines or fewer. */
    onIgnoreShortChange: ((Boolean) -> Unit)? = null,
    /** Providers whose answer for this track carries per-word timing; they get the crown. */
    wordSynced: Set<LyricsSource> = emptySet(),
    instrumental: Boolean = false,
    /** Shown as the last row when given: marks the track as having no lyrics to show. */
    onToggleInstrumental: (() -> Unit)? = null,
) {
    val savedOrder by PlayerSettings.lyricsSourceOrder.collectAsStateWithLifecycle()
    val preferredSource by PlayerSettings.preferredLyricsSource.collectAsStateWithLifecycle()
    val sources = remember(savedOrder) { LyricsSource.ordered(savedOrder) }
    val scrollState = rememberScrollState()

    PlayerDrawer(
        hazeState = hazeState,
        title = stringResource(Res.string.choose_lyrics_provider),
        onDismiss = onDismiss,
        modifier = modifier,
        scrollState = scrollState,
        handlesBack = true,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (onSelectAutomatic != null) {
                AutomaticProviderRow(
                    current = preferredSource == null,
                    onClick = onSelectAutomatic,
                )
                if (onPreferWordSyncedChange != null) {
                    SwitchRow(
                        title = stringResource(Res.string.lyrics_prefer_word_synced),
                        detail = stringResource(Res.string.lyrics_prefer_word_synced_detail),
                        checked = preferWordSynced,
                        onToggle = { onPreferWordSyncedChange(!preferWordSynced) },
                    )
                }
                if (onIgnoreShortChange != null) {
                    SwitchRow(
                        title = stringResource(Res.string.lyrics_ignore_short),
                        detail = stringResource(Res.string.lyrics_ignore_short_detail),
                        checked = ignoreShort,
                        onToggle = { onIgnoreShortChange(!ignoreShort) },
                    )
                }
            }
            ReorderableProviderList(
                order = sources,
                scrollState = scrollState,
                onReorder = { PlayerSettings.setLyricsSourceOrder(it) },
            ) { source, handle ->
                LyricsProviderRow(
                    source = source,
                    state = states[source] ?: LyricsProviderState.NOT_FETCHED,
                    current = source == currentSource,
                    wordSynced = source in wordSynced,
                    handle = handle,
                    onClick = { onSelect(source) },
                )
            }
            if (onToggleInstrumental != null) {
                InstrumentalRow(checked = instrumental, onToggle = onToggleInstrumental)
            }
        }
    }
}

/**
 * The provider rows, each draggable by the handle [content] is given, the same
 * way the Settings list reorders: [totalDrag] and [startIndex] are the only
 * state, the slot and the drawn offset are both derived from them, and the
 * finished order is written once, when the finger lifts.
 *
 * Held near the top or bottom of the drawer, the drawer's own [scrollState]
 * scrolls, and each scrolled pixel is fed back into the drag so the row stays
 * under the finger.
 */
@Composable
private fun ReorderableProviderList(
    order: List<LyricsSource>,
    scrollState: androidx.compose.foundation.ScrollState,
    onReorder: (List<LyricsSource>) -> Unit,
    content: @Composable (source: LyricsSource, handle: Modifier) -> Unit,
) {
    var liveOrder by remember(order) { mutableStateOf(order) }
    var draggedSource by remember { mutableStateOf<LyricsSource?>(null) }
    var totalDrag by remember { mutableFloatStateOf(0f) }
    var startIndex by remember { mutableIntStateOf(0) }
    var startWindowTop by remember { mutableFloatStateOf(0f) }
    var startScroll by remember { mutableIntStateOf(0) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    var lockedPitchPx by remember { mutableFloatStateOf(0f) }
    var listCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var autoScrollSpeed by remember { mutableFloatStateOf(0f) }
    var autoScrollDir by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val gapPx = with(density) { PROVIDER_ROW_GAP.toPx() }
    val edgeZonePx = with(density) { PROVIDER_EDGE_SCROLL_ZONE.toPx() }
    val edgeSpeedPx = with(density) { PROVIDER_EDGE_SCROLL_SPEED.toPx() }

    fun setAutoScroll(speed: Float) {
        autoScrollSpeed = speed
        val dir = when {
            speed > 0f -> 1
            speed < 0f -> -1
            else -> 0
        }
        if (autoScrollDir != dir) autoScrollDir = dir
    }

    fun advanceDrag(delta: Float) {
        val source = draggedSource ?: return
        val pitch = lockedPitchPx
        if (pitch <= 0f) return
        var index = liveOrder.indexOf(source)
        if (index < 0) return
        totalDrag = (totalDrag + delta).coerceIn(
            -startIndex * pitch,
            (liveOrder.lastIndex - startIndex) * pitch,
        )
        while (true) {
            val travelled = totalDrag / pitch
            val moved = (index - startIndex).toFloat()
            if (travelled > moved + PROVIDER_SWAP_THRESHOLD && index < liveOrder.lastIndex) {
                liveOrder = liveOrder.toMutableList().apply { add(index + 1, removeAt(index)) }
                index++
            } else if (travelled < moved - PROVIDER_SWAP_THRESHOLD && index > 0) {
                liveOrder = liveOrder.toMutableList().apply { add(index - 1, removeAt(index)) }
                index--
            } else {
                break
            }
        }
        // Where the held row is on screen now, against the part of the list the
        // drawer is actually showing.
        val visible = listCoords?.takeIf { it.isAttached }?.boundsInWindow()
        if (visible == null) {
            setAutoScroll(0f)
            return
        }
        val top = startWindowTop + totalDrag - (scrollState.value - startScroll)
        val speed = edgeScrollSpeed(
            top = top,
            bottom = top + rowHeightPx,
            viewportStart = visible.top.toInt(),
            viewportEnd = visible.bottom.toInt(),
            zone = edgeZonePx,
            speed = edgeSpeedPx,
        )
        val blocked = when {
            speed < 0f -> index <= 0 || !scrollState.canScrollBackward
            speed > 0f -> index >= liveOrder.lastIndex || !scrollState.canScrollForward
            else -> true
        }
        setAutoScroll(if (blocked) 0f else speed)
    }

    LaunchedEffect(autoScrollDir) {
        if (autoScrollDir == 0) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(1f / 30f)
            previous = now
            val scrolled = scrollState.scrollBy(autoScrollSpeed * seconds)
            if (scrolled == 0f) break
            advanceDrag(scrolled)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { listCoords = it },
        verticalArrangement = Arrangement.spacedBy(PROVIDER_ROW_GAP),
    ) {
        liveOrder.forEach { source ->
            // Keyed so the row, and the drag running in its handle, follows its
            // source from slot to slot instead of being rebuilt in place.
            key(source) {
                val dragging = source == draggedSource
                var rowCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                val handle = Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            startIndex = liveOrder.indexOf(source)
                            startWindowTop = rowCoords?.takeIf { it.isAttached }
                                ?.positionInWindow()?.y ?: 0f
                            startScroll = scrollState.value
                            lockedPitchPx = rowHeightPx + gapPx
                            totalDrag = 0f
                            draggedSource = source
                            setAutoScroll(0f)
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            advanceDrag(delta.y)
                        },
                        onDragEnd = {
                            draggedSource = null
                            totalDrag = 0f
                            setAutoScroll(0f)
                            onReorder(liveOrder)
                        },
                        onDragCancel = {
                            draggedSource = null
                            totalDrag = 0f
                            setAutoScroll(0f)
                            liveOrder = order
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .onSizeChanged { rowHeightPx = it.height.toFloat() }
                        .onGloballyPositioned { rowCoords = it }
                        .graphicsLayer {
                            translationY = if (dragging) {
                                totalDrag - (liveOrder.indexOf(source) - startIndex) * lockedPitchPx
                            } else {
                                0f
                            }
                            if (dragging) {
                                scaleX = 1.02f
                                scaleY = 1.02f
                            }
                        },
                ) {
                    content(source, handle)
                }
            }
        }
    }
}

@Composable
private fun InstrumentalRow(
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(Color.White.copy(alpha = if (checked) 0.10f else 0.05f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(if (checked) Haptic.ToggleOff else Haptic.ToggleOn)
                onToggle()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.lyrics_mark_instrumental),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.lyrics_mark_instrumental_detail),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.play(if (checked) Haptic.ToggleOff else Haptic.ToggleOn)
                onToggle()
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable
private fun AutomaticProviderRow(
    current: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(Color.White.copy(alpha = if (current) 0.10f else 0.05f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !current,
            ) {
                haptics.play(Haptic.Select)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (current) 0.16f else 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (current) Icons.Rounded.Check else Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (current) 1f else 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.lyrics_provider_automatic),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = Color.White.copy(alpha = if (current) 1f else 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.lyrics_provider_automatic_detail),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = if (current) 0.7f else 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(Haptic.Select)
                onToggle()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable
private fun LyricsProviderRow(
    source: LyricsSource,
    state: LyricsProviderState,
    current: Boolean,
    wordSynced: Boolean,
    handle: Modifier,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    val short = !current && state == LyricsProviderState.TOO_SHORT
    val found = current || state == LyricsProviderState.FOUND
    val missing = !found && state == LyricsProviderState.NOT_FOUND
    // A running automatic request can still be selected: it is marked as the
    // requested source and applied when that same in-flight call completes.
    val enabled = !current && state != LyricsProviderState.NOT_FOUND
    val status = when {
        current -> stringResource(Res.string.lyrics_provider_current)
        state == LyricsProviderState.FOUND -> stringResource(Res.string.lyrics_provider_found)
        state == LyricsProviderState.NOT_FOUND -> stringResource(Res.string.lyrics_provider_not_found)
        state == LyricsProviderState.TOO_SHORT -> stringResource(Res.string.lyrics_provider_too_short)
        state == LyricsProviderState.FETCHING -> stringResource(Res.string.lyrics_provider_fetching)
        else -> stringResource(Res.string.lyrics_provider_not_fetched)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(Color.White.copy(alpha = if (current) 0.10f else 0.05f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) {
                haptics.play(Haptic.Select)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    when {
                        found -> FOUND_GREEN.copy(alpha = if (current) 0.32f else 0.22f)
                        missing -> MISSING_RED.copy(alpha = 0.20f)
                        short -> SHORT_ORANGE.copy(alpha = 0.22f)
                        else -> Color.White.copy(alpha = 0.08f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state == LyricsProviderState.FETCHING -> CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.8f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
                found && wordSynced -> Icon(
                    imageVector = CrownIcon,
                    contentDescription = null,
                    tint = CROWN_GOLD,
                    modifier = Modifier.size(21.dp),
                )
                found -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = FOUND_GREEN,
                    modifier = Modifier.size(21.dp),
                )
                short -> Icon(
                    imageVector = Icons.Rounded.PriorityHigh,
                    contentDescription = null,
                    tint = SHORT_ORANGE,
                    modifier = Modifier.size(20.dp),
                )
                missing -> Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = MISSING_RED,
                    modifier = Modifier.size(20.dp),
                )
                else -> Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = source.label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = Color.White.copy(alpha = if (current) 1f else 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = if (current) 0.7f else 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        // Larger than the glyph so a thumb finds it; the drag lives only here,
        // so the rest of the row still taps to pick.
        Box(
            modifier = handle.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = stringResource(Res.string.lyrics_provider_reorder),
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val FOUND_GREEN = Color(0xFF30D158)
private val MISSING_RED = Color(0xFFFF453A)
private val CROWN_GOLD = Color(0xFFFFD60A)
private val SHORT_ORANGE = Color(0xFFFF9F0A)

private val PROVIDER_ROW_GAP = 6.dp
private const val PROVIDER_SWAP_THRESHOLD = 0.6f
private val PROVIDER_EDGE_SCROLL_ZONE = 40.dp
private val PROVIDER_EDGE_SCROLL_SPEED = 260.dp

/** Marks a provider whose lyrics are timed word by word. */
private val CrownIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "bc_crown", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 7.5f)
            lineTo(7.5f, 11.5f)
            lineTo(12f, 4.5f)
            lineTo(16.5f, 11.5f)
            lineTo(21f, 7.5f)
            lineTo(19.2f, 17f)
            lineTo(4.8f, 17f)
            close()
            moveTo(5f, 18.5f)
            lineTo(19f, 18.5f)
            lineTo(19f, 20.5f)
            lineTo(5f, 20.5f)
            close()
        }
    }.build()
}
