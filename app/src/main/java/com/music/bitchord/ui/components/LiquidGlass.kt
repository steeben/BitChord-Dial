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
import com.music.bitchord.data.settings.AppStyle
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
import com.music.bitchord.ui.components.backdrop.internal.DialTileGlassShaderString
import com.music.bitchord.ui.components.backdrop.internal.RuntimeShaderEffect
import com.music.bitchord.ui.components.backdrop.shadow.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/**
 * Whether the app draws its bars, menus and buttons as glass-shaped surfaces:
 * the floating bar, the floating top controls, the menus that grow out of
 * them. True in every app style; what those surfaces are made of is the
 * style's ([AppStyle]), and [liquidGlass] is where that is decided: liquid
 * glass, the frost, or a solid fill.
 */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/** The app style in force: what [liquidGlass] surfaces are made of. */
@Composable
fun appStyle(): AppStyle {
    val liquidGlass by AppSettings.liquidGlass.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    return AppStyle.of(liquidGlass, reduceDynamicBlur)
}

/** Whether the theme in force is the light one. */
@Composable
private fun isLightTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() > 0.5f

/**
 * The Frost style's material, the look the frosted mini player and tab bar
 * always had: the page behind blurred [FROST_BLUR_DP], under the theme's
 * surface at [FROST_FILL_LIGHT] or [FROST_FILL_DARK], inside a hairline.
 */
private const val FROST_BLUR_DP = 24f
private const val FROST_FILL_LIGHT = 0.6f
private const val FROST_FILL_DARK = 0.65f

/** The Frost style's fill: the theme's surface, see-through. */
@Composable
internal fun frostFill(): Color = MaterialTheme.colorScheme.surface.copy(
    alpha = if (isLightTheme()) FROST_FILL_LIGHT else FROST_FILL_DARK,
)

/**
 * The themes' own surfaces, for a surface told what ground it is on rather
 * than left to the theme: the player's buttons, over a sleeve that is dark or
 * bright whatever the app's theme.
 */
private val DARK_SURFACE = Color(0xFF0D0D0F)
private val LIGHT_SURFACE = Color(0xFFF7F7F9)

/**
 * [frostFill] for ground that is [dark] or bright, whatever the theme. A
 * surface text is read off ([carriesText], a drawer) keeps the dense fill; a
 * button on dark ground is lifted instead, see [frostButtonFill].
 */
private fun frostFill(dark: Boolean, carriesText: Boolean): Color {
    val dense = if (dark) DARK_SURFACE.copy(alpha = FROST_FILL_DARK) else LIGHT_SURFACE.copy(alpha = FROST_FILL_LIGHT)
    return if (carriesText) dense else frostButtonFill(dark, dense)
}

/**
 * The hairline around a frosted or solid surface: a trace of white on a dark
 * theme, of black on a light one, where white would not show at all.
 */
@Composable
internal fun surfaceEdgeColor(): Color = surfaceEdgeColor(!isLightTheme())

private fun surfaceEdgeColor(dark: Boolean): Color =
    if (dark) GLASS_EDGE_COLOR else Color.Black.copy(alpha = 0.10f)

/**
 * Whether the app's theme is the dark one, for glass composed under a theme
 * of its own: the player's ⋯ menu is drawn in the dark theme's colours over
 * the player, and still wears the glass options of the theme the app is in.
 * Null where nothing provides it, which leaves it to the theme in force.
 */
val LocalAppThemeDark = staticCompositionLocalOf<Boolean?> { null }

/** Whether the glass options in force are the dark theme's. */
@Composable
private fun glassOptionsDark(): Boolean = LocalAppThemeDark.current ?: !isLightTheme()

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
 * A dim laid over the page behind a glass surface that the glass's backdrop
 * does not record: the scrim under a menu or popup. The backdrop is the page
 * as it is under the scrim, so without this the glass came out brighter than
 * everything around it. [liquidGlass] lays it over what it samples.
 */
val LocalGlassUnderlay = staticCompositionLocalOf { Color.Transparent }

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

/**
 * Whether glass composed under it follows the user's Liquid Glass options.
 * False on pages that take their colours from artwork — the album, playlist
 * and artist pages — so the stock glass is drawn there instead. The player
 * follows the options, as every menu does.
 */
val LocalGlassFollowsOptions = staticCompositionLocalOf { true }

/**
 * A colour washed into the glass's fill, in the order the options drawer
 * shows them: the playing song's first, then white and black, then the
 * colours. How much of it shows is the transparency's to say; at 100% none
 * does, so there is no separate "no tint" any more.
 */
enum class GlassTint(val argb: Long?) {
    SONG(null),
    WHITE(0xFFFFFFFF),
    BLACK(0xFF000000),
    BLUE(0xFF3D7BFF),
    PURPLE(0xFF9B5CFF),
    PINK(0xFFFF4F9A),
    ORANGE(0xFFFF8A3D),
    GREEN(0xFF34C77B),
    ;

    /** The colour itself; [SONG] is the playing song's accent, and none while nothing plays. */
    @Composable
    fun color(): Color? = when (this) {
        SONG -> LocalNowPlayingTint.current
        else -> Color(argb!!)
    }

    companion object {
        /**
         * The tint saved as [name], for the [dark] theme or the light one, or
         * null if nothing was saved. Tints that have since been taken off the
         * row come back as the one nearest them: No tint and Default (the
         * theme's own surface) as the theme's own white or black, and Accent,
         * which was the theme's primary, as the black or white it drew.
         */
        fun fromSaved(name: String?, dark: Boolean): GlassTint? = when (name) {
            null -> null
            "NONE", "DEFAULT" -> themeNeutral(dark)
            "ACCENT" -> if (dark) WHITE else BLACK
            else -> entries.firstOrNull { it.name == name }
        }

        /** White on the light theme, black on the dark one: the theme's own surface, near enough. */
        fun themeNeutral(dark: Boolean): GlassTint = if (dark) BLACK else WHITE
    }
}

/**
 * Classic's fill for a surface: [base] is the caller's own tint, or null for
 * the theme's surface ([themeFill]). See [classicGlassFillColor].
 */
@Composable
private fun glassFill(base: Color?, themeFill: Color): Color {
    if (!LocalGlassFollowsOptions.current) return base ?: themeFill
    val options = CustomizationSettings.glassOptions(clear = false, dark = glassOptionsDark())
    val transparency by options.transparency.collectAsStateWithLifecycle()
    val picked by options.tint.collectAsStateWithLifecycle()
    val darkness by options.darkness.collectAsStateWithLifecycle()
    val saturation by options.saturation.collectAsStateWithLifecycle()
    // The saturation slider reaches the tint too: the tint as picked at the
    // reference saturation, grey at 0%.
    val tint = picked.color()
        ?.saturated(saturation / CustomizationSettings.GLASS_REFERENCE_SATURATION)
    return classicGlassFillColor(
        base = base ?: themeFill,
        tint = tint,
        darkness = darkness,
        transparency = transparency,
    )
}

/**
 * The New style's fill for a surface: [veil] is the caller's own tint, or
 * null for the clear glass's ([clearGlassVeil]). See [clearGlassFillColor].
 */
@Composable
private fun clearGlassFill(veil: Color?, themeTint: Color, dark: Boolean): Color {
    val own = veil ?: clearGlassVeil(dark)
    if (!LocalGlassFollowsOptions.current) return own
    val options = CustomizationSettings.glassOptions(clear = true, dark = glassOptionsDark())
    val transparency by options.transparency.collectAsStateWithLifecycle()
    val darkness by options.darkness.collectAsStateWithLifecycle()
    val saturation by options.saturation.collectAsStateWithLifecycle()
    val picked by options.tint.collectAsStateWithLifecycle()
    val tint = picked.color()
        ?.saturated(saturation / CustomizationSettings.CLEAR_GLASS_REFERENCE_SATURATION)
    return clearGlassFillColor(
        veil = own,
        themeSurface = themeTint,
        tint = tint,
        darkness = darkness,
        transparency = transparency,
    )
}

/** This fill laid over [under]; itself when there is nothing under it. */
private fun Color.over(under: Color): Color = if (under.alpha <= 0f) this else compositeOver(under)

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
    val style = appStyle()
    val useGlass = LocalLiquidGlassEnabled.current && isGlassSupported() && style == AppStyle.GLASS
    val glassTint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val shapeProvider = ShapeProvider { shape }

    return clip(shape)
        .background(
            color = if (useGlass) glassFill(null, glassTint.copy(alpha = SURFACE_OPACITY)) else fallbackColor,
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
    val useGlass = LocalLiquidGlassEnabled.current && isGlassSupported() && appStyle() == AppStyle.GLASS
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
 * That is the Glass app style. In the Frost style the same surface is the
 * frost instead: the backdrop blurred, with no bend and no rim light, under a
 * see-through fill and a hairline (see [frostFill]). In the Solid style it is
 * filled solid. The style is checked here rather than at each call site so
 * there is one answer to it: [MainActivity] also stops recording the backdrop
 * layer in the Solid style, and a surface that still tried to sample would be
 * sampling a layer nothing is drawing into.
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
    /** The scrim the surface sits on, if it sits on one; see [LocalGlassUnderlay]. */
    underlay: Color = LocalGlassUnderlay.current,
    /**
     * The least the glass frosts what it bends, in dp, whatever the user's blur.
     * A surface over a blurred page passes that page's blur: the backdrop is the
     * page unblurred, so glass at its own lighter frost read as a sharp window
     * cut through the blur around it.
     */
    minBlurDp: Float = 0f,
    /**
     * Whether the theme's text is read off this surface: a menu, a popup, an
     * options window. In the light theme its fill is then kept dense enough
     * for black text whatever is behind the glass, and in the dark theme
     * for white text; see [LEGIBLE_LIGHT_FILL]. Every menu passes it, through
     * [glassMenu].
     */
    carriesText: Boolean = false,
    /**
     * Whether the ground it is on is dark, where that is not the theme's to
     * say: the player's buttons sit on the sleeve's colours, dark or bright
     * in either theme. It picks the material's dark or light form (the
     * frost's fill, the glass's own surface, its rim and caustic); the
     * options followed are still the app theme's. Null leaves it to the theme.
     */
    dark: Boolean? = null,
    /**
     * A button lying on a page's picture or wash: the album, playlist and
     * artist pages' Shuffle, Search and Play. The Frost style then gives it
     * the pale see-through lift of [frostPageButtonFill] rather than the
     * bars' dense fill, and a clear [surfaceTint] leaves it the blurred page
     * alone, for a caller that lays its own colour on top (Play).
     */
    pageButton: Boolean = false,
): Modifier {
    if (!isGlassSupported()) return this
    val style = appStyle()
    val light = dark?.not() ?: isLightTheme()
    if (style == AppStyle.SOLID) {
        val solid = if (dark == null) MaterialTheme.colorScheme.surface else if (dark) DARK_SURFACE else LIGHT_SURFACE
        return background(solid, shape)
            .border(GLASS_EDGE_WIDTH, surfaceEdgeColor(!light), shape)
    }
    val legible = carriesText && light
    if (style == AppStyle.FROST) {
        // A caller's own tint stands; a clear one asks for bare glass, which
        // frost is not.
        // Not laid over the underlay: the frost is as see-through on a menu
        // as on the bars, and its fill is dense enough to sit on a dimmed page.
        val fill = when {
            surfaceTint != null && surfaceTint.alpha > 0f -> surfaceTint
            pageButton -> if (surfaceTint != null) Color.Transparent else frostPageButtonFill(!light)
            dark == null -> frostFill()
            else -> frostFill(dark, carriesText)
        }.let { if (legible) it.legibleOnLight() else it }
        val frostBlurPx = with(LocalDensity.current) { maxOf(minBlurDp, FROST_BLUR_DP).dp.toPx() } *
            GLASS_RESOLUTION_SCALE
        return drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(frostBlurPx) },
            highlight = null,
            shadow = null,
            onDrawSurface = { drawRect(color = fill, size = size) },
            exportedBackdrop = exportedBackdrop,
            backdropScale = GLASS_RESOLUTION_SCALE,
        ).border(GLASS_EDGE_WIDTH, surfaceEdgeColor(!light), shape)
    }
    // Test style: see ClearLiquidGlass.kt.
    val followsOptions = LocalGlassFollowsOptions.current
    val themeTint = if (light) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    // The New style: see ClearLiquidGlass.kt. It has its own set of options,
    // one for each theme.
    val clearStyle by CustomizationSettings.clearGlassStyle.collectAsStateWithLifecycle()
    val options = CustomizationSettings.glassOptions(clear = clearStyle, dark = glassOptionsDark())
    // The floor under a menu's text gives way as the glass is made more
    // see-through, and is gone at 100%: no colour shows then, menus included.
    val userTransparency by options.transparency.collectAsStateWithLifecycle()
    val floor = if (!followsOptions) {
        LEGIBLE_LIGHT_FILL
    } else {
        legibleFloor(
            userTransparency,
            if (clearStyle) {
                CustomizationSettings.CLEAR_GLASS_REFERENCE_TRANSPARENCY
            } else {
                CustomizationSettings.GLASS_REFERENCE_TRANSPARENCY
            },
        )
    }
    // Black text gets a pale floor, white text a dark one: a menu's labels
    // are read off it whatever is behind the glass.
    val legibly: Color.() -> Color = {
        when {
            !carriesText -> this
            light -> legibleOnLight(floor)
            else -> legibleOnDark(floor)
        }
    }
    if (clearStyle) {
        val clearBlur by options.blur.collectAsStateWithLifecycle()
        val clearSaturation by options.saturation.collectAsStateWithLifecycle()
        return clearLiquidGlass(
            shape = shape,
            backdrop = backdrop,
            exportedBackdrop = exportedBackdrop,
            surfaceColor = clearGlassFill(surfaceTint, themeTint, dark = !light)
                .legibly()
                .over(underlay),
            blurDp = maxOf(minBlurDp, if (followsOptions) clearBlur else CustomizationSettings.DEFAULT_CLEAR_GLASS_BLUR),
            saturation = if (followsOptions) clearSaturation else CustomizationSettings.DEFAULT_CLEAR_GLASS_SATURATION,
            dark = !light,
            // The light bundled under the lens is white laid on the glass,
            // so it goes as the fill does: none of it at 100% transparency.
            caustic = if (followsOptions) clearGlassStrength(userTransparency) else 1f,
        )
    }
    val density = LocalDensity.current
    val userBlurDp by options.blur.collectAsStateWithLifecycle()
    val blurDp = maxOf(minBlurDp, if (followsOptions) userBlurDp else CustomizationSettings.DEFAULT_GLASS_BLUR)
    val userSaturation by options.saturation.collectAsStateWithLifecycle()
    val saturation = if (followsOptions) userSaturation else CustomizationSettings.DEFAULT_GLASS_SATURATION
    val blurPx = with(density) { blurDp.dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensHeightPx = with(density) { (LENS_HEIGHT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensAmountPx = with(density) { (LENS_AMOUNT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val surfaceTintColor = glassFill(surfaceTint, themeTint.copy(alpha = SURFACE_OPACITY))
        .legibly()
        .over(underlay)

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

/**
 * The one material every menu is made of: the ⋯ and ⋮ menus, the held menu,
 * the top bar's cards, the drawers and the sheets, on the app's pages and on
 * the player alike. It is [liquidGlass] as the app style and the Liquid Glass
 * options have it, with a menu's text read off it, and nothing else: no wash
 * of the caller's own, so two menus side by side are the same glass.
 *
 * What may differ is only what the menu is over: [backdrop] (the pages, or
 * the player), the scrim it sits on ([underlay]), how far that page is
 * already blurred ([minBlurDp]) and whether that ground is [dark].
 */
@Composable
fun Modifier.glassMenu(
    shape: CornerBasedShape,
    backdrop: Backdrop = LocalAppBackdrop.current,
    underlay: Color = LocalGlassUnderlay.current,
    minBlurDp: Float = 0f,
    dark: Boolean? = null,
): Modifier = liquidGlass(
    shape,
    backdrop = backdrop,
    exportedBackdrop = null,
    underlay = underlay,
    minBlurDp = minBlurDp,
    carriesText = true,
    dark = dark,
)

/**
 * The dim under a menu hung off a button: the page goes darker behind it in
 * the dark theme and paler in the light one, where a dark wash made a bright
 * page look dirty.
 */
@Composable
fun menuScrimColor(): Color =
    if (isLightTheme()) Color.White.copy(alpha = MENU_SCRIM_LIGHT) else Color.Black.copy(alpha = MENU_SCRIM_DARK)

private const val MENU_SCRIM_LIGHT = 0.45f
private const val MENU_SCRIM_DARK = 0.32f

/** The rim the dial's glass tiles wear: a little wider than a panel's, for their size. */
private val DIAL_TILE_HIGHLIGHT = Highlight(width = 1.dp)
private val dialTileHighlight: () -> Highlight = { DIAL_TILE_HIGHLIGHT }

/**
 * The Glass style's frost: fixed, whatever the bar's glass options say. The
 * glass is fully clear, with no tint and no darkening; only its edge is
 * frosted, [DIAL_TILE_GLASS_BLUR] at the rim, fading out [DIAL_TILE_BLUR_BAND] in.
 */
private val DIAL_TILE_GLASS_BLUR = 3.dp
private val DIAL_TILE_BLUR_BAND = 6.dp

/**
 * The dial's tiles in their Glass style: each tile's own artwork, filling the
 * whole tile, seen through a pane of clear glass. The glass leaves the art's
 * shape alone (no lens bend along the rim), frosts it [DIAL_TILE_GLASS_BLUR]
 * at the very edge and not at all in the middle, and wears the same angled
 * light and hairline as [liquidGlass]. It adds no tint.
 *
 * The frost needs Android 13; before that the tile keeps just the light and
 * the hairline. Under "reduce dynamic blur" there is no frost either.
 */
fun dialTileGlassEdge(shape: Shape): Modifier = Modifier.composed {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shape is CornerBasedShape) {
        val shader = remember { RuntimeShader(DialTileGlassShaderString) }
        Modifier.graphicsLayer {
            val radius = shape.topStart.toPx(size, this).coerceAtMost(size.minDimension / 2f)
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("radius", radius)
            // No bend: the art keeps its shape right up to the rim.
            shader.setFloatUniform("refractionHeight", 0f)
            shader.setFloatUniform("refractionAmount", 0f)
            shader.setFloatUniform("blurBand", DIAL_TILE_BLUR_BAND.toPx())
            shader.setFloatUniform("blur", if (reduceDynamicBlur) 0f else DIAL_TILE_GLASS_BLUR.toPx())
            renderEffect = RuntimeShaderEffect(shader, "content")
        }
    } else {
        Modifier
    }
    // The light and the hairline go outside the glass, so they stay crisp on top of it.
    Modifier
        .then(HighlightElement(shapeProvider = ShapeProvider { shape }, highlight = dialTileHighlight))
        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
        .then(glass)
}

/**
 * How far dial tiles reach past their cells on each side (see
 * [dialTileOutset]): a touch, the Glass style's size, which every style now
 * shares so switching styles never changes the tiles' size.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun dialTileGlassOutset(style: DialTileStyle): Dp = DIAL_TILE_GLASS_OUTSET

private val DIAL_TILE_GLASS_OUTSET = 2.dp
