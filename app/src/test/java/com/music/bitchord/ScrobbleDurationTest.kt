package com.music.bitchord

import com.music.bitchord.data.scrobbling.scrobbleDurationSeconds
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrobbleDurationTest {

    @Test
    fun `minutes and seconds`() {
        assertEquals(225, scrobbleDurationSeconds("3:45"))
        assertEquals(754, scrobbleDurationSeconds("12:34"))
    }

    @Test
    fun `hours are counted rather than read as nothing`() {
        assertEquals(3_723, scrobbleDurationSeconds("1:02:03"))
    }

    @Test
    fun `anything else is zero`() {
        assertEquals(0, scrobbleDurationSeconds(""))
        assertEquals(0, scrobbleDurationSeconds("45"))
        assertEquals(0, scrobbleDurationSeconds("3:xx"))
        assertEquals(0, scrobbleDurationSeconds("1:2:3:4"))
    }
}
