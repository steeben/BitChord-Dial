package com.music.bitchord.ui.player

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Liquid glass for the player's own buttons, when the app has it switched on.
 *
 * The player is a window of its own over the app, so the app's glass (which
 * bends the app's pages) has nothing of the player to see through. This lets
 * the player hand its backdrop — the artwork's colours, the blur, the clip —
 * to [source], and draw buttons that bend it with [surface]. Null when glass
 * is off: the buttons keep their plain translucent discs.
 */
@Stable
interface PlayerGlass {
    /** Records what is drawn by [modifier]'s node, for [surface] to see through. */
    fun source(modifier: Modifier): Modifier

    /**
     * Records the whole player as it is drawn by [modifier]'s node, controls,
     * title and sleeve included, for a [surface] that is a menu to see
     * through. [source] holds only what is behind the buttons, so a drawer
     * bending that showed a wash of the background with the controls gone.
     * The drawers themselves are drawn outside the node, never inside it.
     */
    fun menuSource(modifier: Modifier): Modifier = modifier

    /**
     * A glass surface in [shape] on [modifier], as the app's glass options
     * have it. [underlay] is a scrim the surface sits on that the backdrop
     * does not record, laid over what the glass samples so the glass is no
     * brighter than the dimmed page around it.
     *
     * [menu] is for a surface text is read off, a drawer: it is then the
     * same glass every menu in the app is made of. [darkGround] says whether
     * what it sits on is dark, which on the player is the sleeve's to say
     * and not the theme's; null leaves it to the theme.
     */
    @Composable
    fun surface(
        modifier: Modifier,
        shape: CornerBasedShape,
        underlay: Color = Color.Transparent,
        menu: Boolean = false,
        darkGround: Boolean? = null,
    ): Modifier
}

val LocalPlayerGlass = staticCompositionLocalOf<PlayerGlass?> { null }

/** [PlayerGlass.menuSource] on this node, where the player has glass at all. */
@Composable
internal fun Modifier.playerMenuSource(): Modifier = LocalPlayerGlass.current?.menuSource(this) ?: this

/**
 * Where on screen (window coordinates) the thing that opened a popup over the
 * player was tapped, for the popup to grow out of and shrink back into. Null
 * when there is no such place: the popup then grows from its own middle.
 */
val LocalPlayerPopupOrigin = staticCompositionLocalOf<Offset?> { null }

/**
 * Whether the player's sleeve is bright enough that its text went dark
 * ([ProvidePlayerInk]'s verdict), for what the app draws over the player from
 * outside it: the ⋯ menu, which is light or dark as the ground under it is.
 */
@Stable
class PlayerSleeveTone {
    var bright by mutableStateOf(false)
}

val LocalPlayerSleeveTone = staticCompositionLocalOf<PlayerSleeveTone?> { null }

/**
 * Whether the app is in its light theme, told by the app. The player picks its
 * ink off the sleeve in either theme, but in the light one a bright sleeve is
 * left bright under dark text sooner than it is darkened for light text.
 */
val LocalPlayerLightTheme = staticCompositionLocalOf { false }
