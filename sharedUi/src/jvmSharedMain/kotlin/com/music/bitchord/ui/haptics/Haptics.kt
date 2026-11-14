package com.music.bitchord.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * What a touch *meant*, not what it should feel like — the shape of the buzz is
 * this file's business, so a screen never has to know what the motor under it
 * can do.
 *
 * Everything here is deliberately brief. The longest pattern is a three-beat
 * one under 65ms; a haptic that outlasts the finger stops reading as a response
 * to the tap and starts reading as the phone ringing.
 */
enum class Haptic {
    /**
     * The lightest single beat, for something that repeats while a finger is
     * still down — a drag crossing a tab boundary, say. Anything firmer becomes
     * a rattle once it fires ten times in a row.
     */
    Tick,

    /**
     * A firm notch, well above [Tick], for steps a finger is counting off
     * slowly: every 2% of a scrub, each square of the logo filling on a pull.
     */
    Bump,

    /** A tile sinking under the finger: soft and low, a click going *in*. */
    PressIn,

    /** The same tile springing back out as it opens: the firmer click *out*. */
    PressOut,

    /** A plain button press with no state behind it: More, Download, Menu. */
    Tap,

    /** A discrete choice landing: a tab, a filter pill, the end of a scrub. */
    Select,

    /** Switching something on — a light lead-in *rising* into a firm beat. */
    ToggleOn,

    /** Switching it back off — the same pair mirrored, so it falls away. */
    ToggleOff,

    /** Forward through the queue: an accelerating triplet. */
    SkipNext,

    /** Backward: [SkipNext] reversed, which is what makes the pair legible. */
    SkipPrevious,

    /** Playback starting — swells into the beat that lands. */
    Resume,

    /** Playback stopping — lands first, then releases. */
    Pause,

    /** Something growing to fill the screen, e.g. the mini player opening. */
    Expand,
}

/**
 * A handle on the device's motor, obtained with [rememberHaptics].
 *
 * The phone's is the vibrator; a desktop has nothing to buzz, and its handle
 * does nothing.
 */
expect class Haptics {
    fun play(haptic: Haptic)
}

/** `val haptics = rememberHaptics()`, then `haptics.play(Haptic.Select)`. */
@Composable
expect fun rememberHaptics(): Haptics

/**
 * The app's own "Disable haptics" switch, and the one check every buzz goes
 * through. [Haptics.play] reads it, and so does the [HapticFeedback] that
 * [rememberGatedHapticFeedback] puts under the whole app, which covers the
 * Compose calls (slider ticks, long presses, text handles).
 *
 * A plain volatile flag and not a state: it is read at the moment of a touch,
 * never during composition, so nothing has to redraw when it changes.
 */
object HapticsGate {
    @Volatile
    var enabled: Boolean = true
}

/** Passes each call on to [delegate] only while [HapticsGate.enabled]. */
class GatedHapticFeedback(private val delegate: HapticFeedback) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (HapticsGate.enabled) delegate.performHapticFeedback(hapticFeedbackType)
    }
}

/**
 * The platform's [LocalHapticFeedback] behind [HapticsGate], to be provided
 * back as [LocalHapticFeedback] at the root of the app.
 */
@Composable
fun rememberGatedHapticFeedback(): HapticFeedback {
    val platform = LocalHapticFeedback.current
    return remember(platform) { GatedHapticFeedback(platform) }
}
