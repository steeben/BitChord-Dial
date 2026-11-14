package com.music.bitchord

import com.music.bitchord.data.ExploreArtCache
import com.music.bitchord.data.artByKey
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.withArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreArtCacheTest {
    private fun category(id: String, params: String? = null, art: String? = null) =
        MoodGenre(title = id, browseId = id, params = params, thumbnailUrl = art)

    private val sections = listOf(
        MoodGenreSection("Moods", listOf(category("a", "p1"), category("a", "p2"))),
        MoodGenreSection("Genres", listOf(category("b"), category("c", art = "https://c/old"))),
    )

    @Test
    fun `what is written reads back the same`() {
        val art = linkedMapOf(
            ExploreArtCache.keyOf("a", "p1") to "https://a/1=w544-h544",
            ExploreArtCache.keyOf("b", null) to "https://b/2?x=1&y=2",
        )
        assertEquals(art, ExploreArtCache.decode(ExploreArtCache.encode(art)))
    }

    @Test
    fun `a damaged file gives what can be read and no more`() {
        val text = "a|p1\thttps://a/1\nno tab here\n\tno key\nb|\t  \n\nc|\thttps://c/3"
        assertEquals(
            mapOf("a|p1" to "https://a/1", "c|" to "https://c/3"),
            ExploreArtCache.decode(text),
        )
        assertTrue(ExploreArtCache.decode("").isEmpty())
    }

    @Test
    fun `two categories on one browse id keep their own pictures`() {
        val out = sections.withArt(
            mapOf(
                ExploreArtCache.keyOf("a", "p1") to "https://a/1",
                ExploreArtCache.keyOf("a", "p2") to "https://a/2",
            ),
        )
        assertEquals("https://a/1", out[0].items[0].thumbnailUrl)
        assertEquals("https://a/2", out[0].items[1].thumbnailUrl)
        assertNull(out[1].items[0].thumbnailUrl)
    }

    @Test
    fun `the same list comes back when nothing changes`() {
        assertSame(sections, sections.withArt(emptyMap()))
        assertSame(sections, sections.withArt(mapOf("not|there" to "https://x")))
        assertSame(sections, sections.withArt(mapOf(ExploreArtCache.keyOf("c", null) to "https://c/old")))
    }

    @Test
    fun `a section nothing changed in is the same section`() {
        val out = sections.withArt(mapOf(ExploreArtCache.keyOf("b", null) to "https://b/1"))
        assertNotSame(sections, out)
        assertSame(sections[0], out[0])
        assertEquals("https://b/1", out[1].items[0].thumbnailUrl)
    }

    @Test
    fun `a picture already shown stays when replace is off`() {
        val art = mapOf(
            ExploreArtCache.keyOf("b", null) to "https://b/new",
            ExploreArtCache.keyOf("c", null) to "https://c/new",
        )
        val kept = sections.withArt(art, replace = false)
        assertEquals("https://b/new", kept[1].items[0].thumbnailUrl)
        assertEquals("https://c/old", kept[1].items[1].thumbnailUrl)

        val replaced = sections.withArt(art)
        assertEquals("https://c/new", replaced[1].items[1].thumbnailUrl)
    }

    @Test
    fun `only categories with a picture are saved`() {
        assertEquals(
            mapOf(ExploreArtCache.keyOf("c", null) to "https://c/old"),
            sections.artByKey(),
        )
    }
}
