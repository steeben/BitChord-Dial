package com.music.bitchord.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.player.LocalPlayerGlass
import com.music.bitchord.ui.player.LocalPlayerPopupOrigin
import com.music.bitchord.ui.utils.containSheetGestures
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private val POPUP_CARD_SHAPE = RoundedCornerShape(ALERT_CORNER)
private val POPUP_SCRIM_COLOR = Color.Black.copy(alpha = 0.4f)
private val POPUP_WIDTH = 320.dp
private val POPUP_CONTENT_MAX_HEIGHT = 420.dp
private val POPUP_SURFACE = Color(0xFF121212)

private const val POPUP_OPEN_MS = 340
private const val POPUP_CLOSE_MS = 240

/** How small the card starts out, and ends up, as a share of its full size. */
private const val POPUP_FROM_SCALE = 0.3f

/** How much of the way shut a predictive back swipe takes the card at its full length. */
private const val POPUP_BACK_SWIPE_SHARE = 0.25f

/** The card is fully there by this much of the way open, so it grows as a card and not as a ghost. */
private const val POPUP_SOLID_AT = 0.4f

/**
 * The card the output drawer's popups open into — the one
 * [AudioPipelineDialog] was drawn in, lifted out so the Cast picker is the same
 * object rather than a second one made to look like it.
 *
 * A scrim that dismisses, a card that swallows the tap so touching it does not,
 * a centred title and subtitle, a scrolling body, and a full-width closing
 * action. Made of what the player's drawers are made of, in the app's style:
 * the menu glass over the whole player in Glass and Frost, a plain fill in
 * Solid — see [com.music.bitchord.ui.player.PlayerDrawer]. Its ink stays the
 * player's white alphas whatever the app's light/dark theme is, as theirs does.
 *
 * It grows out of where it was opened from ([LocalPlayerPopupOrigin], its own
 * middle when there is none) and shrinks back into it, on Done, on the scrim
 * and on back; a predictive back swipe takes it part of the way first.
 *
 * [content] is the scrolling body, handed a [BoxScope] so a body can lay
 * something behind its rows — the pipeline draws its signal line there.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun AudioPopupCard(
    hazeState: HazeState,
    title: String,
    subtitle: String,
    doneLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glass = LocalPlayerGlass.current
    val origin = LocalPlayerPopupOrigin.current
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    // 0 shut, 1 open. Opened on the first composition, and shut again before
    // [onDismiss] takes the card away, so it shrinks off rather than vanishing.
    val open = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { open.animateTo(1f, tween(POPUP_OPEN_MS, easing = FastOutSlowInEasing)) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                open.animateTo(0f, tween(POPUP_CLOSE_MS, easing = FastOutSlowInEasing))
                currentOnDismiss()
            }
        }
    }

    // How far through a predictive back swipe the listener is, 0..1. The card
    // shrinks with it, and comes back if the swipe is let go.
    val backSwipe = remember { Animatable(0f) }
    PredictiveBackHandler(enabled = !closing) { events ->
        try {
            events.collect { event -> backSwipe.snapTo(event.progress) }
            close()
        } catch (cancelled: CancellationException) {
            scope.launch { backSwipe.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
            throw cancelled
        }
    }
    // Read in the layers below only, so a frame of either redraws without
    // recomposing the card.
    val shown: () -> Float = { open.value * (1f - POPUP_BACK_SWIPE_SHARE * backSwipe.value) }

    // Where the card rests, unscaled: what [origin] is turned into a pivot
    // against. Measured on the box around the scaled layer, not on the layer.
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    val pivot = remember(origin, cardBounds) {
        if (origin == null || cardBounds.isEmpty) {
            TransformOrigin.Center
        } else {
            TransformOrigin(
                ((origin.x - cardBounds.left) / cardBounds.width).coerceIn(-1f, 2f),
                ((origin.y - cardBounds.top) / cardBounds.height).coerceIn(-1f, 2f),
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .containSheetGestures()
            .drawBehind { drawRect(POPUP_SCRIM_COLOR, alpha = shown().coerceIn(0f, 1f)) }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = close,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(POPUP_WIDTH)
                .onGloballyPositioned { cardBounds = it.boundsInWindow() }
                .graphicsLayer {
                    val t = shown()
                    val scale = POPUP_FROM_SCALE + (1f - POPUP_FROM_SCALE) * t
                    scaleX = scale
                    scaleY = scale
                    alpha = (t / POPUP_SOLID_AT).coerceIn(0f, 1f)
                    transformOrigin = pivot
                },
        ) {
            // The app's menu glass over the whole player, when the app has
            // glass on: the same surface the drawers are, behind the rows.
            if (glass != null && !reduceDynamicBlur) {
                Box(
                    Modifier
                        .matchParentSize()
                        .then(
                            glass.surface(
                                Modifier,
                                POPUP_CARD_SHAPE,
                                underlay = POPUP_SCRIM_COLOR,
                                menu = true,
                                darkGround = true,
                            ),
                        ),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(POPUP_CARD_SHAPE)
                    .then(
                        when {
                            reduceDynamicBlur -> Modifier.background(POPUP_SURFACE)
                            glass != null -> Modifier
                            else -> Modifier
                                .optimizedHazeEffect(
                                    state = hazeState,
                                    style = HazeMaterials.regular(Color(0xFF141414)),
                                )
                                .background(POPUP_SURFACE.copy(alpha = 0.9f))
                        }
                    )
                    // Swallows the tap before it reaches the scrim behind, so
                    // touching the card itself never dismisses it.
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 19.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.W600,
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = subtitle,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                        ),
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }

                Box(
                    modifier = Modifier
                        .heightIn(max = POPUP_CONTENT_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                    content = content,
                )

                // Full width: the default rule is indented to clear the pipeline's
                // icon lane, which left this one sitting right of centre.
                PipelineRule(Modifier)
                PipelineDoneAction(label = doneLabel, onClick = close)
            }
        }
    }
}
