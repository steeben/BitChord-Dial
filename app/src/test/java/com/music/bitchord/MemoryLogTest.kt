package com.music.bitchord

import com.music.bitchord.ui.performance.MemoryLog
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryLogTest {

    @Test
    fun `a reading is written in megabytes with what is left over as other`() {
        val reading = MemoryLog.Reading(
            totalKb = 400 * 1024,
            javaKb = 50 * 1024,
            nativeKb = 120 * 1024,
            graphicsKb = 170 * 1024,
            codeKb = 40 * 1024,
        )
        assertEquals(20 * 1024, reading.otherKb)
        assertEquals(
            "memory on tab:0: 400MB (java 50MB, native 120MB, graphics 170MB, code 40MB, other 20MB); " +
                "keep Home loaded=true",
            MemoryLog.line("tab:0", reading, homeKept = true),
        )
    }

    @Test
    fun `parts that add up to more than the total leave nothing over`() {
        val reading = MemoryLog.Reading(totalKb = 100, javaKb = 60, nativeKb = 60, graphicsKb = 0, codeKb = 0)
        assertEquals(0, reading.otherKb)
    }

    @Test
    fun `kilobytes round to the nearest megabyte`() {
        val reading = MemoryLog.Reading(totalKb = 1535, javaKb = 1536, nativeKb = 0, graphicsKb = 0, codeKb = 0)
        assertEquals(
            "memory on launch: 1MB (java 2MB, native 0MB, graphics 0MB, code 0MB, other 0MB); keep Home loaded=false",
            MemoryLog.line("launch", reading, homeKept = false),
        )
    }

    @Test
    fun `the device line says what the phone has`() {
        assertEquals(
            "device memory 16.0GB, 6.5GB free, low memory device=false, app heap limit 512MB",
            MemoryLog.deviceLine(
                totalBytes = 16L * 1024 * 1024 * 1024,
                freeBytes = 13L * 512 * 1024 * 1024,
                lowRam = false,
                heapLimitMb = 512,
            ),
        )
    }
}
