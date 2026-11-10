package com.music.bitchord.data

import android.content.Context
import android.content.Intent

/** What a step of the tour points at, for the ring drawn round it. */
enum class TourTarget { ACCOUNT, DIAL_MENU, LIBRARY_MENU }

/**
 * The first-launch tour, a step at a time, in the order shown. [WELCOME]
 * offers the tour or a way out of it; the rest are the tour itself.
 *
 * [onLibrary] is whether the step is shown over the Library tab rather than
 * Home, and [target] the control it points at, if it points at one.
 */
enum class OnboardingStep(val onLibrary: Boolean = false, val target: TourTarget? = null) {
    WELCOME,
    DIAL,
    PINS,
    SETTINGS(target = TourTarget.ACCOUNT),
    MIXES,
    DIAL_MENU(target = TourTarget.DIAL_MENU),
    LIBRARY(onLibrary = true, target = TourTarget.LIBRARY_MENU),
    FINISH,
    ;

    /** The step after this one, or null once the tour is over. */
    fun next(): OnboardingStep? = entries.getOrNull(ordinal + 1)

    /** The step before this one; the welcome has none, and nothing goes back to it. */
    fun previous(): OnboardingStep? = entries.getOrNull(ordinal - 1)?.takeIf { it != WELCOME }

    /** This step's place among the tour's own, from 1; the welcome is not one of them. */
    val number: Int get() = ordinal

    companion object {
        /** How many steps the tour has, not counting the welcome. */
        val count: Int get() = entries.size - 1
    }
}

/**
 * Whether the first-launch tour has been seen. In a file of its own, so
 * neither a backup import nor the stock settings test brings it back.
 */
object Onboarding {

    private const val PREFS = "dial_onboarding"
    private const val KEY_SEEN = "seen"

    fun shouldShow(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SEEN, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SEEN, true).apply()
    }

    /**
     * Starts the app again as if it had never been opened, as far as the tour
     * goes: forgets that it was seen and relaunches, so it plays from the
     * splash as a first launch does.
     */
    fun replay(context: Context) {
        val app = context.applicationContext
        // Written before the process goes, not after.
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_SEEN).commit()
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)?.component ?: return
        app.startActivity(Intent.makeRestartActivityTask(launch))
        Runtime.getRuntime().exit(0)
    }
}
