package com.music.bitchord.ui.player

import com.music.bitchord.ui.components.menuAnchor
import com.music.bitchord.sharedui.resources.*

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.music.bitchord.ui.theme.HideSystemBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song

/**
 * The widest the two columns are allowed to get between them, centred in
 * whatever is left over.
 *
 * Without a cap the lyric column simply takes every pixel past the artwork,
 * and on a wide window that is a column of text with a hand's width of empty
 * backdrop trailing off the right of every line. Lyrics are read down, not
 * across: past a certain measure the extra width is further for the eye to
 * travel back rather than more room for the words.
 */
private val LANDSCAPE_PLAYER_MAX_WIDTH = 1100.dp

/**
 * Below this height the landscape player tightens its right column — smaller
 * transport, shorter gaps — so a phone on its side fits the credits, the
 * scrubber, the transport and the volume bar between its status and
 * navigation bars without any of them falling off the bottom.
 */
private val LANDSCAPE_COMPACT_HEIGHT = 440.dp

/**
 * Side margin of the landscape player's sleeve column and credits on a
 * phone-height window, in place of [PLAYER_GUTTER].
 */
private val LANDSCAPE_GUTTER_COMPACT = 20.dp

/**
 * How the right column hands over between the player, the lyrics and the
 * queue. The left column never moves, so a crossfade is the whole transition —
 * the same page being turned, not a panel being opened.
 */
private const val LANDSCAPE_PANE_FADE_IN_MS = 220
private const val LANDSCAPE_PANE_FADE_IN_DELAY_MS = 90
private const val LANDSCAPE_PANE_FADE_OUT_MS = 140

/** Which of its three things the player's content column is showing. */
internal enum class PlayerPane { Main, Lyrics, Queue }

/** The landscape sleeve's corner radius, at rest. */
internal val LANDSCAPE_ART_CORNER = 10.dp

/** The landscape sleeve's shape at rest, for a caller with no docking to do. */
private val LandscapeArtShape = RoundedCornerShape(LANDSCAPE_ART_CORNER)

/** Room above the landscape columns for the sheet's drag handle. */
private val LANDSCAPE_HANDLE_STRIP = 24.dp

/**
 * The player in a landscape window — a tablet, or a phone on its side. See
 * [landscapePlayerAvailable].
 *
 * Two columns of equal width. The left one is the same in every state: the
 * sleeve, and under it the lyrics / output / queue row with the output name —
 * the controls for *which* of the three the right column shows. The right one
 * is that one thing: the credits and transport, the lyric sheet or the queue.
 * Nothing else is drawn twice and nothing moves between columns, so opening
 * the lyrics or the queue is only ever the right column's page being turned.
 *
 * Purely the arrangement. Every slot is built by [NowPlayingScreen], which is
 * where the state behind all of them lives — the same state the portrait
 * player reads, so rotating mid-song carries the open panel, the scrub and the
 * translation mode straight across.
 *
 * Held to [LANDSCAPE_PLAYER_MAX_WIDTH] and centred, inside the safe-drawing
 * insets: a phone on its side has its status bar, navigation bar and camera
 * cutout down the short edges, and the player's content keeps clear of all
 * three while its backdrop still runs underneath them.
 */
@Composable
internal fun LandscapePlayerLayout(
    pane: PlayerPane,
    background: @Composable (Modifier) -> Unit,
    /** The sleeve; handed a modifier that already fixes its square size. */
    artwork: @Composable (Modifier) -> Unit,
    /** The row under the sleeve, emitted into its column. */
    actions: @Composable () -> Unit,
    /** Credits and transport. [compact] is a phone-height window. */
    mainPane: @Composable (compact: Boolean) -> Unit,
    lyricsPane: @Composable () -> Unit,
    queuePane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /** Off with "Hide song status", which takes the handle with the caption. */
    showHandle: Boolean = true,
    /**
     * The music video behind the player is full screen: everything but
     * [background] fades away. Whoever sets this also covers the screen with
     * the way back, so nothing faded out can still be touched.
     */
    chromeHidden: Boolean = false,
    /**
     * A tap on the player where nothing else takes it — between the columns,
     * round the sleeve, around the credits — while the main pane is up. Null
     * leaves those taps alone.
     */
    onBackgroundTap: (() -> Unit)? = null,
    /**
     * The lyrics beside the song's music video: the video has the left of the
     * screen to itself (see [LandscapeCanvasBackdrop]'s split) and this, the
     * lyric sheet over the scrubber, has the right. Null for the ordinary two
     * columns. The sleeve, the row under it and the panes all stand down
     * while it is up.
     */
    videoLyrics: (@Composable () -> Unit)? = null,
    /** A tap on the video while [videoLyrics] is up: the way back out of the lyrics. */
    onVideoTap: () -> Unit = {},
) {
    val chromeAlpha by animateFloatAsState(
        targetValue = if (chromeHidden || videoLyrics != null) 0f else 1f,
        animationSpec = tween(LANDSCAPE_CANVAS_DIM_MS),
        label = "landscapeChrome",
    )
    val backgroundTap = if (pane == PlayerPane.Main && !chromeHidden) onBackgroundTap else null
    val currentBackgroundTap by rememberUpdatedState(backgroundTap)
    Box(
        modifier = modifier
            .fillMaxSize()
            // Run after the player's own buttons, sliders and drags, which
            // consume what they take, so only an otherwise unclaimed tap lands.
            .pointerInput(backgroundTap != null) {
                if (backgroundTap != null) {
                    detectTapGestures { currentBackgroundTap?.invoke() }
                }
            },
    ) {
        background(Modifier.fillMaxSize())

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = chromeAlpha }
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            val compact = maxHeight < LANDSCAPE_COMPACT_HEIGHT
            val gutter = if (compact) LANDSCAPE_GUTTER_COMPACT else PLAYER_GUTTER

            Row(
                modifier = Modifier
                    .widthIn(max = LANDSCAPE_PLAYER_MAX_WIDTH)
                    .fillMaxSize()
                    .padding(
                        top = LANDSCAPE_HANDLE_STRIP,
                        bottom = if (compact) 8.dp else 20.dp,
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = gutter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The sleeve is square and takes whichever of the two axes
                    // runs out first once the row below has had its height —
                    // the column's width on a big tablet, the height on
                    // anything shorter. Measured here rather than estimated,
                    // so the row under it can never be pushed off the bottom.
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        artwork(Modifier.size(minOf(maxWidth, maxHeight)))
                    }
                    Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
                    actions()
                }

                AnimatedContent(
                    targetState = pane,
                    transitionSpec = {
                        fadeIn(
                            tween(
                                LANDSCAPE_PANE_FADE_IN_MS,
                                delayMillis = LANDSCAPE_PANE_FADE_IN_DELAY_MS,
                            ),
                        ) togetherWith fadeOut(tween(LANDSCAPE_PANE_FADE_OUT_MS))
                    },
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    label = "landscapePane",
                ) { shown ->
                    when (shown) {
                        PlayerPane.Main -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = gutter),
                            contentAlignment = Alignment.Center,
                        ) {
                            mainPane(compact)
                        }
                        // The lyric sheet and the queue both reach back across
                        // [PLAYER_GUTTER] on their own (see
                        // [bleedHorizontally]), so that is the padding they
                        // get: their scroll areas then run exactly to the
                        // column's edges.
                        PlayerPane.Lyrics -> Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = PLAYER_GUTTER),
                        ) {
                            lyricsPane()
                        }
                        PlayerPane.Queue -> Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = PLAYER_GUTTER),
                        ) {
                            queuePane()
                        }
                    }
                }
            }

            // The sheet closes from a downward drag anywhere nothing else
            // claims, as it does in portrait; this is only the promise.
            if (showHandle && videoLyrics == null) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 8.dp)
                        .width(38.dp)
                        .height(5.dp)
                        .shadow(2.dp, RoundedCornerShape(3.dp), clip = false)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.70f)),
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = videoLyrics != null && !chromeHidden,
            enter = fadeIn(tween(LANDSCAPE_PANE_FADE_IN_MS, delayMillis = LANDSCAPE_PANE_FADE_IN_DELAY_MS)),
            exit = fadeOut(tween(LANDSCAPE_PANE_FADE_OUT_MS)),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Held through the fade out, when the caller's has already gone.
            val held = remember { HeldContent() }
            if (videoLyrics != null) held.content = videoLyrics
            val currentVideoTap by rememberUpdatedState(onVideoTap)
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    // Nothing faded out under this can be touched through it.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                val video = landscapeVideoPane(maxWidth, maxHeight)
                val compactPane = maxHeight < LANDSCAPE_COMPACT_HEIGHT
                Row(Modifier.fillMaxSize()) {
                    // The video itself is the backdrop's; this is the tap on it.
                    Box(
                        Modifier
                            .width(video.width)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClickLabel = stringResource(Res.string.close_lyrics),
                            ) { currentVideoTap() },
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End,
                                ),
                            )
                            .padding(
                                start = PLAYER_GUTTER,
                                end = PLAYER_GUTTER,
                                top = 8.dp,
                                bottom = if (compactPane) 4.dp else 16.dp,
                            ),
                    ) {
                        held.content?.invoke()
                    }
                }
            }
        }
    }
}

/** The last content a slot was given, kept to draw while it fades out. */
private class HeldContent {
    var content: (@Composable () -> Unit)? = null
}

/** How much of the window's width the music video takes beside the lyrics. */
private const val LANDSCAPE_VIDEO_SPLIT = 0.56f

/** How long the video takes to draw aside for the lyrics, and to come back. */
private const val LANDSCAPE_VIDEO_SPLIT_MS = 380

/**
 * The music video's place beside the lyrics, in a window [maxWidth] by
 * [maxHeight]: the left of the screen, top to bottom on a phone. On a
 * squarer window it stops at a square, centred, rather than cropping a wide
 * picture down to a tall sliver.
 */
internal fun landscapeVideoPane(maxWidth: Dp, maxHeight: Dp): DpSize {
    val width = maxWidth * LANDSCAPE_VIDEO_SPLIT
    return DpSize(width, minOf(maxHeight, width))
}

/**
 * The right of the screen with the lyrics beside the music video: the sheet,
 * and under it the scrubber, on the same left and right edges as the lines —
 * in place of the romanize / source / translate row of [LandscapeLyricsPane].
 */
@Composable
internal fun LandscapeVideoLyricsPane(
    hasLyrics: Boolean,
    /** Shown in place of the sheet while there are no lines to draw. */
    placeholder: String,
    picking: Boolean,
    pickBar: @Composable () -> Unit,
    scrubber: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    panel: @Composable (Modifier) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (hasLyrics) {
                panel(Modifier.fillMaxSize())
            } else {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.titleMedium,
                    color = playerInk(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        if (picking) {
            Box(Modifier.fillMaxWidth()) { pickBar() }
        } else {
            Box(Modifier.fillMaxWidth()) { scrubber() }
        }
    }
}

/**
 * The landscape player's sleeve: the still cover, and over it the looping
 * clip when there is one.
 *
 * Draws from the screen's own [request][PlayerArtwork.request] rather than a
 * request of its own, so it shares the portrait sleeve's bitmap, its retries
 * and its [loaded][PlayerArtwork.loaded] — rotating never reloads the cover.
 *
 * The clip keeps playing whichever pane is up. The portrait player stops it
 * because its sleeve shrinks to a thumbnail behind a panel; this one never
 * shrinks.
 */
@Composable
internal fun LandscapeArtwork(
    artRequest: ImageRequest,
    artLoaded: Boolean,
    onArtState: (AsyncImagePainter.State) -> Unit,
    canvas: CanvasArtwork?,
    canvasRendered: Boolean,
    isPlaying: Boolean,
    onCanvasRenderedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Halts the clip for a transition — see [CanvasArtworkPlayer]'s own. */
    pausedForTransition: Boolean = false,
    /**
     * The sleeve's shape, asked for each frame: its corner rounds off into the
     * mini player's cover's as the player docks into it.
     */
    sleeveShape: () -> Shape = { LandscapeArtShape },
    /** How much of the sleeve's shadow is cast, 1 at rest. */
    shadowFraction: () -> Float = { 1f },
    /** The last track's cover fading out over this one — see [OutgoingArtwork]. */
    outgoingArt: @Composable BoxScope.() -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val casts = artLoaded || canvasRendered
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // What `.shadow` and then `.clip` laid down, asked for in the
                // layers' own blocks so a docking player can move them
                // without recomposing the sleeve.
                //
                // Only cast once there is a picture: on the flat placeholder
                // tile a shadow reads as a second, darker square.
                .graphicsLayer {
                    shadowElevation = if (casts) 14.dp.toPx() * shadowFraction() else 0f
                    shape = sleeveShape()
                    // As `.shadow` did: it clips only when it casts.
                    clip = casts
                }
                .graphicsLayer {
                    shape = sleeveShape()
                    clip = true
                }
                .background(Color.Black.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!artLoaded && !canvasRendered) {
                Icon(
                    imageVector = BitChordIcons.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxSize(0.36f),
                )
            }
            AsyncImage(
                model = artRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = onArtState,
                // Not drawn under a clip that got there first — see the
                // portrait sleeve's note on TextureView stacking order.
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent { if (artLoaded || !canvasRendered) drawContent() },
            )
            outgoingArt()
            canvas?.let { clip ->
                CanvasArtworkPlayer(
                    canvas = clip,
                    isPlaying = isPlaying,
                    pausedForTransition = pausedForTransition,
                    onRenderedChanged = onCanvasRenderedChange,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        overlay()
    }
}

/**
 * The flat dim over a landscape backdrop clip with the player up: the same
 * darkened tint the portrait Canvas sits under while its controls are up.
 */
private const val LANDSCAPE_CANVAS_DIM = 0.65f

/** The heavier dim over it with the lyrics or the queue open. */
private const val LANDSCAPE_CANVAS_PANEL_DIM = 0.75f

/**
 * The soft blur over the landscape backdrop clip with the player up, so the
 * video reads as a backdrop rather than competing with the text over it.
 * Needs RenderEffect (Android 12+); older devices get the dim alone.
 */
private val LANDSCAPE_CANVAS_BLUR = 3.dp

/** How long the backdrop's dim takes to follow a pane being turned. */
private const val LANDSCAPE_CANVAS_DIM_MS = 260

/**
 * How dark the landscape backdrop clip is laid under the player, which is
 * also how much the player's ink brightens over it.
 */
internal fun landscapeCanvasDim(panelOpen: Boolean): Float =
    if (panelOpen) LANDSCAPE_CANVAS_PANEL_DIM else LANDSCAPE_CANVAS_DIM

/**
 * A Canvas, or a music video standing in for one, run under the whole
 * landscape player: cropped to fill the window behind both columns, with a
 * flat dim over it so the credits, the lyrics and the queue stay readable.
 *
 * The sleeve keeps the still cover (or an animated one) meanwhile — see
 * [LandscapeArtwork]. Until the clip's first frame the backdrop under it,
 * the cover's blur, is what shows, so nothing goes dark waiting for it.
 */
@Composable
internal fun LandscapeCanvasBackdrop(
    canvas: CanvasArtwork,
    isPlaying: Boolean,
    panelOpen: Boolean,
    onRenderedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pausedForTransition: Boolean = false,
    presentationAlpha: () -> Float = { 1f },
    /** The clip is being watched full screen, with no player over it to read. */
    fullscreen: Boolean = false,
    /**
     * The lyrics are up beside a music video: the picture draws aside to the
     * left of the screen (see [landscapeVideoPane]), sharp and undimmed, and
     * the cover's blur shows, dimmed, under the lyrics on the right.
     */
    split: Boolean = false,
) {
    // Laid on from the start rather than at the clip's first frame: the cover's
    // blur under it until then is dimmed the same, so the tint never pops in.
    val dim by animateFloatAsState(
        targetValue = if (fullscreen) 0f else landscapeCanvasDim(panelOpen),
        animationSpec = tween(LANDSCAPE_CANVAS_DIM_MS),
        label = "landscapeCanvasDim",
    )
    // Off when it is watched full screen, like the dim.
    val blurRadius by animateDpAsState(
        targetValue = if (fullscreen || split) 0.dp else LANDSCAPE_CANVAS_BLUR,
        animationSpec = tween(LANDSCAPE_CANVAS_DIM_MS),
        label = "landscapeCanvasBlur",
    )
    val splitT by animateFloatAsState(
        targetValue = if (split && !fullscreen) 1f else 0f,
        animationSpec = tween(LANDSCAPE_VIDEO_SPLIT_MS, easing = FastOutSlowInEasing),
        label = "landscapeVideoSplit",
    )
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // The same player throughout, resized: the picture carries on playing
        // as it draws aside and as it comes back.
        val pane = landscapeVideoPane(maxWidth, maxHeight)
        val videoWidth = lerp(maxWidth, pane.width, splitT)
        val videoHeight = lerp(maxHeight, pane.height, splitT)
        CanvasArtworkPlayer(
            canvas = canvas,
            isPlaying = isPlaying,
            contentMode = CanvasContentMode.CROP,
            pausedForTransition = pausedForTransition,
            presentationAlpha = presentationAlpha,
            onRenderedChanged = onRenderedChange,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(videoWidth, videoHeight)
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
        )
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    // Lifted off the picture as it draws aside, and left on
                    // what shows beside it.
                    val shade = dim * presentationAlpha()
                    val edge = videoWidth.toPx().coerceAtMost(size.width)
                    if (splitT < 1f) {
                        drawRect(
                            Color.Black.copy(alpha = (shade * (1f - splitT)).coerceIn(0f, 1f)),
                            size = Size(edge, size.height),
                        )
                    }
                    if (edge < size.width) {
                        drawRect(
                            Color.Black.copy(alpha = shade.coerceIn(0f, 1f)),
                            topLeft = Offset(edge, 0f),
                            size = Size(size.width - edge, size.height),
                        )
                    }
                },
        )
    }
}

/**
 * Laid over the landscape player while its music video is full screen (see
 * [LandscapePlayerLayout]'s chromeHidden): the system bars go away, and a tap
 * anywhere on the video, or back, puts the player back over it.
 */
@Composable
internal fun LandscapeVideoFullscreenOverlay(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HideSystemBars(true)
    PlayerBackHandler(enabled = true, onBack = onExit)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(Res.string.exit_full_screen_video),
                onClick = onExit,
            ),
    )
}

/**
 * The landscape player's right column in its main state: where the session
 * came from, the credits, the current line, the scrubber, the transport and
 * the volume bar — the portrait half-player, less the row the left column
 * already has.
 *
 * Centred in the column rather than pinned to its foot: there is no artwork
 * above it here to hand the spare height to.
 */
@Composable
internal fun LandscapeMainPane(
    compact: Boolean,
    /** "Playing from …", or null where Settings hides it. */
    caption: String?,
    onOpenCaption: () -> Unit,
    credits: @Composable () -> Unit,
    /** The one-line lyric over the scrubber; null where synced lyrics are off. */
    lyricStrip: (@Composable () -> Unit)?,
    scrubber: @Composable () -> Unit,
    transport: @Composable () -> Unit,
    /** Null where Settings hides the volume bar. */
    volume: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = PLAYER_MAX_WIDTH)
            .fillMaxWidth(),
    ) {
        if (caption != null) {
            PlaybackOriginCaption(
                text = caption,
                onClick = onOpenCaption,
                textAlign = TextAlign.Start,
                contentPadding = PaddingValues(vertical = 2.dp),
            )
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        }
        credits()
        Spacer(Modifier.height(if (compact) 2.dp else 10.dp))
        lyricStrip?.invoke()
        scrubber()
        Spacer(Modifier.height(if (compact) 0.dp else 8.dp))
        transport()
        if (volume != null) {
            Spacer(Modifier.height(if (compact) 0.dp else 12.dp))
            volume()
        }
    }
}

/**
 * Title, artist, like and the three-dot menu, for the landscape player.
 *
 * Marquee rather than a plain ellipsis, matching the portrait player: a tablet
 * is wider, not infinitely wide, and a long title truncated on the one surface
 * with room to scroll it would be the odd one out.
 */
@Composable
internal fun LandscapeCredits(
    song: Song,
    signedIn: Boolean,
    likeStatus: LikeStatus,
    /** The menu glyph is briefly the undo arrow after a quality upgrade. */
    showRevertCue: Boolean,
    onToggleLike: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    /** Null browse id for anyone but the lead credit — see [NowPlayingScreen]. */
    onOpenArtist: (browseId: String?, name: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Set while a music video plays behind the player: puts it full screen. */
    onFullscreenVideo: (() -> Unit)? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Crossfade(
            targetState = song.title to song.artist,
            animationSpec = tween(durationMillis = 300),
            modifier = Modifier.weight(1f),
            label = "landscapeCredits",
        ) {
            Column {
                var titleOverflowing by remember { mutableStateOf(false) }
                MarqueeText(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = PlayerInk,
                    onOverflowChange = { titleOverflowing = it },
                    leading = if (song.isExplicit == true) {
                        { ExplicitBadge(PlayerInk) }
                    } else {
                        null
                    },
                    modifier = Modifier.opensPage(song.albumId, onOpenAlbum),
                )
                Spacer(Modifier.height(2.dp))
                ArtistCreditsMarquee(
                    text = song.artist,
                    credits = remember(song.artist, song.artists, song.artistId) {
                        artistCredits(song.artist, song.artists, song.artistId)
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W500),
                    color = playerInk(alpha = 0.55f),
                    // A title that's also scrolling gets to go first — starting
                    // together reads as clutter, so the artist waits a beat.
                    startDelayMillis = if (titleOverflowing) MARQUEE_ARTIST_STAGGER_MS else 0L,
                    onOpenArtist = onOpenArtist,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        if (onFullscreenVideo != null) {
            CircleGlyph(
                icon = Icons.Rounded.Fullscreen,
                contentDescription = stringResource(Res.string.full_screen_video),
                onClick = onFullscreenVideo,
            )
            Spacer(Modifier.width(8.dp))
        }
        // Same gate as the portrait player's: no account to like against for a
        // guest — but a local file or a finished download is liked on the
        // device, which needs none.
        if (signedIn || song.localUri != null) {
            val liked = likeStatus == LikeStatus.LIKE
            LikeCircleGlyph(
                liked = liked,
                contentDescription = stringResource(
                    if (liked) Res.string.remove_from_liked else Res.string.like,
                ),
                onToggleLike = onToggleLike,
            )
            Spacer(Modifier.width(8.dp))
        }
        // The menu hangs off this button — see [MenuAnchor].
        Box(Modifier.menuAnchor()) {
            CircleGlyph(
                icon = if (showRevertCue) Icons.AutoMirrored.Rounded.Undo else Icons.Rounded.MoreHoriz,
                contentDescription = stringResource(Res.string.more),
                onClick = onOpenMenu,
            )
        }
    }
}

/**
 * The landscape player's right column with the lyrics open: the sheet, and
 * under it the romanize / source / translate bar.
 *
 * The bar is where the portrait player's status line and its two floating
 * toggles went. There the line sits over the scrubber and the toggles float
 * over the foot of the words; here there is no scrubber beside the lyrics, and
 * the bar gives all three a place of their own that costs the sheet no lines.
 */
@Composable
internal fun LandscapeLyricsPane(
    hasLyrics: Boolean,
    /** Shown in place of the sheet while there are no lines to draw. */
    placeholder: String,
    status: String,
    onStatusClick: () -> Unit,
    /** Whether lines are being picked, which is what the bar stands in for. */
    picking: Boolean = false,
    /** The pick bar itself, drawn in place of the row below. */
    pickBar: @Composable () -> Unit = {},
    romanizationToggle: @Composable () -> Unit,
    translationToggle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    panel: @Composable (Modifier) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (hasLyrics) {
                panel(Modifier.fillMaxSize())
            } else {
                // Held at a fixed line rather than through
                // [LyricsUnavailableLine] or [LyricsLoadingLine]: those fade
                // out after a few seconds, which here would leave the whole
                // column blank for as long as the track keeps playing.
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.titleMedium,
                    color = playerInk(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        // While a pick is open the row below gives way to the bar: the two
        // toggles in it would redraw the very lines the pick is indexing, and
        // the status line is the one thing the bar's own Cancel and Share sit
        // where.
        if (picking) {
            Box(Modifier.fillMaxWidth()) { pickBar() }
        } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Fixed slots either side whether or not the toggles are drawn, so
            // the status line stays centred as lyrics arrive.
            Box(Modifier.size(34.dp)) { if (hasLyrics) romanizationToggle() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                LyricsStatusWithChange(
                    status = status,
                    onStatusClick = onStatusClick,
                )
            }
            Box(Modifier.size(34.dp)) { if (hasLyrics) translationToggle() }
        }
        }
    }
}
