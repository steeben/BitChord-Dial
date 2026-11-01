package com.music.bitchord.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.backdrop.Backdrop
import androidx.activity.compose.BackHandler
import com.music.bitchord.ui.utils.containSheetGestures
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.roundToInt

/**
 * The colours of an iOS-style menu panel: a charcoal or frost slab with its
 * own label greys, rather than the theme's surface roles — the panel floats
 * over artwork as often as over a page, and the theme's containers were tuned
 * for neither.
 */
@Immutable
data class IosMenuColors(
    val dark: Boolean,
    val fill: Color,
    val rim: Color,
    val label: Color,
    val secondaryLabel: Color,
    val separator: Color,
    /** The rounded fill under the row a finger is on. */
    val pressed: Color,
)

private val DarkMenu = IosMenuColors(
    dark = true,
    fill = Color(0xFF1C1C1E),
    rim = Color.White.copy(alpha = 0.10f),
    label = Color.White,
    secondaryLabel = Color.White.copy(alpha = 0.70f),
    separator = Color.White.copy(alpha = 0.10f),
    pressed = Color.White.copy(alpha = 0.10f),
)

private val LightMenu = IosMenuColors(
    dark = false,
    fill = Color(0xFFF6F6F7),
    rim = Color.Black.copy(alpha = 0.06f),
    label = Color(0xFF1A1A1A),
    secondaryLabel = Color(0xFF1A1A1A).copy(alpha = 0.55f),
    separator = Color.Black.copy(alpha = 0.09f),
    pressed = Color.Black.copy(alpha = 0.07f),
)

/** The panel colours for the theme in force. */
@Composable
fun iosMenuColors(): IosMenuColors =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DarkMenu else LightMenu

/** The panel's silhouette: a large, rounded corner. */
val IosMenuShape = RoundedCornerShape(26.dp)

/** Wide enough for "Remove from listening history" on one line or two. */
val IosMenuWidth = 256.dp

/**
 * The menu's material, without its contents.
 *
 * With Liquid Glass on, it is Dial's liquid glass, sampling [backdrop] (the
 * app's pages, or the player's own) and following the user's Liquid Glass
 * options — transparency, tint and blur. With it off, it is the frosted slab
 * iOS draws: [hazeState] blurred under a translucent fill when there is a page
 * haze to blur, and the plain slab when there is not.
 *
 * "Reduce dynamic blur" turns both off and leaves the opaque slab: an unblurred
 * page seen through it is exactly what the frost was there to prevent.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.iosMenuPanel(
    backdrop: Backdrop? = null,
    hazeState: HazeState? = null,
    colors: IosMenuColors = iosMenuColors(),
    /** False where the caller casts [iosMenuShadow] itself, apart from the panel. */
    castsShadow: Boolean = true,
): Modifier {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glass = LocalLiquidGlassEnabled.current && isGlassSupported() && !reduceDynamicBlur && backdrop != null
    val shadowed = if (castsShadow && !glass) iosMenuShadow() else this
    return when {
        glass -> shadowed.clip(IosMenuShape).liquidGlass(IosMenuShape, backdrop = backdrop!!)
        !reduceDynamicBlur && hazeState != null -> shadowed
            .clip(IosMenuShape)
            .optimizedHazeEffect(
                state = hazeState,
                style = HazeMaterials.thin(colors.fill),
            )
            .background(colors.fill.copy(alpha = if (colors.dark) 0.55f else 0.45f))
            .border(0.5.dp, colors.rim, IosMenuShape)
        else -> shadowed
            .clip(IosMenuShape)
            .background(colors.fill)
            .border(0.5.dp, colors.rim, IosMenuShape)
    }
}

/** The shadow the menu panel casts. */
fun Modifier.iosMenuShadow(): Modifier = shadow(
    elevation = 24.dp,
    shape = IosMenuShape,
    ambientColor = Color.Black.copy(alpha = 0.30f),
    spotColor = Color.Black.copy(alpha = 0.30f),
    clip = false,
)

/** The break between groups: a hairline inset from the panel's edges, with air either side. */
@Composable
fun IosMenuSeparator(colors: IosMenuColors = iosMenuColors()) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(17.dp)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.separator),
        )
    }
}

/** The tiles across the top of a menu: the actions reached for most, side by side. */
@Composable
fun IosMenuQuickActions(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** One tile of [IosMenuQuickActions]: the glyph over a short label. */
@Composable
fun RowScope.IosMenuQuickAction(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    enabled: Boolean = true,
    /** Draws the glyph in place of [icon], given its tint and size — the like heart. */
    iconContent: (@Composable (tint: Color, size: Dp) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = iosMenuColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color = if (enabled) colors.label else colors.secondaryLabel
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(if (pressed) colors.pressed else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (iconContent != null) {
            iconContent(tint ?: color, 23.dp)
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint ?: color,
                modifier = Modifier.size(23.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One line of an iOS-style menu: the glyph leading in a fixed column, so the
 * labels line up whether a row has one or not, then the label with an
 * optional dimmer line under it, and an optional value at the far end.
 *
 * Pressing paints a rounded fill under the row rather than a ripple, the way
 * the panel's own platform does it.
 */
@Composable
fun IosMenuRow(
    label: String,
    icon: ImageVector?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    iconTint: Color? = null,
    labelColor: Color? = null,
    valueColor: Color? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    /** Draws the glyph in place of [icon], given its tint and size — the like heart. */
    iconContent: (@Composable (tint: Color, size: Dp) -> Unit)? = null,
) {
    val colors = iosMenuColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha = if (enabled) 1f else 0.4f
    val foreground = labelColor ?: colors.label
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) colors.pressed else Color.Transparent)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .heightIn(min = 44.dp)
            .padding(start = 12.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (iconContent != null) {
                iconContent((iconTint ?: foreground).copy(alpha = alpha), 21.dp)
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = (iconTint ?: foreground).copy(alpha = alpha),
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = label,
                color = foreground.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    letterSpacing = (-0.3).sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Text(
                    text = description,
                    color = colors.secondaryLabel.copy(alpha = colors.secondaryLabel.alpha * alpha),
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = value,
                color = (valueColor ?: colors.secondaryLabel).copy(alpha = alpha),
                fontSize = 15.sp,
                maxLines = 1,
            )
        }
        trailing?.invoke()
    }
}

/**
 * A menu hung off a button rather than lifted out of a list — the ⋮ on a row,
 * the ⋯ on a release page and the player's ⋯ all open their menu this way. The
 * panel lines its trailing edge up with [anchor]'s and grows out of the corner
 * nearest the button: for one in the lower half of the screen, as the player's
 * ⋯ is, it settles at the bottom and grows from its bottom corner; for one
 * higher up, a row's ⋮ near the top of a list, it hangs down from the button's
 * top and grows from its top corner. With no anchor it takes the bottom end.
 *
 * Drawn in place, over whatever it is composed on top of, on [iosMenuPanel]'s
 * material: Liquid Glass bending [backdrop] while that is on, the page's
 * [hazeState] frosted while it is off. Both of the app's accessibility switches
 * hold: "Reduce animation" (or the system's animator scale at zero) makes the
 * growth a short fade, and "Reduce dynamic blur" an opaque slab.
 *
 * [item] is non-null while the menu is wanted. The last one is kept so the
 * panel can play its way out after the caller has already let go of it, the
 * same arrangement [HeldContextMenu] uses.
 */
@Composable
fun <T : Any> IosMenuPopover(
    item: T?,
    anchor: Rect?,
    onDismiss: () -> Unit,
    backdrop: Backdrop? = null,
    hazeState: HazeState? = null,
    content: @Composable (T) -> Unit,
) {
    var last by remember { mutableStateOf<Pair<T, Rect?>?>(null) }
    val requested = item?.let { it to anchor }
    if (requested != null && requested.first != last?.first) {
        SideEffect { last = requested }
    }
    val shown = requested ?: last ?: return
    val (menuItem, menuAnchor) = shown
    val open = item != null

    val context = LocalContext.current
    val appReduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val systemAnimationsOff = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val reduceMotion = appReduceAnimation || systemAnimationsOff

    val progress = remember { Animatable(0f) }
    LaunchedEffect(open) {
        if (open) {
            progress.animateTo(
                1f,
                if (reduceMotion) {
                    tween(durationMillis = 120, easing = LinearEasing)
                } else {
                    // Critically damped: it settles without overshooting.
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 600f)
                },
            )
        } else {
            progress.animateTo(0f, tween(durationMillis = if (reduceMotion) 100 else 170, easing = FastOutSlowInEasing))
            last = null
        }
    }

    // Composed after whatever it is drawn over, so back closes the menu first
    // — inside the player's sheet too, rather than taking the player down.
    BackHandler(enabled = open, onBack = onDismiss)

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scrim = Color.Black.copy(alpha = if (dark) 0.30f else 0.14f)
    val insets = WindowInsets.safeDrawing
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }

    Layout(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOffset = it.positionInWindow() }
            // Nothing under the menu moves while it is up: a drag that the
            // rows didn't take would otherwise reach the sheet the player is
            // in and pull the whole player down. Taps still go through.
            .containSheetGestures(),
        content = {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                    .background(scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = open,
                        onClick = onDismiss,
                    ),
            )
            Box(
                Modifier
                    // Taps between rows land on the panel, not the scrim under it.
                    .pointerInput(Unit) {}
                    .iosMenuPanel(backdrop = backdrop, hazeState = hazeState),
                propagateMinConstraints = true,
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    content(menuItem)
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = 14.dp.roundToPx()
        val top = insets.getTop(this) + 8.dp.roundToPx()
        val bottom = height - insets.getBottom(this) - 12.dp.roundToPx()

        val scrimPlaceable = measurables[0].measure(Constraints.fixed(width, height))
        val panelWidth = IosMenuWidth.roundToPx().coerceAtMost(width - margin * 2)
        val panel = measurables[1].measure(
            Constraints(minWidth = panelWidth, maxWidth = panelWidth, maxHeight = (bottom - top).coerceAtLeast(0)),
        )
        val anchorBounds = menuAnchor?.translate(-overlayOffset)
        val right = anchorBounds?.right?.roundToInt() ?: (width - margin)
        val panelX = (right - panel.width)
            .coerceIn(margin, (width - margin - panel.width).coerceAtLeast(margin))
        val lowest = (bottom - panel.height).coerceAtLeast(top)
        val hangsDown = anchorBounds != null && anchorBounds.center.y < height / 2f
        val panelY = if (hangsDown) anchorBounds!!.top.roundToInt().coerceIn(top, lowest) else lowest

        layout(width, height) {
            scrimPlaceable.place(0, 0)
            panel.placeWithLayer(panelX, panelY) {
                val p = progress.value
                alpha = (p * 2.5f).coerceIn(0f, 1f)
                if (!reduceMotion) {
                    transformOrigin = TransformOrigin(1f, if (hangsDown) 0f else 1f)
                    val scale = lerp(0.3f, 1f, p)
                    scaleX = scale
                    scaleY = scale
                }
            }
        }
    }
}
