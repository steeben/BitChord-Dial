package com.music.bitchord

import com.music.bitchord.data.stats.AutoBackup
import com.music.bitchord.data.stats.AutoBackup.Frequency
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class AutoBackupScheduleTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val hour = TimeUnit.HOURS.toMillis(1)
    private val now = 100 * day

    @Test
    fun offIsNeverDue() {
        assertFalse(AutoBackup.isDue(Frequency.OFF, 0L, now))
    }

    @Test
    fun neverBackedUpIsDue() {
        assertTrue(AutoBackup.isDue(Frequency.MONTHLY, 0L, now))
    }

    @Test
    fun dueOnceTheIntervalHasPassed() {
        assertFalse(AutoBackup.isDue(Frequency.DAILY, now - 20 * hour, now))
        assertTrue(AutoBackup.isDue(Frequency.DAILY, now - day, now))
        assertFalse(AutoBackup.isDue(Frequency.WEEKLY, now - 6 * day, now))
        assertTrue(AutoBackup.isDue(Frequency.WEEKLY, now - 7 * day, now))
        assertFalse(AutoBackup.isDue(Frequency.MONTHLY, now - 29 * day, now))
        assertTrue(AutoBackup.isDue(Frequency.MONTHLY, now - 30 * day, now))
    }

    @Test
    fun aCheckJustShortOfTheIntervalStillCounts() {
        assertTrue(AutoBackup.isDue(Frequency.DAILY, now - day + 30 * 60 * 1000L, now))
    }
}
