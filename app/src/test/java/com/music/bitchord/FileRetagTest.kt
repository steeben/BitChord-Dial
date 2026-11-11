package com.music.bitchord

import com.music.bitchord.download.FlacTagger
import com.music.bitchord.download.Id3Tagger
import com.music.bitchord.download.Mp4Tagger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saving a song fix into one of the listener's own files. Unlike a fresh
 * download, the file has tags already, so each of these checks the same three
 * things: the new details are in, what wasn't asked to change is still there,
 * and the audio is byte for byte what it was.
 */
class FileRetagTest {

    private val cover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

    /** An MPEG audio frame header and some payload. */
    private val mpegAudio = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00) + ByteArray(64) { it.toByte() }

    private fun u32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte(),
    )

    private fun le32(value: Int): ByteArray = u32(value).reversedArray()

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)

    private fun ByteArray.indexOfBytes(needle: ByteArray): Int {
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun ByteArray.has(text: String, charset: java.nio.charset.Charset = Charsets.UTF_8) =
        indexOfBytes(text.toByteArray(charset)) >= 0

    private fun Id3Tagger.Rewrite.over(original: ByteArray) =
        metadata + original.copyOfRange(audioStart, original.size)

    // ── MP3 ────────────────────────────────────────────────────────────────

    private fun id3Frame(id: String, payload: ByteArray, synchsafe: Boolean = false): ByteArray {
        val size = if (synchsafe) {
            byteArrayOf((payload.size ushr 21).toByte(), (payload.size ushr 14 and 0x7F).toByte(), (payload.size ushr 7 and 0x7F).toByte(), (payload.size and 0x7F).toByte())
        } else {
            u32(payload.size)
        }
        return id.toByteArray(Charsets.ISO_8859_1) + size + byteArrayOf(0, 0) + payload
    }

    private fun id3Tag(version: Int, frames: ByteArray, padding: Int = 0): ByteArray {
        val size = frames.size + padding
        val header = "ID3".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(version.toByte(), 0, 0) +
            byteArrayOf((size ushr 21).toByte(), (size ushr 14 and 0x7F).toByte(), (size ushr 7 and 0x7F).toByte(), (size and 0x7F).toByte())
        return header + frames + ByteArray(padding)
    }

    private fun latin1Text(text: String) = byteArrayOf(0) + text.toByteArray(Charsets.ISO_8859_1)

    @Test
    fun `an mp3 without a tag gets one in front of its untouched audio`() {
        val rewrite = Id3Tagger.header(mpegAudio, "Title", "Artist", "Album", cover, "image/jpeg")
        assertNotNull(rewrite)
        val out = rewrite!!.over(mpegAudio)
        assertEquals(0, rewrite.audioStart)
        assertEquals("ID3", String(out, 0, 3, Charsets.ISO_8859_1))
        assertEquals(3, out[3].toInt())
        assertTrue(out.has("Title", Charsets.UTF_16LE))
        assertTrue(out.indexOfBytes(cover) > 0)
        assertArrayEquals(mpegAudio, out.copyOfRange(out.size - mpegAudio.size, out.size))
    }

    @Test
    fun `an mp3 keeps the frames it was not asked to change`() {
        val tag = id3Tag(
            3,
            id3Frame("TIT2", latin1Text("Old Title")) + id3Frame("TRCK", latin1Text("7")) +
                id3Frame("TPE1", latin1Text("Old Artist")) + id3Frame("TALB", latin1Text("Old Album")),
            padding = 40,
        )
        val original = tag + mpegAudio
        val rewrite = Id3Tagger.header(original, "New Title", "New Artist", null, null, "image/jpeg")!!
        assertEquals(tag.size, rewrite.audioStart)
        val out = rewrite.over(original)
        assertTrue(out.has("New Title", Charsets.UTF_16LE))
        assertFalse(out.has("Old Title", Charsets.ISO_8859_1))
        assertFalse(out.has("Old Artist", Charsets.ISO_8859_1))
        // No album was given, so the file's own stays; so does the track number.
        assertTrue(out.has("Old Album", Charsets.ISO_8859_1))
        assertTrue(out.indexOfBytes(id3Frame("TRCK", latin1Text("7"))) > 0)
        assertArrayEquals(mpegAudio, out.copyOfRange(out.size - mpegAudio.size, out.size))
    }

    @Test
    fun `a v2_4 tag stays v2_4 and is written in utf-8`() {
        val tag = id3Tag(4, id3Frame("TIT2", byteArrayOf(3) + "Old".toByteArray(), synchsafe = true) +
            id3Frame("TRCK", byteArrayOf(3) + "7".toByteArray(), synchsafe = true))
        val original = tag + mpegAudio
        val out = Id3Tagger.header(original, "Nëw 日本", "Artist", "Album", null, "image/jpeg")!!.over(original)
        assertEquals(4, out[3].toInt())
        assertTrue(out.has("Nëw 日本"))
        assertTrue(out.indexOfBytes(id3Frame("TRCK", byteArrayOf(3) + "7".toByteArray(), synchsafe = true)) > 0)
    }

    @Test
    fun `files that are not mp3s are refused`() {
        assertNull(Id3Tagger.header("OggS".toByteArray() + ByteArray(64), "T", "A", null, null, "image/jpeg"))
        // An ID3 tag in front of something that isn't MPEG audio (raw AAC here).
        val aac = id3Tag(3, id3Frame("TIT2", latin1Text("x"))) + byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0) + ByteArray(32)
        assertNull(Id3Tagger.header(aac, "T", "A", null, null, "image/jpeg"))
        // v2.2 has three-letter frame names, which this doesn't convert.
        assertNull(Id3Tagger.header(id3Tag(2, ByteArray(0)) + mpegAudio, "T", "A", null, null, "image/jpeg"))
    }

    // ── FLAC ───────────────────────────────────────────────────────────────

    private fun flacBlock(type: Int, payload: ByteArray, last: Boolean = false): ByteArray =
        byteArrayOf((if (last) type or 0x80 else type).toByte(), (payload.size ushr 16).toByte(), (payload.size ushr 8).toByte(), payload.size.toByte()) + payload

    private fun vorbisComment(vararg fields: String): ByteArray {
        val vendor = "someone else".toByteArray()
        var out = le32(vendor.size) + vendor + le32(fields.size)
        fields.forEach { out += le32(it.toByteArray().size) + it.toByteArray() }
        return out
    }

    @Test
    fun `a flac keeps its other fields and its picture when no cover is given`() {
        val frames = byteArrayOf(0xFF.toByte(), 0xF8.toByte()) + ByteArray(40) { (it * 3).toByte() }
        val picture = ByteArray(48) { 9 }
        val original = "fLaC".toByteArray() +
            flacBlock(0, ByteArray(34)) +
            flacBlock(4, vorbisComment("title=Old Title", "ARTIST=Old Artist", "TRACKNUMBER=7", "ALBUM=Old Album")) +
            flacBlock(6, picture) +
            flacBlock(1, ByteArray(100), last = true) +
            frames
        val rewrite = FlacTagger.header(original, "New Title", "New Artist", null, null, null, "image/jpeg", keepExisting = true)!!
        val out = rewrite.metadata + original.copyOfRange(rewrite.audioStart, original.size)
        assertTrue(out.has("TITLE=New Title"))
        assertTrue(out.has("ARTIST=New Artist"))
        // Field names are case-insensitive: the lower-case old title goes too.
        assertFalse(out.has("Old Title"))
        assertFalse(out.has("Old Artist"))
        assertTrue(out.has("TRACKNUMBER=7"))
        assertTrue(out.has("ALBUM=Old Album"))
        assertTrue(out.has("someone else"))
        assertTrue(out.indexOfBytes(picture) > 0)
        assertArrayEquals(frames, out.copyOfRange(out.size - frames.size, out.size))

        val withCover = FlacTagger.header(original, "New Title", "New Artist", null, null, cover, "image/jpeg", keepExisting = true)!!
        assertTrue(withCover.metadata.indexOfBytes(cover) > 0)
        assertEquals(-1, withCover.metadata.indexOfBytes(picture))
    }

    // ── M4A ────────────────────────────────────────────────────────────────

    private fun box(type: String, payload: ByteArray): ByteArray =
        u32(8 + payload.size) + type.toByteArray(Charsets.ISO_8859_1) + payload

    private fun textItem(type: String, text: String) =
        box(type, box("data", u32(1) + u32(0) + text.toByteArray()))

    /** `ftyp`, a tagged `moov` whose one chunk offset points at `mdat`'s payload, then `mdat`. */
    private fun taggedMp4(mdatPayload: ByteArray, moovFirst: Boolean): Pair<ByteArray, Int> {
        val ftyp = box("ftyp", ByteArray(8))
        val trkn = box("trkn", box("data", u32(0) + u32(0) + byteArrayOf(0, 0, 0, 7, 0, 0, 0, 0)))
        val ilst = box("ilst", textItem("©nam", "Old Title") + trkn + textItem("©ART", "Old Artist"))
        val hdlr = box("hdlr", ByteArray(8) + "mdir".toByteArray() + ByteArray(13))
        val udta = box("udta", box("meta", ByteArray(4) + hdlr + ilst + box("free", ByteArray(32))))
        fun moov(offset: Int): ByteArray {
            val stco = box("stco", u32(0) + u32(1) + u32(offset))
            return box("moov", box("trak", box("mdia", box("minf", box("stbl", stco)))) + udta)
        }
        val mdat = box("mdat", mdatPayload)
        return if (moovFirst) {
            val offset = ftyp.size + moov(0).size + 8
            ftyp + moov(offset) + mdat to offset
        } else {
            val offset = ftyp.size + 8
            ftyp + mdat + moov(offset) to offset
        }
    }

    private fun Mp4Tagger.Splice.over(original: ByteArray) =
        original.copyOfRange(0, start) + replacement + original.copyOfRange(end, original.size)

    private fun chunkOffset(file: ByteArray): Int = readU32(file, file.indexOfBytes("stco".toByteArray()) + 12)

    @Test
    fun `an m4a has its details replaced and its chunk offset moved with the audio`() {
        val payload = ByteArray(50) { (it + 1).toByte() }
        val (original, _) = taggedMp4(payload, moovFirst = true)
        val out = Mp4Tagger.retag(original, "New Title", "New Artist", "New Album", cover, coverIsPng = false)!!.over(original)
        assertTrue(out.has("New Title"))
        assertTrue(out.has("New Album"))
        assertFalse(out.has("Old Title"))
        assertFalse(out.has("Old Artist"))
        assertTrue(out.indexOfBytes("trkn".toByteArray()) > 0)
        assertTrue(out.indexOfBytes(cover) > 0)
        // Exactly one of each: the old metadata was rebuilt, not added to.
        val firstIlst = out.indexOfBytes("ilst".toByteArray())
        assertEquals(-1, out.copyOfRange(firstIlst + 4, out.size).indexOfBytes("ilst".toByteArray()))
        // The chunk offset still lands on the first byte of the audio.
        val offset = chunkOffset(out)
        assertArrayEquals(payload, out.copyOfRange(offset, offset + payload.size))
        // And the boxes still add up to the file.
        var pos = 0
        val types = mutableListOf<String>()
        while (pos < out.size) {
            types += String(out, pos + 4, 4, Charsets.ISO_8859_1)
            pos += readU32(out, pos)
        }
        assertEquals(listOf("ftyp", "moov", "mdat"), types)
        assertEquals(out.size, pos)
    }

    @Test
    fun `audio ahead of the index is not moved`() {
        val payload = ByteArray(50) { (it + 1).toByte() }
        val (original, offset) = taggedMp4(payload, moovFirst = false)
        val out = Mp4Tagger.retag(original, "New Title", "New Artist", null, null, coverIsPng = false)!!.over(original)
        assertEquals(offset, chunkOffset(out))
        assertArrayEquals(payload, out.copyOfRange(offset, offset + payload.size))
        assertTrue(out.has("New Title"))
    }

    @Test
    fun `a file that is not an mp4 is refused`() {
        assertNull(Mp4Tagger.retag("OggS".toByteArray() + ByteArray(64), "T", "A", null, null, false))
        assertNull(Mp4Tagger.retag(box("ftyp", ByteArray(8)) + box("mdat", ByteArray(8)), "T", "A", null, null, false))
    }
}
