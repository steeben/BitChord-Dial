package com.music.bitchord

import com.music.bitchord.data.podcasts.EpisodeDate
import com.music.bitchord.data.podcasts.EpisodeProgress
import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeDateTest {

    /** 14 November 2026, 10:30. */
    private val now: Calendar = GregorianCalendar(2026, Calendar.NOVEMBER, 14, 10, 30)

    @Test
    fun aDateOfThisYear_hasNoYear() {
        assertEquals("Oct 24", EpisodeDate.label("Oct 24", now))
        assertEquals("Nov 3", EpisodeDate.label("Nov 3, 2026", now))
    }

    @Test
    fun aDateOfAYearBefore_hasItsYear_withNoComma() {
        assertEquals("Nov 14 2025", EpisodeDate.label("Nov 14, 2025", now))
        assertEquals("Feb 3 2024", EpisodeDate.label("February 3, 2024", now))
    }

    @Test
    fun howLongAgo_isWorkedOutToTheDay() {
        assertEquals("Nov 13", EpisodeDate.label("1d ago", now))
        assertEquals("Nov 14", EpisodeDate.label("5h ago", now))
        assertEquals("Nov 13", EpisodeDate.label("11 hours ago", now))
        assertEquals("Nov 14", EpisodeDate.label("40 min ago", now))
        assertEquals("Oct 31", EpisodeDate.label("2 weeks ago", now))
        // Over the turn of a year.
        val january: Calendar = GregorianCalendar(2027, Calendar.JANUARY, 2, 9, 0)
        assertEquals("Dec 30 2026", EpisodeDate.label("3 days ago", january))
    }

    @Test
    fun whatIsNotRead_isShownAsItIs() {
        assertEquals("3 months ago", EpisodeDate.label("3 months ago", now))
        assertEquals("1mo ago", EpisodeDate.label("1mo ago", now))
        assertEquals("vor 2 Tagen", EpisodeDate.label("vor 2 Tagen", now))
        assertEquals("2025/11/14", EpisodeDate.label(" 2025/11/14 ", now))
        assertNull(EpisodeDate.label(null, now))
        assertNull(EpisodeDate.label("  ", now))
    }

    @Test
    fun progress_isAWholePercent_andTheEndIsPlayedOut() {
        assertNull(EpisodeProgress.percentOf(0, 3_600_000))
        assertNull(EpisodeProgress.percentOf(20_000, 3_600_000))
        assertNull(EpisodeProgress.percentOf(20_000, 0))
        assertEquals(1, EpisodeProgress.percentOf(36_000, 3_600_000))
        assertEquals(50, EpisodeProgress.percentOf(1_800_000, 3_600_000))
        assertEquals(100, EpisodeProgress.percentOf(3_500_000, 3_600_000))
    }

    @Test
    fun progress_keepsItsPercentAndItsTime_andTheOldestGoFirst() {
        val minute = 60_000L
        val packed = EpisodeProgress.pack(42, 1_000 * minute)
        assertEquals(42, EpisodeProgress.percentIn(packed))

        val entries = mapOf(
            "new" to EpisodeProgress.pack(5, 300 * minute),
            "old" to EpisodeProgress.pack(100, 100 * minute),
            "mid" to EpisodeProgress.pack(60, 200 * minute),
        )
        assertEquals(listOf("old", "mid"), EpisodeProgress.oldest(entries, 2))
    }

    @Test
    fun progress_showsTheLargerOfYouTubesAndTheApps() {
        assertEquals(40, EpisodeProgress.shown(fromYouTube = 40, fromApp = 12))
        assertEquals(55, EpisodeProgress.shown(fromYouTube = 0, fromApp = 55))
        assertEquals(0, EpisodeProgress.shown(fromYouTube = 0, fromApp = null))
    }
}
