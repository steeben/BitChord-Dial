package com.music.bitchord.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.music.bitchord.data.settings.AppStyle

/**
 * The least fill a light theme's glass carries under a menu's text. The
 * theme's text is black, and what shows through clear glass is whatever is
 * behind it: over a dark sleeve or a coloured page it could not be read.
 */
internal const val LEGIBLE_LIGHT_FILL = 0.6f

/** This fill, brought up to [floor] with the light theme's own white where it is fainter. */
internal fun Color.legibleOnLight(floor: Float = LEGIBLE_LIGHT_FILL): Color = legibleOver(Color(0xFFFAFAFA), floor)

/**
 * The same for white text: brought up to [floor] with the dark theme's own
 * near-black, so a dark menu over a white sleeve or a bright page keeps
 * something under its labels.
 */
internal fun Color.legibleOnDark(floor: Float = LEGIBLE_LIGHT_FILL): Color = legibleOver(Color(0xFF121212), floor)

private fun Color.legibleOver(ground: Color, floor: Float): Color {
    if (alpha >= floor || floor <= 0f) return this
    val under = (floor - alpha) / (1f - alpha)
    return compositeOver(ground.copy(alpha = under))
}

/**
 * The floor [legibleOnLight] keeps at [transparency], when the glass's fill is
 * measured against [defaultTransparency]: all of [LEGIBLE_LIGHT_FILL] up to it, fading
 * to none at 100%. Full transparency means no colour at all on the glass, a
 * menu's included; the text over it is then the user's to judge.
 */
internal fun legibleFloor(transparency: Float, defaultTransparency: Float): Float {
    val strength = ((1f - transparency) / (1f - defaultTransparency)).coerceIn(0f, 1f)
    return LEGIBLE_LIGHT_FILL * strength
}

/**
 * Whether the player's ⋯ menu is drawn in the dark theme's colours. In the
 * dark theme, always. In the light theme it is light as every other menu is,
 * wherever its fill holds black text up by itself: Frost's dense fill and
 * Solid's opaque one do, over any sleeve. Liquid glass shows the sleeve
 * through, so there the menu is light only over a sleeve that is bright
 * ([sleeveBright]) and stays dark over a dark one.
 */
internal fun playerMenuDark(appThemeDark: Boolean, style: AppStyle, sleeveBright: Boolean): Boolean =
    appThemeDark || (style == AppStyle.GLASS && !sleeveBright)
