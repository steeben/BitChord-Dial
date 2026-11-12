package com.music.bitchord.data

import com.music.bitchord.data.model.Song

/**
 * One text detail on the page that compares a song's own details with the
 * ones found for it: what the file says, what the search found, which of the
 * two is kept, and what the listener typed over either.
 *
 * Each side keeps its own typing, so trying the other side and coming back
 * doesn't lose it.
 */
data class DetailChoice(
    val file: String,
    val found: String,
    val useFound: Boolean = true,
    val fileEdit: String? = null,
    val foundEdit: String? = null,
) {
    /** Both sides say the same thing, so there is nothing to choose between. */
    val same: Boolean get() = file.trim() == found.trim()

    /** What the [foundSide] (or the file's) cell reads. */
    fun text(foundSide: Boolean): String = if (foundSide) foundEdit ?: found else fileEdit ?: file

    /** Whether the listener typed over that side. */
    fun edited(foundSide: Boolean): Boolean = (if (foundSide) foundEdit else fileEdit) != null

    /** What the song will be given. */
    val value: String get() = text(useFound).trim()

    /** The kept value is the found one, letter for letter, so the ids found with it still belong to it. */
    val keepsFound: Boolean get() = value == found.trim()

    fun pick(foundSide: Boolean): DetailChoice = copy(useFound = foundSide)

    /**
     * Keeps [typed] on the [foundSide] (or the file's). Typing nothing, or
     * the side's own words, clears the edit. Which side is kept doesn't
     * change: only the kept side can be typed over.
     */
    fun typed(foundSide: Boolean, typed: String): DetailChoice {
        val text = typed.trim()
        val own = if (foundSide) found else file
        val edit = text.takeUnless { it.isEmpty() || it == own.trim() }
        return if (foundSide) copy(foundEdit = edit) else copy(fileEdit = edit)
    }
}

/**
 * Everything the listener chooses on the compare page that opens once a song
 * is picked in "Find this song's details". It opens with every found detail
 * chosen, which is what a pick used to do outright.
 */
data class SongDetailChoices(
    val title: DetailChoice,
    val artist: DetailChoice,
    val album: DetailChoice,
    val fileCover: String?,
    val foundCover: String?,
    val useFoundCover: Boolean = true,
) {
    /** There are two pictures to choose between. */
    val coverDiffers: Boolean get() = foundCover != null && foundCover != fileCover

    /**
     * Every detail from one side. What was typed stays with its side, and a
     * detail both sides agree on stays as it is, typed over or not.
     */
    fun useAll(foundSide: Boolean): SongDetailChoices {
        fun DetailChoice.from() = if (same) this else pick(foundSide)
        return copy(title = title.from(), artist = artist.from(), album = album.from(), useFoundCover = foundSide)
    }

    /**
     * [match] reading as chosen, for [SongMetadataFixes.fix]. An artist or
     * album that is no longer the found one drops the found one's id, and a
     * null cover leaves the song the picture it has.
     */
    fun resolve(match: Song): Song = match.copy(
        title = title.value.ifEmpty { match.title },
        artist = artist.value,
        artistId = match.artistId.takeIf { artist.keepsFound },
        artists = emptyList(),
        albumName = album.value.ifEmpty { null },
        albumId = match.albumId.takeIf { album.keepsFound },
        thumbnailUrl = foundCover.takeIf { useFoundCover && coverDiffers },
    )

    companion object {
        /** [file] is the song as its file has it, [match] the song picked for it. */
        fun of(file: SongMetadataFixes.Details, match: Song) = SongDetailChoices(
            title = DetailChoice(file.title, match.title),
            artist = DetailChoice(file.artist, match.artist),
            album = DetailChoice(file.albumName.orEmpty(), match.albumName.orEmpty()),
            fileCover = file.thumbnailUrl,
            foundCover = match.thumbnailUrl,
        )
    }
}
