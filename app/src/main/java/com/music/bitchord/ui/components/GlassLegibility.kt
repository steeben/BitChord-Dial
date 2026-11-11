package com.music.bitchord.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * The least fill a light theme's glass carries under a menu's text. The
 * theme's text is black, and what shows through clear glass is whatever is
 * behind it: over a dark sleeve or a coloured page it could not be read.
 */
internal const val LEGIBLE_LIGHT_FILL = 0.6f

/** This fill, brought up to [LEGIBLE_LIGHT_FILL] with the light theme's own white where it is fainter. */
internal fun Color.legibleOnLight(): Color {
    if (alpha >= LEGIBLE_LIGHT_FILL) return this
    val under = (LEGIBLE_LIGHT_FILL - alpha) / (1f - alpha)
    return compositeOver(Color(0xFFFAFAFA).copy(alpha = under))
}
