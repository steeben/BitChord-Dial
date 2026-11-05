package com.music.bitchord

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The home-screen icon choices. Each is an `<activity-alias>` in the manifest
 * carrying the launcher intent filter; exactly one is enabled at a time.
 *
 * BitChord's glass and neon icons are adaptive icons whose foreground already
 * holds the finished icon in its own shape (Apple squircle, Samsung squircle
 * or Pixel circle), so [previewRes] — that foreground — shows the variant as
 * designed rather than through the device's launcher mask.
 */
enum class AppIcon(val alias: String, val iconRes: Int, val labelRes: Int, val previewRes: Int? = null) {
    // Dark is the one that ships enabled. Bright keeps the old Default alias
    // name, so an install that had picked it explicitly keeps it.
    Dark("LauncherDark", R.mipmap.ic_launcher_dark, R.string.app_icon_dark),
    Bright("LauncherDefault", R.mipmap.ic_launcher, R.string.app_icon_bright),

    // BitChord's own icons, after Dial's. Their aliases are prefixed so
    // none of them takes over a Dial icon's alias name.
    BitChord("LauncherBitChord", R.mipmap.ic_bitchord, R.string.app_icon_bitchord),
    Monochrome("LauncherBitChordMonochrome", R.mipmap.ic_monochrome, R.string.app_icon_monochrome),
    LiquidGlass("LauncherBitChordGlass", R.mipmap.ic_liquid_glass, R.string.app_icon_liquid_glass, R.mipmap.ic_liquid_glass_foreground),
    LiquidGlassDark("LauncherBitChordGlassDark", R.mipmap.ic_liquid_glass_dark, R.string.app_icon_liquid_glass_dark, R.mipmap.ic_liquid_glass_dark_foreground),
    LiquidGlassClear("LauncherBitChordGlassClear", R.mipmap.ic_liquid_glass_clear, R.string.app_icon_liquid_glass_clear, R.mipmap.ic_liquid_glass_clear_foreground),
    LiquidGlassClearDark("LauncherBitChordGlassClearDark", R.mipmap.ic_liquid_glass_clear_dark, R.string.app_icon_liquid_glass_clear_dark, R.mipmap.ic_liquid_glass_clear_dark_foreground),
    SamsungGlass("LauncherBitChordSamsungGlass", R.mipmap.ic_samsung_glass, R.string.app_icon_samsung_glass, R.mipmap.ic_samsung_glass_foreground),
    SamsungGlassDark("LauncherBitChordSamsungGlassDark", R.mipmap.ic_samsung_glass_dark, R.string.app_icon_samsung_glass_dark, R.mipmap.ic_samsung_glass_dark_foreground),
    SamsungGlassClear("LauncherBitChordSamsungGlassClear", R.mipmap.ic_samsung_glass_clear, R.string.app_icon_samsung_glass_clear, R.mipmap.ic_samsung_glass_clear_foreground),
    SamsungGlassClearDark("LauncherBitChordSamsungGlassClearDark", R.mipmap.ic_samsung_glass_clear_dark, R.string.app_icon_samsung_glass_clear_dark, R.mipmap.ic_samsung_glass_clear_dark_foreground),
    PixelGlass("LauncherBitChordPixelGlass", R.mipmap.ic_pixel_glass, R.string.app_icon_pixel_glass, R.mipmap.ic_pixel_glass_foreground),
    PixelGlassDark("LauncherBitChordPixelGlassDark", R.mipmap.ic_pixel_glass_dark, R.string.app_icon_pixel_glass_dark, R.mipmap.ic_pixel_glass_dark_foreground),
    PixelGlassClear("LauncherBitChordPixelGlassClear", R.mipmap.ic_pixel_glass_clear, R.string.app_icon_pixel_glass_clear, R.mipmap.ic_pixel_glass_clear_foreground),
    PixelGlassClearDark("LauncherBitChordPixelGlassClearDark", R.mipmap.ic_pixel_glass_clear_dark, R.string.app_icon_pixel_glass_clear_dark, R.mipmap.ic_pixel_glass_clear_dark_foreground),
    NeonSilver("LauncherBitChordNeonSilver", R.mipmap.ic_neon_silver, R.string.app_icon_neon_silver, R.mipmap.ic_neon_silver_foreground),
    NeonCyan("LauncherBitChordNeonCyan", R.mipmap.ic_neon_cyan, R.string.app_icon_neon_cyan, R.mipmap.ic_neon_cyan_foreground),
    NeonViolet("LauncherBitChordNeonViolet", R.mipmap.ic_neon_violet, R.string.app_icon_neon_violet, R.mipmap.ic_neon_violet_foreground),
    SamsungNeonSilver("LauncherBitChordSamsungNeonSilver", R.mipmap.ic_samsung_neon_silver, R.string.app_icon_samsung_neon_silver, R.mipmap.ic_samsung_neon_silver_foreground),
    SamsungNeonCyan("LauncherBitChordSamsungNeonCyan", R.mipmap.ic_samsung_neon_cyan, R.string.app_icon_samsung_neon_cyan, R.mipmap.ic_samsung_neon_cyan_foreground),
    SamsungNeonViolet("LauncherBitChordSamsungNeonViolet", R.mipmap.ic_samsung_neon_violet, R.string.app_icon_samsung_neon_violet, R.mipmap.ic_samsung_neon_violet_foreground),
    PixelNeonSilver("LauncherBitChordPixelNeonSilver", R.mipmap.ic_pixel_neon_silver, R.string.app_icon_pixel_neon_silver, R.mipmap.ic_pixel_neon_silver_foreground),
    PixelNeonCyan("LauncherBitChordPixelNeonCyan", R.mipmap.ic_pixel_neon_cyan, R.string.app_icon_pixel_neon_cyan, R.mipmap.ic_pixel_neon_cyan_foreground),
    PixelNeonViolet("LauncherBitChordPixelNeonViolet", R.mipmap.ic_pixel_neon_violet, R.string.app_icon_pixel_neon_violet, R.mipmap.ic_pixel_neon_violet_foreground);

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
         * The Glass and Glass Dark icons were removed; their aliases stay in the
         * manifest (drawn with the Dark icon) only so an install that had picked
         * one is never left without a home-screen entry. Moves such an install
         * onto Dark and switches the retired alias off.
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

        private val RETIRED_ALIASES = listOf("LauncherGlassLight", "LauncherGlassDark")

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
