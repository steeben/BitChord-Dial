package com.music.bitchord

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The colours the home-screen icon comes in. */
enum class AppIconColor(val labelRes: Int) {
    Red(R.string.app_icon_color_red),
    Blue(R.string.app_icon_color_blue),
    Green(R.string.app_icon_color_green),
    Pink(R.string.app_icon_color_pink),
    Orange(R.string.app_icon_color_orange),
    Purple(R.string.app_icon_color_purple),
    Yellow(R.string.app_icon_color_yellow),
    Mono(R.string.app_icon_color_mono),
}

/**
 * The home-screen icon choices. Each is an `<activity-alias>` in the manifest
 * carrying the launcher intent filter; exactly one is enabled at a time.
 * Every [color] comes as a pair, a dark icon and a bright one.
 */
enum class AppIcon(val alias: String, val iconRes: Int, val color: AppIconColor, val dark: Boolean) {
    // Dark is the one that ships enabled. Bright keeps the old Default alias
    // name, so an install that had picked it explicitly keeps it.
    Dark("LauncherDark", R.mipmap.ic_launcher_dark, AppIconColor.Red, true),
    Bright("LauncherDefault", R.mipmap.ic_launcher, AppIconColor.Red, false),
    BlueDark("LauncherBlueDark", R.mipmap.ic_launcher_blue_dark, AppIconColor.Blue, true),
    Blue("LauncherBlue", R.mipmap.ic_launcher_blue, AppIconColor.Blue, false),
    GreenDark("LauncherGreenDark", R.mipmap.ic_launcher_green_dark, AppIconColor.Green, true),
    Green("LauncherGreen", R.mipmap.ic_launcher_green, AppIconColor.Green, false),
    PinkDark("LauncherPinkDark", R.mipmap.ic_launcher_pink_dark, AppIconColor.Pink, true),
    Pink("LauncherPink", R.mipmap.ic_launcher_pink, AppIconColor.Pink, false),
    OrangeDark("LauncherOrangeDark", R.mipmap.ic_launcher_orange_dark, AppIconColor.Orange, true),
    Orange("LauncherOrange", R.mipmap.ic_launcher_orange, AppIconColor.Orange, false),
    PurpleDark("LauncherPurpleDark", R.mipmap.ic_launcher_purple_dark, AppIconColor.Purple, true),
    Purple("LauncherPurple", R.mipmap.ic_launcher_purple, AppIconColor.Purple, false),
    YellowDark("LauncherYellowDark", R.mipmap.ic_launcher_yellow_dark, AppIconColor.Yellow, true),
    Yellow("LauncherYellow", R.mipmap.ic_launcher_yellow, AppIconColor.Yellow, false),
    MonoDark("LauncherMonoDark", R.mipmap.ic_launcher_mono_dark, AppIconColor.Mono, true),
    Mono("LauncherMono", R.mipmap.ic_launcher_mono, AppIconColor.Mono, false);

    // The aliases are declared against the namespace, not the flavour's
    // applicationId, so the class name is fixed while the package is not.
    private fun component(context: Context) =
        ComponentName(context.packageName, "com.music.bitchord.$alias")

    companion object {
        /** The icon of [color] in its dark or bright version. */
        fun of(color: AppIconColor, dark: Boolean): AppIcon =
            entries.first { it.color == color && it.dark == dark }

        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull {
                pm.getComponentEnabledSetting(it.component(context)) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } ?: Dark
        }

        /**
         * The Glass and Glass Dark icons, and later all of BitChord's own icons,
         * were removed; their aliases stay in the manifest (drawn with the Dark
         * icon) only so an install that had picked one is never left without a
         * home-screen entry. Moves such an install onto Dark and switches the
         * retired alias off.
         */
        fun migrateRetired(context: Context) {
            val pm = context.packageManager
            val retired = RETIRED_ALIASES.map { ComponentName(context.packageName, "com.music.bitchord.$it") }
            val anyRetiredEnabled = retired.any {
                runCatching { pm.getComponentEnabledSetting(it) }.getOrNull() ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            if (!anyRetiredEnabled) return
            apply(context, Dark)
            retired.forEach {
                runCatching {
                    pm.setComponentEnabledSetting(
                        it,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }

        private val RETIRED_ALIASES = listOf(
            "LauncherGlassLight",
            "LauncherGlassDark",
            "LauncherBitChord",
            "LauncherBitChordMonochrome",
            "LauncherBitChordGlass",
            "LauncherBitChordGlassDark",
            "LauncherBitChordGlassClear",
            "LauncherBitChordGlassClearDark",
            "LauncherBitChordSamsungGlass",
            "LauncherBitChordSamsungGlassDark",
            "LauncherBitChordSamsungGlassClear",
            "LauncherBitChordSamsungGlassClearDark",
            "LauncherBitChordPixelGlass",
            "LauncherBitChordPixelGlassDark",
            "LauncherBitChordPixelGlassClear",
            "LauncherBitChordPixelGlassClearDark",
            "LauncherBitChordNeonSilver",
            "LauncherBitChordNeonCyan",
            "LauncherBitChordNeonViolet",
            "LauncherBitChordSamsungNeonSilver",
            "LauncherBitChordSamsungNeonCyan",
            "LauncherBitChordSamsungNeonViolet",
            "LauncherBitChordPixelNeonSilver",
            "LauncherBitChordPixelNeonCyan",
            "LauncherBitChordPixelNeonViolet",
        )

        /** Enables [icon] before disabling the rest so there is never a moment with no launcher entry. */
        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(
                    it.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
