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
 *
 * [disableHaptics] draws nothing, but it is here for the same person: the
 * mode is the one switch for a phone that has little to spare, and a
 * vibration is work that phone can go without.
 */
data class EffectSettings(
    val appStyle: AppStyle,
    val reduceAnimation: Boolean,
    val ambientBackground: Boolean,
    val dialTileStyle: DialTileStyle,
    val lyricsBlur: Boolean,
    val animatedCanvas: Boolean,
    val disableHaptics: Boolean,
) {
    /**
     * One line for the prefs: [FORMAT], then the fields in order, with `;`
     * between.
     */
    fun encode(): String = listOf(
        FORMAT,
        appStyle.name,
        reduceAnimation,
        ambientBackground,
        dialTileStyle.name,
        lyricsBlur,
        animatedCanvas,
        disableHaptics,
    ).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = ";"
        private const val FIELDS = 7

        /** The fields a build from before the haptics joined the list wrote. */
        private const val FIELDS_BEFORE_HAPTICS = 6

        /**
         * First on the line since the haptics joined the list. Without it a
         * line of seven could be this one or the old one with the refresh
         * rate, and the two do not read the same.
         */
        private const val FORMAT = "v2"

        /** What Performance mode sets: nothing sampled, blurred or kept moving. */
        val LOW_COST = EffectSettings(
            appStyle = AppStyle.SOLID,
            reduceAnimation = true,
            ambientBackground = false,
            dialTileStyle = DialTileStyle.NONE,
            lyricsBlur = false,
            animatedCanvas = false,
            disableHaptics = true,
        )

        /**
         * What [encode] wrote, or null for anything else.
         *
         * Two older lines are still read. A build from before the haptics
         * joined the list wrote six fields and no [FORMAT]; one from before
         * the refresh rate left it wrote that third of seven, and it is passed
         * over. Neither build's Performance mode touched the haptics, so
         * neither line has them to put back: they read as [LOW_COST]'s value,
         * which [restored] leaves as the person has them now.
         */
        fun decode(text: String?): EffectSettings? {
            val all = text?.split(SEPARATOR) ?: return null
            val parts = when {
                all.size == FIELDS + 1 && all[0] == FORMAT -> all.drop(1)
                all.size == FIELDS_BEFORE_HAPTICS -> all + LOW_COST.disableHaptics.toString()
                all.size == FIELDS_BEFORE_HAPTICS + 1 ->
                    all.take(2) + all.drop(3) + LOW_COST.disableHaptics.toString()
                else -> return null
            }
            return runCatching {
                if (all.size == FIELDS_BEFORE_HAPTICS + 1) all[2].toBooleanStrict()
                EffectSettings(
                    appStyle = AppStyle.valueOf(parts[0]),
                    reduceAnimation = parts[1].toBooleanStrict(),
                    ambientBackground = parts[2].toBooleanStrict(),
                    dialTileStyle = DialTileStyle.valueOf(parts[3]),
                    lyricsBlur = parts[4].toBooleanStrict(),
                    animatedCanvas = parts[5].toBooleanStrict(),
                    disableHaptics = parts[6].toBooleanStrict(),
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
                disableHaptics = if (now.disableHaptics == LOW_COST.disableHaptics) saved.disableHaptics else now.disableHaptics,
            )
    }
}
