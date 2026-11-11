package com.music.bitchord.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import coil3.compose.LocalPlatformContext
import com.music.bitchord.ui.graphics.ColorUtils
import com.music.bitchord.ui.graphics.forPixelAccess
import com.music.bitchord.ui.graphics.paletteSwatches
import com.music.bitchord.ui.graphics.toImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.player.PlayerPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The colours an album, playlist or artist page paints itself in.
 *
 * Apple Music's release pages are not one design tinted five ways — the whole
 * page is derived from the sleeve, down to which grey the metadata line is. So
 * rather than hand callers a raw swatch and let each of them guess, this is the
 * finished set: a page tint, an accent that is legible *on that tint*, and the
 * two text colours and hairline that go with them.
 *
 * Every value is theme-aware. The same sleeve yields a near-black tint in dark
 * mode and a pale wash of the same hue in light mode, which is the only way the
 * pages stay readable when the app's theme disagrees with the artwork's.
 */
@Immutable
data class ArtworkPalette(
    /** The page's background wash. */
    val background: Color,
    /**
     * The colour the artwork's own bottom edge blurs down to.
     *
     * A blur wide enough to lose the picture leaves the mean of what it
     * sampled, so a page that starts from this colour where the artwork stops
     * reads as that blur carrying on rather than as a second surface beginning.
     * Lighter than [background]. A detail page is this colour, flat, all the
     * way down.
     */
    val wash: Color,
    /** Fill for the glass buttons and chips that sit on [background]. */
    val elevated: Color,
    /** The artwork's own colour, contrast-corrected — titles, icons, Play. */
    val accent: Color,
    val onBackground: Color,
    val onBackgroundVariant: Color,
    val divider: Color,
)

/**
 * Colours that came with the artwork rather than from decoding it — Apple
 * Music publishes a background and a text colour for every artist photograph.
 */
@Immutable
data class ArtworkKeyColors(val background: Color, val accent: Color)

/**
 * Pulls [ArtworkPalette] out of the artwork at [imageUrl], or out of
 * [keyColors] when the source already supplied them and there is nothing to
 * decode.
 *
 * Artwork that has already been read once is tinted on the very first frame,
 * off [seedCache] — a sheet opened from a page it shares a cover with, or a
 * page opened twice, has nothing to wait for and nothing to fade. Only a sleeve
 * genuinely being seen for the first time starts from the theme's own colours
 * and warms into the artwork's, so it never flashes a placeholder tint.
 * "Reduce animation" turns that crossfade into a cut.
 */
@Composable
fun rememberArtworkPalette(
    imageUrl: String?,
    dark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
    /**
     * The artwork size to read, which should be whichever one the surface
     * already has on screen, matching the backdrop drawn from it.
     *
     * A quantiser cares about a thumbnail's resolution no more than a blur
     * does, so the only thing this choice decides is whether the read comes
     * out of the cache or off the network.
     */
    artPx: Int = CARD_ART_PX,
    keyColors: ArtworkKeyColors? = null,
): ArtworkPalette {
    val scheme = MaterialTheme.colorScheme
    val reduceAnimation by PlayerPlatform.host.settings.reduceAnimation.collectAsStateWithLifecycle()
    // Always asked, so the composable call is unconditional; handed nothing to
    // read when the colours are already in hand.
    val decoded = rememberArtworkSeed(if (keyColors == null) imageUrl else null, artPx)
    val seed = keyColors?.toSeed() ?: decoded
    // Whether the colours were there from the first frame. If they were, there
    // is nothing to crossfade *from* and animating would only put a delay in
    // front of a surface that could already be right.
    val knownUpFront = remember(imageUrl) { decoded != null }

    val target = seed?.toPalette(dark) ?: ArtworkPalette(
        background = scheme.background,
        wash = scheme.background,
        elevated = scheme.surfaceVariant,
        accent = scheme.primary,
        onBackground = scheme.onBackground,
        onBackgroundVariant = scheme.onSurfaceVariant,
        divider = scheme.outline,
    )

    val spec: AnimationSpec<Color> = if (reduceAnimation || knownUpFront) {
        snap()
    } else {
        tween(TINT_FADE_MS)
    }
    return ArtworkPalette(
        background = animateColorAsState(target.background, spec, label = "tintBackground").value,
        wash = animateColorAsState(target.wash, spec, label = "tintWash").value,
        elevated = animateColorAsState(target.elevated, spec, label = "tintElevated").value,
        accent = animateColorAsState(target.accent, spec, label = "tintAccent").value,
        onBackground = animateColorAsState(target.onBackground, spec, label = "tintOn").value,
        onBackgroundVariant = animateColorAsState(
            target.onBackgroundVariant, spec, label = "tintOnVariant",
        ).value,
        divider = animateColorAsState(target.divider, spec, label = "tintDivider").value,
    )
}

/**
 * The playing song's colour, for chrome that takes it on: the Home mark and
 * title, the selected tab, the library's logo and menu glyphs. Null while
 * nothing plays, so each caller keeps its own colour then:
 * `LocalNowPlayingTint.current ?: MaterialTheme.colorScheme.onBackground`.
 *
 * Provided once, at the app's root, from [rememberNowPlayingTint].
 */
val LocalNowPlayingTint = compositionLocalOf<Color?> { null }

/**
 * The accent [rememberArtworkPalette] would give the artwork at [artworkUrl] —
 * the sleeve's one accent ([pickAccentSwatch]), made legible on the theme's background — or
 * null while nothing plays. Until a new sleeve has been read the last song's
 * colour holds, and the change crossfades rather than cuts unless "Reduce
 * animation" is on.
 */
@Composable
fun rememberNowPlayingTint(
    artworkUrl: String?,
    dark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
): Color? {
    val reduceAnimation by PlayerPlatform.host.settings.reduceAnimation.collectAsStateWithLifecycle()
    val seed = rememberArtworkSeed(artworkUrl, CARD_ART_PX)
    var settled by remember { mutableStateOf<Color?>(null) }
    val target = when {
        artworkUrl == null -> null
        seed != null -> seed.toPalette(dark).accent
        else -> settled
    }
    SideEffect { settled = target }
    if (target == null) return null
    val spec: AnimationSpec<Color> = if (reduceAnimation) snap() else tween(TINT_FADE_MS)
    return animateColorAsState(target, spec, label = "nowPlayingTint").value
}

/**
 * Reads top-band relative luminance from the cached palette decode.
 * Returns raw artwork luminance without applying top scrim calculations.
 */
@Composable
fun rememberArtworkTopBandLuminance(
    imageUrl: String?,
    artPx: Int = CARD_ART_PX,
): Float? = rememberArtworkSeed(imageUrl, artPx)?.topBandLuminance

/**
 * How bright the artwork's bottom band is ([controlsBandLuminance]): the part of the
 * sleeve the player's credits and controls sit over, and that the backdrop under them is
 * coloured from. Null while the decode is still on its way.
 */
@Composable
fun rememberArtworkControlsBandLuminance(
    imageUrl: String?,
    artPx: Int = CARD_ART_PX,
): Float? = rememberArtworkSeed(imageUrl, artPx)?.controlsBandLuminance

/**
 * The mean relative luminance of the whole sleeve, off the same cached decode
 * as [rememberArtworkTopBandLuminance]: how white the artwork is overall,
 * which the player leans its text toward the accent by.
 */
@Composable
fun rememberArtworkLuminance(
    imageUrl: String?,
    artPx: Int = CARD_ART_PX,
): Float? = rememberArtworkSeed(imageUrl, artPx)?.luminance

/**
 * The colour the artwork is mostly made of, as read and unadjusted, off the
 * same cached decode as [rememberArtworkPalette]. Null while the decode is
 * still on its way.
 */
@Composable
fun rememberArtworkDominant(
    imageUrl: String?,
    artPx: Int = CARD_ART_PX,
): Color? = rememberArtworkSeed(imageUrl, artPx)?.dominant

@Composable
private fun rememberArtworkSeed(imageUrl: String?, artPx: Int): Seed? {
    val context = LocalPlatformContext.current
    var seed by remember(imageUrl) { mutableStateOf(imageUrl?.let(seedCache::get)) }

    LaunchedEffect(imageUrl, artPx) {
        if (imageUrl == null || seed != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl.artworkAt(artPx))
            .size(PALETTE_PX)
            .forPixelAccess()
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toImageBitmap() ?: return@LaunchedEffect
        val found = withContext(Dispatchers.Default) { seedOf(bitmap) } ?: return@LaunchedEffect
        seedCache[imageUrl] = found
        seed = found
    }
    return seed
}

/**
 * Colours already read, keyed by artwork URL.
 *
 * Reading them again costs a decode and a quantise for an answer that cannot
 * have changed — the artwork at a URL is the artwork at that URL. Access is
 * from composition and from the resumption of [rememberArtworkPalette]'s
 * effect, both on the main thread, so it needs no locking of its own.
 */
private val seedCache = object : LinkedHashMap<String, Seed>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, Seed>) = size > SEED_CACHE_ENTRIES
}

/** Deep enough to cover a session's browsing without holding a screenful of colours. */
private const val SEED_CACHE_ENTRIES = 128

private const val PALETTE_PX = 128

/** Short: this is a surface settling into its colour, not an effect in itself. */
private const val TINT_FADE_MS = 260

/**
 * The raw artwork colours: what the page is mostly made of, its brightest
 * note, and what its bottom edge averages out to.
 */
private data class Seed(
    val dominant: Color,
    val vibrant: Color,
    val edge: Color,
    val topBandLuminance: Float,
    val luminance: Float,
    val controlsBandLuminance: Float,
)

private fun seedOf(bitmap: ImageBitmap): Seed? {
    fun swatches(clearFilters: Boolean) =
        paletteSwatches(bitmap, SWATCH_COUNT, clearFilters)

    // The default filter deliberately throws away near-black and near-white.
    // That is useful while looking for an accent, but it is the wrong answer
    // to "what colour is this page mostly made of?" A dark photograph would
    // otherwise be reduced to whatever warm face or tiny coloured detail
    // survived the filter, and a monochrome sleeve to an anti-aliased fringe.
    // Both cases used to turn into the same maroon page.
    val all = swatches(clearFilters = true)
    if (all.isEmpty()) return null

    val dominant = all.maxBy { it.population }
    val vibrant = pickAccentSwatch(all.map { it.rgb to it.population })
    return Seed(
        dominant = Color(dominant.rgb),
        vibrant = Color(vibrant),
        edge = bitmap.bottomEdgeColor(),
        topBandLuminance = bitmap.topBandRelativeLuminance(),
        luminance = bitmap.relativeLuminance(),
        controlsBandLuminance = bitmap.controlsBandLuminance(),
    )
}

/**
 * The one accent colour a sleeve gets, out of its quantised swatches
 * (`rgb to population`): the whole app wears it for the playing song, from the
 * Home logo and the mini player to the full player and the ambient blobs.
 *
 * Scored on HSL chroma (saturation scaled by how far the lightness is from
 * either pole) against the square root of population: chroma is what makes a
 * colour read as a colour, so a saturated swatch beats a white or pale one, and
 * the square root keeps a speck from winning over a colour the sleeve is
 * actually made of. Near-black swatches are never picked while anything else
 * is there; a sleeve that is nothing but near-black gets its brightest swatch.
 */
internal fun pickAccentSwatch(swatches: List<Pair<Int, Int>>): Int {
    require(swatches.isNotEmpty())
    val hsl = FloatArray(3)
    fun lightness(rgb: Int): Float {
        ColorUtils.colorToHSL(rgb, hsl)
        return hsl[2]
    }
    val candidates = swatches.filter { (rgb, _) -> lightness(rgb) >= ACCENT_MIN_LIGHTNESS }
    if (candidates.isEmpty()) return swatches.maxBy { (rgb, _) -> lightness(rgb) }.first
    return candidates.maxBy { (rgb, population) ->
        ColorUtils.colorToHSL(rgb, hsl)
        val chroma = hsl[1] * (1f - kotlin.math.abs(2f * hsl[2] - 1f))
        // Pale tints (lightness past ACCENT_PALE_FROM) are marked down on top of
        // chroma's own falloff, so a cream or a pastel loses to a real colour.
        val pale = if (hsl[2] > ACCENT_PALE_FROM) 0.5f else 1f
        (chroma * pale + ACCENT_CHROMA_FLOOR) * sqrt(population.toFloat())
    }.first
}

/** Below this HSL lightness a swatch is near-black and never the accent. */
private const val ACCENT_MIN_LIGHTNESS = 0.16f

/** Above this HSL lightness a swatch counts as pale and is marked down. */
private const val ACCENT_PALE_FROM = 0.80f

/**
 * A sliver of score every swatch gets, so a sleeve with no colour at all still
 * picks its most common usable grey instead of an arbitrary one.
 */
private const val ACCENT_CHROMA_FLOOR = 0.01f

/**
 * A seed with every note taken from the supplied colours: the page is the
 * background from top to edge, and the accent is the supplied one.
 */
private fun ArtworkKeyColors.toSeed() = Seed(
    dominant = background,
    vibrant = accent,
    edge = background,
    topBandLuminance = relativeLuminance(background.toArgb()),
    luminance = relativeLuminance(background.toArgb()),
    controlsBandLuminance = relativeLuminance(background.toArgb()),
)

private const val SWATCH_COUNT = 24

/**
 * The mean of the artwork's bottom band — what a blur wide enough to lose the
 * picture leaves behind at that edge.
 *
 * A flat mean rather than a quantised swatch on purpose: a blur has no notion
 * of which colour is *important*, and the page under the artwork has to match
 * what the blur actually produced, not what the picture is about.
 */
private fun ImageBitmap.bottomEdgeColor(): Color {
    val band = (height * EDGE_BAND).toInt().coerceIn(1, height)
    val pixels = IntArray(width * band)
    readPixels(pixels, startX = 0, startY = height - band, width = width, height = band)

    var red = 0L
    var green = 0L
    var blue = 0L
    pixels.forEach { pixel ->
        red += (pixel shr 16) and 0xFF
        green += (pixel shr 8) and 0xFF
        blue += pixel and 0xFF
    }
    val count = pixels.size.coerceAtLeast(1)
    return Color(
        red = (red / count).toInt(),
        green = (green / count).toInt(),
        blue = (blue / count).toInt(),
    )
}

/** How much of the artwork's height the edge colour is read from. */
private const val EDGE_BAND = 0.18f

/**
 * The status inset occupies only the upper sliver of the full-bleed hero on a
 * phone. Keep this tight so titles or faces lower in the cover do not decide
 * the icon colour for pixels that are never behind the system bar.
 */
private const val TOP_BAND = 0.10f

private fun ImageBitmap.topBandRelativeLuminance(): Float = topBandLuminance(TOP_BAND)

/**
 * The mean relative luminance of the top [fraction] of this picture. Works on
 * a frame read back off a playing clip as well as on a decoded sleeve; at the
 * sizes those come in at it is cheap enough for the main thread.
 */
fun ImageBitmap.topBandLuminance(fraction: Float): Float {
    val band = (height * fraction).toInt().coerceIn(1, height)
    val pixels = IntArray(width * band)
    readPixels(pixels, startX = 0, startY = 0, width = width, height = band)
    return averageRelativeLuminance(pixels)
}

private fun ImageBitmap.relativeLuminance(): Float {
    val pixels = IntArray(width * height)
    readPixels(pixels, startX = 0, startY = 0, width = width, height = height)
    return averageRelativeLuminance(pixels)
}

/**
 * How bright the bottom [CONTROLS_BAND] of a picture is, for text laid over it: the
 * [CONTROLS_BAND_PERCENTILE] relative luminance of its pixels rather than their mean, so
 * a band that is half white and half black reads as bright (the text would be lost over
 * the white half) instead of averaging out to a grey that suits either ink.
 *
 * Works on a sleeve or on a frame grabbed from a Canvas or music video; read off the main
 * thread, it is a sort of a few thousand values at the sizes those come in at.
 */
fun ImageBitmap.controlsBandLuminance(): Float {
    val band = (height * CONTROLS_BAND).toInt().coerceIn(1, height)
    val pixels = IntArray(width * band)
    readPixels(pixels, startX = 0, startY = height - band, width = width, height = band)
    return bandBrightness(pixels)
}

/** The [CONTROLS_BAND_PERCENTILE] relative luminance of [pixels]; 0 for none. */
internal fun bandBrightness(pixels: IntArray): Float {
    if (pixels.isEmpty()) return 0f
    // Strided: the answer is a percentile, and every other pixel of a decode
    // this small gives the same one.
    val step = if (pixels.size > 4096) 2 else 1
    val values = FloatArray((pixels.size + step - 1) / step) { relativeLuminance(pixels[it * step]) }
    values.sort()
    return values[((values.size - 1) * CONTROLS_BAND_PERCENTILE).toInt()]
}

/**
 * The share of a picture's height, from its bottom edge, that the controls band is read
 * from. On a phone the full-bleed banner ends behind the credits and the deck sits on
 * the backdrop coloured from that same bottom edge, so the bottom third is what is
 * behind them; a card-sized sleeve's backdrop is coloured from it too.
 */
private const val CONTROLS_BAND = 0.35f

/** See [bandBrightness]: 70% of the band is at or below this brightness. */
private const val CONTROLS_BAND_PERCENTILE = 0.70f

/** WCAG relative luminance: average in linear light, never gamma-encoded RGB. */
internal fun averageRelativeLuminance(pixels: IntArray): Float {
    if (pixels.isEmpty()) return 0f
    return pixels.sumOf { relativeLuminance(it).toDouble() }.div(pixels.size).toFloat()
}

internal fun relativeLuminance(argb: Int): Float {
    fun linear(channel: Int): Float {
        val srgb = channel / 255f
        return if (srgb <= 0.04045f) srgb / 12.92f else ((srgb + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    }
    return 0.2126f * linear((argb shr 16) and 0xFF) +
        0.7152f * linear((argb shr 8) and 0xFF) +
        0.0722f * linear(argb and 0xFF)
}

/**
 * Maps artwork luminance to top scrim opacity to keep white status bar
 * icons legible over light album covers while staying subtle on dark ones.
 */
internal fun topBandScrimAlpha(artworkLuminance: Float?): Float {
    val luminance = artworkLuminance?.coerceIn(0f, 1f) ?: 0f
    return PLAYER_STATUS_SCRIM_MIN_ALPHA +
        (PLAYER_STATUS_SCRIM_MAX_ALPHA - PLAYER_STATUS_SCRIM_MIN_ALPHA) * luminance
}

private const val PLAYER_STATUS_SCRIM_MIN_ALPHA = 0.16f
private const val PLAYER_STATUS_SCRIM_MAX_ALPHA = 0.65f

private fun Seed.toPalette(dark: Boolean): ArtworkPalette = if (dark) {
    ArtworkPalette(
        // Deep enough that white body text clears contrast on any sleeve, but
        // not so deep the hue is gone — the whole point is that the page is
        // recognisably *this* record's colour.
        background = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.20f, maximum = 0.62f) },
            lightness = { 0.13f },
        ),
        // Follows the edge's own brightness within a band that stays clear of
        // white body text at the top and of [background] at the bottom: a
        // sleeve that ends dark hands over almost invisibly, one that ends
        // bright leaves a page that is visibly lit from under the artwork.
        wash = edge.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.18f, maximum = 0.58f) },
            lightness = { it.coerceIn(0.14f, 0.24f) },
        ),
        elevated = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.20f, maximum = 0.62f) },
            lightness = { 0.22f },
        ),
        accent = accentOf(vibrant, dark = true),
        onBackground = Color.White,
        // Well above the grey the untinted screens use for secondary text. A
        // tint is a *coloured* background, not a black one, so the contrast a
        // dim grey has against black is not the contrast it has here — artist
        // names were sinking into the wash on mid-toned sleeves.
        onBackgroundVariant = Color.White.copy(alpha = 0.80f),
        divider = Color.White.copy(alpha = 0.12f),
    )
} else {
    ArtworkPalette(
        background = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.14f, maximum = 0.50f) },
            lightness = { 0.91f },
        ),
        wash = edge.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.12f, maximum = 0.46f) },
            lightness = { it.coerceIn(0.78f, 0.90f) },
        ),
        elevated = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.14f, maximum = 0.50f) },
            lightness = { 0.83f },
        ),
        accent = accentOf(vibrant, dark = false),
        onBackground = Color.Black,
        onBackgroundVariant = Color.Black.copy(alpha = 0.70f),
        divider = Color.Black.copy(alpha = 0.10f),
    )
}

/**
 * [color] made into an accent: saturated enough to read as a colour and lit
 * so it stays legible on the theme's background. The same lift the artwork
 * pages give their sleeve's brightest note, so an accent taken from anything
 * else (the ambient background's colour, say) sits beside them as one family.
 */
fun accentOf(color: Color, dark: Boolean): Color = color.withHsl(
    saturation = { adaptedArtworkSaturation(it, minimum = 0.55f, maximum = 1f) },
    lightness = { if (dark) it.coerceIn(0.62f, 0.78f) else it.coerceIn(0.30f, 0.44f) },
)

/**
 * Keeps neutral artwork neutral instead of inventing a hue for it.
 *
 * HSL represents grey with hue zero. Raising that grey to a saturation floor
 * therefore does not make it "more colourful"; it manufactures red, which
 * becomes brown/maroon once the page lightness is lowered. A small real amount
 * of colour is kept as-is, while an unmistakably chromatic swatch can still be
 * strengthened enough to make controls legible and the page recognisable.
 */
internal fun adaptedArtworkSaturation(source: Float, minimum: Float, maximum: Float): Float {
    val saturation = source.coerceIn(0f, 1f)
    return if (saturation < CHROMATIC_SATURATION_THRESHOLD) {
        saturation
    } else {
        saturation.coerceIn(minimum, maximum)
    }
}

/** Below this, boosting saturation makes quantisation noise visible as a tint. */
private const val CHROMATIC_SATURATION_THRESHOLD = 0.12f

private fun Color.withHsl(
    saturation: (Float) -> Float = { it },
    lightness: (Float) -> Float = { it },
): Color {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }
    hsl[1] = saturation(hsl[1]).coerceIn(0f, 1f)
    hsl[2] = lightness(hsl[2]).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Perceived brightness, used only to tell a dark theme from a light one. */
private fun Color.luminance(): Float = ColorUtils.calculateLuminance(toArgb()).toFloat()

/**
 * The page palette [rememberArtworkPalette] would give artwork already in
 * hand as [bitmap], for work done off screen (a generated playlist cover).
 * Null when the picture has no colours to read.
 */
fun artworkPaletteOf(bitmap: ImageBitmap, dark: Boolean): ArtworkPalette? = seedOf(bitmap)?.toPalette(dark)
