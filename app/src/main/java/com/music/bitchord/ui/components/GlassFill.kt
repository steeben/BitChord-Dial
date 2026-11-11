package com.music.bitchord.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.music.bitchord.data.settings.CustomizationSettings

/**
 * How strongly a [GlassTint] is washed over a glass surface at the default
 * transparency. It is laid over the surface's own fill rather than mixed into
 * it, so a faint bar and a dense drawer take on the same amount of colour.
 */
internal const val TINT_WASH = 0.28f

/** How far black the tint darkness slider goes at its top. */
internal const val MAX_SHADE = 0.85f

/** Classic's fill opacity at its default transparency. */
internal const val SURFACE_OPACITY = 1f - CustomizationSettings.DEFAULT_GLASS_TRANSPARENCY

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
 * The defaults give back the look it ships with.
 *
 * The veil, the tint and the darkening are at full strength up to the default
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
    val strength = (surface / (1f - CustomizationSettings.DEFAULT_CLEAR_GLASS_TRANSPARENCY)).coerceIn(0f, 1f)
    var fill = veil?.let { it.copy(alpha = it.alpha * strength) } ?: Color.Transparent
    if (themeSurface != null && surface > 0f) fill = fill.compositeOver(themeSurface.copy(alpha = surface))
    if (tint != null) fill = tint.copy(alpha = TINT_WASH * strength).compositeOver(fill)
    if (darkness > 0f) {
        fill = Color.Black.copy(alpha = (darkness * MAX_SHADE * strength).coerceIn(0f, 1f)).compositeOver(fill)
    }
    return fill
}

/** This colour with its saturation scaled by [amount]: 0 is its grey, 1 is itself. */
internal fun Color.saturated(amount: Float): Color {
    val grey = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    fun channel(c: Float) = (grey + (c - grey) * amount).coerceIn(0f, 1f)
    return Color(channel(red), channel(green), channel(blue), alpha)
}
