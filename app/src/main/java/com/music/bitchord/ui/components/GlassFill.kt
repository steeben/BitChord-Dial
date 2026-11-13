package com.music.bitchord.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.music.bitchord.data.settings.CustomizationSettings

/**
 * How strongly a [GlassTint] is washed over a glass surface at the reference
 * transparency. It is laid over the surface's own fill rather than mixed into
 * it, so a faint bar and a dense drawer take on the same amount of colour.
 */
internal const val TINT_WASH = 0.28f

/** How far black the tint darkness slider goes at its top. */
internal const val MAX_SHADE = 0.85f

/**
 * How much white a frosted button lays over the dark ground it is told it is
 * on: the player's heart, ⋯ and bottom pill, over the sleeve.
 */
internal const val FROST_BUTTON_LIFT = 0.16f

/**
 * The Frost style's fill for a button on ground that is [dark] or bright: the
 * player's heart, ⋯ and bottom pill. On dark ground the theme's near-black
 * surface made the buttons black holes in a sleeve that is a colour, not
 * black, and on bright ground its near-white one made them white discs, so
 * they are a pale lift of the blurred sleeve instead, like the page buttons
 * ([frostPageButtonFill]).
 *
 * [underlay] is the scrim or veil the button sits on that its backdrop does
 * not hold. It goes under the lift, so the button is the darkened (or
 * lightened) band lifted, not the bare sleeve showing through a hole in it.
 */
internal fun frostButtonFill(dark: Boolean, underlay: Color = Color.Transparent): Color {
    val lift = Color.White.copy(alpha = if (dark) FROST_BUTTON_LIFT else FROST_PAGE_BUTTON_VEIL)
    return if (underlay.alpha <= 0f) lift else lift.compositeOver(underlay)
}

/**
 * How much white a frosted button lays over a light page: enough to stand
 * off the page as a pane, with the page's picture still plain through it.
 */
internal const val FROST_PAGE_BUTTON_VEIL = 0.35f

/**
 * The Frost style's fill for a button lying on a page's picture: an album,
 * playlist or artist page's Shuffle and Search. The bars' fill (the theme's
 * surface at 60 to 65%) left nothing of the page to see through a button
 * that small, so it read as a solid disc. It is a veil of white over the
 * blurred page instead: the player buttons' [FROST_BUTTON_LIFT] on a [dark]
 * page, [FROST_PAGE_BUTTON_VEIL] on a light one.
 */
internal fun frostPageButtonFill(dark: Boolean): Color =
    Color.White.copy(alpha = if (dark) FROST_BUTTON_LIFT else FROST_PAGE_BUTTON_VEIL)

/**
 * The Glass style's own rule for the buttons on the main player: the heart,
 * the ⋯ and the bottom pill. They do not follow the Liquid Glass options. They
 * are fully see-through, so no tint or darkening is left on them whatever the
 * options say, over [PLAYER_BUTTON_GLASS_BLUR_DP] of blur, with the sleeve's
 * colours at full saturation ([PLAYER_BUTTON_GLASS_SATURATION]). With the options'
 * dark tint they were darker than a bright sleeve whose ink had gone dark,
 * and the dark glyphs on them were hard to read.
 */
internal const val PLAYER_BUTTON_GLASS_TRANSPARENCY = 1f
internal const val PLAYER_BUTTON_GLASS_BLUR_DP = 10f
internal const val PLAYER_BUTTON_GLASS_SATURATION = 1f

/** Classic's fill opacity at the transparency its fill is measured against. */
internal const val SURFACE_OPACITY = 1f - CustomizationSettings.GLASS_REFERENCE_TRANSPARENCY

/**
 * Classic's fill: [base] (the theme's surface, or the caller's own, or none),
 * then the user's [tint] and [darkness] laid over it at fixed strengths, all
 * scaled by [transparency]. With no tint and no darkness it gives back exactly
 * [base]; at full transparency nothing is left of any of it.
 *
 * The tint and the darkening are layers of their own rather than mixed into
 * [base], so they come out the same on every surface. Mixed in, they took on
 * [base]'s opacity: the nav bar's faint fill barely showed a tint the menus'
 * dense one was flooded with.
 */
internal fun classicGlassFillColor(base: Color?, tint: Color?, darkness: Float, transparency: Float): Color {
    val density = ((1f - transparency) / SURFACE_OPACITY).coerceAtLeast(0f)
    var fill = base?.let { it.copy(alpha = (it.alpha * density).coerceIn(0f, 1f)) } ?: Color.Transparent
    if (tint != null) fill = tint.copy(alpha = (TINT_WASH * density).coerceIn(0f, 1f)).compositeOver(fill)
    if (darkness > 0f) {
        fill = Color.Black.copy(alpha = (darkness * MAX_SHADE * density).coerceIn(0f, 1f)).compositeOver(fill)
    }
    return fill
}

/**
 * The New style's fill: its clear [veil] (if any), over
 * as much of the theme's own surface ([themeSurface], likewise) as
 * [transparency] leaves, with the user's [tint] and [darkness] laid on top.
 *
 * The veil, the tint and the darkening are at full strength up to the reference
 * transparency and fade out from there, so that at full transparency no
 * colour is left on the glass whichever tint is picked.
 */
internal fun clearGlassFillColor(
    veil: Color?,
    themeSurface: Color?,
    tint: Color?,
    darkness: Float,
    transparency: Float,
): Color {
    val surface = (1f - transparency).coerceIn(0f, 1f)
    val strength = clearGlassStrength(transparency)
    var fill = veil?.let { it.copy(alpha = it.alpha * strength) } ?: Color.Transparent
    if (themeSurface != null && surface > 0f) fill = fill.compositeOver(themeSurface.copy(alpha = surface))
    if (tint != null) fill = tint.copy(alpha = TINT_WASH * strength).compositeOver(fill)
    if (darkness > 0f) {
        fill = Color.Black.copy(alpha = (darkness * MAX_SHADE * strength).coerceIn(0f, 1f)).compositeOver(fill)
    }
    return fill
}

/**
 * How much of the New style's veil, tint, shade and caustic is left at
 * [transparency]: all of it up to the reference transparency, none at 100%.
 */
internal fun clearGlassStrength(transparency: Float): Float =
    ((1f - transparency) / (1f - CustomizationSettings.CLEAR_GLASS_REFERENCE_TRANSPARENCY)).coerceIn(0f, 1f)

/** This colour with its saturation scaled by [amount]: 0 is its grey, 1 is itself. */
internal fun Color.saturated(amount: Float): Color {
    val grey = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    fun channel(c: Float) = (grey + (c - grey) * amount).coerceIn(0f, 1f)
    return Color(channel(red), channel(green), channel(blue), alpha)
}
