package com.music.bitchord

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The home-screen icon choices. Each is an `<activity-alias>` in the manifest
 * carrying the launcher intent filter; exactly one is enabled at a time.
 */
enum class AppIcon(val alias: String, val iconRes: Int, val labelRes: Int) {
    // Dark is the one that ships enabled. Bright keeps the old Default alias
    // name, so an install that had picked it explicitly keeps it.
    Dark("LauncherDark", R.mipmap.ic_launcher_dark, R.string.app_icon_dark),
    Bright("LauncherDefault", R.mipmap.ic_launcher, R.string.app_icon_bright);

    // The aliases are declared against the namespace, not the flavour's
    // applicationId, so the class name is fixed while the package is not.
    private fun component(context: Context) =
        ComponentName(context.packageName, "com.music.bitchord.$alias")

    companion object {
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
