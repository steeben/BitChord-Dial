package com.music.bitchord

import com.music.bitchord.ui.screens.CoverPixels
import com.music.bitchord.ui.screens.coverIsOwnPicture
import com.music.bitchord.ui.screens.playlistCoverIsOwnByAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistOwnCoverTest {

    private fun rgb(r: Int, g: Int, b: Int) = (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun picture(width: Int, height: Int = width, colour: (x: Float, y: Float) -> Int) =
        CoverPixels(width, height, IntArray(width * height) { colour((it % width) / width.toFloat(), (it / width) / height.toFloat()) })

    /** A sleeve in one colour, a little lighter towards its foot so it isn't flat. */
    private fun sleeve(r: Int, g: Int, b: Int, px: Int = 96) =
        picture(px) { _, y -> rgb(r + (40 * y).toInt(), g + (40 * y).toInt(), b + (40 * y).toInt()) }

    /** Four pictures as YouTube Music's default cover lays them out. */
    private fun grid(a: CoverPixels, b: CoverPixels, c: CoverPixels, d: CoverPixels, px: Int = 96): CoverPixels {
        val half = px / 2
        val parts = listOf(a, b, c, d)
        return CoverPixels(px, px, IntArray(px * px) {
            val x = it % px
            val y = it / px
            val part = parts[(if (y < half) 0 else 2) + (if (x < half) 0 else 1)]
            part.at((x % half) * part.width / half, (y % half) * part.height / half)
        })
    }

    private val red = sleeve(200, 20, 20)
    private val green = sleeve(20, 180, 40)
    private val blue = sleeve(20, 40, 200)
    private val yellow = sleeve(210, 200, 30)

    /** A photo: one smooth sweep of grey from corner to corner. */
    private val photo = picture(96) { x, y -> ((x + y) * 127).toInt().let { rgb(it, it, it) } }

    @Test
    fun `a photo the owner chose is the playlist's own`() {
        assertTrue(coverIsOwnPicture(photo, listOf(red, green, blue, yellow)))
        assertTrue(coverIsOwnPicture(photo, emptyList()))
    }

    @Test
    fun `the default grid of the playlist's sleeves is not`() {
        assertFalse(coverIsOwnPicture(grid(red, green, blue, yellow), listOf(red, green, blue, yellow)))
        // One album four times over has no seams to see, only its sleeve.
        assertFalse(coverIsOwnPicture(grid(red, red, red, red), listOf(red)))
    }

    @Test
    fun `a grid of sleeves from further down the list is known by its seams`() {
        assertFalse(coverIsOwnPicture(grid(red, green, blue, yellow), emptyList()))
        assertFalse(coverIsOwnPicture(grid(red, green, blue, yellow), listOf(sleeve(120, 120, 120))))
    }

    @Test
    fun `one of the playlist's sleeves on its own is not`() {
        assertFalse(coverIsOwnPicture(sleeve(20, 180, 40, px = 120), listOf(red, green)))
    }

    @Test
    fun `a video still is compared by its middle square`() {
        val still = picture(128, 96) { x, y -> if (x < 0.125f || x > 0.875f) 0 else green.at(0, (y * 96).toInt()) }
        assertFalse(coverIsOwnPicture(still, listOf(green)))
    }

    @Test
    fun `a cover too small to read is left as it was`() {
        assertFalse(coverIsOwnPicture(picture(4) { _, _ -> 0 }, emptyList()))
    }

    @Test
    fun `some covers are known by their address alone`() {
        // Spotify: a grid, an album's sleeve, liked songs; then an uploaded cover and a mix's.
        assertEquals(false, playlistCoverIsOwnByAddress("https://mosaic.scdn.co/640/ab67616d00001e02aaaab67616d00001e02bbb"))
        assertEquals(false, playlistCoverIsOwnByAddress("https://i.scdn.co/image/ab67616d0000b273abcdef"))
        assertEquals(false, playlistCoverIsOwnByAddress("https://misc.scdn.co/liked-songs/liked-songs-640.png"))
        assertEquals(true, playlistCoverIsOwnByAddress("https://image-cdn-ak.spotifycdn.com/image/ab67706c0000da84abcdef"))
        assertEquals(true, playlistCoverIsOwnByAddress("https://seed-mix-image.spotifycdn.com/v6/img/artist/abc/en/default"))
        // YouTube: a video's still is the first song's; the rest has to be looked at.
        assertEquals(false, playlistCoverIsOwnByAddress("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"))
        assertNull(playlistCoverIsOwnByAddress("https://i.ytimg.com/pl_c/PL123/studio_square_thumbnail.jpg"))
        assertNull(playlistCoverIsOwnByAddress("https://yt3.googleusercontent.com/abc=s1200"))
        assertEquals(false, playlistCoverIsOwnByAddress(null))
    }

    @Test
    fun `a cover set on this device is the playlist's own unless the app generated it`() {
        assertEquals(true, playlistCoverIsOwnByAddress("file:///data/user/0/app/files/playlist_covers/cover_123_456.jpg"))
        assertEquals(false, playlistCoverIsOwnByAddress("file:///data/user/0/app/files/playlist_covers/cover_gen_123_456.jpg"))
    }
}
