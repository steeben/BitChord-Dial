package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** One letter of an [AlphabetIndexer] and the first item filed under it. */
private data class IndexSection(val label: String, val firstIndex: Int)

/**
 * The letters [keys] (one per item, in the list's order) file under, with the
 * first item for each — or empty when the list isn't in alphabetical order (a
 * date sort, say), or too short or too narrow in its letters to be worth an index.
 */
private fun indexSections(keys: List<String>): List<IndexSection> {
    if (keys.size < MIN_ITEMS) return emptyList()
    val sections = mutableListOf<IndexSection>()
    keys.forEachIndexed { index, key ->
        val label = sectionLabel(key)
        if (sections.none { it.label == label }) sections += IndexSection(label, index)
    }
    if (sections.size < MIN_SECTIONS) return emptyList()
    // Only an alphabetical list, either way round. "#" (digits, symbols) may sit
    // at either end.
    val letters = sections.map { it.label }.filter { it != "#" }
    val ascending = letters == letters.sorted()
    val descending = letters == letters.sortedDescending()
    return if (ascending || descending) sections else emptyList()
}

private fun sectionLabel(key: String): String {
    val first = indexSortKey(key).firstOrNull() ?: return "#"
    return if (first in 'a'..'z') first.uppercaseChar().toString() else "#"
}

private val DIACRITICS = Regex("\\p{Mn}+")

/**
 * What a library list sorts by so the A–Z index matches it: lower case, any
 * leading punctuation dropped, accents folded to their base letter — so
 * "(What's the Story)" files under W and "Édith" under E, as a phone's
 * contacts do.
 */
fun indexSortKey(title: String): String {
    val start = title.indexOfFirst { it.isLetterOrDigit() }
    val trimmed = if (start < 0) title else title.substring(start)
    return java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase(Locale.ROOT)
}

/**
 * Sorted by the [indexSortKey] of each item's [title], each key worked out once.
 * A plain `sortedBy { indexSortKey(..) }` works it out on every comparison —
 * a normalise and a regex, twice, some hundred thousand times for a large
 * library — on the frame a library page opens, which stalled its slide in.
 */
inline fun <T> Iterable<T>.sortedByIndexKey(
    descending: Boolean = false,
    crossinline title: (T) -> String,
): List<T> {
    val keyed = map { indexSortKey(title(it)) to it }
    val sorted = if (descending) keyed.sortedByDescending { it.first } else keyed.sortedBy { it.first }
    return sorted.map { it.second }
}

/**
 * A strip of letters down the right-hand edge of a library list: touch or drag
 * along it and the list jumps to the first item filed under that letter, with
 * the letter shown large beside the finger.
 *
 * Shows only the letters the list actually has, and nothing at all when the
 * list isn't in alphabetical order. [onJump] is given the item's index in [keys].
 * Placed with padding that keeps it clear of the floating bars and miniplayer.
 */
@Composable
fun AlphabetIndexer(
    keys: List<String>,
    onJump: suspend (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = remember(keys) { indexSections(keys) }
    if (sections.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val latestJump by rememberUpdatedState(onJump)
    var active by remember { mutableIntStateOf(-1) }

    BoxWithConstraints(modifier.fillMaxHeight().width(STRIP_WIDTH + BUBBLE_SIZE + 12.dp)) {
        val rowHeight = min(MAX_ROW, maxHeight / sections.size)
        val stripHeight = rowHeight * sections.size
        val top = (maxHeight - stripHeight) / 2
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(STRIP_WIDTH)
                .height(stripHeight)
                .pointerInput(sections) {
                    val rowPx = rowHeight.toPx()
                    fun pick(y: Float) {
                        val index = (y / rowPx).toInt().coerceIn(0, sections.lastIndex)
                        if (index != active) {
                            active = index
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { latestJump(sections[index].firstIndex) }
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        pick(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            pick(change.position.y)
                        }
                        active = -1
                    }
                },
        ) {
            sections.forEachIndexed { index, section ->
                Box(Modifier.height(rowHeight), contentAlignment = Alignment.Center) {
                    Text(
                        text = section.label,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (index == active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        // The letter under the finger, large, level with it.
        val shown = active
        AnimatedVisibility(
            visible = shown >= 0,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut() + scaleOut(targetScale = 0.7f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    val y = (top + rowHeight * shown.coerceAtLeast(0) + rowHeight / 2 - BUBBLE_SIZE / 2)
                    IntOffset(0, y.roundToPx().coerceAtLeast(0))
                },
        ) {
            Box(
                Modifier
                    .size(BUBBLE_SIZE)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = sections.getOrNull(shown.coerceAtLeast(0))?.label.orEmpty(),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private val STRIP_WIDTH = 22.dp
private val MAX_ROW = 18.dp
private val BUBBLE_SIZE = 52.dp
private const val MIN_ITEMS = 20
private const val MIN_SECTIONS = 4
