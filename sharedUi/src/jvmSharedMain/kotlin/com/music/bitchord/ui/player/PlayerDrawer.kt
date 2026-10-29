package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.ui.components.optimizedHazeEffect
import com.music.bitchord.ui.utils.containSheetGestures
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val DRAWER_SHAPE = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)

/**
 * The widest the drawer itself gets, however wide the window behind it is.
 *
 * Full-bleed it is fine on a phone and wrong on a tablet: the rows inside are a
 * label and a radio mark, so stretched across a landscape window each one is a
 * word at the far left, a mark at the far right and a hand's width of nothing
 * between them. Matches Material's own [BottomSheetDefaults.SheetMaxWidth], so
 * this drawer and the sheets the rest of the app puts up at the M3 default
 * settle at the same width instead of at two.
 *
 * No effect on a phone, which is narrower than this everywhere it runs.
 */
internal val DRAWER_MAX_WIDTH = 640.dp
internal val ROW_SHAPE = RoundedCornerShape(16.dp)
internal val SCRIM_COLOR = Color.Black.copy(alpha = 0.5f)

/**
 * How much of its own height the drawer has to be dragged before letting go
 * dismisses it rather than springing back. A quarter is enough to be a decision
 * and little enough that a flick reads as one.
 */
private const val DISMISS_DRAG_FRACTION = 0.25f

/**
 * How much of its own height the drawer follows a predictive back swipe down
 * by, at the swipe's full length. Enough to read as the drawer leaving; the
 * rest of the way is the exit once the swipe commits.
 */
private const val BACK_SWIPE_TRAVEL = 0.4f

/** The wash over the drawer's liquid glass: dark enough for its white rows on any artwork. */
private val DRAWER_GLASS_TINT = Color(0xFF121212).copy(alpha = 0.6f)

private const val DRAWER_EXIT_MS = 200

/** How far the drawer's glass runs on below its bottom edge, to keep its rim off screen. */
private val GLASS_OVERHANG = 64.dp

/**
 * The bottom-edge drawer shell every player sheet in this package puts up —
 * [AudioOutputSheet] and [ListenTogetherMembersSheet] alike: dark over a
 * scrim, a grab handle, a title, drag down to put it away. Pulled out because
 * the drag gesture, the scrim fade tied to it, and the haze background are
 * identical between the two and worth keeping in exactly one place.
 *
 * Public because a platform's own player sheet draws in it too: the phone's
 * lyrics card goes up in this same shell rather than in a second one that would
 * have to re-learn the drag gesture, the scrim fade and the frosted material.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun PlayerDrawer(
    hazeState: HazeState,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Air between the title and whatever the drawer opens with. */
    titleGap: Dp = 14.dp,
    /** The drawer's own scroll, for content that has to drive it (a drag near its edge). */
    scrollState: ScrollState = rememberScrollState(),
    /**
     * Whether back puts the drawer away itself, following a predictive back
     * swipe down. False where the caller handles back, or while something the
     * drawer opened is up over it and should take back first.
     */
    handlesBack: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceDynamicBlur by PlayerSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glass = LocalPlayerGlass.current
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    // Up on the first composition so the drawer travels up from the edge
    // instead of appearing over the player fully formed, and down again before
    // [onDismiss] takes it away, so it slides off rather than vanishing.
    val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
    val closing = !visibility.targetState
    val close: () -> Unit = { visibility.targetState = false }
    LaunchedEffect(visibility.isIdle) {
        if (visibility.isIdle && !visibility.currentState) currentOnDismiss()
    }

    // How far through a predictive back swipe the listener is, 0..1. The
    // drawer sinks with it, and comes back up if the swipe is let go.
    val backSwipe = remember { Animatable(0f) }
    PlayerPredictiveBackHandler(
        enabled = handlesBack && !closing,
        onProgress = { progress -> scope.launch { backSwipe.snapTo(progress) } },
        onCancel = { scope.launch { backSwipe.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) } },
        onBack = close,
    )
    val scrimFade by animateFloatAsState(
        targetValue = if (closing) 0f else 1f,
        animationSpec = tween(DRAWER_EXIT_MS),
        label = "playerDrawerScrim",
    )

    // How far the drawer has been dragged down, in pixels. Released, it either
    // springs back or goes — see [DISMISS_DRAG_FRACTION].
    var drag by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    val offset by animateFloatAsState(
        targetValue = drag,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "playerDrawerOffset",
    )
    // Fades with the drawer rather than staying at full strength under a sheet
    // halfway off the screen, which is what makes the drag feel connected.
    val backOffset = backSwipe.value * height * BACK_SWIPE_TRAVEL
    val scrimAlpha = scrimFade * if (height > 0) (1f - (offset + backOffset) / height).coerceIn(0f, 1f) else 1f

    // The drawer's rows scroll, and that scroll takes the finger before the
    // drag detector below ever sees it. So a pull down past the top of the
    // list moves the drawer itself — the way a sheet with a list in it
    // behaves — and pushing back up returns it before the list scrolls again.
    // Everything left over is kept here rather than handed on to the player's
    // sheet, which would otherwise be dragged down along with the drawer.
    //
    // Only where scrolling is dragging, though: see [drawerFollowsListScroll].
    // Where it is not, the connection is left off entirely and the drawer
    // answers to its own drag alone, because a scroll leftover means something
    // else there and must not be read as a pull.
    val dismissOnRelease: () -> Unit = {
        if (height > 0 && drag > height * DISMISS_DRAG_FRACTION) close() else drag = 0f
    }
    val drawerScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (drag <= 0f || available.y >= 0f) return Offset.Zero
                val used = available.y.coerceAtLeast(-drag)
                drag += used
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) drag += available.y
                // Upward leftover is taken only as far as the drawer has already
                // been dragged. Returning all of it would hand the player sheet
                // — the very thing this connection is here to keep clear — the
                // gesture meant to undo the drag, and the drawer would have no
                // way back to where it started, because a list resting on its
                // top consumes nothing and so never produces a pre-scroll to
                // give back.
                if (drag > 0f && available.y < 0f) {
                    val used = available.y.coerceAtLeast(-drag)
                    drag += used
                    return Offset(0f, used)
                }
                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag <= 0f) return Velocity.Zero
                dismissOnRelease()
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .containSheetGestures()
            .background(SCRIM_COLOR.copy(alpha = SCRIM_COLOR.alpha * scrimAlpha))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = close,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
            exit = slideOutVertically(tween(DRAWER_EXIT_MS, easing = FastOutSlowInEasing)) { it },
        ) {
        Box(
            modifier = Modifier
                // Capped so a phone full of rows scrolls inside the drawer
                // rather than growing one into a full-screen page.
                .heightIn(max = 560.dp)
                // Capped before the fill, so [Modifier.fillMaxWidth] fills to
                // the cap rather than to the window. Centred by the parent's
                // own BottomCenter alignment once it is narrower.
                .widthIn(max = DRAWER_MAX_WIDTH)
                .fillMaxWidth()
                .onSizeChanged { height = it.height }
                .offset { IntOffset(0, (offset + backSwipe.value * height * BACK_SWIPE_TRAVEL).roundToInt()) },
        ) {
        // The player's liquid glass, as its buttons wear, when the app has it
        // on — washed darker than theirs, because this is a panel of white
        // text rather than a glyph on a disc. Drawn behind the rows rather than
        // on them, and run on past the bottom of the screen, so the glass rim
        // shows along the top and down the sides but never across the bottom
        // edge the drawer rests on.
        if (glass != null && !reduceDynamicBlur) {
            Box(
                Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val overhang = GLASS_OVERHANG.roundToPx()
                        val placeable = measurable.measure(
                            constraints.copy(
                                minHeight = constraints.minHeight + overhang,
                                maxHeight = constraints.maxHeight + overhang,
                            ),
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                    }
                    .then(glass.surface(Modifier, DRAWER_SHAPE, tint = DRAWER_GLASS_TINT)),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DRAWER_SHAPE)
                .then(
                    when {
                        reduceDynamicBlur -> Modifier.background(Color(0xFF121212))
                        glass != null -> Modifier
                        else -> Modifier
                            .optimizedHazeEffect(
                                state = hazeState,
                                style = HazeMaterials.regular(Color(0xFF141414)),
                            )
                            .background(Color(0xFF121212).copy(alpha = 0.9f))
                    }
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                )
                // Dragged down to dismiss, like every other sheet in the app.
                // Upward drag is clamped to zero rather than followed: there is
                // nothing above the drawer to reveal.
                .then(if (drawerFollowsListScroll) Modifier.nestedScroll(drawerScroll) else Modifier)
                .pointerInput(height) {
                    detectVerticalDragGestures(
                        onDragEnd = dismissOnRelease,
                        onDragCancel = { drag = 0f },
                    ) { _, delta ->
                        drag = (drag + delta).coerceAtLeast(0f)
                    }
                }
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The grab handle every sheet here has, and the thing that says the
            // drawer can be pulled away before anybody tries it.
            Box(
                Modifier
                    .padding(bottom = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = titleGap),
            )
            content()
        }
        }
        }
    }
}
