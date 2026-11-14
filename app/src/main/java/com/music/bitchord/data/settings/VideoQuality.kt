package com.music.bitchord.data.settings

/**
 * The video quality the listener set in Settings, Performance. It applies to
 * a music video and to a music video that is shown as the Canvas of a song.
 *
 * [AUTO] is the default. It keeps the rule the app had before this setting:
 * 1080p, or 480p on a metered connection. The other values are the same on
 * all connections.
 */
enum class VideoQuality {
    AUTO,
    P480,
    P1080,
    MAX,
    ;

    /** The largest video height to ask for. [metered] is null when the network is not known. */
    fun maxHeight(metered: Boolean?): Int = when (this) {
        AUTO -> if (metered == true) METERED_HEIGHT else FULL_HD_HEIGHT
        P480 -> METERED_HEIGHT
        P1080 -> FULL_HD_HEIGHT
        MAX -> MAX_HEIGHT
    }

    companion object {
        /** The limit of [AUTO] on a metered connection. */
        const val METERED_HEIGHT = 480

        /**
         * The limit of [AUTO] on other connections. A height above this limit
         * can use a codec that some phones cannot decode. Thus this is also
         * the height the player goes back to when a larger one fails.
         */
        const val FULL_HD_HEIGHT = 1080

        /** The limit of [MAX]: 4K. */
        const val MAX_HEIGHT = 2160

        /**
         * The height to ask for. [held] is the height from the quality box of
         * the video page, which has priority. [steppedDown] is true after a
         * height above [FULL_HD_HEIGHT] failed to play for this video.
         */
        fun heightToAsk(setting: VideoQuality, held: Int?, metered: Boolean?, steppedDown: Boolean): Int {
            val asked = held ?: setting.maxHeight(metered)
            return if (steppedDown) minOf(asked, FULL_HD_HEIGHT) else asked
        }

        /** The stored name back to its value. An unknown name gives [AUTO]. */
        fun decode(stored: String?): VideoQuality = entries.firstOrNull { it.name == stored } ?: AUTO
    }
}
