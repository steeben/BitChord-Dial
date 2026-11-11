package com.music.bitchord.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
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
 *
 * While Liquid Glass is on the sheet is made of it, like the app's menus,
 * rather than a slab of [containerColor]: it bends and frosts the page behind
 * it, as every menu does (see [glassMenu]). [containerColor] is the fill again with glass off, unsupported
 * or under "reduce dynamic blur". The glass samples the app's pages, so a sheet
 * that opens over something that is not one of them (the player, the Replay
 * story) passes [glass] false and keeps its solid fill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideDownBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    glass: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val useGlass = glass && LocalLiquidGlassEnabled.current && isGlassSupported() && !reduceDynamicBlur
    val scrim = BottomSheetDefaults.ScrimColor
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
        shape = SheetShape,
        containerColor = if (useGlass) Color.Transparent else containerColor,
        scrimColor = scrim,
        // On glass the handle and the insets move inside the glass, so the
        // glass runs the sheet's whole height, under the navigation bar too.
        dragHandle = if (useGlass) null else dragHandle,
        contentWindowInsets = if (useGlass) {
            { WindowInsets(0, 0, 0, 0) }
        } else {
            { BottomSheetDefaults.windowInsets }
        },
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
        if (useGlass) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // Over the sheet's scrim, which the backdrop doesn't
                    // record, and frosted at least enough that the page's
                    // text doesn't come through sharp behind the rows.
                    .glassMenu(SheetShape, underlay = scrim, minBlurDp = SHEET_MIN_BLUR_DP)
                    .windowInsetsPadding(BottomSheetDefaults.windowInsets),
            ) {
                if (dragHandle != null) {
                    Box(Modifier.align(Alignment.CenterHorizontally)) { dragHandle() }
                }
                content()
            }
        } else {
            content()
        }
    }
}

/** M3's own sheet shape, as a [RoundedCornerShape]: the glass's lens needs a corner-based one. */
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** The least frost a glass sheet gives the page behind it, in dp. */
private const val SHEET_MIN_BLUR_DP = 16f
