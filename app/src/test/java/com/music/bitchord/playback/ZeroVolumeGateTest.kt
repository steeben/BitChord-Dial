package com.music.bitchord.playback

import com.music.bitchord.playback.ZeroVolumeGate.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class ZeroVolumeGateTest {
    @Test
    fun pausesAtZeroAndResumesWhenRaised() {
        val gate = ZeroVolumeGate()
        assertEquals(Action.NONE, gate.onVolume(5, enabled = true, playing = true))
        assertEquals(Action.PAUSE, gate.onVolume(0, enabled = true, playing = true))
        gate.onPlayWhenReadyChanged(false)
        assertEquals(Action.RESUME, gate.onVolume(1, enabled = true, playing = false))
    }

    @Test
    fun doesNotResumeAManualPause() {
        val gate = ZeroVolumeGate()
        gate.onVolume(5, enabled = true, playing = false)
        assertEquals(Action.NONE, gate.onVolume(0, enabled = true, playing = false))
        assertEquals(Action.NONE, gate.onVolume(3, enabled = true, playing = false))
    }

    @Test
    fun startingAtZeroIsABaselineNotAChange() {
        val gate = ZeroVolumeGate()
        assertEquals(Action.NONE, gate.onVolume(0, enabled = true, playing = true))
        assertEquals(Action.NONE, gate.onVolume(0, enabled = true, playing = true))
    }

    @Test
    fun playingAgainAtZeroForgetsThePause() {
        val gate = ZeroVolumeGate()
        gate.onVolume(5, enabled = true, playing = true)
        gate.onVolume(0, enabled = true, playing = true)
        gate.onPlayWhenReadyChanged(true)
        assertEquals(Action.NONE, gate.onVolume(4, enabled = true, playing = true))
    }

    @Test
    fun disabledNeverPauses() {
        val gate = ZeroVolumeGate()
        gate.onVolume(5, enabled = false, playing = true)
        assertEquals(Action.NONE, gate.onVolume(0, enabled = false, playing = true))
        assertEquals(Action.NONE, gate.onVolume(5, enabled = false, playing = false))
    }

    @Test
    fun turningOffWhilePausedDoesNotResume() {
        val gate = ZeroVolumeGate()
        gate.onVolume(5, enabled = true, playing = true)
        gate.onVolume(0, enabled = true, playing = true)
        assertEquals(Action.NONE, gate.onVolume(5, enabled = false, playing = false))
    }
}
