package com.music.bitchord.data.settings

/**
 * What the app's bars, menus and cards are made of. One choice in Settings,
 * kept as the two switches it replaced: [AppSettings.liquidGlass] and
 * [AppSettings.reduceDynamicBlur].
 */
enum class AppStyle {
    /** Liquid glass: the page behind, bent and blurred. */
    GLASS,

    /** The frosted blur the app wears with liquid glass off. */
    FROST,

    /** Solid fills, nothing sampled or blurred. */
    SOLID,
    ;

    companion object {
        /** The style two switches amount to. Solid wins: it is what is drawn when both are on. */
        fun of(liquidGlass: Boolean, reduceDynamicBlur: Boolean): AppStyle = when {
            reduceDynamicBlur -> SOLID
            liquidGlass -> GLASS
            else -> FROST
        }

        /** The styles there are to choose from. Glass needs Android 12; older devices choose between the other two. */
        fun choices(glassSupported: Boolean): List<AppStyle> = if (glassSupported) entries else listOf(FROST, SOLID)
    }
}
