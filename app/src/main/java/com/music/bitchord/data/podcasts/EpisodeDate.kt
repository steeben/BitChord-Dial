package com.music.bitchord.data.podcasts

import java.util.Calendar

/**
 * When an episode came out, as its row on a show's page says it: "Nov 14"
 * for one of this year, "Nov 14 2025" for one of a year before.
 *
 * YouTube Music words it nearly so already ("Oct 24", "Nov 14, 2025"), but
 * the newest episodes it dates by how long ago ("1d ago", "5h ago"), and
 * those are worked out to the day. Anything it words another way (a language
 * other than English) is shown as YouTube wrote it.
 */
object EpisodeDate {

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** "1d ago", "5h ago", "40 min ago", "2 weeks ago". Not months or years: those are not exact to the day. */
    private val AGO = Regex(
        """^(\d+)\s*(minutes?|mins?|m|hours?|hrs?|h|days?|d|weeks?|wks?|w)\s+ago$""",
        RegexOption.IGNORE_CASE,
    )

    /** "Oct 24", "Nov 14, 2025", "November 14, 2025". */
    private val DATED = Regex("""^([A-Za-z]{3})[a-z]*\.? (\d{1,2})(?:,? (\d{4}))?$""")

    /** [released] as the row shows it at the time [now]; null when there is nothing to show. */
    fun label(released: String?, now: Calendar = Calendar.getInstance()): String? {
        val text = released?.trim().orEmpty()
        if (text.isEmpty()) return null
        AGO.matchEntire(text)?.let { match ->
            val count = match.groupValues[1].toIntOrNull() ?: return text
            val day = now.clone() as Calendar
            when (match.groupValues[2].lowercase().first()) {
                'm' -> day.add(Calendar.MINUTE, -count)
                'h' -> day.add(Calendar.HOUR_OF_DAY, -count)
                'd' -> day.add(Calendar.DAY_OF_YEAR, -count)
                else -> day.add(Calendar.WEEK_OF_YEAR, -count)
            }
            return format(MONTHS[day.get(Calendar.MONTH)], day.get(Calendar.DAY_OF_MONTH), day.get(Calendar.YEAR), now)
        }
        DATED.matchEntire(text)?.let { match ->
            val month = MONTHS.firstOrNull { it.equals(match.groupValues[1], ignoreCase = true) } ?: return text
            val day = match.groupValues[2].toIntOrNull() ?: return text
            // A date with no year is one of this year.
            val year = match.groupValues[3].toIntOrNull() ?: now.get(Calendar.YEAR)
            return format(month, day, year, now)
        }
        return text
    }

    private fun format(month: String, day: Int, year: Int, now: Calendar): String =
        if (year == now.get(Calendar.YEAR)) "$month $day" else "$month $day $year"
}
