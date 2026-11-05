package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop

/**
 * What the dial's glass tiles see through: the colour behind the pages (the
 * window's floor and the ambient blobs) without the pages themselves. The
 * tiles sit inside the pages, which are what [LocalAppBackdrop] records, so
 * they cannot sample that layer without sampling themselves.
 */
val LocalDialTileBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * The colour behind the pages, drawn under them: the ambient blobs, and,
 * while the dial's tiles are glass, the window's floor under those, recorded
 * into [LocalDialTileBackdrop] for the tiles to bend.
 *
 * Only recorded while something reads it, as [LocalAppBackdrop] is: Glass
 * tiles, on a phone that can draw the glass, without "reduce dynamic blur".
 */
@Composable
fun DialTileBackdropLayer(blobs: AmbientBlobs?) {
    val backdrop = LocalDialTileBackdrop.current
    val style by CustomizationSettings.dialTileStyle.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val record = backdrop != null && style == DialTileStyle.GLASS && isGlassSupported() && !reduceDynamicBlur
    Box(
        Modifier
            .fillMaxSize()
            .then(if (record && backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
            .ambientBlobs(blobs),
    )
}
