package com.music.bitchord.ui.player

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
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
