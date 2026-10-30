/*
 * The glass rendering itself is Kyant0/backdrop (Apache-2.0), vendored at
 * [com.music.bitchord.ui.components.backdrop] — see that package for the
 * upstream attribution. This file is the integration glue, adapted from
 * EchoMusicApp/Echo-Music's GlassEffectConfig/Modifier.liquidGlass
 * (GPL-3.0), cut down from Echo's full per-component/vibrancy-slider config
 * to the on/off switch and the transparency, tint and blur options BitChord
 * exposes in Settings. It is used by the
 * floating nav bar and, while enabled, the app-wide floating top controls.
 */
package com.music.bitchord.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.compositeOver
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.theme.LocalNowPlayingTint
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.drawBackdrop
import com.music.bitchord.ui.components.backdrop.effects.blur
import com.music.bitchord.ui.components.backdrop.effects.colorControls
import com.music.bitchord.ui.components.backdrop.effects.lens
import com.music.bitchord.ui.components.backdrop.highlight.Highlight
import com.music.bitchord.ui.components.backdrop.highlight.HighlightElement
import com.music.bitchord.ui.components.backdrop.internal.ShapeProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.music.bitchord.ui.components.backdrop.RuntimeShader
import com.music.bitchord.ui.components.backdrop.internal.RoundedRectRefractionShaderString
import com.music.bitchord.ui.components.backdrop.internal.RuntimeShaderEffect
import com.music.bitchord.ui.components.backdrop.shadow.Shadow
import androidx.compose.ui.unit.dp

/** Whether the liquid glass nav bar is turned on — see [AppSettings.liquidGlass]. */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/** The backdrop content (app UI) that a liquid glass surface samples from. */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { error("No AppBackdrop provided") }

/**
 * Where a [liquidGlass] surface composed under it also records the glass it
 * draws — the sampled, blurred and tinted backdrop, without its rim or shadow —
 * so a lens above it can refract that surface rather than the page behind it.
 * The tab bar's travelling selection pill is the one reader; see
 * [com.music.bitchord.ui.components.floatingtabbar.GlassSelectionPill].
 */
internal val LocalGlassExport = compositionLocalOf<LayerBackdrop?> { null }

/**
 * The backdrop blur pipeline requires [android.graphics.RenderEffect] on a
 * [android.graphics.RenderNode], available from Android 12 (API 31).
 */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

/**
 * Apple-matched defaults (Echo's GlassEffectConfig()). The fill's opacity, the
 * saturation and the blur radius are the user's, from [CustomizationSettings]; these are what
 * those options are measured against.
 */
private const val LENS_HEIGHT = 0.5f
private const val LENS_AMOUNT = 0.5f
private const val LENS_MAX_DP = 48f
private const val SURFACE_OPACITY = 1f - CustomizationSettings.DEFAULT_GLASS_TRANSPARENCY

/**
 * How strongly a [GlassTint] is washed over a glass surface at the default
 * transparency. It is laid over the surface's own fill rather than mixed into
 * it, so a faint bar and a dense drawer take on the same amount of colour.
 */
private const val TINT_WASH = 0.28f

/** How far black the tint darkness slider goes at its top. */
private const val MAX_SHADE = 0.85f

/**
 * Whether glass composed under it follows the user's Liquid Glass options.
 * False on surfaces that take their colours from artwork — the album, playlist
 * and artist pages and the player — so the stock glass is drawn there instead.
 */
val LocalGlassFollowsOptions = staticCompositionLocalOf { true }

/** A colour washed into the glass's fill; [DEFAULT] leaves the theme's own. */
enum class GlassTint(val argb: Long?) {
    DEFAULT(null),
    ACCENT(null),
    SONG(null),
    BLUE(0xFF3D7BFF),
    PURPLE(0xFF9B5CFF),
    PINK(0xFFFF4F9A),
    ORANGE(0xFFFF8A3D),
    GREEN(0xFF34C77B),
    ;

    /**
     * The colour itself, or null for [DEFAULT]. [ACCENT] is the theme's
     * primary; [SONG] is the playing song's accent, and none while nothing plays.
     */
    @Composable
    fun color(): Color? = when (this) {
        DEFAULT -> null
        ACCENT -> MaterialTheme.colorScheme.primary
        SONG -> LocalNowPlayingTint.current
        else -> Color(argb!!)
    }
}

/**
 * The fill a glass surface is washed with: [base] (the theme's tint, or the
 * caller's own), then the user's tint and darkness laid over it at fixed
 * strengths, all scaled by their transparency. The default settings give back
 * exactly [base].
 *
 * The tint and the darkening are layers of their own rather than mixed into
 * [base], so they come out the same on every surface. Mixed in, they took on
 * [base]'s opacity: the nav bar's faint fill barely showed a tint the menus'
 * dense one was flooded with.
 */
@Composable
private fun glassFill(base: Color): Color {
    if (!LocalGlassFollowsOptions.current) return base
    val transparency by CustomizationSettings.glassTransparency.collectAsStateWithLifecycle()
    val tint = CustomizationSettings.glassTint.collectAsStateWithLifecycle().value.color()
    val darkness by CustomizationSettings.glassTintDarkness.collectAsStateWithLifecycle()
    val density = ((1f - transparency) / SURFACE_OPACITY).coerceAtLeast(0f)
    var fill = base.copy(alpha = (base.alpha * density).coerceIn(0f, 1f))
    if (tint != null) fill = tint.copy(alpha = (TINT_WASH * density).coerceIn(0f, 1f)).compositeOver(fill)
    if (darkness > 0f) fill = Color.Black.copy(alpha = (darkness * MAX_SHADE * density).coerceIn(0f, 1f)).compositeOver(fill)
    return fill
}

/**
 * Resolution fraction the glass surface records and processes its backdrop at.
 *
 * A third, which is what Echo's `glassResolutionScale` comes to at this blur
 * radius. It was briefly raised to a half while the sharp-text bug was being
 * chased — wrongly, as it turned out: the cause was a transparent backdrop, not
 * the resample (see MainActivity's `paintBackdrop`). A third is nine times fewer
 * pixels than full resolution through the colour matrix, the blur and the lens
 * shader, on as many as six surfaces at once in the middle of a fold, and the
 * blur is what hides the upscale.
 */
private const val GLASS_RESOLUTION_SCALE = 0.33f

/**
 * The hairline along a bar's edge, and what stands in for the glass rim
 * wherever the glass itself is not drawn.
 *
 * A surface filled with the theme's own `surface` colour has no edge of its own
 * against a dark page — it is the same near-black the page is. The glass gets
 * its edge from [Highlight], and this is that edge for everything that does not.
 */
internal val GLASS_EDGE_WIDTH = 0.5.dp
internal val GLASS_EDGE_COLOR = Color.White.copy(alpha = 0.10f)

/**
 * Icon and label colour for content sitting on a glass surface.
 *
 * Glass shows whatever is behind it rather than the theme's surface colour, so
 * the usual onSurface greys have nothing dependable to sit against. Pure black
 * or white off the theme's luminance is the only tint that holds against
 * arbitrary artwork, and it is what Echo's own glass nav bar uses.
 */
@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White

/**
 * Selected-tab indicator colour for a glass surface: white in both themes.
 *
 * In light theme that is the inverse of [glassContentColor], a bright lift
 * behind a dark glyph. In dark theme it is a faint white lift rather than a
 * black scrim pressed into the bar's glass — the selected tab is raised out
 * of the bar, as on iOS, not sunk.
 */
@Composable
fun glassIndicatorColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color.White.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = DARK_INDICATOR_OPACITY)
    }

/** The selected tab's pill in dark theme. */
private const val DARK_INDICATOR_OPACITY = 0.14f

/**
 * A lightweight visual match for liquid glass over a stable background.
 *
 * This keeps the same translucent tint, directional highlight and hairline as
 * [liquidGlass], but intentionally performs no backdrop capture, blur, lens
 * refraction or shadow rendering. When Liquid Glass is disabled or unsupported,
 * [fallbackColor] preserves the control's existing filled appearance.
 */
@Composable
fun Modifier.lightweightLiquidGlass(
    shape: CornerBasedShape,
    fallbackColor: Color,
): Modifier {
    val useGlass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val glassTint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val shapeProvider = ShapeProvider { shape }

    return clip(shape)
        .background(
            color = if (useGlass) glassFill(glassTint.copy(alpha = SURFACE_OPACITY)) else fallbackColor,
            shape = shape,
        )
        .then(
            if (useGlass) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = { Highlight.Default },
                )
            } else {
                Modifier
            },
        )
        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
}

/**
 * Glass's light and edge on a control that keeps its own solid fill — the
 * detail pages' Play circle, beside glass buttons it should read as one set
 * with. A sheen down from the top and the same rim as the glass buttons, plus
 * the glass highlight while Liquid Glass is on. No frosted tint: the fill is
 * the artwork's colour and stays it.
 */
@Composable
fun Modifier.glassSheen(shape: CornerBasedShape): Modifier {
    val useGlass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val shapeProvider = ShapeProvider { shape }
    return clip(shape)
        .drawWithContent {
            drawContent()
            drawRect(GLASS_SHEEN)
        }
        .then(
            if (useGlass) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = { Highlight.Default },
                )
            } else {
                Modifier
            },
        )
        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
}

/** The light a glass button catches across its top half. */
private val GLASS_SHEEN = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.22f),
    0.5f to Color.White.copy(alpha = 0.04f),
    1f to Color.Transparent,
)

/**
 * Renders this composable as a liquid glass surface sampling [LocalAppBackdrop]:
 * vibrancy, blur and lens refraction, then a theme-adaptive surface tint (light
 * glass on light theme, dark on dark). Returns the receiver unchanged on devices
 * without RenderEffect support — callers should still gate on [isGlassSupported]
 * to fall back to the regular Haze treatment there.
 *
 * Under "reduce dynamic blur" the surface is filled solid instead, which is what
 * that setting promises everywhere else in the app. It is checked here rather
 * than at each call site so there is one answer to it: [MainActivity] also stops
 * recording the backdrop layer when it is on, and a surface that still tried to
 * sample would be sampling a layer nothing is drawing into.
 *
 * [shape] is restricted to [CornerBasedShape] because the backdrop's lens effect
 * throws for any other shape type.
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,
    /** What it bends: the app's pages unless told otherwise (the player has its own). */
    backdrop: Backdrop = LocalAppBackdrop.current,
    exportedBackdrop: LayerBackdrop? = LocalGlassExport.current,
    /** Drawn over the glass in place of the theme's default tint, when set. */
    surfaceTint: Color? = null,
): Modifier {
    if (!isGlassSupported()) return this
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    if (reduceDynamicBlur) {
        return background(MaterialTheme.colorScheme.surface, shape)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
    }
    val density = LocalDensity.current
    val followsOptions = LocalGlassFollowsOptions.current
    val userBlurDp by CustomizationSettings.glassBlur.collectAsStateWithLifecycle()
    val blurDp = if (followsOptions) userBlurDp else CustomizationSettings.DEFAULT_GLASS_BLUR
    val userSaturation by CustomizationSettings.glassSaturation.collectAsStateWithLifecycle()
    val saturation = if (followsOptions) userSaturation else CustomizationSettings.DEFAULT_GLASS_SATURATION
    val blurPx = with(density) { blurDp.dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensHeightPx = with(density) { (LENS_HEIGHT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensAmountPx = with(density) { (LENS_AMOUNT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val themeTint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val surfaceTintColor = glassFill(surfaceTint ?: themeTint.copy(alpha = SURFACE_OPACITY))

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = saturation)
            blur(blurPx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = true,
                    chromaticAberration = true,
                )
            }
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default },
        onDrawSurface = {
            drawRect(color = surfaceTintColor, size = size)
        },
        exportedBackdrop = exportedBackdrop,
        backdropScale = GLASS_RESOLUTION_SCALE,
    )
}

/** The rim the dial's glass tiles wear: a little wider than a panel's, for their size. */
private val DIAL_TILE_HIGHLIGHT = Highlight(width = 1.dp)
private val dialTileHighlight: () -> Highlight = { DIAL_TILE_HIGHLIGHT }

/** How far in from a glass tile's edge its artwork bends, and how far it is pulled. */
private val DIAL_TILE_WARP_BAND = 12.dp
private val DIAL_TILE_WARP_AMOUNT = 9.dp

/**
 * Liquid glass's edge, for the dial's tiles in their Glass style: the tile's
 * own artwork bent a little along its rim, as a panel's lens bends what is
 * under it, with the same angled light and hairline as [liquidGlass] on top.
 * Nothing is blurred; the middle of the tile is left as it is. The bend needs
 * Android 13; before that the tile keeps just the light and the hairline.
 */
fun dialTileGlassEdge(shape: Shape): Modifier = Modifier.composed {
    val warp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shape is CornerBasedShape) {
        val shader = remember { RuntimeShader(RoundedRectRefractionShaderString) }
        Modifier.graphicsLayer {
            val radius = shape.topStart.toPx(size, this).coerceAtMost(size.minDimension / 2f)
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("offset", 0f, 0f)
            shader.setFloatUniform("cornerRadii", radius, radius, radius, radius)
            shader.setFloatUniform("refractionHeight", DIAL_TILE_WARP_BAND.toPx())
            shader.setFloatUniform("refractionAmount", -DIAL_TILE_WARP_AMOUNT.toPx())
            shader.setFloatUniform("depthEffect", 0f)
            renderEffect = RuntimeShaderEffect(shader, "content")
        }
    } else {
        Modifier
    }
    // The light and the hairline go outside the bend, so they stay crisp on top of it.
    Modifier
        .then(HighlightElement(shapeProvider = ShapeProvider { shape }, highlight = dialTileHighlight))
        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
        .then(warp)
}
