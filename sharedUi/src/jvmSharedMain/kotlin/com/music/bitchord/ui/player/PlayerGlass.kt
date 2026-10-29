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
     * A glass surface in [shape] on [modifier], washed with [tint] in place of
     * the theme's own when one is given.
     */
    @Composable
    fun surface(modifier: Modifier, shape: CornerBasedShape, tint: Color? = null): Modifier
}

val LocalPlayerGlass = staticCompositionLocalOf<PlayerGlass?> { null }
