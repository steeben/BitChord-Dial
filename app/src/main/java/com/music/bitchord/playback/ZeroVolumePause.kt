package com.music.bitchord.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Whether turning the media volume all the way down should pause, and turning
 * it back up should resume.
 *
 * Kept free of Android so the rules can be tested: the only thing that may be
 * resumed is a pause this gate made itself. A listener who paused by hand and
 * then turned the volume down and up again did not ask for music.
 */
class ZeroVolumeGate {
    enum class Action { NONE, PAUSE, RESUME }

    private var lastVolume: Int? = null
    var pausedForSilence = false
        private set

    fun onVolume(volume: Int, enabled: Boolean, playing: Boolean): Action {
        val previous = lastVolume
        lastVolume = volume
        // The first reading is a baseline, not a change the listener made.
        if (previous == null || previous == volume) return Action.NONE
        return when {
            volume == 0 && enabled && playing -> {
                pausedForSilence = true
                Action.PAUSE
            }
            volume > 0 && pausedForSilence -> {
                pausedForSilence = false
                if (enabled) Action.RESUME else Action.NONE
            }
            else -> Action.NONE
        }
    }

    /** Any play, from anywhere, means the pause is no longer ours to undo. */
    fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
        if (playWhenReady) pausedForSilence = false
    }
}

/**
 * Watches the music stream volume and applies [ZeroVolumeGate].
 *
 * Two signals, because neither is reliable on every device: the settings
 * observer is the documented route, and the volume broadcast is what some
 * OEM builds send instead. The gate ignores a reading that matches the last
 * one, so hearing about the same change twice does nothing.
 */
class ZeroVolumePause(
    private val context: Context,
    private val enabled: () -> Boolean,
    private val playing: () -> Boolean,
    private val pause: () -> Unit,
    private val resume: () -> Unit,
) {
    private val gate = ZeroVolumeGate()
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = check()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val stream = intent.getIntExtra(EXTRA_STREAM_TYPE, AudioManager.STREAM_MUSIC)
            if (stream == AudioManager.STREAM_MUSIC) check()
        }
    }

    fun start() {
        check()
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(VOLUME_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun stop() {
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        runCatching { context.unregisterReceiver(receiver) }
    }

    fun onPlayWhenReadyChanged(playWhenReady: Boolean) = gate.onPlayWhenReadyChanged(playWhenReady)

    private fun check() {
        val volume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: return
        when (gate.onVolume(volume, enabled(), playing())) {
            ZeroVolumeGate.Action.PAUSE -> pause()
            ZeroVolumeGate.Action.RESUME -> resume()
            ZeroVolumeGate.Action.NONE -> Unit
        }
    }

    private companion object {
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
