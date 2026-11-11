package com.music.bitchord.download

import java.io.ByteArrayOutputStream

/**
 * Rewrites the ID3v2 tag at the front of an MP3 so it carries a new title,
 * artist, album and cover, keeping every other frame the file already had.
 *
 * Only for the listener's own files, when a song fix is saved into the file
 * (see [FileRetagger]): nothing this app downloads is an MP3. That is also why
 * it merges rather than writes fresh, unlike the download taggers. The file
 * has a track number, a year and a genre that are the listener's, and losing
 * them to a rename would be a poor trade.
 *
 * An MP3's tag is a block in front of the audio and nothing in the audio
 * points back at it, so the whole job is a new tag and the frames copied
 * through from wherever the old tag ended. [header] hands back those two
 * things so the copy can be streamed.
 *
 * ID3v2.3 and v2.4 are read; a file with no tag gets a v2.3 one, which is the
 * version every player reads. The ancient v2.2 (three-letter frame names) is
 * refused rather than converted.
 */
object Id3Tagger {

    /** The new tag, and where the audio starts in the original file. */
    class Rewrite(val metadata: ByteArray, val audioStart: Int)

    /** Whether [head] opens like an MP3: an ID3v2 tag, or an MPEG audio frame. */
    fun recognises(head: ByteArray): Boolean = hasTag(head) || startsWithMpegAudio(head, 0)

    /**
     * @param head the start of the file, long enough to hold the whole tag.
     * @return null when the file isn't one this can safely rewrite, or [head]
     *   is too short to hold its tag.
     */
    fun header(
        head: ByteArray,
        title: String,
        artist: String,
        album: String?,
        cover: ByteArray?,
        coverMime: String,
    ): Rewrite? = runCatching { build(head, title, artist, album, cover, coverMime) }.getOrNull()

    private fun build(
        head: ByteArray,
        title: String,
        artist: String,
        album: String?,
        cover: ByteArray?,
        coverMime: String,
    ): Rewrite? {
        val replaced = buildSet {
            if (title.isNotBlank()) add("TIT2")
            if (artist.isNotBlank()) add("TPE1")
            if (!album.isNullOrBlank()) add("TALB")
            if (cover != null && cover.isNotEmpty()) add("APIC")
        }
        if (replaced.isEmpty()) return null

        var version = 3
        var audioStart = 0
        val kept = mutableListOf<ByteArray>()
        if (hasTag(head)) {
            version = head[3].toInt() and 0xFF
            if (version != 3 && version != 4) return null
            val flags = head[5].toInt() and 0xFF
            val size = synchsafe(head, 6) ?: return null
            // v2.4 can repeat its header as a footer after the frames.
            val footer = if (version == 4 && flags and FLAG_FOOTER != 0) HEADER else 0
            audioStart = HEADER + size + footer
            if (HEADER + size > head.size) return null
            var body = head.copyOfRange(HEADER, HEADER + size)
            // v2.3 escapes the whole tag; v2.4 does it frame by frame, and
            // those frames are carried across as they are.
            if (version == 3 && flags and FLAG_UNSYNC != 0) body = withoutUnsync(body)
            var pos = 0
            if (flags and FLAG_EXTENDED != 0) {
                pos = if (version == 3) {
                    4 + (u32(body, 0) ?: return null).toInt()
                } else {
                    synchsafe(body, 0) ?: return null
                }
                if (pos < 0 || pos > body.size) return null
            }
            // What follows the tag has to be MPEG audio. An ID3 tag can sit
            // in front of other things (raw AAC, even a FLAC), and those are
            // not files to put an MP3's tag on.
            var audio = audioStart
            while (audio < head.size && head[audio].toInt() == 0) audio++
            if (!startsWithMpegAudio(head, audio)) return null
            frames(body, pos, version).forEach { frame ->
                if (frame.id !in replaced) kept += body.copyOfRange(frame.start, frame.end)
            }
        } else if (!startsWithMpegAudio(head, 0)) {
            return null
        }

        val out = ByteArrayOutputStream()
        if (title.isNotBlank()) out.write(textFrame("TIT2", title, version))
        if (artist.isNotBlank()) out.write(textFrame("TPE1", artist, version))
        if (!album.isNullOrBlank()) out.write(textFrame("TALB", album, version))
        kept.forEach(out::write)
        if (cover != null && cover.isNotEmpty()) out.write(pictureFrame(cover, coverMime, version))
        // Room for the next edit by any tagger, which is what padding is for.
        out.write(ByteArray(PADDING))
        val body = out.toByteArray()
        if (body.size > MAX_TAG_BYTES) return null

        val tag = ByteArray(HEADER + body.size)
        MAGIC.copyInto(tag, 0)
        tag[3] = version.toByte()
        // Revision 0, and no flags: nothing here is escaped, extended or footed.
        writeSynchsafe(tag, 6, body.size)
        body.copyInto(tag, HEADER)
        return Rewrite(tag, audioStart)
    }

    private class Frame(val id: String, val start: Int, val end: Int)

    /** The frames of [body] from [from], stopping at the padding or anything that isn't a frame. */
    private fun frames(body: ByteArray, from: Int, version: Int): List<Frame> {
        val out = mutableListOf<Frame>()
        var pos = from
        while (pos + FRAME_HEADER <= body.size) {
            if (!isFrameId(body, pos)) break
            val plain = u32(body, pos + 4) ?: break
            val size = if (version == 4) {
                // v2.4 sizes are synchsafe, but an old iTunes wrote plain ones.
                // The right reading is the one that lands on another frame, on
                // padding, or on the end of the tag.
                val safe = synchsafe(body, pos + 4)
                if (safe != null && landsOnFrame(body, pos + FRAME_HEADER + safe)) safe.toLong() else plain
            } else {
                plain
            }
            if (size <= 0 || pos + FRAME_HEADER + size > body.size) break
            val end = pos + FRAME_HEADER + size.toInt()
            out += Frame(String(body, pos, 4, Charsets.ISO_8859_1), pos, end)
            pos = end
        }
        return out
    }

    private fun landsOnFrame(body: ByteArray, at: Int): Boolean =
        at == body.size || (at < body.size && (body[at].toInt() == 0 || (at + 4 <= body.size && isFrameId(body, at))))

    private fun isFrameId(body: ByteArray, at: Int): Boolean {
        for (i in 0 until 4) {
            val c = body[at + i].toInt().toChar()
            if (c !in 'A'..'Z' && c !in '0'..'9') return false
        }
        return true
    }

    /** A text frame: UTF-16 with a byte order mark in v2.3, which has no UTF-8; UTF-8 in v2.4. */
    private fun textFrame(id: String, text: String, version: Int): ByteArray {
        val payload = if (version == 4) {
            byteArrayOf(ENCODING_UTF8) + text.toByteArray(Charsets.UTF_8)
        } else {
            byteArrayOf(ENCODING_UTF16, 0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        }
        return frame(id, payload, version)
    }

    /** An `APIC` frame holding [cover] as the front cover. */
    private fun pictureFrame(cover: ByteArray, mime: String, version: Int): ByteArray {
        val out = ByteArrayOutputStream(cover.size + 32)
        out.write(ENCODING_LATIN1.toInt())
        out.write(mime.toByteArray(Charsets.ISO_8859_1))
        out.write(0)
        out.write(PICTURE_FRONT_COVER)
        out.write(0) // no description
        out.write(cover)
        return frame("APIC", out.toByteArray(), version)
    }

    private fun frame(id: String, payload: ByteArray, version: Int): ByteArray {
        val out = ByteArray(FRAME_HEADER + payload.size)
        id.toByteArray(Charsets.ISO_8859_1).copyInto(out, 0)
        if (version == 4) {
            writeSynchsafe(out, 4, payload.size)
        } else {
            out[4] = (payload.size ushr 24).toByte()
            out[5] = (payload.size ushr 16).toByte()
            out[6] = (payload.size ushr 8).toByte()
            out[7] = payload.size.toByte()
        }
        payload.copyInto(out, FRAME_HEADER)
        return out
    }

    private fun hasTag(head: ByteArray): Boolean =
        head.size >= HEADER && head[0] == MAGIC[0] && head[1] == MAGIC[1] && head[2] == MAGIC[2]

    /**
     * Eleven set bits and a layer that isn't the reserved one. The layer check
     * is what tells an MP3 from raw AAC, which opens with the same sync bits.
     */
    private fun startsWithMpegAudio(head: ByteArray, at: Int): Boolean {
        if (at + 2 > head.size) return false
        val second = head[at + 1].toInt() and 0xFF
        return (head[at].toInt() and 0xFF) == 0xFF && second and 0xE0 == 0xE0 && second and 0x06 != 0
    }

    /** Four bytes of seven bits each, or null when one has its top bit set. */
    private fun synchsafe(bytes: ByteArray, at: Int): Int? {
        if (at + 4 > bytes.size) return null
        var value = 0
        for (i in 0 until 4) {
            val b = bytes[at + i].toInt() and 0xFF
            if (b and 0x80 != 0) return null
            value = (value shl 7) or b
        }
        return value
    }

    private fun writeSynchsafe(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = ((value ushr 21) and 0x7F).toByte()
        bytes[at + 1] = ((value ushr 14) and 0x7F).toByte()
        bytes[at + 2] = ((value ushr 7) and 0x7F).toByte()
        bytes[at + 3] = (value and 0x7F).toByte()
    }

    private fun u32(bytes: ByteArray, at: Int): Long? {
        if (at + 4 > bytes.size) return null
        return ((bytes[at].toLong() and 0xFF) shl 24) or ((bytes[at + 1].toLong() and 0xFF) shl 16) or
            ((bytes[at + 2].toLong() and 0xFF) shl 8) or (bytes[at + 3].toLong() and 0xFF)
    }

    /** Undoes unsynchronisation: every `FF 00` was an `FF` with a zero put after it. */
    private fun withoutUnsync(body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(body.size)
        var i = 0
        while (i < body.size) {
            out.write(body[i].toInt())
            if (body[i] == 0xFF.toByte() && i + 1 < body.size && body[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    private val MAGIC = "ID3".toByteArray(Charsets.ISO_8859_1)
    private const val HEADER = 10
    private const val FRAME_HEADER = 10
    private const val FLAG_UNSYNC = 0x80
    private const val FLAG_EXTENDED = 0x40
    private const val FLAG_FOOTER = 0x10
    private const val ENCODING_LATIN1: Byte = 0
    private const val ENCODING_UTF16: Byte = 1
    private const val ENCODING_UTF8: Byte = 3
    private const val PICTURE_FRONT_COVER = 3
    private const val PADDING = 1024

    /** The most a synchsafe size can describe. */
    private const val MAX_TAG_BYTES = (1 shl 28) - 1
}
