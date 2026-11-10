package com.music.bitchord.ui.components

/**
 * Holds the system splash on screen until Home is ready to be seen.
 *
 * On Android 12 and up the system splash plays the logo building itself
 * (`drawable/splash_logo`), an animated vector the system runs off the app's
 * main thread, so it stays smooth however busy the app is starting up. The
 * activity holds back its first frame, and with it the splash's exit, until
 * [open] is set; then the splash fades into the app.
 */
object LaunchGate {
    @Volatile
    var open: Boolean = false

    /** When the activity was created, for the splash animation's minimum run. */
    @Volatile
    var createdAt: Long = 0L

    /** How long the splash animation takes, its fade-out included; see `drawable/splash_logo`. */
    const val ANIMATION_MS = 1_500L

    /** The longest the first frame is held back, whatever happens. */
    const val MAX_HOLD_MS = 6_000L

    /** How long the splash takes to fade into the app once it lets go. */
    const val EXIT_FADE_MS = 160L

    /** How long the splash takes to sweep off the app, corner to corner; see [LaunchWipeView]. */
    const val EXIT_WIPE_MS = 650L
}
