package com.music.bitchord.ui.player

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.glassMenu
import com.music.bitchord.ui.components.liquidGlass

/**
 * The app's liquid glass, aimed at the player's own backdrop rather than the
 * pages behind the player's window. See [PlayerGlass].
 */
class AppPlayerGlass(
    private val backdrop: LayerBackdrop,
    /**
     * The whole player, for its drawers and popups: [backdrop] is only what
     * is behind the buttons. Null where there is one backdrop for both.
     */
    private val menuBackdrop: LayerBackdrop? = null,
) : PlayerGlass {
    override fun source(modifier: Modifier): Modifier = modifier.layerBackdrop(backdrop)

    override fun menuSource(modifier: Modifier): Modifier =
        if (menuBackdrop != null) modifier.layerBackdrop(menuBackdrop) else modifier

    @Composable
    override fun surface(
        modifier: Modifier,
        shape: CornerBasedShape,
        underlay: Color,
        menu: Boolean,
        darkGround: Boolean?,
        button: Boolean,
    ): Modifier = if (menu) {
        modifier.glassMenu(shape, backdrop = menuBackdrop ?: backdrop, underlay = underlay, dark = darkGround)
    } else {
        modifier.liquidGlass(
            shape,
            backdrop = backdrop,
            exportedBackdrop = null,
            underlay = underlay,
            dark = darkGround,
            playerButton = button,
        )
    }
}
