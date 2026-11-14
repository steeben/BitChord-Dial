package com.music.bitchord.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.os.Build
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.music.bitchord.ui.rememberIsForeground
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.music.bitchord.data.Http
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasCache
import com.music.bitchord.data.canvas.CanvasDownloads
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.ceil
import kotlin.math.roundToInt
import java.util.Locale

private const val TAG = "CanvasArtworkPlayer"

/**
 * How long a clip gets to paint itself onto a surface it was just handed back
 * before the still art is brought in behind it instead. Long enough to cover a
 * decoder being re-created from cold, short enough that a clip which is never
 * coming back does not sit there as a hole for the length of a glance.
 */
private const val REPAINT_TIMEOUT_MS = 700L

/**
 * A clip's first appearance: held this long after its first frame lands, then
 * swept in from the top-left corner over [REVEAL_WIPE_MS]. Only the first time
 * a given clip shows — a clip coming back onto a surface it lost, or standing
 * in from its remembered frame, crossfades straight back as before.
 */
private const val REVEAL_DELAY_MS = 1_000L
private const val REVEAL_WIPE_MS = 1_600
/** See [CANVAS_REVEAL_WIPE_BAND]. */
private const val REVEAL_WIPE_BAND = CANVAS_REVEAL_WIPE_BAND

/**
 * The looping video that plays over a track's cover art, sized to fill and
 * clipped by whatever laid it out.
 *
 * A second, deliberately unassuming ExoPlayer: silent, with its audio track
 * switched off entirely so a clip's soundtrack is never even fetched, and no
 * audio attributes — taking focus here would duck the music this is decorating.
 * It follows the transport, so pausing the track stops the sleeve moving too.
 *
 * Nothing is drawn until the first frame arrives, and the fade in from there
 * means a failed or slow clip simply leaves the still art showing rather than
 * flashing a black square over it. [CanvasArtwork.fallbackUrl] gets one try if
 * the first rendition won't decode.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun AndroidCanvasArtworkPlayer(
    canvas: CanvasArtwork,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    contentMode: CanvasContentMode = CanvasContentMode.CROP,
    /** Align a contained portrait clip to the top; other callers retain centered FIT. */
    alignPortraitTop: Boolean = false,
    /** The decoded video's display aspect, or zero until Media3 knows it. */
    onAspectRatioChanged: (Float) -> Unit = {},
    /** The full player bounds required before a portrait hero may reveal its first frame. */
    portraitRevealBounds: IntSize = IntSize.Zero,
    /** Fades a full-player portrait clip away as the existing sleeve collapses. */
    presentationAlpha: () -> Float = { 1f },
    /** Fires once the clip has an actual frame on screen, and again if it drops back to none. */
    onRenderedChanged: (Boolean) -> Unit = {},
    /** A single frame off the playing clip, for callers that want to re-tint around it. */
    onFrameCaptured: (ImageBitmap) -> Unit = {},
    /**
     * Keep calling [onFrameCaptured] every so many milliseconds instead of
     * only once — for a caller re-tinting its backdrop off a playing clip,
     * which is worth following as it plays rather than settling on whatever
     * colours its opening frame happened to have. That holds for every
     * source, not just
     * [CanvasSource.SPOTIFY][com.music.bitchord.data.canvas.CanvasSource.SPOTIFY]:
     * a clip is a clip, and one that pans or cuts changes colour under its own
     * still sleeve exactly the same way regardless of who published it. Null
     * when a caller has nothing worth re-tinting off a moving colour at all —
     * re-reading a texture off the GPU costs a frame stall, so this stays
     * opt-in rather than always-on.
     */
    refreshFrameEveryMs: Long? = null,
    /**
     * The longest edge of the bitmap [onFrameCaptured] is handed.
     *
     * This is the whole cost of following a clip. `getBitmap()` with no
     * arguments hands back a copy at the view's own size — full-bleed, so most
     * of a phone screen, five or six megabytes read back off the GPU and
     * allocated afresh on every call. Nobody wants that resolution: the one
     * caller there is averages the frame down to a handful of colours. Asking
     * for a small copy instead makes the readback scale during the blit, which
     * is what turns a refresh from something worth doing every few seconds into
     * something affordable several times a second.
     */
    frameCapturePx: Int = FRAME_CAPTURE_PX,
    /**
     * How much of whatever is behind the clip it is currently hiding: 0 while
     * nothing is drawn, ramping to 1 as the first frame fades in, and back down
     * if it drops out again.
     *
     * A caller that stacks a still image under the clip needs this to take that
     * image back out from under it, and cannot get there from
     * [onRenderedChanged] alone — that fires when the fade *starts*. It matters
     * most with [bottomFade]: one gradient over each of two stacked layers
     * leaves the lower one showing through the upper one instead of the backdrop
     * showing through both, so the still art stays half-visible over the clip
     * for as long as it is left lit underneath.
     */
    onCoverChanged: (Float) -> Unit = {},
    /**
     * Share of the clip's height, measured up from its bottom edge, over which
     * it dissolves to nothing — 0 for a hard edge. See [setBottomFade] for why
     * this is a parameter here rather than a mask the caller could draw.
     */
    bottomFade: Float = 0f,
    /** Optional end of the fade in view pixels; defaults to the view's bottom edge. */
    bottomFadeEndPx: Float? = null,
    /**
     * Halts decoding for the length of a caller-driven transition — the sleeve
     * collapsing into the queue or lyrics panel and back — rather than only at
     * the two ends of it. That collapse is driven by the same clock as this
     * clip's own fade, and a decoder left running through it competes with the
     * slide for the same frame budget; the stutter that produced this flag was
     * the decode, not the animation. The clip keeps its last frame on screen
     * while paused, so there is nothing to fade back in once it lifts.
     */
    pausedForTransition: Boolean = false,
) {
    val context = LocalContext.current

    var url by remember(canvas) { mutableStateOf(canvas.url) }
    var rendered by remember(canvas) { mutableStateOf(false) }
    // [rendered], held back by [REVEAL_DELAY_MS] the first time a clip shows.
    // Everything the caller sees — the fade, the handoff, the re-tint — runs
    // off this rather than [rendered], so the still art and its backdrop stay
    // exactly as they were until the clip actually starts to come in.
    var revealed by remember(canvas) { mutableStateOf(false) }
    var revealedOnce by remember(canvas) { mutableStateOf(false) }
    // How far the first reveal's diagonal sweep has got; 1 is no mask at all.
    val wipe = remember(canvas) { Animatable(1f) }
    // The clip's opening frame from the last time it played, if it has played
    // this session: shown in its place until the new player has a frame of its
    // own, so coming back to a clip already loaded never flashes the still
    // sleeve first. Given up after a moment if no frame arrives.
    var placeholder by remember(canvas) { mutableStateOf(CanvasFrameMemory.get(canvas.url)) }
    LaunchedEffect(placeholder != null, rendered) {
        if (placeholder == null) return@LaunchedEffect
        // Held through the clip's own fade in over it, then dropped.
        delay(if (rendered) PLACEHOLDER_FADE_MS else PLACEHOLDER_WAIT_MS)
        placeholder = null
    }
    LaunchedEffect(rendered) {
        if (!rendered) {
            revealed = false
            return@LaunchedEffect
        }
        if (revealedOnce || placeholder != null) {
            // Back from a lost surface, or over its own remembered frame:
            // nothing new is arriving, so no ceremony about it.
            wipe.snapTo(1f)
            revealedOnce = true
            revealed = true
            return@LaunchedEffect
        }
        wipe.snapTo(0f)
        delay(REVEAL_DELAY_MS)
        revealedOnce = true
        revealed = true
        wipe.animateTo(1f, tween(durationMillis = REVEAL_WIPE_MS, easing = LinearEasing))
    }
    // Aspect of the clip itself. Zero until the decoder reports it, which is
    // also the signal that there is nothing sensible to crop to yet.
    // Started from the remembered frame's when there is one, so a clip
    // mounted again is laid out at its own shape from its first frame rather
    // than at the banner's until the decoder reports back.
    var clipAspect by remember(canvas) { mutableFloatStateOf(CanvasFrameMemory.aspect(canvas.url) ?: 0f) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    var textureView by remember(canvas) { mutableStateOf<TextureView?>(null) }
    // Frames are counted rather than flagged, because [rendered] cannot answer
    // the question the repaint below has to ask: "did a frame land on *this*
    // surface", not "has one ever landed".
    var frameTick by remember(canvas) { mutableIntStateOf(0) }
    // Bumped each time the view is handed a surface to replace one that was
    // taken away — which, in practice, means each time the app comes back from
    // off screen. Not bumped for the first surface of all, which arrives with
    // nothing needing doing to it. See the repaint effect below.
    var surfaceGeneration by remember(canvas) { mutableIntStateOf(0) }
    val currentContentMode by rememberUpdatedState(contentMode)
    val currentAlignPortraitTop by rememberUpdatedState(alignPortraitTop)
    val currentPortraitRevealBounds by rememberUpdatedState(portraitRevealBounds)
    val currentPresentationAlpha by rememberUpdatedState(presentationAlpha)
    val reportAspect by rememberUpdatedState(onAspectRatioChanged)

    val player = remember {
        ExoPlayer.Builder(context)
            // Shares the app's one OkHttp client, as everything that fetches
            // over the network here does — and wrapped in CanvasCache so a
            // loop past the first is read off disk rather than re-fetched;
            // see that object's doc for why this matters far more here than
            // it would for a clip played once.
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    // Canvases saved with downloaded songs come first, so a
                    // saved clip plays offline and is never fetched again.
                    CanvasDownloads.dataSourceFactory(
                        CanvasCache.dataSourceFactory(OkHttpDataSource.Factory(Http.client)),
                    ),
                ),
            )
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val width = videoSize.width * videoSize.pixelWidthHeightRatio
                val aspect = if (width.isFinite() && width > 0f && videoSize.height > 0) {
                    width / videoSize.height
                } else 0f
                if (aspect != clipAspect) rendered = false
                clipAspect = aspect
                reportAspect(aspect)
                textureView?.applyContentTransform(clipAspect, currentContentMode, currentAlignPortraitTop)
            }

            override fun onPlayerError(error: PlaybackException) {
                // One retry, at the other rendition. If that is the one that
                // just failed there is nowhere left to go: leave the still
                // art up rather than looping through a broken URL.
                val alternate = canvas.fallbackUrl
                if (alternate != null && alternate != url) {
                    url = alternate
                } else {
                    rendered = false
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            // Where the clip had got to, and what it looked like there, for
            // the player that takes over from this one — the other side of a
            // turn of the phone, most often — to carry on from rather than
            // start the loop over from its opening frame.
            CanvasClipClock.record(canvas, player.currentPosition, player.duration, player.isPlaying)
            if (clipAspect > 0f && rendered) {
                textureView?.captureWhole(PLACEHOLDER_PX)
                    ?.let { CanvasFrameMemory.put(canvas.url, it.asImageBitmap(), clipAspect) }
            }
            player.release()
        }
    }

    LaunchedEffect(url) {
        rendered = false
        // Only the fallback rendition is a new shape to wait for. The first
        // one keeps whatever was remembered of it: zeroed here, a portrait
        // clip mounted again dropped back to the banner for a frame.
        if (url != canvas.url) {
            clipAspect = 0f
            reportAspect(0f)
        }
        val item = MediaItem.Builder().setUri(url)
        mimeTypeOf(url)?.let { item.setMimeType(it) }
        // A saved HLS clip holds one rendition: ask for only that one.
        CanvasDownloads.streamKeysFor(url).takeIf { it.isNotEmpty() }?.let { item.setStreamKeys(it) }
        // Picked up where it was left, when it was playing a moment ago.
        player.setMediaItem(item.build(), CanvasClipClock.resumeAt(canvas))
        player.prepare()
    }

    // The remembered frame and position kept current while the clip plays,
    // since a surface can be taken away (a rotation) before anything here
    // hears it is going. Without it the clip came back on its opening frame
    // and started its loop over, which read as it loading again.
    LaunchedEffect(revealed, clipAspect) {
        if (!revealed || clipAspect <= 0f) return@LaunchedEffect
        while (isActive) {
            delay(PLACEHOLDER_REFRESH_MS)
            if (!player.isPlaying) continue
            withFrameMillis { }
            textureView?.captureWhole(PLACEHOLDER_PX)
                ?.let { CanvasFrameMemory.put(canvas.url, it.asImageBitmap(), clipAspect) }
            CanvasClipClock.record(canvas, player.currentPosition, player.duration, true)
        }
    }

    // Gated on the app being on screen as well as on the caller's own state.
    //
    // This is a video decoder. Left to [isPlaying] alone it goes on decoding
    // frames into a surface nobody can see for as long as the composition is
    // alive — which, with the phone in a pocket and music playing, is the whole
    // album. Worse on a detail page, whose caller passes a constant `true`
    // because "the page is only up while it's being read": true of a page being
    // looked at, not of one left open behind a locked screen.
    //
    // Held inside this component rather than asked of each caller, so no call
    // site can forget it. The player now runs continuously in the foreground
    // regardless of playback state, so coming back from background always has
    // a surface ready and `onRenderedFirstFrame()` fires naturally.
    val foreground = rememberIsForeground()
    LaunchedEffect(foreground, pausedForTransition) {
        player.playWhenReady = foreground && !pausedForTransition
    }

    // Repaint onto a surface that has just been handed back. A TextureView's
    // SurfaceTexture does not survive every background/layout transition, and
    // ExoPlayer's old "first frame rendered" callback says nothing about the
    // replacement surface. Keep the still artwork visible while waiting, ask
    // the decoder to paint at its current position, and only hand back to the
    // clip when onSurfaceTextureUpdated confirms real pixels below.
    LaunchedEffect(surfaceGeneration) {
        if (surfaceGeneration == 0) return@LaunchedEffect
        rendered = false
        val before = frameTick
        if (player.playbackState != Player.STATE_IDLE) {
            player.seekTo(player.currentPosition)
        }
        delay(REPAINT_TIMEOUT_MS)
        if (frameTick == before) {
            rendered = false
        }
    }

    val reportRendered by rememberUpdatedState(onRenderedChanged)
    // To the caller the clip is up while its remembered frame stands in for it.
    val shown = revealed || placeholder != null
    LaunchedEffect(shown) { reportRendered(shown) }
    LaunchedEffect(revealed) {
        if (!revealed) return@LaunchedEffect
        // Let the surface actually paint the frame that just triggered this
        // before reading it back — grabbing it the instant the callback fires
        // can still catch the previous, empty buffer.
        withFrameMillis { }
        val view = textureView ?: return@LaunchedEffect
        val bitmap = view.captureAt(frameCapturePx, clipAspect, contentMode, alignPortraitTop)
        if (bitmap != null) {
            Log.d(TAG, "frame captured after rendered=true, size=${bitmap.width}x${bitmap.height}")
            onFrameCaptured(bitmap.asImageBitmap())
        } else {
            Log.w(TAG, "frame capture returned null after rendered=true")
        }
        // The opening frame as the view shows it, kept for next time. Only once
        // the clip's aspect is known and its transform is on the view: a frame
        // read back before that is the clip stretched to the view's bounds, and
        // it would stand in, squished, every time the clip is mounted again.
        if (CanvasFrameMemory.get(canvas.url) == null && clipAspect > 0f) {
            view.captureWhole(PLACEHOLDER_PX)?.let { CanvasFrameMemory.put(canvas.url, it.asImageBitmap(), clipAspect) }
        }
    }

    // The opt-in follow-up to the capture above, for a caller that asked for
    // one — see [refreshFrameEveryMs]. A separate effect rather than a loop
    // folded into the one above: that one is keyed on [rendered] so it fires
    // again on every fade-in, and this one only needs to start once a fade-in
    // has actually happened and then keep going for as long as it holds.
    LaunchedEffect(revealed, refreshFrameEveryMs, frameCapturePx, clipAspect, contentMode, alignPortraitTop) {
        val interval = refreshFrameEveryMs ?: return@LaunchedEffect
        Log.d(TAG, "periodic frame refresh started, interval=$interval")
        if (!revealed) return@LaunchedEffect
        while (isActive) {
            delay(interval)
            val view = textureView ?: continue
            val bitmap = view.captureAt(frameCapturePx, clipAspect, contentMode, alignPortraitTop)
            if (bitmap != null) {
                Log.d(TAG, "periodic frame captured, size=${bitmap.width}x${bitmap.height}")
                onFrameCaptured(bitmap.asImageBitmap())
            } else {
                Log.w(TAG, "periodic frame capture returned null")
            }
        }
    }

    val sweeping = revealed && wipe.value < 1f
    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        // Long enough to read as a crossfade from the still artwork. Straight
        // up while there is a sweep, which does the fading itself: that keeps
        // the cover this reports equal to the sweep's progress, which is what
        // a caller erasing its still art on the same front needs to follow.
        animationSpec = tween(
            durationMillis = if (sweeping) 0 else 650,
            easing = FastOutSlowInEasing,
        ),
        label = "canvasAlpha",
    )

    // Published rather than left for the caller to mirror with a second
    // animation off [onRenderedChanged]: one fade, one account of how far along
    // it is. Zeroed on the way out, or a caller would be left holding something
    // hidden behind a clip that is no longer mounted.
    val reportCover by rememberUpdatedState(onCoverChanged)
    LaunchedEffect(Unit) {
        snapshotFlow {
            // Scaled by the sweep too: a still taken out from under a clip
            // that has only covered a corner of it leaves the rest a hole.
            maxOf(alpha * wipe.value, if (placeholder != null) 1f else 0f) * currentPresentationAlpha()
        }.collect { reportCover(it) }
    }
    DisposableEffect(Unit) {
        onDispose {
            // The parent owns the still/canvas handoff. Never leave it holding
            // a Success from a player or TextureView that no longer exists.
            reportRendered(false)
            reportCover(0f)
            reportAspect(0f)
        }
    }

    Box(modifier.onSizeChanged { bounds = it }) {
    placeholder?.let { frame ->
        Image(
            bitmap = frame,
            contentDescription = null,
            // Cropped rather than stretched. The frame was read back at the
            // size the view had then, and a remount can land mid-resize — on
            // the way back from the queue the banner is still settling — where
            // filling the bounds squashed it until the clip's own frame took over.
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    this.alpha = currentPresentationAlpha()
                    // Offscreen so the fade below erases the frame rather
                    // than whatever is drawn behind it.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                // The clip's own bottom fade, which the view's RenderEffect
                // (or its frame, below API 31) puts on the video but not on
                // this stand-in. Left off, coming back to the player or
                // turning the phone upright showed the frame with a hard
                // bottom edge until the video took over and the fade popped in.
                .drawWithContent {
                    drawContent()
                    val endY = (bottomFadeEndPx ?: size.height).coerceIn(0f, size.height)
                    if (bottomFade > 0.001f && endY > 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = endY * (1f - bottomFade.coerceAtMost(1f)),
                                endY = endY,
                            ),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    }
                },
        )
    }
    AndroidView(
        factory = { viewContext ->
            val texture = TextureView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // Blend rather than punch a hole: the still sleeve stays
                // visible underneath for the length of the fade.
                isOpaque = false
                this.alpha = 0f
                // The transform is worked out for the view's size, and the
                // update block below only runs on recomposition — which has
                // usually come and gone before a freshly mounted view is laid
                // out, and doesn't come at all for a resize nothing in
                // composition asked for. Left to it, the clip showed stretched
                // to the old bounds until something else recomposed.
                addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                        (view as TextureView).applyContentTransform(
                            clipAspect, currentContentMode, currentAlignPortraitTop,
                        )
                    }
                }
                player.setVideoTextureView(this)
                // setVideoTextureView installs ExoPlayer's own listener, and
                // the player has to keep it — it is how the surface reaches
                // the video renderer at all. So wrap it rather than replace
                // it: everything is passed straight through, and the one
                // callback that matters here is noted on the way past.
                //
                // Asking the lifecycle instead would be simpler and wrong. The
                // surface comes back on the first traversal after the activity
                // is visible, which is *after* ON_RESUME — a repaint fired
                // there lands on the placeholder surface and the real one
                // arrives blank a moment later.
                val delegate = surfaceTextureListener
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    /** Whether the next surface is a replacement for one taken away. */
                    private var replacing = false

                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        delegate?.onSurfaceTextureAvailable(surface, width, height)
                        // The first surface needs nothing: prepare() paints it.
                        if (!replacing) return
                        replacing = false
                        Log.d(TAG, "surface recreated (gen $surfaceGeneration), waiting for frame")
                        surfaceGeneration++
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        delegate?.onSurfaceTextureSizeChanged(surface, width, height)
                        if (currentContentMode == CanvasContentMode.FIT_PORTRAIT && clipAspect in 0f..1f) {
                            rendered = false
                        }
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        replacing = true
                        Log.d(TAG, "surface destroyed, rendered=$rendered")
                        // The old buffer is gone now, not when/if ExoPlayer
                        // later reports another first frame. Restore the still
                        // artwork immediately so an empty replacement surface
                        // can never become the only visible artwork layer.
                        rendered = false
                        return delegate?.onSurfaceTextureDestroyed(surface) ?: true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        delegate?.onSurfaceTextureUpdated(surface)
                        // This callback is the proof that the current
                        // TextureView, rather than some previously destroyed
                        // surface, contains a drawable video buffer.
                        // The frame is not handed to the still artwork until
                        // its aspect and transform are ready: cropped too, or
                        // the first frame showed stretched to the view.
                        if (!rendered) {
                            val transformed = textureView?.applyContentTransform(
                                clipAspect, currentContentMode, currentAlignPortraitTop,
                            ) == true
                            val portrait = currentContentMode == CanvasContentMode.FIT_PORTRAIT &&
                                clipAspect > 0f && clipAspect < 1f
                            val expected = currentPortraitRevealBounds
                            val view = textureView
                            val layoutReady = !portrait ||
                                (expected != IntSize.Zero && view?.width == expected.width &&
                                    view?.height == expected.height)
                            if (transformed && layoutReady) {
                                rendered = true
                                frameTick++
                                Log.d(TAG, "first frame on surface (tick $frameTick, gen $surfaceGeneration)")
                            }
                        }
                    }
                }
            }
            textureView = texture
            // Wrapped on every API level so there is one view tree to reason
            // about: below API 31 the frame is what draws [bottomFade], and
            // above it the frame is just a box around the texture.
            FadingBottomFrame(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                addView(texture)
            }
        },
        update = { frame ->
            val view = frame.getChildAt(0) as TextureView
            val applied = frame.applied
            // Set on the view itself. A Compose alpha layer over a TextureView
            // is not reliably composited, and this is the same fade either way.
            view.alpha = if (contentMode == CanvasContentMode.FIT_PORTRAIT && clipAspect <= 0f) {
                0f
            } else {
                // Called here, in the view's update, so a fade driven by the
                // player's collapse re-runs this block rather than
                // recomposing the player around it.
                alpha * presentationAlpha()
            }
            // Only when something they are made of has changed. The fade above
            // re-runs this block on every frame of a collapse or of the player
            // closing into the mini player, and each pass built a fresh matrix,
            // gradient and three RenderEffects for a picture that hadn't
            // moved — and re-setting the effect makes the node rebuild it.
            if (applied.transformDiffers(clipAspect, contentMode, alignPortraitTop, view.width, view.height)) {
                view.applyContentTransform(clipAspect, contentMode, alignPortraitTop)
            }
            frame.wipe = wipe.value
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (applied.fadeDiffers(bottomFade, bounds, bottomFadeEndPx)) {
                    view.setBottomFade(bottomFade, bounds, bottomFadeEndPx)
                }
            } else {
                frame.fadeFraction = bottomFade
                frame.fadeEndPx = bottomFadeEndPx
            }
        },
        modifier = Modifier.matchParentSize(),
    )
    }
}

/**
 * The opening frame of the last few clips played, as each view showed it, for
 * [CanvasArtworkPlayer] to stand in with when the same clip is mounted again.
 */
private object CanvasFrameMemory {
    private class Remembered(val frame: ImageBitmap, val aspect: Float)

    // A few more than the three a skip back and forth needs, so the next
    // song's clip, loaded ahead of its turn, doesn't push out the frame of the
    // one playing now — which is the one turning the phone needs.
    private val frames = object : LinkedHashMap<String, Remembered>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Remembered>?): Boolean = size > 5
    }

    @Synchronized
    fun get(url: String): ImageBitmap? = frames[url]?.frame

    /** The clip's own aspect when its frame was kept; null without one. */
    @Synchronized
    fun aspect(url: String): Float? = frames[url]?.aspect

    @Synchronized
    fun put(url: String, frame: ImageBitmap, aspect: Float = 0f) {
        frames[url] = Remembered(frame, aspect)
    }
}

/**
 * Where each clip had got to when its player last let it go, so the next
 * player for it — after a rotation, or coming back to the song — carries on
 * rather than starting the loop over. Kept under both renditions' urls: the
 * phone held upright plays Apple's tall cut and sideways its square one, and
 * they run on the same clock.
 */
private object CanvasClipClock {
    private class Mark(val positionMs: Long, val durationMs: Long, val playing: Boolean, val atNanos: Long)

    private val marks = object : LinkedHashMap<String, Mark>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Mark>?): Boolean = size > 12
    }

    @Synchronized
    fun record(canvas: CanvasArtwork, positionMs: Long, durationMs: Long, playing: Boolean) {
        if (positionMs < 0L) return
        val mark = Mark(positionMs, durationMs, playing, System.nanoTime())
        marks[canvas.url] = mark
        canvas.fallbackUrl?.let { marks[it] = mark }
        canvas.portraitUrl?.let { marks[it] = mark }
    }

    @Synchronized
    fun resumeAt(canvas: CanvasArtwork): Long {
        val mark = listOfNotNull(canvas.url, canvas.fallbackUrl, canvas.portraitUrl)
            .mapNotNull { marks[it] }
            .maxByOrNull { it.atNanos } ?: return 0L
        val elapsedMs = (System.nanoTime() - mark.atNanos) / 1_000_000L
        // Still running while it was away only if it was playing and has
        // only just gone; otherwise it waits where it was.
        val position = if (mark.playing && elapsedMs in 0L..CLOCK_CARRY_MS) mark.positionMs + elapsedMs else mark.positionMs
        return if (mark.durationMs > 0L) position % mark.durationMs else 0L
    }
}

private const val CLOCK_CARRY_MS = 30_000L
private const val PLACEHOLDER_REFRESH_MS = 3_000L

/** See [PlayerHost.rememberedCanvasAspect]. */
internal fun rememberedCanvasFrameAspect(url: String): Float? = CanvasFrameMemory.aspect(url)

/**
 * The view's whole picture as it is shown, at most [maxPx] on its longest edge;
 * null without one.
 *
 * getBitmap reads the surface back stretched to the bitmap and leaves the
 * view's own transform out, so the crop is put back here. Kept as read, a
 * 9:16 clip in a taller view came back a fifth too narrow, and stood in that
 * way each time the player opened onto it until the clip's own frame took over.
 */
private fun TextureView.captureWhole(maxPx: Int): Bitmap? {
    if (width <= 0 || height <= 0) return null
    val scale = (maxPx.toFloat() / maxOf(width, height)).coerceAtMost(1f)
    return runCatching {
        val w = (width * scale).roundToInt().coerceAtLeast(1)
        val h = (height * scale).roundToInt().coerceAtLeast(1)
        val raw = getBitmap(w, h) ?: return null
        val transform = getTransform(null)
        if (transform.isIdentity) return raw
        // The transform is in the view's pixels; the bitmap is the view scaled.
        val matrix = Matrix().apply {
            setScale(1f / scale, 1f / scale)
            postConcat(transform)
            postScale(scale, scale)
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { shown ->
            Canvas(shown).drawBitmap(raw, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            raw.recycle()
        }
    }.getOrNull()
}

private const val PLACEHOLDER_PX = 1080
private const val PLACEHOLDER_WAIT_MS = 1_500L
private const val PLACEHOLDER_FADE_MS = 700L

/**
 * A frame off the clip, no bigger than [maxPx] on its longest edge — see
 * [CanvasArtworkPlayer]'s `frameCapturePx`.
 *
 * The aspect is kept rather than squared off. Nothing downstream draws this,
 * but everything downstream *averages* it, and squashing one axis would quietly
 * reweight which part of the frame each average is mostly made of.
 *
 * Null whenever the view has no frame to give — it is laid out but not yet
 * measured, or its surface has gone. A caller that gets null should keep what
 * it already had; the next tick will have one.
 */
private fun TextureView.captureAt(
    maxPx: Int,
    clipAspect: Float,
    contentMode: CanvasContentMode,
    alignPortraitTop: Boolean,
): Bitmap? {
    val viewWidth = width
    val viewHeight = height
    if (viewWidth <= 0 || viewHeight <= 0) return null
    val scale = maxPx.toFloat() / maxOf(viewWidth, viewHeight)
    return runCatching {
        // TextureView.getBitmap copies its texture layer, before the view's
        // RenderEffect; the pre-31 mask is on the parent frame instead.
        // Neither display-only fade is baked into the mesh's sampled frame.
        val frame = if (scale >= 1f) {
            getBitmap()
        } else {
            getBitmap(
                (viewWidth * scale).roundToInt().coerceAtLeast(1),
                (viewHeight * scale).roundToInt().coerceAtLeast(1),
            )
        } ?: return null
        if (contentMode != CanvasContentMode.FIT_PORTRAIT || clipAspect >= 1f || clipAspect <= 0f) {
            return frame
        }

        // getBitmap includes the view's transform. Read back only at the small
        // requested size, then exclude the transparent contain margins before
        // handing pixels to the mesh or legacy palette (both average RGB).
        val viewAspect = viewWidth.toFloat() / viewHeight
        val contentWidth = if (clipAspect < viewAspect) frame.height * clipAspect else frame.width.toFloat()
        val contentHeight = if (clipAspect < viewAspect) frame.height.toFloat() else frame.width / clipAspect
        val left = ceil((frame.width - contentWidth) / 2f).toInt().coerceIn(0, frame.width - 1)
        val top = if (alignPortraitTop) 0 else
            ceil((frame.height - contentHeight) / 2f).toInt().coerceIn(0, frame.height - 1)
        val right = (frame.width - left).coerceAtLeast(left + 1)
        val bottom = if (alignPortraitTop) contentHeight.toInt().coerceIn(1, frame.height) else
            (frame.height - top).coerceAtLeast(top + 1)
        Bitmap.createBitmap(frame, left, top, right - left, bottom - top)
    }.getOrNull()
}

/**
 * Big enough that averaging it is stable, small enough that reading it back off
 * the GPU is not an event. Every consumer reduces this to a handful of colours.
 */
private const val FRAME_CAPTURE_PX = 128

/**
 * A TextureView stretches its content to its own bounds. Compensate with a
 * transform: cover by default, or contain for a portrait clip when the hero
 * explicitly requests it. An unknown size clears the old transform.
 */
private fun TextureView.applyContentTransform(
    clipAspect: Float,
    contentMode: CanvasContentMode,
    alignPortraitTop: Boolean,
): Boolean {
    val bounds = IntSize(width, height)
    if (bounds.width <= 0 || bounds.height <= 0 || !clipAspect.isFinite() || clipAspect <= 0f) {
        setTransform(Matrix())
        return false
    }
    val viewAspect = bounds.width.toFloat() / bounds.height
    val pivotX = bounds.width / 2f
    val pivotY = if (alignPortraitTop && contentMode == CanvasContentMode.FIT_PORTRAIT && clipAspect < 1f) {
        0f
    } else bounds.height / 2f
    val matrix = Matrix().apply {
        val fit = contentMode == CanvasContentMode.FIT_PORTRAIT && clipAspect < 1f
        if (fit && clipAspect > viewAspect) {
            setScale(1f, viewAspect / clipAspect, pivotX, pivotY)
        } else if (fit) {
            setScale(clipAspect / viewAspect, 1f, pivotX, pivotY)
        } else if (clipAspect > viewAspect) {
            setScale(clipAspect / viewAspect, 1f, pivotX, pivotY)
        } else {
            setScale(1f, viewAspect / clipAspect, pivotX, pivotY)
        }
    }
    setTransform(matrix)
    return true
}

/**
 * Dissolves the clip's bottom edge into whatever is behind it.
 *
 * Done here, on the view's own RenderNode, rather than with a DstIn mask in the
 * caller's draw scope: a TextureView's frames are composited from its surface
 * and a Compose blend drawn over the node simply doesn't reach them — the mask
 * lands on the layer around the video and leaves the video's own hard edge
 * exactly where it was.
 *
 * [RenderEffect] is API 31+; below that [FadingBottomFrame] does the same job
 * the older way, with a saveLayer and a Porter-Duff mask.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun TextureView.setBottomFade(fraction: Float, bounds: IntSize, endPx: Float?) {
    val endY = endPx?.coerceIn(0f, bounds.height.toFloat()) ?: bounds.height.toFloat()
    if (fraction <= 0.001f || endY <= 0f) {
        setRenderEffect(null)
        return
    }
    val gradient = LinearGradient(
        0f,
        endY * (1f - fraction.coerceAtMost(1f)),
        0f,
        endY,
        android.graphics.Color.BLACK,
        android.graphics.Color.TRANSPARENT,
        Shader.TileMode.CLAMP,
    )
    // createOffsetEffect(0, 0) is the identity effect over the node's own
    // content, which is the only way to name "what this view drew" as the
    // destination of a blend.
    setRenderEffect(
        RenderEffect.createBlendModeEffect(
            RenderEffect.createOffsetEffect(0f, 0f),
            RenderEffect.createShaderEffect(gradient),
            BlendMode.DST_IN,
        ),
    )
}

/**
 * The pre-[Build.VERSION_CODES.S] bottom fade: the same dissolve
 * [setBottomFade] gets from a [RenderEffect], done the way it was done before
 * there was one.
 *
 * Draw the child into an offscreen layer, paint a gradient over that layer with
 * [PorterDuff.Mode.DST_IN], then compose the result down. Because the layer is
 * this group's — not the TextureView's own node — the video frames are inside it
 * by the time the mask lands, which is exactly what a Compose blend over the
 * texture cannot achieve.
 *
 * It costs a full-screen offscreen buffer per frame, so it stays off entirely
 * while [fadeFraction] is zero: with no fade asked for this is a plain
 * FrameLayout and `dispatchDraw` takes the ordinary path.
 */
/**
 * What the update block last applied to the clip's view, so it re-applies a
 * transform or a fade only when one of its inputs has actually changed.
 */
private class AppliedLook {
    private var aspect = Float.NaN
    private var mode: CanvasContentMode? = null
    private var alignTop = false
    private var width = -1
    private var height = -1

    private var fade = Float.NaN
    private var fadeBounds = IntSize(-1, -1)
    private var fadeEnd: Float? = Float.NaN

    fun transformDiffers(
        clipAspect: Float,
        contentMode: CanvasContentMode,
        alignPortraitTop: Boolean,
        viewWidth: Int,
        viewHeight: Int,
    ): Boolean {
        if (clipAspect == aspect && contentMode == mode && alignPortraitTop == alignTop &&
            viewWidth == width && viewHeight == height
        ) return false
        aspect = clipAspect
        mode = contentMode
        alignTop = alignPortraitTop
        width = viewWidth
        height = viewHeight
        return true
    }

    fun fadeDiffers(fraction: Float, bounds: IntSize, endPx: Float?): Boolean {
        if (fraction == fade && bounds == fadeBounds && endPx == fadeEnd) return false
        fade = fraction
        fadeBounds = bounds
        fadeEnd = endPx
        return true
    }
}

private class FadingBottomFrame(context: Context) : FrameLayout(context) {
    /** See [AppliedLook]. */
    val applied = AppliedLook()

    /** Share of the height, from the bottom, over which the child dissolves. */
    var fadeFraction: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped == field) return
            field = clamped
            // A software layer would defeat the point — the texture has to stay
            // hardware-composited — so this is only ever the invalidate.
            gradient = null
            invalidate()
        }
    /** Optional video bottom in this frame's pixels; null retains the historical view bottom. */
    var fadeEndPx: Float? = null
        set(value) {
            if (value == field) return
            field = value
            gradient = null
            invalidate()
        }

    /**
     * How far a first reveal has swept in from the top-left corner: 0 shows
     * nothing, 1 is no mask. Done here, on every API level, for the same reason
     * the pre-S bottom fade is — this group's layer is the one place a mask
     * reaches the video's own frames.
     */
    var wipe: Float = 1f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped == field) return
            field = clamped
            invalidate()
        }

    private val maskPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val wipePaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private var gradient: LinearGradient? = null
    private var gradientHeight = 0

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradient = null
    }

    override fun dispatchDraw(canvas: Canvas) {
        val fade = fadeFraction
        val endY = fadeEndPx?.coerceIn(0f, height.toFloat()) ?: height.toFloat()
        val fading = fade > 0.001f && endY > 0f
        val wiping = wipe < 1f
        if (!fading && !wiping) {
            super.dispatchDraw(canvas)
            return
        }
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        if (fading) drawBottomMask(canvas, fade, endY)
        if (wiping) drawWipeMask(canvas)
        canvas.restoreToCount(layer)
    }

    /**
     * Opaque behind the sweep's front, clear ahead of it, with a soft band
     * between. The front runs along the top-left to bottom-right diagonal,
     * starting a band's width before the corner so nothing shows at 0 and
     * ending past the far corner so nothing is masked at 1.
     */
    private fun drawWipeMask(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val start = -REVEAL_WIPE_BAND + wipe * (1f + REVEAL_WIPE_BAND)
        val end = start + REVEAL_WIPE_BAND
        wipePaint.shader = LinearGradient(
            w * start,
            h * start,
            w * end,
            h * end,
            android.graphics.Color.BLACK,
            android.graphics.Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, wipePaint)
    }

    private fun drawBottomMask(canvas: Canvas, fade: Float, endY: Float) {
        val shader = gradient?.takeIf { gradientHeight == height } ?: LinearGradient(
            0f,
            endY * (1f - fade),
            0f,
            endY,
            android.graphics.Color.BLACK,
            android.graphics.Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        ).also {
            gradient = it
            gradientHeight = height
        }
        maskPaint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
    }
}

/**
 * Apple serves HLS, Tidal and the community index serve MP4. Naming the type
 * saves ExoPlayer a sniff, and an unrecognised URL is left for it to work out.
 */
private fun mimeTypeOf(url: String): String? {
    val path = url.substringBefore('?').lowercase(Locale.ROOT)
    return when {
        path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        path.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
        else -> null
    }
}

/** The phone's decoder behind [CanvasArtworkPlayer] — see [PlayerHost.CanvasVideo]. */
@Composable
internal fun AndroidCanvasVideo(spec: CanvasVideoSpec, modifier: Modifier) {
    val musicVideoId = spec.canvas.musicVideoId
    if (musicVideoId != null) {
        AndroidMusicVideoCanvas(musicVideoId, spec, modifier)
        return
    }
    AndroidCanvasArtworkPlayer(
        canvas = spec.canvas,
        isPlaying = spec.isPlaying,
        modifier = modifier,
        contentMode = spec.contentMode,
        alignPortraitTop = spec.alignPortraitTop,
        onAspectRatioChanged = spec.onAspectRatioChanged,
        portraitRevealBounds = spec.portraitRevealBounds,
        presentationAlpha = spec.presentationAlpha,
        onRenderedChanged = spec.onRenderedChanged,
        onFrameCaptured = spec.onFrameCaptured,
        refreshFrameEveryMs = spec.refreshFrameEveryMs,
        frameCapturePx = spec.frameCapturePx,
        onCoverChanged = spec.onCoverChanged,
        bottomFade = spec.bottomFade,
        bottomFadeEndPx = spec.bottomFadeEndPx,
        pausedForTransition = spec.pausedForTransition,
    )
}

/**
 * The playing music video standing in as the phone player's Canvas: the same
 * full-screen layer, crop, diagonal first sweep and cover reporting as a
 * looping clip, but its frames come from [MusicVideoEngine] — the one decoder
 * held to the audio — rather than from a player of its own.
 *
 * Claims the engine's picture over the mini player while it is mounted and
 * the app is on screen; nothing is decoded for it otherwise.
 */
@OptIn(UnstableApi::class)
@Composable
private fun AndroidMusicVideoCanvas(videoId: String, spec: CanvasVideoSpec, modifier: Modifier) {
    val foreground = rememberIsForeground()
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    DisposableEffect(textureView, foreground) {
        val target = textureView
        val release = if (target != null && foreground) {
            MusicVideoEngine.claim(target, MUSIC_VIDEO_CANVAS_PRIORITY)
        } else null
        onDispose { release?.invoke() }
    }

    val shownId by MusicVideoEngine.shownVideoId.collectAsStateWithLifecycle()
    val frameOn by MusicVideoEngine.frameOn.collectAsStateWithLifecycle()
    val engineAspect by MusicVideoEngine.aspect.collectAsStateWithLifecycle()
    val playing by MusicVideoEngine.playing.collectAsStateWithLifecycle()
    val rendered = textureView != null && frameOn === textureView && shownId == videoId
    // Unknown until this video's first frame is up: the engine's aspect is
    // the last video's until then.
    val clipAspect = if (rendered) engineAspect else 0f

    val currentPresentationAlpha by rememberUpdatedState(spec.presentationAlpha)
    val reportRendered by rememberUpdatedState(spec.onRenderedChanged)
    val reportCover by rememberUpdatedState(spec.onCoverChanged)
    val reportAspect by rememberUpdatedState(spec.onAspectRatioChanged)
    LaunchedEffect(clipAspect) { reportAspect(clipAspect) }

    // The first frame gets the Canvas's own entrance: a beat, then the sweep
    // in from the top-left corner. Only ever once per video: coming back to it
    // — from the queue or the lyrics, or a turn to landscape and back — finds
    // the Canvas already up, standing in with the frame it was last showing
    // until the live picture lands, rather than putting the still sleeve back
    // and running the entrance again.
    val memoryKey = "$MUSIC_VIDEO_FRAME_KEY$videoId"
    var revealedOnce by remember(videoId) { mutableStateOf(videoId in MusicVideoCanvasMemory.revealed) }
    var placeholder by remember(videoId) {
        mutableStateOf(CanvasFrameMemory.get(memoryKey)?.takeIf { revealedOnce })
    }
    var revealed by remember(videoId) { mutableStateOf(placeholder != null) }
    val wipe = remember { Animatable(1f) }
    LaunchedEffect(placeholder != null, rendered) {
        if (placeholder == null) return@LaunchedEffect
        // Let go as soon as the live picture is up under it, and given up on
        // if the picture is not coming back. Not held the clip's 700 ms: while
        // this frame stood in, the progressive blur under the player's credits
        // did not take the video, and it came in all at once after the player
        // had finished opening. [rendered] already means a frame was drawn to
        // this view, so two frames are enough for it to be on screen.
        if (rendered) {
            withFrameMillis { }
            withFrameMillis { }
        } else {
            delay(MUSIC_VIDEO_PLACEHOLDER_WAIT_MS)
        }
        placeholder = null
    }
    LaunchedEffect(rendered, placeholder != null) {
        if (!rendered) {
            revealed = placeholder != null
            return@LaunchedEffect
        }
        if (revealedOnce) {
            wipe.snapTo(1f)
            revealed = true
            return@LaunchedEffect
        }
        wipe.snapTo(0f)
        delay(REVEAL_DELAY_MS)
        revealedOnce = true
        MusicVideoCanvasMemory.revealed += videoId
        revealed = true
        wipe.animateTo(1f, tween(durationMillis = REVEAL_WIPE_MS, easing = LinearEasing))
    }
    LaunchedEffect(revealed) { reportRendered(revealed) }
    // The frame on screen, kept fresh for the next time this Canvas comes
    // back. Read every few seconds while it plays, since a surface can be
    // torn down (a rotation) before anything here hears it is going.
    LaunchedEffect(rendered, revealedOnce) {
        if (!rendered || !revealedOnce) return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            textureView?.captureWhole(MUSIC_VIDEO_FRAME_PX)?.let { CanvasFrameMemory.put(memoryKey, it.asImageBitmap()) }
            delay(MUSIC_VIDEO_FRAME_REFRESH_MS)
        }
    }

    val sweeping = revealed && wipe.value < 1f
    // Straight in when it is coming back: the Canvas never left.
    val returning = placeholder != null
    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = if (sweeping || returning) 0 else 650, easing = FastOutSlowInEasing),
        label = "musicVideoCanvasAlpha",
    )
    LaunchedEffect(Unit) {
        snapshotFlow { alpha * wipe.value * currentPresentationAlpha() }.collect { reportCover(it) }
    }

    // A video being watched keeps the screen awake, as the 16:9 player does.
    val awake = rendered && playing
    LaunchedEffect(textureView, awake) { textureView?.keepScreenOn = awake }
    DisposableEffect(Unit) {
        onDispose {
            textureView?.keepScreenOn = false
            reportRendered(false)
            reportCover(0f)
            reportAspect(0f)
        }
    }

    Box(modifier) {
    placeholder?.let { frame ->
        Image(
            bitmap = frame,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    this.alpha = currentPresentationAlpha()
                    // Offscreen so the fade below erases the frame rather
                    // than whatever is drawn behind it.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                // The banner's bottom fade, as the frame below puts it on the
                // live picture. Left off, a podcast episode's video came back
                // with a hard bottom edge as the player opened, until the live
                // picture took over and the fade popped in.
                .drawWithContent {
                    drawContent()
                    val fade = spec.bottomFade
                    val endY = (spec.bottomFadeEndPx ?: size.height).coerceIn(0f, size.height)
                    if (fade > 0.001f && endY > 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = endY * (1f - fade.coerceAtMost(1f)),
                                endY = endY,
                            ),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    }
                },
        )
    }
    AndroidView(
        factory = { viewContext ->
            val texture = TextureView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                isOpaque = false
                this.alpha = 0f
                // A resize nothing in composition asked for still has to re-crop.
                addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                        (v as TextureView).applyContentTransform(
                            MusicVideoEngine.aspect.value, CanvasContentMode.CROP, false,
                        )
                    }
                }
            }
            textureView = texture
            FadingBottomFrame(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                addView(texture)
            }
        },
        update = { frame ->
            val view = frame.getChildAt(0) as TextureView
            view.alpha = alpha * spec.presentationAlpha()
            // Always cropped to cover: the video is the whole screen's
            // backdrop, or a podcast episode's picture in the cover's place.
            if (frame.applied.transformDiffers(engineAspect, CanvasContentMode.CROP, false, view.width, view.height)) {
                view.applyContentTransform(engineAspect, CanvasContentMode.CROP, false)
            }
            frame.wipe = wipe.value
            // The banner's dissolve into the backdrop, for an episode's picture
            // that runs as the banner. None is asked for under the whole screen.
            frame.fadeFraction = spec.bottomFade
            frame.fadeEndPx = spec.bottomFadeEndPx
        },
        onRelease = { frame ->
            if (textureView === frame.getChildAt(0)) textureView = null
        },
        modifier = Modifier.matchParentSize(),
    )
    }
}

/** Above the 16:9 player's surface (1) and the mini player's (0). */
private const val MUSIC_VIDEO_CANVAS_PRIORITY = 2

/** Keys a music video's last frame in [CanvasFrameMemory], apart from any clip URL. */
private const val MUSIC_VIDEO_FRAME_KEY = "music-video-frame:"

/** The size that frame is kept at: a stand-in for a second or two, not a still worth keeping sharp. */
private const val MUSIC_VIDEO_FRAME_PX = 720

/** How often the music video Canvas's frame is read back for its next return. */
private const val MUSIC_VIDEO_FRAME_REFRESH_MS = 2_000L

/**
 * How long a returning music video Canvas holds its last frame for the live
 * picture: longer than a clip's, since the stream may have to be fetched again.
 */
private const val MUSIC_VIDEO_PLACEHOLDER_WAIT_MS = 6_000L

/** Music video Canvases that have made their entrance this session. */
private object MusicVideoCanvasMemory {
    val revealed: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())
}
