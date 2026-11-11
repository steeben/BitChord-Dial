package com.music.bitchord.data

/**
 * Whether a YouTube upload says of itself that it is something to watch: a
 * music video, a lyric video, a visualizer and the like.
 *
 * A song's music video can stand in as its Canvas (see [VideoCanvases]), and
 * a search for one finds plenty that are the song over a still cover, a
 * reaction or a live bootleg. Only an upload that names itself as one of
 * these, in its title or its description, is taken.
 */
object MusicVideoTerms {
    private const val VIDEO_KINDS =
        """\b(?:music|lyrics?|animated|animation|performance|dance|concept)\s+video\b""" +
            """|\bofficial\s+(?:\w+\s+){0,2}video\b""" +
            """|\bv[ií]deo\s+(?:oficial|officiel|ufficiale|musical|lyric)\b""" +
            """|\bv[ií]deo\s*clipe?\b|\bclipe?\s+(?:officiel|oficial)\b""" +
            """|\bvisuali[sz]er\b|\bofficial\s+visuals?\b|\bvisual\s+video\b"""

    /** What a description has to say: a phrase, since most carry the word "lyrics" anyway. */
    private val phrases = Regex(VIDEO_KINDS, RegexOption.IGNORE_CASE)

    /** A title is short enough for "(Lyrics)" or "M/V" alone to mean it. */
    private val titleOnly = Regex("""\blyrics?\b|\bM/V\b|\bMV\b""", RegexOption.IGNORE_CASE)

    fun inTitle(title: String?): Boolean =
        !title.isNullOrBlank() && (phrases.containsMatchIn(title) || titleOnly.containsMatchIn(title))

    fun inDescription(description: String?): Boolean =
        !description.isNullOrBlank() && phrases.containsMatchIn(description)
}
