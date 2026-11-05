package com.music.bitchord

import com.music.bitchord.playback.sameAlbumForAutomix
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomixSameAlbumTest {
    @Test
    fun matchingAlbumIdsAreTheSameAlbum() {
        assertTrue(sameAlbumForAutomix("MPREb_1", "Album", "A", "MPREb_1", "Other name", "B"))
    }

    @Test
    fun differentAlbumIdsAreNotTheSameAlbumEvenWithTheSameName() {
        assertFalse(sameAlbumForAutomix("MPREb_1", "Greatest Hits", "A", "MPREb_2", "Greatest Hits", "A"))
    }

    @Test
    fun withoutIdsTheNameAndArtistMustBothMatch() {
        assertTrue(sameAlbumForAutomix(null, "Album ", "Artist", "", "album", "artist"))
        assertFalse(sameAlbumForAutomix(null, "Greatest Hits", "A", null, "Greatest Hits", "B"))
    }

    @Test
    fun missingAlbumIsNeverTheSameAlbum() {
        assertFalse(sameAlbumForAutomix(null, null, "A", null, null, "A"))
        assertFalse(sameAlbumForAutomix(null, "", "A", null, "", "A"))
    }
}
