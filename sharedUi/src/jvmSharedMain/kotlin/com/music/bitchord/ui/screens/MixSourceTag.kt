package com.music.bitchord.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.spotify.SPOTIFY_PAGE_PREFIX

/** The app a mix on the Mixes Dial is from, and the colour its tag is filled with. */
internal enum class MixSource(val ground: Color) {
    SPOTIFY(Color(0xFF1ED760)),
    YT_MUSIC(Color(0xFFFF0000)),
}

/** Which app [item] is from: Spotify's tiles open a Spotify page, every other is YouTube Music's. */
internal fun mixSourceOf(item: ShelfItem): MixSource =
    if (item.browseId?.startsWith(SPOTIFY_PAGE_PREFIX) == true) MixSource.SPOTIFY else MixSource.YT_MUSIC

/**
 * The tag in a mix tile's top left corner saying which app the mix is from:
 * Spotify's mark on green, or YouTube Music's on red. It sits flush in the
 * corner, where the tile's own clip rounds it, with its inner corner rounded
 * to match.
 */
@Composable
internal fun MixSourceTag(source: MixSource, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .size(TAG_SIZE)
            .clip(RoundedCornerShape(bottomEnd = 8.dp))
            .background(source.ground),
    ) {
        when (source) {
            MixSource.SPOTIFY -> spotifyMark()
            MixSource.YT_MUSIC -> youTubeMusicMark()
        }
    }
}

private val TAG_SIZE = 22.dp

/** Both marks are drawn on a 24 × 24 grid, scaled to the tag. */
private const val GRID = 24f

/** Spotify's three bowed lines, widest and heaviest on top. */
private fun DrawScope.spotifyMark() {
    val u = size.minDimension / GRID
    fun wave(fromX: Float, fromY: Float, bowY: Float, toX: Float, toY: Float, width: Float) {
        val path = Path().apply {
            moveTo(fromX * u, fromY * u)
            quadraticTo(12f * u, bowY * u, toX * u, toY * u)
        }
        drawPath(path, Color.White, style = Stroke(width = width * u, cap = StrokeCap.Round))
    }
    wave(5.4f, 9.0f, 5.6f, 18.6f, 10.2f, 2.3f)
    wave(6.3f, 12.7f, 9.9f, 17.4f, 13.7f, 2.0f)
    wave(7.2f, 16.1f, 13.9f, 16.2f, 16.9f, 1.7f)
}

/** YouTube Music's ring with the play triangle inside it. */
private fun DrawScope.youTubeMusicMark() {
    val u = size.minDimension / GRID
    val centre = Offset(size.width / 2f, size.height / 2f)
    drawCircle(Color.White, radius = 6.6f * u, center = centre, style = Stroke(width = 1.5f * u))
    val play = Path().apply {
        moveTo(10.1f * u, 8.6f * u)
        lineTo(15.4f * u, 12f * u)
        lineTo(10.1f * u, 15.4f * u)
        close()
    }
    drawPath(play, Color.White)
}
