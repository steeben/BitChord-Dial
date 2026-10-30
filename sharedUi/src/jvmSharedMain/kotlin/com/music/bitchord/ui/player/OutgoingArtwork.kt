package com.music.bitchord.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
    val history = remember { ArtworkHistory() }
    // Decided in composition, in the very pass that hands the sleeve its new
    // cover. A new cover that is already in memory draws in that same frame,
    // so an overlay that waited for an effect to start arrived one frame late
    // and the new cover flashed up whole before the old one came back over it.
    val fade = remember(art) {
        // A skip arriving mid-fade fades from whichever of the two is most
        // on screen, rather than snapping back to the older one.
        val previous = history.fade
        val from = previous?.from?.takeIf { !previous.done && previous.alpha.value > 0.5f }
            ?: history.settled
        ArtworkFade(from?.takeIf { it !== art && !reduceAnimation }).also { history.fade = it }
    }

    LaunchedEffect(fade) {
        if (fade.from == null) return@LaunchedEffect
        // Hold the old cover until the new full-size one is in, or failing
        // that its small copy, so the fade reveals a picture and not a tile.
        // Bounded, so a cover that never arrives doesn't pin the old one.
        withTimeoutOrNull(FULL_COVER_WAIT_MS) { snapshotFlow { art.loaded }.first { it } }
            ?: withTimeoutOrNull(ANY_COVER_WAIT_MS) {
                snapshotFlow { art.loaded || art.previewLoaded }.first { it }
            }
        fade.alpha.animateTo(0f, tween(ARTWORK_FADE_MS, easing = FastOutSlowInEasing))
        fade.done = true
    }
    LaunchedEffect(art) {
        snapshotFlow { art.loaded || art.previewLoaded }.first { it }
        history.settled = art
    }

    val leaving = fade.from?.takeIf { !fade.done } ?: return
    val painter = leaving.painter ?: leaving.previewPainter ?: return
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.graphicsLayer { alpha = fade.alpha.value },
    )
}

/** One cover's fade out: [from] is null when there is nothing to fade. */
@Stable
private class ArtworkFade(val from: PlayerArtwork?) {
    val alpha = Animatable(1f)
    var done by mutableStateOf(false)
}

/** What [OutgoingArtwork] remembers across covers. Not state: nothing draws from it. */
private class ArtworkHistory {
    /** The last cover that actually reached the screen. */
    var settled: PlayerArtwork? = null
    var fade: ArtworkFade? = null
}

private const val FULL_COVER_WAIT_MS = 400L
private const val ANY_COVER_WAIT_MS = 1600L
private const val ARTWORK_FADE_MS = 700
