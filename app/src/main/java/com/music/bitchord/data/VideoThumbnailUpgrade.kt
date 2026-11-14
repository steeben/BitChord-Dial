package com.music.bitchord.data

import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.transformations
import coil3.size.Dimension
import coil3.size.Size

/**
 * Loads a larger copy of a YouTube video thumbnail where it is drawn large.
 *
 * The size comes from the request itself (what the image is drawn at, or the
 * size the caller set), so a list row keeps the small listed picture and only
 * a card, a banner or the player pays for a large one. Coil still decodes the
 * file down to the size asked for, so memory follows the drawn size, not the
 * file's.
 *
 * Each larger name is a try. If it fails, the next one is tried, and last the
 * address the request came with. A name the host answered 404 for is
 * remembered for this run of the app, so it is asked for once. See
 * [VideoThumbnails].
 *
 * Must be added before [LetterboxCrop.Install], which then sees the address
 * that is in fact loaded.
 */
class VideoThumbnailUpgrade : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data as? String ?: return chain.proceed()
        val candidates = VideoThumbnails.larger(url, longerSide(chain.size))
        for (candidate in candidates) {
            val key = candidate.substringBefore('#')
            if (isMissing(key)) continue
            val builder = request.newBuilder().data(candidate)
            // The 4:3 names carry black bars the listed picture does not have.
            // Trim them whatever Crop thumbnails says, so the picture keeps
            // the shape it had.
            if (VideoThumbnails.isLetterboxed(candidate) && request.transformations.none { it is LetterboxCrop }) {
                builder.transformations(request.transformations + LetterboxCrop())
            }
            val result = chain.withRequest(builder.build()).proceed()
            if (result !is ErrorResult) return result
            val status = (result.throwable as? HttpException)?.response?.code
            if (status == 404) {
                markMissing(key)
            } else {
                // Offline, or the host is in trouble: a second large file
                // would fail the same way. Go to the listed picture, which
                // may be in the disk cache.
                break
            }
        }
        return chain.proceed()
    }

    private fun longerSide(size: Size): Int {
        val width = (size.width as? Dimension.Pixels)?.px ?: return 0
        val height = (size.height as? Dimension.Pixels)?.px ?: return 0
        return maxOf(width, height)
    }

    private companion object {
        private const val REMEMBERED = 512

        /** Addresses the host answered 404 for, oldest dropped first. */
        private val missing = object : LinkedHashMap<String, Unit>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean =
                size > REMEMBERED
        }

        private fun isMissing(key: String): Boolean = synchronized(missing) { missing.containsKey(key) }

        private fun markMissing(key: String) {
            synchronized(missing) { missing[key] = Unit }
        }
    }
}
