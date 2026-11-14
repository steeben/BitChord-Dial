package com.music.bitchord.data.settings

import com.music.bitchord.ui.components.DialTileStyle

/**
 * The settings that decide how much each frame has to draw, as they stand.
 *
 * Performance mode is a preset over these: turned on, it keeps what they were
 * and sets [LOW_COST]; turned off, it puts back what it kept. Each setting
 * keeps its own row in Settings, so what the mode did can be seen and any one
 * of them changed while it is on.
 */
data class EffectSettings(
    val appStyle: AppStyle,
    val reduceAnimation: Boolean,
    /** Settings' "High performance mode": a higher refresh rate, with full effects. */
    val highRefreshRate: Boolean,
    val ambientBackground: Boolean,
    val dialTileStyle: DialTileStyle,
    val lyricsBlur: Boolean,
    val animatedCanvas: Boolean,
) {
    /** One line for the prefs: the fields in order, with `;` between. */
    fun encode(): String = listOf(
        appStyle.name,
        reduceAnimation,
        highRefreshRate,
        ambientBackground,
        dialTileStyle.name,
        lyricsBlur,
        animatedCanvas,
    ).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = ";"

        /** What Performance mode sets: nothing sampled, blurred or kept moving. */
        val LOW_COST = EffectSettings(
            appStyle = AppStyle.SOLID,
            reduceAnimation = true,
            highRefreshRate = false,
            ambientBackground = false,
            dialTileStyle = DialTileStyle.NONE,
            lyricsBlur = false,
            animatedCanvas = false,
        )

        /** What [encode] wrote, or null for anything else. */
        fun decode(text: String?): EffectSettings? {
            val parts = text?.split(SEPARATOR) ?: return null
            if (parts.size != 7) return null
            return runCatching {
                EffectSettings(
                    appStyle = AppStyle.valueOf(parts[0]),
                    reduceAnimation = parts[1].toBooleanStrict(),
                    highRefreshRate = parts[2].toBooleanStrict(),
                    ambientBackground = parts[3].toBooleanStrict(),
                    dialTileStyle = DialTileStyle.valueOf(parts[4]),
                    lyricsBlur = parts[5].toBooleanStrict(),
                    animatedCanvas = parts[6].toBooleanStrict(),
                )
            }.getOrNull()
        }

        /**
         * What to set when the mode ends, having been turned on over [saved]
         * with the settings standing at [now]. A setting still at its
         * [LOW_COST] value goes back to what it was; one that was changed
         * while the mode was on was changed on purpose, and stays.
         */
        fun restored(saved: EffectSettings, now: EffectSettings): EffectSettings {
            val out = EffectSettings(
                appStyle = if (now.appStyle == LOW_COST.appStyle) saved.appStyle else now.appStyle,
                reduceAnimation = if (now.reduceAnimation == LOW_COST.reduceAnimation) saved.reduceAnimation else now.reduceAnimation,
                highRefreshRate = if (now.highRefreshRate == LOW_COST.highRefreshRate) saved.highRefreshRate else now.highRefreshRate,
                ambientBackground = if (now.ambientBackground == LOW_COST.ambientBackground) saved.ambientBackground else now.ambientBackground,
                dialTileStyle = if (now.dialTileStyle == LOW_COST.dialTileStyle) saved.dialTileStyle else now.dialTileStyle,
                lyricsBlur = if (now.lyricsBlur == LOW_COST.lyricsBlur) saved.lyricsBlur else now.lyricsBlur,
                animatedCanvas = if (now.animatedCanvas == LOW_COST.animatedCanvas) saved.animatedCanvas else now.animatedCanvas,
            )
            // The high refresh rate goes with full effects only (see
            // AppSettings.setHighPerformanceMode). Where a change made while
            // the mode was on left the two at odds, the effects asked for win.
            val fullEffects = out.appStyle != AppStyle.SOLID && !out.reduceAnimation
            return if (out.highRefreshRate && !fullEffects) out.copy(highRefreshRate = false) else out
        }
    }
}
