package com.music.bitchord.data

import com.music.bitchord.data.innertube.Innertube
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** One comment under a video, as YouTube lists it. Counts are YouTube's own text ("250K"). */
data class VideoComment(
    val id: String,
    val author: String,
    val avatarUrl: String?,
    val text: String,
    /** "7 years ago", in the app's language. */
    val published: String?,
    val likes: String?,
    val replies: String?,
    /** Written by the channel the video is on. */
    val byCreator: Boolean = false,
)

/**
 * A page of a video's comments.
 *
 * @property continuation What asks for the page after this one; null on the last.
 * @property total How many comments the video has, as YouTube writes it
 * ("10,644,156"); only the first page says.
 * @property off True when the video takes no comments at all.
 */
data class VideoCommentsPage(
    val comments: List<VideoComment>,
    val continuation: String? = null,
    val total: String? = null,
    val off: Boolean = false,
)

/**
 * The comments under a YouTube video, for the player's comments drawer on a
 * podcast episode. Read as YouTube's own page reads them, with no account:
 * nothing is posted, liked or recorded.
 *
 * Two requests for the first page: the video's watch data names where its
 * comments start, and that is then asked for. YouTube Music has no comments
 * of its own to ask.
 */
object VideoComments {

    private const val NEXT_URL = "https://www.youtube.com/youtubei/v1/next"

    /** The web client to ask as. YouTube answers an older version the same way. */
    private const val WEB_CLIENT_VERSION = "2.20250101.00.00"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Safari/537.36"

    private val client = HttpClient(OkHttp) {
        engine { preconfigured = Http.client }
        install(HttpTimeout) { requestTimeoutMillis = 15_000 }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** The first comments under [videoId], in YouTube's "Top comments" order. */
    suspend fun first(videoId: String): Result<VideoCommentsPage> = attempt {
        val token = VideoCommentsParser.startToken(post { put("videoId", videoId) })
            ?: return@attempt VideoCommentsPage(emptyList(), off = true)
        VideoCommentsParser.page(post { put("continuation", token) })
    }

    /** The page a [VideoCommentsPage.continuation] asks for. */
    suspend fun more(continuation: String): Result<VideoCommentsPage> = attempt {
        VideoCommentsParser.page(post { put("continuation", continuation) })
    }

    private suspend fun post(body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject {
        val request = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB")
                    put("clientVersion", WEB_CLIENT_VERSION)
                    put("hl", Innertube.currentLanguage)
                    put("gl", Innertube.currentRegion)
                }
            }
            body()
        }
        val response = client.post(NEXT_URL) {
            parameter("prettyPrint", "false")
            header("User-Agent", USER_AGENT)
            // Answers the consent question and opts into nothing — see [SongInfoRepository].
            header("Cookie", "SOCS=CAI")
            setBody(TextContent(request.toString(), ContentType.Application.Json))
        }
        check(response.status.value == 200) { "comments: HTTP ${response.status.value}" }
        return json.parseToJsonElement(response.bodyAsText()) as JsonObject
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/** Reads YouTube's answers for [VideoComments]. */
internal object VideoCommentsParser {

    /**
     * Where a video's comments start, off its watch data: the continuation
     * its comments section holds in place of the comments. Null for a video
     * that has no such section, which is one that takes no comments.
     */
    fun startToken(watch: JsonObject): String? =
        watch.find("itemSectionRenderer")
            .filter { it.str("sectionIdentifier") == "comment-item-section" }
            .firstNotNullOfOrNull { section -> section.find("continuationCommand").firstNotNullOfOrNull { it.str("token") } }

    /**
     * One page of comments. The page lists its threads by key and keeps what
     * each says apart, among its entity updates; replies are other threads'
     * and are not on it.
     */
    fun page(response: JsonObject): VideoCommentsPage {
        val items = (response["onResponseReceivedEndpoints"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .flatMap { endpoint ->
                (endpoint.obj("reloadContinuationItemsCommand") ?: endpoint.obj("appendContinuationItemsAction"))
                    ?.arr("continuationItems").orEmpty()
            }
            .mapNotNull { it as? JsonObject }
        val said = (response.obj("frameworkUpdates")?.obj("entityBatchUpdate")?.arr("mutations")).orEmpty()
            .mapNotNull { (it as? JsonObject)?.obj("payload")?.obj("commentEntityPayload") }
        val byKey = said.associateBy { it.str("key") }
        val keys = items.mapNotNull {
            it.obj("commentThreadRenderer")?.obj("commentViewModel")?.obj("commentViewModel")?.str("commentKey")
        }
        // In the page's own order; without one, in the order they were said.
        val listed = keys.mapNotNull { byKey[it] }
            .ifEmpty { said.filter { it.obj("properties")?.get("replyLevel").int() == 0 } }
        return VideoCommentsPage(
            comments = listed.mapNotNull(::comment).distinctBy { it.id },
            // The page's own "more", not a thread's "more replies".
            continuation = items.mapNotNull { it.obj("continuationItemRenderer") }.lastOrNull()
                ?.find("continuationCommand")?.firstNotNullOfOrNull { it.str("token") },
            total = items.firstNotNullOfOrNull { it.obj("commentsHeaderRenderer") }
                ?.obj("countText")?.arr("runs")?.firstOrNull()
                ?.let { (it as? JsonObject)?.str("text") }
                ?.takeIf { it.any(Char::isDigit) },
        )
    }

    private fun comment(payload: JsonObject): VideoComment? {
        val properties = payload.obj("properties") ?: return null
        val text = properties.obj("content")?.str("content")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val author = payload.obj("author")
        val toolbar = payload.obj("toolbar")
        return VideoComment(
            id = properties.str("commentId") ?: payload.str("key") ?: return null,
            author = author?.str("displayName").orEmpty(),
            avatarUrl = author?.str("avatarThumbnailUrl"),
            text = text,
            published = properties.str("publishedTime"),
            likes = toolbar?.str("likeCountNotliked")?.trim()?.takeIf { it.isCount() },
            replies = toolbar?.str("replyCount")?.trim()?.takeIf { it.isCount() },
            byCreator = (author?.get("isCreator") as? JsonPrimitive)?.contentOrNull == "true",
        )
    }

    /** A count worth showing: YouTube leaves it empty, or "0", for none. */
    private fun String.isCount(): Boolean = any(Char::isDigit) && this != "0"

    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    /** Every object held under [key], anywhere below, in document order. */
    private fun JsonElement.find(key: String): List<JsonObject> = when (this) {
        is JsonObject -> listOfNotNull(this[key] as? JsonObject) + values.flatMap { it.find(key) }
        is JsonArray -> flatMap { it.find(key) }
        else -> emptyList()
    }
}
