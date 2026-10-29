package com.music.bitchord

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The home-screen icon choices. Each is an `<activity-alias>` in the manifest
 * carrying the launcher intent filter; exactly one is enabled at a time.
 *
 * The glass icons are full adaptive icons built from the logo grid (see
 * docs/icons/glass_icon.py), so the launcher's own mask shapes them like any
 * other icon on the device.
 */
enum class AppIcon(val alias: String, val iconRes: Int, val labelRes: Int) {
    Default("LauncherDefault", R.mipmap.ic_launcher, R.string.app_icon_default),
    GlassLight("LauncherGlassLight", R.mipmap.ic_launcher_glass_light, R.string.app_icon_glass_light),
    GlassDark("LauncherGlassDark", R.mipmap.ic_launcher_glass_dark, R.string.app_icon_glass_dark),
    Dark("LauncherDark", R.mipmap.ic_launcher_dark, R.string.app_icon_dark);

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
            } ?: Default
        }

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
