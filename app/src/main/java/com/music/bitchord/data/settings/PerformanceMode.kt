package com.music.bitchord.data.settings

import com.music.bitchord.ui.components.DialTileStyle

/**
 * The settings that decide how much each frame has to draw, as they stand.
 *
 * Performance mode is a preset over these: turned on, it keeps what they were
 * and sets [LOW_COST]; turned off, it puts back what it kept. Each setting
 * keeps its own row in Settings, so what the mode did can be seen and any one
 * of them changed while it is on.
 *
 * The display's refresh rate ("High performance mode" in Settings) is not one
 * of them. It was, while that switch could only be on with full effects, and
 * so Performance mode turned it off: the person who wanted both the lightest
 * drawing and the fastest display could not have them.
 */
data class EffectSettings(
    val appStyle: AppStyle,
    val reduceAnimation: Boolean,
    val ambientBackground: Boolean,
    val dialTileStyle: DialTileStyle,
    val lyricsBlur: Boolean,
    val animatedCanvas: Boolean,
) {
    /** One line for the prefs: the fields in order, with `;` between. */
    fun encode(): String = listOf(
        appStyle.name,
        reduceAnimation,
        ambientBackground,
        dialTileStyle.name,
        lyricsBlur,
        animatedCanvas,
    ).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = ";"
        private const val FIELDS = 6

        /** What Performance mode sets: nothing sampled, blurred or kept moving. */
        val LOW_COST = EffectSettings(
            appStyle = AppStyle.SOLID,
            reduceAnimation = true,
            ambientBackground = false,
            dialTileStyle = DialTileStyle.NONE,
            lyricsBlur = false,
            animatedCanvas = false,
        )

        /**
         * What [encode] wrote, or null for anything else. A build from before
         * the refresh rate left the list wrote it third of seven: that line is
         * read with the third field passed over.
         */
        fun decode(text: String?): EffectSettings? {
            val all = text?.split(SEPARATOR) ?: return null
            val parts = when (all.size) {
                FIELDS -> all
                FIELDS + 1 -> all.take(2) + all.drop(3)
                else -> return null
            }
            return runCatching {
                if (all.size > FIELDS) all[2].toBooleanStrict()
                EffectSettings(
                    appStyle = AppStyle.valueOf(parts[0]),
                    reduceAnimation = parts[1].toBooleanStrict(),
                    ambientBackground = parts[2].toBooleanStrict(),
                    dialTileStyle = DialTileStyle.valueOf(parts[3]),
                    lyricsBlur = parts[4].toBooleanStrict(),
                    animatedCanvas = parts[5].toBooleanStrict(),
                )
            }.getOrNull()
        }

        /**
         * What to set when the mode ends, having been turned on over [saved]
         * with the settings standing at [now]. A setting still at its
         * [LOW_COST] value goes back to what it was; one that was changed
         * while the mode was on was changed on purpose, and stays.
         */
        fun restored(saved: EffectSettings, now: EffectSettings): EffectSettings =
            EffectSettings(
                appStyle = if (now.appStyle == LOW_COST.appStyle) saved.appStyle else now.appStyle,
                reduceAnimation = if (now.reduceAnimation == LOW_COST.reduceAnimation) saved.reduceAnimation else now.reduceAnimation,
                ambientBackground = if (now.ambientBackground == LOW_COST.ambientBackground) saved.ambientBackground else now.ambientBackground,
                dialTileStyle = if (now.dialTileStyle == LOW_COST.dialTileStyle) saved.dialTileStyle else now.dialTileStyle,
                lyricsBlur = if (now.lyricsBlur == LOW_COST.lyricsBlur) saved.lyricsBlur else now.lyricsBlur,
                animatedCanvas = if (now.animatedCanvas == LOW_COST.animatedCanvas) saved.animatedCanvas else now.animatedCanvas,
            )
    }
}
