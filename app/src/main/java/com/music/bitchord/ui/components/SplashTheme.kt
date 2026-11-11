package com.music.bitchord.ui.components

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import com.music.bitchord.R
import com.music.bitchord.data.settings.ThemeMode

/**
 * Keeps the system splash in step with the app's own theme setting: white with
 * black squares in the light theme, black with white squares in the dark.
 *
 * The splash is drawn by the system before the app runs, from resources, so on
 * its own it can only follow the phone's light or dark mode (values-v31 and
 * values-night-v31). From Android 13 an app can name the splash look for its
 * next launches, so the app's Light or Dark setting is passed on whenever it
 * changes; System leaves it to the phone's mode.
 */
object SplashTheme {
    /** Whether the splash this launch opened on was the dark one. */
    fun isDark(context: Context, mode: ThemeMode): Boolean {
        val phoneDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return phoneDark
        return splashDarkFor(mode, phoneDark)
    }

    /** Names the splash look for the app's next launches (Android 13 and up). */
    fun remember(activity: Activity, mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val style = when (mode) {
            ThemeMode.SYSTEM -> Resources.ID_NULL
            ThemeMode.LIGHT -> R.style.Theme_BitChord_SplashLight
            ThemeMode.DARK -> R.style.Theme_BitChord_SplashDark
        }
        runCatching { activity.splashScreen.setSplashScreenTheme(style) }
    }
}

/** Whether the splash is dark under the app's [mode], with the phone in dark mode or not. */
internal fun splashDarkFor(mode: ThemeMode, phoneDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> phoneDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
