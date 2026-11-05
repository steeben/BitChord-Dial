package com.music.bitchord

import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.screens.StorageUsage
import com.music.bitchord.ui.screens.storageBarFractions
import com.music.bitchord.ui.screens.storageBarScale
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageUsageTest {

    private val gb = 1024L * 1024 * 1024

    private fun usage(audio: Long = 0, artwork: Long = 0, canvases: Long = 0, other: Long = 0, free: Long = 0) =
        StorageUsage(audio, artwork, canvases, other, free)

    @Test
    fun `the bar stands for the cache limit while use is under it`() {
        val used = usage(audio = gb, artwork = gb / 2)
        assertEquals(5 * gb, storageBarScale(used, 5 * gb))
        val fractions = storageBarFractions(used, storageBarScale(used, 5 * gb))
        assertEquals(0.2f, fractions[0], 0.0001f)
        assertEquals(0.1f, fractions[1], 0.0001f)
    }

    @Test
    fun `use past the limit fills the bar rather than overflowing it`() {
        val used = usage(audio = 3 * gb, other = gb)
        val scale = storageBarScale(used, 2 * gb)
        assertEquals(4 * gb, scale)
        assertEquals(1f, storageBarFractions(used, scale).sum(), 0.0001f)
    }

    @Test
    fun `with no limit the bar is used plus free space`() {
        val used = usage(audio = gb, free = 3 * gb)
        val scale = storageBarScale(used, AppSettings.UNLIMITED_CACHE_LIMIT_BYTES)
        assertEquals(4 * gb, scale)
        assertEquals(0.25f, storageBarFractions(used, scale)[0], 0.0001f)
    }
}
