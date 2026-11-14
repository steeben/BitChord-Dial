package com.music.bitchord.data

/**
 * Larger copies of a YouTube video thumbnail.
 *
 * YouTube Music lists a video's picture at 400x225 at most (a signed, resized
 * address such as `…/vi/<id>/sddefault.jpg?sqp=…&rs=…`). That is enough for a
 * list row and far short of a card, a page banner or the player, where a song
 * that is a video showed its "album art" scaled up four or five times.
 *
 * The same picture is on the image host at fixed sizes under fixed names, with
 * no signature:
 *
 *  - `maxresdefault.jpg`: 1280x720, 16:9. Only for videos uploaded in HD.
 *  - `sddefault.jpg`: 640x480, the 16:9 picture between two black bars.
 *    Missing for some old or small uploads.
 *
 * A missing name answers 404. So these are tries, largest first, and the
 * address YouTube Music gave stays the last resort: it always exists.
 */
object VideoThumbnails {
    /** Up to here the listed 400x225 picture is enough: rows, the mini player, queue items. */
    const val LISTED_ENOUGH_PX = 256

    /** Up to here `sddefault` is asked for; above it, `maxresdefault` first. Cards stay below it. */
    const val SD_ENOUGH_PX = 600

    private const val MAX_RES = "maxresdefault"
    private const val SD = "sddefault"

    private val ADDRESS = Regex("""^https?://(?:i\.ytimg\.com|img\.youtube\.com)/vi(?:_webp)?/([A-Za-z0-9_-]{11})/([A-Za-z0-9_]+)\.(?:jpg|webp)""")

    /**
     * The larger addresses to try, in order, for [url] drawn [px] pixels on
     * its longer side. Empty when [url] is not a video thumbnail, when the
     * listed picture is large enough for [px], or when [url] is already the
     * largest name.
     *
     * A `#fragment` on [url] (the player's `#audio-only` mark) is kept.
     */
    fun larger(url: String, px: Int): List<String> {
        if (px <= LISTED_ENOUGH_PX) return emptyList()
        val match = ADDRESS.find(url) ?: return emptyList()
        val id = match.groupValues[1]
        val name = match.groupValues[2]
        val signed = url.contains("sqp=")
        val fragment = url.substringAfter('#', "").let { if (it.isEmpty()) "" else "#$it" }
        val names = buildList {
            if (px > SD_ENOUGH_PX) add(MAX_RES)
            add(SD)
        }.filter { candidate ->
            // The name already in hand, unsigned, is that same file: no gain.
            // Signed, it is a small resized copy of it, so the full file is.
            when {
                !signed && name == candidate -> false
                !signed && name == MAX_RES -> false
                else -> true
            }
        }
        return names.map { "https://i.ytimg.com/vi/$id/$it.jpg$fragment" }
    }

    /** Whether [url] is one of the 4:3 names that hold the picture between black bars. */
    fun isLetterboxed(url: String): Boolean {
        val match = ADDRESS.find(url) ?: return false
        return !url.contains("sqp=") && match.groupValues[2] in setOf(SD, "hqdefault", "default")
    }
}
