package com.music.bitchord.playback

/**
 * Whether two queued songs come from the same album, for Automix's
 * "Mix within the same album" setting.
 *
 * The album id decides when both sides carry one. Without ids, a matching
 * album name only counts with a matching artist too, so two different
 * "Greatest Hits" don't read as one record.
 */
internal fun sameAlbumForAutomix(
    currentAlbumId: String?,
    currentAlbumName: String?,
    currentArtist: String?,
    nextAlbumId: String?,
    nextAlbumName: String?,
    nextArtist: String?,
): Boolean {
    if (!currentAlbumId.isNullOrBlank() && !nextAlbumId.isNullOrBlank()) {
        return currentAlbumId == nextAlbumId
    }
    val name = currentAlbumName?.trim().orEmpty()
    if (name.isEmpty() || !name.equals(nextAlbumName?.trim(), ignoreCase = true)) return false
    return currentArtist?.trim().orEmpty().equals(nextArtist?.trim().orEmpty(), ignoreCase = true)
}
