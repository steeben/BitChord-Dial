package com.music.bitchord.data.settings

/**
 * A video quality the listener can set in Settings, Performance. There is one
 * choice for Wi-Fi and one for mobile data. The choices apply to a music video
 * and to a music video that is shown as the Canvas of a song.
 *
 * The defaults keep the rule the app had before these settings: 1080p on
 * Wi-Fi and 480p on mobile data.
 */
enum class VideoQuality(
    /** The largest video height to ask for. */
    val maxHeight: Int,
) {
    P480(480),
    P1080(1080),

    /** The highest available height, to a limit of 4K. */
    MAX(2160),
    ;

    companion object {
        val WIFI_DEFAULT = P1080
        val MOBILE_DEFAULT = P480

        /**
         * A height above this limit can use a codec that some phones cannot
         * decode. Thus this is the height the player goes back to when a
         * larger one fails.
         */
        const val SAFE_HEIGHT = 1080

        /**
         * The choice for the connection. Mobile data is a metered connection,
         * which includes a metered hotspot. [metered] is null when the network
         * is not known, and then the Wi-Fi choice applies.
         */
        fun forConnection(wifi: VideoQuality, mobile: VideoQuality, metered: Boolean?): VideoQuality =
            if (metered == true) mobile else wifi

        /**
         * The height to ask for. [held] is the height from the quality box of
         * the video page, which has priority. [steppedDown] is true after a
         * height above [SAFE_HEIGHT] failed to play for this video.
         */
        fun heightToAsk(setting: VideoQuality, held: Int?, steppedDown: Boolean): Int {
            val asked = held ?: setting.maxHeight
            return if (steppedDown) minOf(asked, SAFE_HEIGHT) else asked
        }

        /** The stored name back to its value. An unknown name gives [default]. */
        fun decode(stored: String?, default: VideoQuality): VideoQuality =
            entries.firstOrNull { it.name == stored } ?: default
    }
}
