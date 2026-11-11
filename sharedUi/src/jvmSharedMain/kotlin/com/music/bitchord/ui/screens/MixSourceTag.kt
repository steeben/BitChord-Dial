package com.music.bitchord.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.spotify.SPOTIFY_PAGE_PREFIX
import com.music.bitchord.ui.graphics.ColorUtils
import com.music.bitchord.ui.theme.rememberArtworkDominant

/** The app a mix on the Mixes Dial is from. */
internal enum class MixSource { SPOTIFY, YT_MUSIC }

/** Which app [item] is from: Spotify's tiles open a Spotify page, every other is YouTube Music's. */
internal fun mixSourceOf(item: ShelfItem): MixSource =
    if (item.browseId?.startsWith(SPOTIFY_PAGE_PREFIX) == true) MixSource.SPOTIFY else MixSource.YT_MUSIC

/**
 * The tag in a mix tile's top left corner saying which app the mix is from:
 * Spotify's mark or YouTube Music's, in white on a darkened colour of the
 * tile's own cover ([mixTagGround]), so it reads as part of the artwork
 * rather than as a sticker on it. It sits flush in the corner, where the
 * tile's own clip rounds it, with its inner corner rounded to match, and is
 * opaque: it is also what hides the watermark printed in that corner of the
 * cover.
 */
@Composable
internal fun MixSourceTag(source: MixSource, artworkUrl: String?, modifier: Modifier = Modifier) {
    val cover = rememberArtworkDominant(artworkUrl)
    // A cover read before is its colour from the first frame; one still being
    // read starts neutral and settles into it.
    val ground by animateColorAsState(
        targetValue = cover?.let { Color(mixTagGround(it.toArgb())) } ?: TAG_GROUND_UNREAD,
        animationSpec = tween(TAG_FADE_MS),
        label = "mixTagGround",
    )
    Canvas(
        modifier
            .size(TAG_SIZE)
            .clip(RoundedCornerShape(bottomEnd = 10.dp))
            .background(ground),
    ) {
        when (source) {
            MixSource.SPOTIFY -> spotifyMark()
            MixSource.YT_MUSIC -> youTubeMusicMark()
        }
    }
}

/**
 * The tag's ground for a cover that is mostly [coverArgb]: the same hue, held
 * dark and no more than moderately saturated, then darkened further until the
 * white mark stands clear of it. A grey cover stays grey, and a near-black one
 * is lifted just enough for the tag to show as a shape on it.
 */
fun mixTagGround(coverArgb: Int): Int {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(coverArgb, it) }
    hsl[1] = hsl[1].coerceIn(0f, TAG_MAX_SATURATION)
    hsl[2] = hsl[2].coerceIn(TAG_MIN_LIGHTNESS, TAG_MAX_LIGHTNESS)
    var ground = ColorUtils.HSLToColor(hsl)
    // Lightness is not brightness: a yellow or green at a lightness a blue is
    // dark at is still bright, so those are taken down until the mark reads.
    while (whiteContrast(ground) < TAG_MARK_CONTRAST && hsl[2] > TAG_MIN_LIGHTNESS) {
        hsl[2] = (hsl[2] - 0.02f).coerceAtLeast(TAG_MIN_LIGHTNESS)
        ground = ColorUtils.HSLToColor(hsl)
    }
    return ground or 0xFF000000.toInt()
}

/** The WCAG contrast of white on [argb]. */
internal fun whiteContrast(argb: Int): Double = 1.05 / (ColorUtils.calculateLuminance(argb) + 0.05)

private val TAG_SIZE = 24.dp

/** The ground while the cover's colour is still being read. */
private val TAG_GROUND_UNREAD = Color(0xFF26262B)
private const val TAG_FADE_MS = 260

private const val TAG_MAX_SATURATION = 0.6f
private const val TAG_MIN_LIGHTNESS = 0.18f
private const val TAG_MAX_LIGHTNESS = 0.32f

/** How far the white mark must stand off the ground. */
private const val TAG_MARK_CONTRAST = 4.5

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
