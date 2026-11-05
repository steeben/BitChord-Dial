package com.music.bitchord.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.dp

/** How the dials' tiles are edged. */
enum class DialTileStyle {
    /** The plain hairline every thumbnail has. */
    NONE,

    /** A bevelled rim that catches the light along the top. */
    EDGE,

    /** Liquid glass's own rim, bending the artwork slightly along the edge, with none of its blur. */
    GLASS,
}

val LocalDialTileStyle = staticCompositionLocalOf { DialTileStyle.NONE }

/**
 * The edge [DialTileStyle.GLASS] draws, handed down by the app, which owns the
 * glass. Without one, glass tiles fall back to the [DialTileStyle.EDGE] rim.
 */
val LocalDialTileGlassEdge = staticCompositionLocalOf<((Shape) -> Modifier)?> { null }

/**
 * How far past its cell a tile reaches on each side, set by the app: glass
 * tiles set their artwork inside a rim of glass, and grow outward by most of
 * that rim so the art inside stays close to the size the other styles show.
 * Zero while tiles aren't glass, or the glass is drawn without its rim.
 */
val LocalDialTileOutset = staticCompositionLocalOf { 0.dp }

/**
 * A dial tile drawn [LocalDialTileOutset] past its cell on every side, the
 * cell itself, and so the grid, left where it was. Goes before the tile's
 * clip, so the clip grows with it.
 */
fun Modifier.dialTileOutset(): Modifier = composed {
    val outset = LocalDialTileOutset.current
    if (outset <= 0.dp) return@composed Modifier
    Modifier.layout { measurable, constraints ->
        val grow = outset.roundToPx()
        val placeable = measurable.measure(constraints.offset(2 * grow, 2 * grow))
        layout(
            (placeable.width - 2 * grow).coerceAtLeast(0),
            (placeable.height - 2 * grow).coerceAtLeast(0),
        ) {
            placeable.place(-grow, -grow)
        }
    }
}

/**
 * A dial tile's edge, as [LocalDialTileStyle] says: the hairline every
 * thumbnail has; a bevelled rim that catches the light — bright along the top,
 * fading down the sides, a faint return along the foot — with a soft sheen
 * just inside it; or liquid glass's rim, bending the art along the edge without
 * blurring it.
 */
fun Modifier.dialTileBorder(shape: Shape): Modifier = composed {
    val glassEdge = LocalDialTileGlassEdge.current
    when (LocalDialTileStyle.current) {
        DialTileStyle.NONE -> return@composed thumbnailBorder(shape)
        DialTileStyle.GLASS -> if (glassEdge != null) return@composed glassEdge(shape)
        DialTileStyle.EDGE -> Unit
    }
    drawWithCache {
        val rimWidth = 1.5.dp.toPx()
        // Inset by half the stroke so the whole rim sits inside the clip.
        val outline = shape.createOutline(
            size.copy(width = size.width - rimWidth, height = size.height - rimWidth),
            layoutDirection,
            this,
        )
        val rim = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.55f),
            0.18f to Color.White.copy(alpha = 0.22f),
            0.5f to Color.White.copy(alpha = 0.07f),
            0.85f to Color.White.copy(alpha = 0.05f),
            1f to Color.White.copy(alpha = 0.16f),
        )
        val sheen = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.10f),
            0.12f to Color.Transparent,
        )
        onDrawWithContent {
            drawContent()
            drawRect(sheen)
            translate(rimWidth / 2f, rimWidth / 2f) {
                drawOutline(outline, rim, style = Stroke(rimWidth))
            }
        }
    }
}
