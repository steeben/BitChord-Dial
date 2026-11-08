package com.music.bitchord.data

/** The app's own name as the user sees it: never "BitChord" or "BitChord Dial". */
private val OLD_BRAND_TITLE = Regex("""^(\s*#*\s*)BitChord(?: Dial)?\b""")

/**
 * Release notes open with a title line naming the version. Releases published
 * before the app went by "Dial" alone call it "BitChord Dial 1.4.1", and the
 * update sheet shows that line as-is under "What's new", so it is renamed here.
 * Only the first line is touched: notes that mention BitChord further down are
 * talking about it on purpose.
 */
internal fun dialReleaseNotes(notes: String): String {
    val end = notes.indexOf('\n').let { if (it < 0) notes.length else it }
    val first = notes.substring(0, end).replaceFirst(OLD_BRAND_TITLE, "$1Dial")
    return first + notes.substring(end)
}
