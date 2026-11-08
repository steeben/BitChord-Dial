package com.music.bitchord

import com.music.bitchord.data.dialReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesBrandTest {

    @Test
    fun `old title line reads Dial`() {
        assertEquals("Dial 1.4.1\n\n### Replay", dialReleaseNotes("BitChord Dial 1.4.1\n\n### Replay"))
        assertEquals("# Dial 1.2.0", dialReleaseNotes("# BitChord 1.2.0"))
    }

    @Test
    fun `only the title line is renamed`() {
        val notes = "Dial 1.5.0\n- instead of the BitChord logo"
        assertEquals(notes, dialReleaseNotes(notes))
        assertEquals(
            "Dial 1.4.1\n- BitChord Dial mark",
            dialReleaseNotes("BitChord Dial 1.4.1\n- BitChord Dial mark"),
        )
    }
}
