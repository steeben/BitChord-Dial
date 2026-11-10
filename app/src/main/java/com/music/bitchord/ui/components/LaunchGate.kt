package com.music.bitchord.ui.components

/**
 * Holds the system splash on screen until Home is ready to be seen.
 *
 * On Android 12 and up the system splash plays the logo building itself
 * (`drawable/splash_logo`), an animated vector the system runs off the app's
 * main thread, so it stays smooth however busy the app is starting up. The
 * activity holds back its first frame, and with it the splash's exit, until
 * [open] is set; then the splash, its logo still lit, sweeps off the app.
 */
object LaunchGate {
    @Volatile
    var open: Boolean = false

    /** When the activity was created, for the splash animation's minimum run. */
    @Volatile
    var createdAt: Long = 0L

    /**
     * The least the splash is shown: the logo building itself (1040ms, see
     * `drawable/splash_logo`) and a beat with it fully lit before it leaves.
     */
    const val ANIMATION_MS = 1_250L

    /** The longest the first frame is held back, whatever happens. */
    const val MAX_HOLD_MS = 6_000L

    /** How long the splash takes to fade into the app once it lets go. */
    const val EXIT_FADE_MS = 160L

    /** How long the splash takes to sweep off the app, corner to corner; see [LaunchWipeView]. */
    const val EXIT_WIPE_MS = 650L
}
