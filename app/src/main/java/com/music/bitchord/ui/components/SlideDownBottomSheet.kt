package com.music.bitchord.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch

/**
 * A [ModalBottomSheet] whose predictive back slides it down rather than
 * shrinking it.
 *
 * M3's own predictive back scales the whole sheet towards the middle of the
 * screen as the back swipe goes, which reads as the menu being pushed away
 * rather than put away — not how anything else in the app leaves. Here M3's
 * back is turned off and the sheet answers the swipe itself: it moves down by
 * as much of its visible height as the swipe has progressed, letting go
 * finishes the slide off the bottom, and a cancelled swipe springs it home.
 *
 * Takes only the parameters the app's sheets pass; everything else is M3's
 * default. The player sheet is not one of these: it runs its own motion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideDownBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismissRequest)
    // How far down the back swipe has moved the sheet, in pixels.
    val backOffset = remember { Animatable(0f) }
    // The window the sheet is laid out in, top to bottom, so the part of the
    // sheet actually on screen can be told from the part below the fold.
    var windowHeight by remember { mutableIntStateOf(0) }
    var sheetHeight by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .onGloballyPositioned {
                sheetHeight = it.size.height
                windowHeight = it.findRootCoordinates().size.height
            }
            .graphicsLayer { translationY = backOffset.value },
        sheetState = sheetState,
        containerColor = containerColor,
        dragHandle = dragHandle,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        // With M3's back off, the dialog's dispatcher ends here.
        PredictiveBackHandler(enabled = sheetState.isVisible) { events ->
            // Only what is on screen: a half-open sheet is laid out at full
            // height with the rest of it below the window's edge.
            val top = runCatching { sheetState.requireOffset() }.getOrNull()
            val travel = if (top != null && windowHeight > 0) {
                (windowHeight - top).coerceIn(0f, sheetHeight.toFloat().coerceAtLeast(1f))
            } else {
                sheetHeight.toFloat()
            }
            try {
                events.collect { event -> backOffset.snapTo(travel * event.progress) }
                // Carried on from where the swipe left it, as a drag would be.
                scope.launch {
                    try {
                        sheetState.hide()
                    } finally {
                        dismiss()
                        backOffset.snapTo(0f)
                    }
                }
            } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                scope.launch { backOffset.animateTo(0f, spring()) }
                throw cancelled
            }
        }
        content()
    }
}
