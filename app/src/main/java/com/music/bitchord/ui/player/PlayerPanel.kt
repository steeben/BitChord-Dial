package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.AudioOnlyVideos
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.ui.components.optimizedHazeEffect
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Shape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.music.bitchord.ui.components.ExplicitSongTitle
import com.music.bitchord.ui.components.MiniPlayerNextGlyph
import com.music.bitchord.ui.components.PlayPauseGlyph
import androidx.compose.foundation.shape.CornerBasedShape
import com.music.bitchord.ui.components.GLASS_EDGE_WIDTH
import com.music.bitchord.ui.components.glassContentColor
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.components.surfaceEdgeColor
import com.music.bitchord.ui.components.liquidGlass
import com.music.bitchord.ui.components.miniPlayerProgress
import com.music.bitchord.ui.components.pageAccent
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.components.rememberRemoteArtworkUrl
import com.music.bitchord.ui.components.thumbnailContentScale
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.MiniPlayerShape
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlin.math.roundToInt

/**
 * Up to this much of the open fraction the pill's own row is still on the
 * panel, and the pill's material still under the player's plain fill. Shorter
 * in a light theme: the pill there is pale and the player dark, so the less
 * of the movement spent between them, the less of it is a muddy grey.
 */
private const val PILL_UNTIL_DARK = 0.2f
private const val PILL_UNTIL_LIGHT = 0.12f

/** What the panel is filled with once it has left the pill behind. */
private val PanelFill = Color(0xFF151517)

/** The corner the panel rounds to on its way up, as a sheet's top corners do. */
private val SHEET_CORNER = 22.dp

/** The panel's radius this frame, from the dock — read at draw. */
private class PanelCorner(private val dock: PlayerDock) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float =
        dock.panelCornerPx(SHEET_CORNER.value * density.density, density.density)
}

/**
 * The mini player's pill and the full player as one surface, in the page's
 * own window.
 *
 * The container wears the pill's material — the bar's liquid glass, or the
 * plain mini player's surface — and its width, height and corners follow
 * [PlayerDock.panelBounds]: it starts as the pill, exactly where the pill is,
 * and grows to the whole window. The pill's row fades off it, the player's
 * plain fill comes over its material, and the player fades in, its cover
 * flying out of the pill's (see the sleeve in NowPlayingScreen). The real
 * pill stands aside the whole time this is up (see [PlayerDock.panelShown]),
 * so there is never a second one to see.
 */
@Composable
fun PlayerPanel(
    dock: PlayerDock,
    motion: PlayerSheetMotion,
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    /** Whether the pill underneath is the glass bar's rather than the plain mini player. */
    glass: Boolean,
    /** The app's pages, for the plain mini player's frosted blur, which the panel wears as the pill does. */
    hazeState: HazeState,
    /** The playhead, drawn along the pill's edge as the pill draws it; read only when drawing. */
    position: PlaybackPosition?,
    durationMs: Long,
    scrimAlpha: Float,
    onLaidOut: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
    /** Drawn above the panel, unclipped: the ⋯ menu. */
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    // Whatever takes the panel away, the pill comes back with it.
    DisposableEffect(dock) {
        onDispose { dock.releasePanel() }
    }
    val full = remember { arrayOf(IntSize.Zero) }
    val shape = remember(dock) { RoundedCornerShape(PanelCorner(dock)) }
    val lightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val pillUntil = if (lightTheme) PILL_UNTIL_LIGHT else PILL_UNTIL_DARK
    // Fully up, the player's own background covers everything: no material,
    // no fill, nothing under it to pay for.
    val covered by remember(dock) {
        derivedStateOf { dock.attached && dock.openFraction() >= 1f }
    }
    val pillVisible by remember(dock, pillUntil) {
        derivedStateOf { dock.panelFill(pillUntil) < 1f }
    }
    val material = when {
        covered || !pillVisible -> Modifier
        glass -> Modifier.liquidGlass(shape)
        else -> Modifier.plainPillSurface(shape, hazeState)
    }

    Box(
        modifier
            .fillMaxSize()
            // The window's size, taken before anything inside is measured:
            // the player is laid out at it whatever size the panel is.
            .layout { measurable, constraints ->
                full[0] = IntSize(constraints.maxWidth, constraints.maxHeight)
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .onSizeChanged(onLaidOut)
            .onGloballyPositioned { dock.panelPlaced(it) }
            .drawBehind {
                val open = dock.openFraction()
                // Not under a player that is fully up: its own background is
                // opaque, and a full-screen blend behind it would be paid on
                // every frame for nothing anyone can see.
                if (open > 0f && !(open >= 1f && dock.attached)) {
                    drawRect(Color.Black.copy(alpha = scrimAlpha * open))
                }
            },
    ) {
        Box(
            Modifier
                .panelBounds(dock)
                // The press that started the pull swelled the pill, and the
                // panel takes it over still swollen: it wears the same swell,
                // drawn as the pill draws it, and lets it go as the press does
                // and as it grows, rather than snapping to the pill's resting size.
                .drawWithContent {
                    val swell = dock.pillPress() - 1f
                    val factor = 1f + swell * (1f - dock.openFraction() / pillUntil).coerceIn(0f, 1f)
                    scale(factor) { this@drawWithContent.drawContent() }
                }
                .then(material)
                .playerPullDown(motion),
        ) {
            if (!covered) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = dock.panelFill(pillUntil) }
                        .background(PanelFill, shape),
                )
            }
            Box(Modifier.fillMaxSize().clip(shape)) {
                Box(
                    Modifier
                        .windowSized(dock) { full[0] }
                        .graphicsLayer {
                            // Until the player has placed itself it can't fly
                            // its cover, and its first frame would be the full
                            // player squeezed into the pill: the pill's own row
                            // stands in for it. Once it takes part, it fades
                            // itself and flies its cover outside that fade.
                            alpha = if (dock.attached) 1f else (dock.openFraction() / pillUntil).coerceIn(0f, 1f)
                        },
                ) { content() }
                if (pillVisible) {
                    val density = LocalDensity.current
                    val windowWidth = LocalWindowInfo.current.containerSize.width
                    val pill = remember(dock) { dock.miniPillInWindow() }
                    val rowHeight = with(density) { (pill?.height ?: 0f).toDp() }.takeIf { it > 0.dp } ?: 56.dp
                    // Docked inline in the tab bar, the glass pill is narrow
                    // and drops its artist line and skip button; the row
                    // does the same.
                    val inline = glass && pill != null && pill.width < windowWidth * 0.8f
                    PanelMiniRow(
                        dock = dock,
                        song = song,
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        glass = glass,
                        inline = inline,
                        onTap = motion::open,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .height(rowHeight)
                            .graphicsLayer {
                                alpha = (1f - dock.openFraction() / pillUntil).coerceIn(0f, 1f)
                            },
                    )
                }
            }
            if (pillVisible) {
                // The pill's playhead, along the panel's own bottom edge and
                // round its corners as they grow, fading with the pill's row.
                val playhead = pageAccent()
                val px = LocalDensity.current.density
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            alpha = (1f - dock.openFraction() / pillUntil).coerceIn(0f, 1f)
                        }
                        .miniPlayerProgress(
                            position = position,
                            durationMs = durationMs,
                            color = playhead,
                            glow = glass,
                            shape = shape,
                            corner = { _ ->
                                if (glass) dock.panelCornerPx(SHEET_CORNER.value * px, px) else 0f
                            },
                        ),
                )
            }
        }
        overlay()
    }
}

/**
 * The plain mini player's surface, as [com.music.bitchord.ui.components.MiniPlayer]
 * wears it: the pages behind seen through a frosted blur, with a hairline rim.
 * A solid fill here made the pill turn opaque the moment the panel took over
 * from it and see-through again the moment it handed back.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun Modifier.plainPillSurface(shape: CornerBasedShape, hazeState: HazeState): Modifier {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    return clip(shape)
        .then(
            when {
                isGlassSupported() -> Modifier.liquidGlass(shape)
                reduceDynamicBlur -> Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .border(GLASS_EDGE_WIDTH, surfaceEdgeColor(), shape)
                else -> Modifier
                    .optimizedHazeEffect(
                        state = hazeState,
                        style = HazeMaterials.thin(MaterialTheme.colorScheme.surface),
                    )
                    .border(GLASS_EDGE_WIDTH, surfaceEdgeColor(), shape)
            },
        )
}

/** The container's place this frame: size and position come from the dock. */
private fun Modifier.panelBounds(dock: PlayerDock): Modifier = layout { measurable, constraints ->
    val window = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
    val rect = dock.panelBounds(window)
    val width = rect.width.roundToInt().coerceAtLeast(1)
    val height = rect.height.roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
    }
}

/**
 * The full player, laid out at the whole window's size whatever the panel is,
 * and put back where the window's left edge is, so the panel reveals it rather
 * than squeezing it.
 */
private fun Modifier.windowSized(dock: PlayerDock, full: () -> IntSize): Modifier =
    layout { measurable, constraints ->
        val size = full().takeIf { it.width > 0 && it.height > 0 }
            ?: IntSize(constraints.maxWidth, constraints.maxHeight)
        val placeable = measurable.measure(Constraints.fixed(size.width, size.height))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val rect = dock.panelBounds(Size(size.width.toFloat(), size.height.toFloat()))
            placeable.place(-rect.left.roundToInt(), 0)
        }
    }

/**
 * The pill's row, on the panel: the same cover slot, title, artist and
 * transport as the pill, where the pill had them, so the hand-over from the
 * pill to the panel can't be seen. For looking at only: the cover itself is
 * the player's, flying, and a tap anywhere on it brings the player back up.
 */
@Composable
private fun PanelMiniRow(
    dock: PlayerDock,
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    glass: Boolean,
    inline: Boolean,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    val showNext by CustomizationSettings.miniPlayerNextButton.collectAsStateWithLifecycle()
    val audioOnlyVideos by AudioOnlyVideos.ids.collectAsStateWithLifecycle()
    val contentColor = if (glass) glassContentColor() else MaterialTheme.colorScheme.onBackground
    val secondary = if (glass) contentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
    val artSize = if (inline) 32.dp else 40.dp
    val artWidth = if (!glass && song.isVideo && song.videoId !in audioOnlyVideos) 71.dp else artSize
    val shapeChoice by CustomizationSettings.miniPlayerShape.collectAsStateWithLifecycle()
    val circular = shapeChoice == MiniPlayerShape.CIRCULAR
    val artCorner = when {
        circular -> if (glass) artSize / 2 else 20.dp
        glass && inline -> 6.dp
        else -> 8.dp
    }
    val artUrl = rememberRemoteArtworkUrl(song)
    val glyphSlot = if (inline) 32.dp else 40.dp
    val glyphSize = if (inline) 24.dp else 32.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onTap,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (inline) 8.dp else 12.dp,
                    vertical = if (inline) 4.dp else 8.dp,
                ),
        ) {
            // The pill's cover, until the player's own takes off from here; it
            // stands aside for as long as that one is flying.
            AsyncImage(
                model = artUrl?.artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = if (song.isVideo) ContentScale.Crop else thumbnailContentScale(),
                modifier = Modifier
                    .size(width = artWidth, height = artSize)
                    .graphicsLayer { alpha = if (dock.coversMiniArt) 0f else 1f }
                    .clip(RoundedCornerShape(artCorner))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(if (inline) 8.dp else 10.dp))
            if (inline) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Column(Modifier.weight(1f)) {
                    ExplicitSongTitle(song = song, style = MaterialTheme.typography.titleMedium, color = contentColor)
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(Modifier.size(glyphSlot), contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = contentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(if (inline) 18.dp else 22.dp),
                    )
                } else {
                    PlayPauseGlyph(
                        isPlaying = isPlaying,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(glyphSize),
                    )
                }
            }
            if (!inline && showNext) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(glyphSlot), contentAlignment = Alignment.Center) {
                    MiniPlayerNextGlyph(tint = contentColor)
                }
            }
        }
    }
}

/**
 * Drag the open player down to put it away, as the modal sheet it used to be
 * did. A drag nothing inside claims, and the pull a list leaves over when it
 * is already at its top, both move the panel through [PlayerSheetMotion]: the
 * same motion the pill's pull and every close run on.
 */
@Composable
internal fun Modifier.playerPullDown(motion: PlayerSheetMotion): Modifier {
    val connection = remember(motion) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Back up first: a pulled panel returns before the list scrolls again.
                if (!motion.pulling || available.y >= 0f) return Offset.Zero
                return Offset(0f, motion.pullBy(available.y))
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                return Offset(0f, motion.pullBy(available.y))
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!motion.pulling) return Velocity.Zero
                motion.releasePull(available.y)
                return available
            }
        }
    }
    val dragState = rememberDraggableState { delta -> motion.pullBy(delta) }
    return this
        .nestedScroll(connection)
        .draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            onDragStopped = { velocity -> motion.releasePull(velocity) },
        )
}
