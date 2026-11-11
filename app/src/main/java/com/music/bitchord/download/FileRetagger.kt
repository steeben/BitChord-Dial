package com.music.bitchord.download

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saves a song fix into the listener's own music file: the title, artist,
 * album and cover picked with "Find this song's details" replace the ones in
 * the file's tags, so the file reads the same in any other player.
 *
 * Off unless the listener turns it on in Settings, under Your data, because
 * it changes a file that isn't this app's and can't be put back.
 *
 * MP3, FLAC and M4A are written ([Id3Tagger], [FlacTagger], [Mp4Tagger]), and
 * each keeps the fields it isn't asked to change. Anything else comes back as
 * [Outcome.Unsupported] with the file untouched.
 *
 * The new file is built whole in the cache first and only then copied over
 * the original, so a format this can't follow, a failed cover fetch or a full
 * disk costs nothing. Android asks the listener before an app changes a music
 * file it didn't create; that question is [Outcome.NeedsAccess], and
 * [finish] carries on once it has been answered.
 */
object FileRetagger {

    sealed interface Outcome {
        data object Saved : Outcome

        /** Not a format there is a tagger for, or a layout the tagger wouldn't touch. */
        data object Unsupported : Outcome

        /** The listener, or the system, said no to changing the file. */
        data object Denied : Outcome

        data object Failed : Outcome

        /** Show [sender] to the listener, then call [finish] with [pending] if they agree, or [discard]. */
        class NeedsAccess(val sender: IntentSender, val pending: Pending) : Outcome
    }

    /** A finished rewrite waiting for permission to replace the original. */
    class Pending internal constructor(internal val uri: Uri, internal val temp: File)

    /** The file [song] plays from, when it is one of the listener's own on this device. */
    fun fileOf(song: Song): String? =
        (song.localUri ?: song.videoId).takeIf { it.startsWith("content://") || it.startsWith("file://") }

    /**
     * Writes [details]' title, artist and album into [file], and the cover at
     * [coverUrl] when there is one to fetch. Without a cover the file keeps
     * the picture it has.
     */
    suspend fun write(context: Context, file: String, details: Song, coverUrl: String?): Outcome =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val uri = Uri.parse(file)
                val temp = try {
                    val artwork = coverUrl?.takeIf { it.startsWith("http") }
                        ?.let { MediaTagger.artworkFor(context, details.copy(thumbnailUrl = it)) }
                    build(context, uri, details, artwork)
                } catch (t: Throwable) {
                    // Throwable, for the OutOfMemoryError a very large M4A can raise.
                    Log.w(TAG, "could not retag $uri: $t")
                    return@withLock Outcome.Failed
                } ?: return@withLock Outcome.Unsupported
                commit(context, Pending(uri, temp), mayAsk = true)
            }
        }

    /** Replaces the original now that the listener has allowed it. */
    suspend fun finish(context: Context, pending: Pending): Outcome =
        withContext(Dispatchers.IO) { lock.withLock { commit(context, pending, mayAsk = false) } }

    fun discard(pending: Pending) {
        pending.temp.delete()
    }

    private fun commit(context: Context, pending: Pending, mayAsk: Boolean): Outcome {
        try {
            MediaTagger.replaceWith(context, pending.uri, pending.temp)
            pending.temp.delete()
            return Outcome.Saved
        } catch (t: Throwable) {
            Log.w(TAG, "could not write tags to ${pending.uri}: $t")
            val sender = if (mayAsk) accessRequest(context, pending.uri, t) else null
            if (sender != null) return Outcome.NeedsAccess(sender, pending)
            pending.temp.delete()
            return if (t is SecurityException) Outcome.Denied else Outcome.Failed
        }
    }

    /** The system's own "allow this app to change this file?" question, where there is one to ask. */
    private fun accessRequest(context: Context, uri: Uri, failure: Throwable): IntentSender? = runCatching {
        when {
            failure !is SecurityException && failure !is java.io.IOException -> null
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri.authority == MediaStore.AUTHORITY ->
                MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && failure is RecoverableSecurityException ->
                failure.userAction.actionIntent.intentSender
            else -> null
        }
    }.getOrNull()

    /** The retagged copy of [uri] in the cache, or null when it isn't a file this can retag. */
    private fun build(context: Context, uri: Uri, details: Song, artwork: MediaTagger.Artwork?): File? {
        val head = MediaTagger.readPrefix(context, uri, HEAD_BYTES) ?: error("could not read the file")
        val cover = artwork?.bytes
        val mime = artwork?.mime ?: "image/jpeg"
        val temp = File(context.cacheDir, "retag-${System.nanoTime()}.part")
        try {
            when {
                head.startsWith(FLAC, 0) -> {
                    val rewrite = FlacTagger.header(
                        head, details.title, details.artist, details.albumName,
                        lyrics = null, cover = cover, coverMime = mime, keepExisting = true,
                    ) ?: return null
                    MediaTagger.spliceFront(context, uri, rewrite.metadata, rewrite.audioStart, temp)
                }
                head.startsWith(FTYP, 4) -> {
                    // An MP4's index can sit at either end and holds positions
                    // in the file, so this one has to be read whole.
                    val bytes = if (head.size < HEAD_BYTES) head else {
                        if (MediaTagger.lengthOf(context, uri) > MP4_MAX_BYTES) return null
                        MediaTagger.readAll(context, uri) ?: error("could not read the file")
                    }
                    val splice = Mp4Tagger.retag(
                        bytes, details.title, details.artist, details.albumName, cover, coverIsPng = false,
                    ) ?: return null
                    temp.outputStream().buffered().use { sink ->
                        sink.write(bytes, 0, splice.start)
                        sink.write(splice.replacement)
                        sink.write(bytes, splice.end, bytes.size - splice.end)
                    }
                }
                Id3Tagger.recognises(head) -> {
                    val rewrite = Id3Tagger.header(
                        head, details.title, details.artist, details.albumName, cover, mime,
                    ) ?: return null
                    MediaTagger.spliceFront(context, uri, rewrite.metadata, rewrite.audioStart, temp)
                }
                else -> return null
            }
            return temp
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    private fun ByteArray.startsWith(other: ByteArray, at: Int): Boolean {
        if (at + other.size > size) return false
        for (i in other.indices) if (this[at + i] != other[i]) return false
        return true
    }

    /** One rewrite at a time: an M4A is held in memory while it is rebuilt. */
    private val lock = Mutex()

    private const val TAG = "BitChord"

    /** How much of a file is read to find its tags: far more than an MP3's or a FLAC's ever run to. */
    private const val HEAD_BYTES = 16 * 1024 * 1024

    /** Past this an M4A is left alone rather than read into memory. */
    private const val MP4_MAX_BYTES = 192L * 1024 * 1024

    private val FLAC = "fLaC".toByteArray(Charsets.US_ASCII)
    private val FTYP = "ftyp".toByteArray(Charsets.US_ASCII)
}
