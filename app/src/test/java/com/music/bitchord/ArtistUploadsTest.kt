package com.music.bitchord

import com.music.bitchord.data.ArtistUploads
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistUploadsTest {
    private fun song(title: String, artist: String, duration: String?) =
        Song(videoId = "song0000000", title = title, artist = artist, thumbnailUrl = null, durationText = duration)

    private fun video(title: String, channel: String, duration: String?, id: String = "video000000") =
        Song(videoId = id, title = title, artist = channel, thumbnailUrl = null, durationText = duration, isVideo = true)

    private val atlas = song("ATLAS (feat. aquoferne & Davis West)", "Pretty Patterns, RINO", "3:20")

    @Test fun theArtistsOwnUploadOfTheSameLengthIsTaken() {
        assertTrue(ArtistUploads.matches(atlas, video("ATLAS - Patterns ft. @rino @aquoferne @DavisWest", "Pretty Patterns", "3:20")))
    }

    @Test fun theLengthMayBeTwoSecondsOffAndNoMore() {
        val title = "ATLAS - Patterns ft. @rino"
        assertTrue(ArtistUploads.matches(atlas, video(title, "Pretty Patterns", "3:18")))
        assertTrue(ArtistUploads.matches(atlas, video(title, "Pretty Patterns", "3:22")))
        assertFalse(ArtistUploads.matches(atlas, video(title, "Pretty Patterns", "3:17")))
        assertFalse(ArtistUploads.matches(atlas, video(title, "Pretty Patterns", "3:23")))
    }

    @Test fun aLengthThatIsNotKnownIsNotAMatch() {
        assertFalse(ArtistUploads.matches(atlas, video("ATLAS", "Pretty Patterns", null)))
        assertFalse(ArtistUploads.matches(atlas.copy(durationText = null), video("ATLAS", "Pretty Patterns", "3:20")))
    }

    @Test fun anotherChannelIsNotTheArtist() {
        assertFalse(ArtistUploads.matches(atlas, video("Patterns - ATLAS [가사 번역]", "MayNut", "3:20")))
        assertFalse(ArtistUploads.matches(atlas, video("ATLAS", "Pretty Patterns Fans", "3:20")))
    }

    @Test fun anotherSongOfTheArtistsIsNotTaken() {
        assertFalse(ArtistUploads.matches(atlas, video("Daydream - Patterns", "Pretty Patterns", "3:20")))
    }

    @Test fun anAudioOnlyUploadIsNotTaken() {
        assertFalse(ArtistUploads.matches(atlas, video("ATLAS (Official Audio)", "Pretty Patterns", "3:20")))
        assertTrue(ArtistUploads.matches(atlas, video("ATLAS (Official Video) [4K, Dolby Audio]", "Pretty Patterns", "3:20")))
    }

    @Test fun channelWordsAreNotPartOfTheName() {
        listOf(
            "Pretty Patterns",
            "Pretty Patterns Official",
            "Official Pretty Patterns",
            "PrettyPatternsVEVO",
            "Pretty Patterns Music",
            "PrettyPatterns TV",
            "pretty-patterns",
            "RINO",
            "Rino Official",
        ).forEach { assertTrue(it, ArtistUploads.isBy(it, "Pretty Patterns, RINO")) }
    }

    @Test fun otherNamesAreNotTheArtist() {
        listOf("Patterns", "Pretty", "Pretty Patterns Lyrics", "Rhino", "MayNut", "Official", "", "Pretty Patterns - Topic")
            .forEach { assertFalse(it, ArtistUploads.isBy(it, "Pretty Patterns, RINO")) }
    }

    @Test fun aLongNameMayBeOneLetterOff() {
        assertTrue(ArtistUploads.isBy("BeyonceVEVO", "Beyoncé"))
        assertTrue(ArtistUploads.isBy("Sigur Ros", "Sigur Rós"))
        assertFalse(ArtistUploads.isBy("Queensryche", "Queen"))
        // Too short for one letter to be a slip.
        assertFalse(ArtistUploads.isBy("Kasba", "Kasbo"))
    }

    @Test fun aNameWithAJoiningWordIsComparedWhole() {
        assertTrue(ArtistUploads.isBy("Florence and the Machine", "Florence & The Machine"))
        assertTrue(ArtistUploads.isBy("FlorenceAndTheMachineVEVO", "Florence and the Machine"))
    }

    @Test fun aNameThatIsOnlyChannelWordsIsKept() {
        assertTrue(ArtistUploads.isBy("Music", "Music"))
    }

    @Test fun theNearestInLengthIsTheBest() {
        val picks = listOf(
            video("ATLAS (fan edit)", "MayNut", "3:20", "fan00000000"),
            video("ATLAS - Patterns (teaser cut)", "Pretty Patterns", "3:22", "near0000000"),
            video("ATLAS - Patterns ft. @rino", "Pretty Patterns", "3:20", "exact000000"),
        )
        assertEquals("exact000000", ArtistUploads.best(picks, atlas)?.videoId)
        assertNull(ArtistUploads.best(picks.take(1), atlas))
    }
}
