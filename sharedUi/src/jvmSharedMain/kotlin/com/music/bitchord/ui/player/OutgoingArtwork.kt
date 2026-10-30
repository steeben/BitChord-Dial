package com.music.bitchord.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The previous track's cover, left on top of [art] and faded out once [art]
 * has something to show — so a skip dissolves from one sleeve into the next
 * rather than cutting to an empty tile and fading the new cover in over it.
 *
 * Drawn over the sleeve's own image, inside its clip, so it takes the sleeve's
 * shape, shrink and banner fade for free. The new cover underneath is drawn
 * exactly as it always was; this only decides how long the old one lingers.
 *
 * Two tracks off one album share a [PlayerArtwork] (it is keyed on the cover),
 * so skipping between them has nothing to fade and doesn't.
 */
@Composable
internal fun OutgoingArtwork(art: PlayerArtwork, modifier: Modifier = Modifier) {
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    // The last cover that actually reached the screen.
    var settled by remember { mutableStateOf<PlayerArtwork?>(null) }
    var outgoing by remember { mutableStateOf<PlayerArtwork?>(null) }
    val fade = remember { Animatable(1f) }

    // Declared before the effect that settles [art], so it reads [settled]
    // as the previous cover even when the new one is a memory-cache hit.
    LaunchedEffect(art) {
        // A skip arriving mid-fade fades from whichever of the two is most
        // on screen, rather than snapping back to the older one.
        val from = outgoing?.takeIf { fade.value > 0.5f } ?: settled
        if (from == null || from === art || reduceAnimation) {
            outgoing = null
            return@LaunchedEffect
        }
        outgoing = from
        fade.snapTo(1f)
        // Hold the old cover until the new full-size one is in, or failing
        // that its small copy, so the fade reveals a picture and not a tile.
        // Bounded, so a cover that never arrives doesn't pin the old one.
        withTimeoutOrNull(FULL_COVER_WAIT_MS) { snapshotFlow { art.loaded }.first { it } }
            ?: withTimeoutOrNull(ANY_COVER_WAIT_MS) {
                snapshotFlow { art.loaded || art.previewLoaded }.first { it }
            }
        fade.animateTo(0f, tween(ARTWORK_FADE_MS, easing = FastOutSlowInEasing))
        outgoing = null
    }
    LaunchedEffect(art) {
        snapshotFlow { art.loaded || art.previewLoaded }.first { it }
        settled = art
    }

    val leaving = outgoing ?: return
    val painter = leaving.painter ?: leaving.previewPainter ?: return
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.graphicsLayer { alpha = fade.value },
    )
}

private const val FULL_COVER_WAIT_MS = 400L
private const val ANY_COVER_WAIT_MS = 1600L
private const val ARTWORK_FADE_MS = 700
