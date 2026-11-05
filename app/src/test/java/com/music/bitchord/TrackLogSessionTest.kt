package com.music.bitchord

import com.music.bitchord.data.TrackLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TrackLogSessionTest {

    @Test
    fun `a session keeps every line from start to stop, and nothing after`() {
        val file = File.createTempFile("tracklog-session", ".txt").apply { deleteOnExit() }
        TrackLog.d("Before", "not recorded")
        TrackLog.startSession(file, header = listOf("header line"))
        assertTrue(TrackLog.recording.value)
        TrackLog.d("Player", "first", about = "abc")
        TrackLog.toSession('W', "Lyrics", "from the data layer")
        assertEquals(file, TrackLog.stopSession())
        assertFalse(TrackLog.recording.value)
        TrackLog.d("After", "not recorded either")

        val text = file.readText()
        assertTrue(text.startsWith("header line\n"))
        assertTrue(text.contains("D/Player {abc}: first"))
        assertTrue(text.contains("W/Lyrics: from the data layer"))
        assertFalse(text.contains("not recorded"))
        assertTrue(text.contains("(2 lines)"))
    }

    @Test
    fun `stopping with nothing running returns nothing`() {
        assertNull(TrackLog.stopSession())
    }
}
