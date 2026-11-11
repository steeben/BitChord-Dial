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
 * banner. White is the rule and black the exception: the glyphs only go black
 * over a banner that is very bright (a white or cream sleeve, a pale sky),
 * where white would be lost. A mid-bright photograph (sunlit grass, about
 * 0.4) keeps white, which reads well on it; the earlier 0.30 flipped there.
 */
internal const val CHROME_DARK_BELOW = 0.60f

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

/**
 * How bright the top of a banner is when a moving clip may be playing over its
 * still picture: [still] is the picture's top band, [clip] the latest frame's
 * (null until one has been read) and [clipShowing] how far the clip has faded
 * in over the picture, 0 to 1. Artist banners are often clips whose opening
 * frames look nothing like the photo under them.
 */
internal fun bannerArtLuminance(still: Float?, clip: Float?, clipShowing: Float): Float? {
    val showing = clipShowing.coerceIn(0f, 1f)
    if (clip == null || showing <= 0f) return still
    if (still == null) return clip
    return still + (clip - still) * showing
}

/**
 * The share of a clip frame's height, from its top, read for [bannerArtLuminance]:
 * the status bar and the top controls together, on a banner a little over half
 * the screen tall.
 */
internal const val CLIP_TOP_BAND = 0.25f

/** How often a playing banner clip is read again, so the controls follow it as it plays. */
internal const val CLIP_TONE_REFRESH_MS = 1_000L
