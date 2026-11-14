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
    fun `a session carried on adds to its file`() {
        val file = File.createTempFile("tracklog-carried", ".txt").apply { deleteOnExit() }
        TrackLog.startSession(file, header = listOf("header line"))
        TrackLog.toSession('I', "Log", "before the app closed")
        // The process ends here: nothing stops the session.
        TrackLog.startSession(file, header = listOf("header line"), append = true)
        TrackLog.toSession('I', "Log", "after the app opened")
        TrackLog.stopSession()

        val text = file.readText()
        assertTrue(text.startsWith("header line\nstarted: "))
        assertTrue(text.contains("I/Log: before the app closed"))
        assertTrue(text.contains("\nheader line\ncarried on: "))
        assertTrue(text.indexOf("before the app closed") < text.indexOf("carried on: "))
        assertTrue(text.indexOf("carried on: ") < text.indexOf("after the app opened"))
    }

    @Test
    fun `carrying on with no file yet starts one`() {
        val file = File.createTempFile("tracklog-new", ".txt").apply { delete(); deleteOnExit() }
        TrackLog.startSession(file, header = listOf("header line"), append = true)
        TrackLog.stopSession()
        assertTrue(file.readText().startsWith("header line\nstarted: "))
    }

    @Test
    fun `stopping with nothing running returns nothing`() {
        assertNull(TrackLog.stopSession())
    }
}
