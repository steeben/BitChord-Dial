package com.music.bitchord.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the floating top controls and the status bar are sitting on, when a
 * page knows: an artist, album or playlist page says whether the top of its
 * banner is dark. The controls and the status icons then go white over a dark
 * banner and black over a bright one, whatever the app's theme is; a light
 * theme's black glyphs could not be seen on a dark photograph.
 *
 * Kept by owner, since the page arriving sets it before the page leaving has
 * gone: a page only takes back what it put there itself.
 */
object TopChromeTone {
    /** Whether the page under the top controls is dark there; null follows the theme. */
    var overDark by mutableStateOf<Boolean?>(null)
        private set
    private var owner: Any? = null

    fun set(by: Any, dark: Boolean?) {
        owner = by
        overDark = dark
    }

    fun clear(by: Any) {
        if (owner !== by) return
        owner = null
        overDark = null
    }
}

/** True under the top bar of a page that wears its artwork: the one place [TopChromeTone] applies. */
val LocalOnArtworkPage = staticCompositionLocalOf { false }

/**
 * Relative luminance (WCAG, 0..1) under which white glyphs are used over a
 * banner. Black and white read equally well at about 0.18; a little above
 * that, since the controls' glass lifts what is behind it.
 */
internal const val CHROME_DARK_BELOW = 0.30f

/**
 * Whether what is under the top controls is dark. [artLuminance] is the top
 * of the banner picture (null until it has been read), [washLuminance] the
 * page colour it is laid on, and [artShowing] how much of the picture is
 * still up there: 1 at the top of the page, less as it fades or scrolls off.
 */
internal fun topChromeOverDark(artLuminance: Float?, washLuminance: Float, artShowing: Float): Boolean? {
    if (artLuminance == null) return null
    val showing = artShowing.coerceIn(0f, 1f)
    val under = washLuminance + (artLuminance - washLuminance) * showing
    return under < CHROME_DARK_BELOW
}
