package com.music.bitchord.data.canvas

/**
 * The three kinds of moving picture the player can put where the cover is.
 *
 * - [ANIMATED_COVER]: an album's motion artwork (Apple, Tidal, the community index).
 * - [CANVAS]: Spotify's per-track Canvas.
 * - [MUSIC_VIDEO]: the song's music video, shown as its Canvas.
 */
enum class AnimatedArtKind {
    ANIMATED_COVER,
    CANVAS,
    MUSIC_VIDEO,
}

/** What kind of moving picture this clip is — see [AnimatedArtKind]. */
val CanvasArtwork.kind: AnimatedArtKind
    get() = when {
        musicVideoId != null -> AnimatedArtKind.MUSIC_VIDEO
        source == CanvasSource.SPOTIFY -> AnimatedArtKind.CANVAS
        else -> AnimatedArtKind.ANIMATED_COVER
    }

/**
 * Which kinds of moving artwork the listener allows, and which wins when a
 * song has more than one. Set in Appearance settings.
 *
 * [order] always holds every kind exactly once, best first; [enabled] is the
 * kinds switched on.
 */
data class AnimatedArtPrefs(
    val order: List<AnimatedArtKind> = DEFAULT_ORDER,
    val enabled: Set<AnimatedArtKind> = AnimatedArtKind.entries.toSet(),
) {
    fun allows(kind: AnimatedArtKind): Boolean = kind in enabled

    /** The looping-clip kinds the providers are asked for, best first. */
    val clipOrder: List<AnimatedArtKind>
        get() = order.filter { it != AnimatedArtKind.MUSIC_VIDEO && it in enabled }

    /** Whether Spotify's Canvas is asked for before the animated covers. */
    val canvasFirst: Boolean get() = clipOrder.firstOrNull() == AnimatedArtKind.CANVAS

    /** Enabled clip kinds ranked above the music video. */
    private val clipKindsAboveVideo: List<AnimatedArtKind>
        get() = order.takeWhile { it != AnimatedArtKind.MUSIC_VIDEO }
            .filter { it != AnimatedArtKind.MUSIC_VIDEO && it in enabled }

    /**
     * Whether a music video's picture should take the slot rather than [clip]
     * (null when no clip was found). [clipSettled] says the clip search is
     * over, so a null [clip] is final: until then a video ranked below a clip
     * kind waits, so a clip found a moment later never pushes it out mid-play.
     */
    fun videoOutranks(clip: AnimatedArtKind?, clipSettled: Boolean): Boolean {
        if (AnimatedArtKind.MUSIC_VIDEO !in enabled) return false
        val above = clipKindsAboveVideo
        return if (clip == null) clipSettled || above.isEmpty() else clip !in above
    }

    /** Stored form: `ANIMATED_COVER,CANVAS,MUSIC_VIDEO`. */
    fun encodeOrder(): String = order.joinToString(",") { it.name }

    /** Stored form of [enabled], in [order]. */
    fun encodeEnabled(): String = order.filter { it in enabled }.joinToString(",") { it.name }

    /** [kind] moved one place up (negative [by]) or down. */
    fun move(kind: AnimatedArtKind, by: Int): AnimatedArtPrefs {
        val from = order.indexOf(kind)
        if (from < 0) return this
        val to = (from + by).coerceIn(0, order.lastIndex)
        if (to == from) return this
        return copy(order = order.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun withEnabled(kind: AnimatedArtKind, on: Boolean): AnimatedArtPrefs =
        copy(enabled = if (on) enabled + kind else enabled - kind)

    companion object {
        val DEFAULT_ORDER = listOf(
            AnimatedArtKind.ANIMATED_COVER,
            AnimatedArtKind.CANVAS,
            AnimatedArtKind.MUSIC_VIDEO,
        )

        /**
         * Reads the stored order: unknown names are dropped and any kind
         * missing (one added in a later version) goes on the end.
         */
        fun decodeOrder(stored: String?): List<AnimatedArtKind> {
            if (stored.isNullOrBlank()) return DEFAULT_ORDER
            val saved = stored.split(",")
                .mapNotNull { name -> AnimatedArtKind.entries.firstOrNull { it.name == name.trim() } }
                .distinct()
            return saved + AnimatedArtKind.entries.filter { it !in saved }
        }

        /** Reads the stored enabled set; null (never saved) means all on. */
        fun decodeEnabled(stored: String?): Set<AnimatedArtKind> {
            if (stored == null) return AnimatedArtKind.entries.toSet()
            return stored.split(",")
                .mapNotNull { name -> AnimatedArtKind.entries.firstOrNull { it.name == name.trim() } }
                .toSet()
        }
    }
}
