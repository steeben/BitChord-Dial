package com.music.bitchord.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.Onboarding
import com.music.bitchord.data.OnboardingStep
import com.music.bitchord.data.TourTarget
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AppStyle
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.screens.SegmentedControl
import com.music.bitchord.ui.screens.SpeedDialIntro
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** How long after the splash has gone the welcome waits, so it doesn't land on the dial's entrance. */
private const val START_DELAY_MS = 900L

/** How often a step looks for the control it points at: it may still be on its way in. */
private const val ANCHOR_POLL_MS = 120L

/** The dim behind a step that points at something: deeper than an alert's, so the ring stands out. */
private val SPOT_SCRIM_COLOR = Color.Black.copy(alpha = 0.5f)

/** The room between a pointed-at control and its ring, and the ring's weight. */
private val SPOT_MARGIN = 5.dp
private val RING_WIDTH = 2.dp

/** How far above the bottom of the screen a step's card sits, clear of the dial's top rows. */
private val CARD_BOTTOM_GAP = 96.dp

/**
 * Where the controls the tour points at are on screen, as each last laid
 * itself out. Plain, not state: the tour reads it while a step is up, and
 * nothing else should recompose because a button moved.
 */
object TourAnchors {
    val bounds = HashMap<TourTarget, Rect>()
}

/** Marks a control as one the tour points at; see [OnboardingHost]. */
fun Modifier.tourAnchor(target: TourTarget): Modifier =
    onGloballyPositioned { TourAnchors.bounds[target] = it.boundsInRoot() }

/**
 * The first-launch tour: a welcome that offers it or a way out, then a card
 * per step over the app itself, with a ring round the control a step is
 * about. [onShowLibrary] puts the Library tab (true) or Home (false) behind
 * the card. Shaped like [UpdateAvailableDialog]; see [OnboardingStep].
 */
@Composable
fun OnboardingHost(hazeState: HazeState, onShowLibrary: (Boolean) -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf<OnboardingStep?>(null) }
    LaunchedEffect(Unit) {
        if (step != null) return@LaunchedEffect
        val due = withContext(Dispatchers.IO) { Onboarding.shouldShow(context) }
        if (!due) return@LaunchedEffect
        // Not under the splash, nor over the dial's own entrance.
        snapshotFlow { SpeedDialIntro.canStart }.first { it }
        delay(START_DELAY_MS)
        step = OnboardingStep.WELCOME
    }
    val current = step ?: return

    val showLibrary by rememberUpdatedState(onShowLibrary)
    LaunchedEffect(current) {
        if (current != OnboardingStep.WELCOME) showLibrary(current.onLibrary)
    }
    val finish = {
        Onboarding.markSeen(context)
        step = null
    }
    // Back is a step back, and never out: leaving is what Skip is for.
    BackHandler { current.previous()?.let { step = it } }

    var origin by remember { mutableStateOf(Offset.Zero) }
    var spot by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(current) {
        spot = null
        val target = current.target ?: return@LaunchedEffect
        while (true) {
            spot = TourAnchors.bounds[target]
            delay(ANCHOR_POLL_MS)
        }
    }
    val ringColor = MaterialTheme.colorScheme.primary
    val ringAlpha by rememberInfiniteTransition(label = "tourRing").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "tourRingAlpha",
    )
    val scrim = if (current.target != null) SPOT_SCRIM_COLOR else SCRIM_COLOR
    val centred = current == OnboardingStep.WELCOME || current == OnboardingStep.FINISH
    val shape = RoundedCornerShape(ALERT_CORNER)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            ),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                // Its own layer, for the hole cut out of the dim below.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(scrim)
            val rect = spot?.translate(-origin)
            if (rect != null) {
                val radius = maxOf(rect.width, rect.height) / 2f + SPOT_MARGIN.toPx()
                drawCircle(Color.Black, radius, rect.center, blendMode = BlendMode.Clear)
                drawCircle(ringColor.copy(alpha = ringAlpha), radius, rect.center, style = Stroke(RING_WIDTH.toPx()))
            }
        }
        CompositionLocalProvider(LocalGlassUnderlay provides scrim) {
            val contentColor = popupContentColor()
            Column(
                modifier = Modifier
                    .align(if (centred) Alignment.Center else Alignment.BottomCenter)
                    .then(
                        if (centred) Modifier else Modifier.navigationBarsPadding().padding(bottom = CARD_BOTTOM_GAP),
                    )
                    .width(ALERT_WIDTH)
                    .popupSurface(shape, hazeState),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 19.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (current != OnboardingStep.WELCOME) {
                        Text(
                            text = stringResource(R.string.onboarding_step_count, current.number, OnboardingStep.count),
                            modifier = Modifier.padding(bottom = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        text = stringResource(current.titleRes()),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                        color = contentColor,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(current.bodyRes()),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                        color = contentColor,
                        textAlign = TextAlign.Center,
                    )
                    if (current == OnboardingStep.WELCOME) LookChoices(contentColor)
                }
                AlertRule()
                when (current) {
                    OnboardingStep.WELCOME -> {
                        AlertAction(stringResource(R.string.onboarding_take_tour), emphasised = true, onClick = {
                            step = current.next()
                        })
                        AlertRule()
                        AlertAction(stringResource(R.string.onboarding_skip), emphasised = false, onClick = finish)
                    }
                    OnboardingStep.FINISH ->
                        AlertAction(stringResource(R.string.onboarding_finish), emphasised = true, onClick = finish)
                    else -> {
                        AlertAction(stringResource(R.string.onboarding_next), emphasised = true, onClick = {
                            step = current.next()
                        })
                        AlertRule()
                        AlertAction(stringResource(R.string.onboarding_skip), emphasised = false, onClick = finish)
                    }
                }
            }
        }
    }
}

/**
 * The welcome's two choices of look, the theme and the app style: the same
 * settings as in Settings, so the app behind the card, and the card itself,
 * change as they are picked. On the welcome rather than a step of the tour,
 * so skipping the tour doesn't skip them.
 */
@Composable
private fun LookChoices(contentColor: Color) {
    val theme by AppSettings.themeMode.collectAsStateWithLifecycle()
    val liquidGlass by AppSettings.liquidGlass.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glassSupported = isGlassSupported()
    val styles = AppStyle.choices(glassSupported)
    val style = AppStyle.of(liquidGlass && glassSupported, reduceDynamicBlur)

    LookHeading(stringResource(R.string.theme), contentColor, top = 16.dp)
    SegmentedControl(
        options = listOf(
            stringResource(R.string.system),
            stringResource(R.string.light),
            stringResource(R.string.dark),
        ),
        selectedIndex = THEME_CHOICES.indexOf(theme),
        onSelect = { AppSettings.setThemeMode(THEME_CHOICES[it]) },
    )
    LookHeading(stringResource(R.string.app_style), contentColor, top = 12.dp)
    SegmentedControl(
        options = styles.map {
            stringResource(
                when (it) {
                    AppStyle.GLASS -> R.string.app_style_glass
                    AppStyle.FROST -> R.string.app_style_frost
                    AppStyle.SOLID -> R.string.app_style_solid
                },
            )
        },
        selectedIndex = styles.indexOf(style),
        onSelect = { AppSettings.setAppStyle(styles[it]) },
    )
    Text(
        text = stringResource(R.string.onboarding_look_note),
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = contentColor.copy(alpha = 0.6f),
        textAlign = TextAlign.Center,
    )
}

/** The themes in the order the welcome offers them, as the labels above are written. */
private val THEME_CHOICES = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

@Composable
private fun LookHeading(text: String, contentColor: Color, top: Dp) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(top = top, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = contentColor.copy(alpha = 0.6f),
    )
}

private fun OnboardingStep.titleRes(): Int = when (this) {
    OnboardingStep.WELCOME -> R.string.onboarding_welcome_title
    OnboardingStep.DIAL -> R.string.onboarding_dial_title
    OnboardingStep.PINS -> R.string.onboarding_pins_title
    OnboardingStep.SETTINGS -> R.string.onboarding_settings_title
    OnboardingStep.MIXES -> R.string.onboarding_mixes_title
    OnboardingStep.DIAL_MENU -> R.string.onboarding_dial_menu_title
    OnboardingStep.LIBRARY -> R.string.onboarding_library_title
    OnboardingStep.FINISH -> R.string.onboarding_finish_title
}

private fun OnboardingStep.bodyRes(): Int = when (this) {
    OnboardingStep.WELCOME -> R.string.onboarding_welcome_body
    OnboardingStep.DIAL -> R.string.onboarding_dial_body
    OnboardingStep.PINS -> R.string.onboarding_pins_body
    OnboardingStep.SETTINGS -> R.string.onboarding_settings_body
    OnboardingStep.MIXES -> R.string.onboarding_mixes_body
    OnboardingStep.DIAL_MENU -> R.string.onboarding_dial_menu_body
    OnboardingStep.LIBRARY -> R.string.onboarding_library_body
    OnboardingStep.FINISH -> R.string.onboarding_finish_body
}
