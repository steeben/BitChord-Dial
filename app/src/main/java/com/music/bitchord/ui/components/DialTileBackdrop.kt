package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop

/**
 * A layer the colour behind the dial's pages could be recorded into. The
 * Glass tiles used to bend it; they now bend their own artwork (see
 * [dialTileGlassEdge]), so nothing records into it any more.
 */
val LocalDialTileBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** The colour behind the pages, drawn under them: the ambient blobs. */
@Composable
fun DialTileBackdropLayer(blobs: AmbientBlobs?) {
    Box(Modifier.fillMaxSize().ambientBlobs(blobs))
}
