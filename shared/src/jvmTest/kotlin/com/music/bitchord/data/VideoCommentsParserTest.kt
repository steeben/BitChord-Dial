package com.music.bitchord.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shapes trimmed from YouTube's own answers for a video's comments. */
class VideoCommentsParserTest {

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun thread(key: String) = """
        { "commentThreadRenderer": {
          "commentViewModel": { "commentViewModel": { "commentKey": "$key" } },
          "replies": { "commentRepliesRenderer": { "contents": [ { "continuationItemRenderer": {
            "continuationEndpoint": { "continuationCommand": { "token": "REPLIES-$key" } }
          } } ] } }
        } }
    """

    private fun said(
        key: String,
        text: String,
        likes: String = "250K",
        replies: String = "772",
        level: Int = 0,
        creator: Boolean = false,
    ) = """
        { "payload": { "commentEntityPayload": {
          "key": "$key",
          "properties": {
            "commentId": "id-$key", "content": { "content": "$text" },
            "publishedTime": "7 years ago", "replyLevel": $level
          },
          "author": {
            "displayName": "@someone", "avatarThumbnailUrl": "https://yt3.ggpht.com/a",
            "isVerified": true, "isCreator": $creator
          },
          "toolbar": { "likeCountNotliked": "$likes", "replyCount": "$replies" }
        } } }
    """

    @Test
    fun startToken_isTheOneTheCommentsSectionHolds() {
        val watch = json(
            """
            { "contents": { "twoColumnWatchNextResults": { "results": { "results": { "contents": [
              { "itemSectionRenderer": { "sectionIdentifier": "related", "contents": [ { "continuationItemRenderer": {
                "continuationEndpoint": { "continuationCommand": { "token": "RELATED" } } } } ] } },
              { "itemSectionRenderer": { "sectionIdentifier": "comment-item-section", "contents": [ { "continuationItemRenderer": {
                "continuationEndpoint": { "continuationCommand": { "token": "COMMENTS" } } } } ] } }
            ] } } } } }
            """,
        )
        assertEquals("COMMENTS", VideoCommentsParser.startToken(watch))
        // A video that takes no comments has no such section.
        assertNull(VideoCommentsParser.startToken(json("""{ "contents": {} }""")))
    }

    @Test
    fun page_listsItsThreadsInOrder_withWhatEachSays() {
        val response = json(
            """
            { "onResponseReceivedEndpoints": [
              { "reloadContinuationItemsCommand": { "slot": "RELOAD_CONTINUATION_SLOT_HEADER", "continuationItems": [
                { "commentsHeaderRenderer": { "countText": { "runs": [ { "text": "10,644,156" }, { "text": " Comments" } ] } } }
              ] } },
              { "reloadContinuationItemsCommand": { "slot": "RELOAD_CONTINUATION_SLOT_BODY", "continuationItems": [
                ${thread("k2")}, ${thread("k1")},
                { "continuationItemRenderer": { "continuationEndpoint": { "continuationCommand": { "token": "PAGE2" } } } }
              ] } }
            ],
            "frameworkUpdates": { "entityBatchUpdate": { "mutations": [
              { "payload": { "commentSharedEntityPayload": {} } },
              ${said("k1", "first said", likes = " ", replies = "0")},
              ${said("k2", "  second said  ", creator = true)}
            ] } } }
            """,
        )
        val page = VideoCommentsParser.page(response)

        assertEquals(listOf("second said", "first said"), page.comments.map { it.text })
        // The page's own next page, not a thread's replies.
        assertEquals("PAGE2", page.continuation)
        assertEquals("10,644,156", page.total)
        assertFalse(page.off)

        val top = page.comments.first()
        assertEquals("id-k2", top.id)
        assertEquals("@someone", top.author)
        assertEquals("https://yt3.ggpht.com/a", top.avatarUrl)
        assertEquals("7 years ago", top.published)
        assertEquals("250K", top.likes)
        assertEquals("772", top.replies)
        assertTrue(top.byCreator)
        // No likes and no replies are not counts to show.
        assertNull(page.comments[1].likes)
        assertNull(page.comments[1].replies)
        assertFalse(page.comments[1].byCreator)
    }

    @Test
    fun laterPage_isAppended_andTheLastHasNoNext() {
        val response = json(
            """
            { "onResponseReceivedEndpoints": [
              { "appendContinuationItemsAction": { "continuationItems": [ ${thread("k3")} ] } }
            ],
            "frameworkUpdates": { "entityBatchUpdate": { "mutations": [
              ${said("k3", "third said")},
              ${said("r1", "a reply", level = 1)}
            ] } } }
            """,
        )
        val page = VideoCommentsParser.page(response)

        assertEquals(listOf("third said"), page.comments.map { it.text })
        assertNull(page.continuation)
        assertNull(page.total)
    }

    @Test
    fun page_withNothingInIt_isEmpty() {
        val page = VideoCommentsParser.page(json("""{ "responseContext": {} }"""))

        assertTrue(page.comments.isEmpty())
        assertNull(page.continuation)
    }
}
