package com.music.bitchord.data

import android.content.Context
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import java.io.File

/**
 * The picture each Explore category last showed, kept on disk.
 *
 * A category button carries no picture of its own: its picture is the first
 * cover on the page it opens, so each one costs a full page request. Kept here,
 * a launch shows every picture at once, with no request for them, and nobody
 * who never opens Explore pays for its pictures at all.
 */
object ExploreArtCache {
    private const val FILE = "explore_art.tsv"

    /** A category's key: its browse id and params, as [MoodGenre] carries them. */
    fun keyOf(browseId: String, params: String?): String = "$browseId|${params.orEmpty()}"

    fun keyOf(item: MoodGenre): String = keyOf(item.browseId, item.params)

    fun read(context: Context): Map<String, String> = runCatching {
        val file = File(context.cacheDir, FILE)
        if (!file.exists()) return emptyMap()
        decode(file.readText())
    }.getOrDefault(emptyMap())

    fun write(context: Context, art: Map<String, String>) {
        runCatching {
            val target = File(context.cacheDir, FILE)
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.writeText(encode(art))
            temp.renameTo(target)
        }
    }

    /** One category to a line, its key and its picture's address with a tab between. */
    internal fun encode(art: Map<String, String>): String =
        art.entries.joinToString("\n") { (key, url) -> "$key\t$url" }

    internal fun decode(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        text.lineSequence().forEach { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEach
            val url = line.substring(tab + 1).trim()
            if (url.isNotEmpty()) out[line.substring(0, tab)] = url
        }
        return out
    }
}

/**
 * These sections with [art] put on the categories it has a picture for. A
 * category [art] says nothing about keeps the picture it has, and with
 * [replace] off so does every category that already has one. The same list is
 * returned when nothing changes, so a state holding it is not written again.
 */
fun List<MoodGenreSection>.withArt(
    art: Map<String, String>,
    replace: Boolean = true,
): List<MoodGenreSection> {
    if (art.isEmpty()) return this
    var changed = false
    val out = map { section ->
        var sectionChanged = false
        val items = section.items.map { item ->
            val url = art[ExploreArtCache.keyOf(item)]
            val open = replace || item.thumbnailUrl.isNullOrBlank()
            if (open && url != null && url != item.thumbnailUrl) {
                sectionChanged = true
                item.copy(thumbnailUrl = url)
            } else {
                item
            }
        }
        if (sectionChanged) {
            changed = true
            section.copy(items = items)
        } else {
            section
        }
    }
    return if (changed) out else this
}

/** The pictures these sections show now, by category key. */
fun List<MoodGenreSection>.artByKey(): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    forEach { section ->
        section.items.forEach { item ->
            item.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { out[ExploreArtCache.keyOf(item)] = it }
        }
    }
    return out
}
