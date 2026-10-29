package com.music.bitchord.ui.player

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.liquidGlass

/**
 * The app's liquid glass, aimed at the player's own backdrop rather than the
 * pages behind the player's window. See [PlayerGlass].
 */
class AppPlayerGlass(private val backdrop: LayerBackdrop) : PlayerGlass {
    override fun source(modifier: Modifier): Modifier = modifier.layerBackdrop(backdrop)

    @Composable
    override fun surface(modifier: Modifier, shape: CornerBasedShape, tint: Color?): Modifier =
        modifier.liquidGlass(shape, backdrop = backdrop, exportedBackdrop = null, surfaceTint = tint)
}
