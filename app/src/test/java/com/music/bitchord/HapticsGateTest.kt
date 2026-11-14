package com.music.bitchord

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.music.bitchord.ui.haptics.GatedHapticFeedback
import com.music.bitchord.ui.haptics.HapticsGate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** The "Disable haptics" switch must stop the Compose haptics, and only while it is on. */
class HapticsGateTest {

    private class Counting : HapticFeedback {
        var calls = 0
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            calls++
        }
    }

    @After
    fun restore() {
        HapticsGate.enabled = true
    }

    @Test
    fun `haptics pass through by default`() {
        val platform = Counting()
        GatedHapticFeedback(platform).performHapticFeedback(HapticFeedbackType.LongPress)
        assertEquals(1, platform.calls)
    }

    @Test
    fun `nothing passes while haptics are disabled`() {
        val platform = Counting()
        val gated = GatedHapticFeedback(platform)
        HapticsGate.enabled = false
        gated.performHapticFeedback(HapticFeedbackType.LongPress)
        gated.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        assertEquals(0, platform.calls)
        HapticsGate.enabled = true
        gated.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        assertEquals(1, platform.calls)
    }
}
