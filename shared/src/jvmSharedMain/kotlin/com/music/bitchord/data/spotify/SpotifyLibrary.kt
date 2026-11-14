package com.music.bitchord.data.spotify

import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** A Spotify playlist's page id in the detail stack: this plus the playlist id (or [SpotifyLibrary.LIKED_ID]). */
const val SPOTIFY_PAGE_PREFIX = "spotify:playlist:"

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val owner: String?,
    val imageUrl: String?,
)

/** A podcast show Spotify recommends: its name, who publishes it, and its cover. */
data class SpotifyShow(
    val id: String,
    val name: String,
    val publisher: String?,
    val imageUrl: String?,
)

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Int,
    val imageUrl: String?,
)

object SpotifyLibrary {
    /** The web player's bearer, and its client token when there is one. */
    class Tokens(val bearer: String, val clientToken: String?)

    /**
     * Where the tokens come from: the phone mints them in a WebView, the desktop in JavaFX's.
     * Null tokens mean signed out or expired.
     */
    fun interface Auth {
        suspend fun tokens(): Tokens?
    }

    /** Installed by each application at start-up; until then every call fails as signed out. */
    @Volatile
    var auth: Auth? = null

    private const val GQL = "https://api-partner.spotify.com/pathfinder/v2/query"
    private const val LIBRARY = "973e511ca44261fda7eebac8b653155e7caee3675abb4fb110cc1b8c78b091c3"
    private const val PLAYLIST = "346811f856fb0b7e4f6c59f8ebea78dd081c6e2fb01b77c954b26259d5fc6763"
    /** The pseudo-playlist id for the Liked Songs collection, which has no playlist uri. */
    const val LIKED_ID = "liked"
    /** Spotify's own Liked Songs artwork, the purple heart; the collection has no cover of its own. */
    private const val LIKED_COVER = "https://misc.scdn.co/liked-songs/liked-songs-640.png"
    private const val LIKED = "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240"
    /** The web player's own search page query, the one Meld reads Spotify's catalogue through. */
    private const val SEARCH = "4801118d4a100f756e833d33984436a3899cff359c532f8fd3aaf174b60b3b49"
    /**
     * The web player's home page query, newest first: Spotify retires these
     * hashes now and then, so an older one is tried when the newest is refused.
     */
    private val HOME = listOf(
        "5fb7da7a03ee2776c0670856609cb85a189a3cecc36cc160b91cde79063b9cf1",
        "23e37f2e58d82d567f27080101d36609009d8c3676457b1086cb0acc55b72a5d",
    )
    /** One shelf of the home page on its own, for when the whole page can't be read. */
    private const val HOME_SECTION = "eb3fba2d388cf4fc4d696b1757a58584e9538a3b515ea742e9cc9465807340be"
    /** Home's "Your top mixes" shelf: the same uri for every account. */
    internal const val TOP_MIXES_SECTION = "spotify:section:0JQ5DAnM3wGh0gz1MXnu89"
    /** The phone's canvas client sent this one; kept so its requests look unchanged. */
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Safari/537.36"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Every playlist in the library, however many pages it takes. The app's
     * own "Played in Dial" playlist is left out: it is there for Spotify's
     * recommendations to read, not to be browsed here.
     */
    suspend fun playlists(): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        // Liked Songs is not a playlist to Spotify's library query, so it is
        // always offered first rather than depending on it turning up there.
        listOf(SpotifyPlaylist(id = LIKED_ID, name = "Liked Songs", owner = null, imageUrl = LIKED_COVER)) +
            SpotifyPlayedList.withoutPlayedList(libraryPlaylists())
    }

    /**
     * The playlists on Home's "Your top mixes" shelf, in Spotify's order:
     * the genre, mood, decade and artist mixes it made for this account,
     * whether or not they are saved to the library. Read from the home page,
     * and failing that from the shelf's own query.
     */
    suspend fun topMixes(): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val headers = authHeaders()
        val timeZone = java.util.TimeZone.getDefault().id
        val homeVariables = buildJsonObject {
            put("homeEndUserIntegration", "INTEGRATION_WEB_PLAYER")
            put("timeZone", timeZone)
            put("sp_t", "")
            put("facet", "")
            put("sectionItemsLimit", 20)
            put("includeEpisodeContentRatingsV2", false)
        }
        val fromHome = HOME.firstNotNullOfOrNull { hash ->
            runCatching { parseHomeTopMixes(gql("home", hash, homeVariables, headers)) }
                .getOrNull()?.takeIf { it.isNotEmpty() }
        }
        fromHome ?: run {
            val variables = buildJsonObject {
                put("uri", TOP_MIXES_SECTION)
                put("country", java.util.Locale.getDefault().country)
                put("timeZone", timeZone)
                put("sp_t", "")
                put("sectionItemsLimit", 20)
                put("sectionItemsOffset", 0)
            }
            parseHomeSectionPlaylists(gql("homeSection", HOME_SECTION, variables, headers))
        }
    }

    /**
     * The podcast shows Spotify recommends to this account, in its order:
     * the shows of the episodes on its home page ("Episodes you might like",
     * "Episodes to try"), and any shows that page lists as shows. Spotify has
     * no shelf of recommended shows for every account, but it has these.
     */
    suspend fun recommendedShows(): List<SpotifyShow> = withContext(Dispatchers.IO) {
        val headers = authHeaders()
        val variables = buildJsonObject {
            put("homeEndUserIntegration", "INTEGRATION_WEB_PLAYER")
            put("timeZone", java.util.TimeZone.getDefault().id)
            put("sp_t", "")
            put("facet", "")
            put("sectionItemsLimit", 20)
            put("includeEpisodeContentRatingsV2", false)
        }
        HOME.firstNotNullOfOrNull { hash ->
            runCatching { parseHomeShows(gql("home", hash, variables, headers)) }
                .getOrNull()?.takeIf { it.isNotEmpty() }
        }.orEmpty()
    }

    /** The id of the library's playlist called [name], if there is one. */
    suspend fun playlistIdNamed(name: String): String? = withContext(Dispatchers.IO) {
        libraryPlaylists().firstOrNull { it.name == name }?.id
    }

    /**
     * How many songs a playlist holds, and its first [limit] entries as uid to song uri: the
     * oldest, in a playlist that is only ever added to at the end.
     */
    suspend fun playlistHead(playlistId: String, limit: Int): Pair<Int, List<Pair<String, String>>> =
        withContext(Dispatchers.IO) {
            val variables = buildJsonObject {
                put("uri", "spotify:playlist:$playlistId")
                put("offset", 0)
                put("limit", limit)
                put("enableWatchFeedEntrypoint", false)
            }
            parsePlaylistHead(gql("fetchPlaylist", PLAYLIST, variables, authHeaders()))
        }

    private suspend fun libraryPlaylists(): List<SpotifyPlaylist> {
        val headers = authHeaders()
        val collected = mutableListOf<SpotifyPlaylist>()
        var offset = 0
        while (true) {
            val (items, total, raw) = playlistPage(headers, offset)
            collected += items
            offset += raw
            if (raw == 0 || offset >= total) break
        }
        return collected
    }

    /**
     * Every track of a playlist, however long. [onPage] gets the running list
     * after each page, so a thousand-song playlist fills in as it loads
     * rather than appearing all at once at the end.
     */
    suspend fun tracks(
        playlistId: String,
        onPage: (List<SpotifyTrack>) -> Unit = {},
    ): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val headers = authHeaders()
        val collected = mutableListOf<SpotifyTrack>()
        var offset = 0
        while (true) {
            val (items, total, raw) = if (playlistId == LIKED_ID) {
                likedPage(headers, offset)
            } else {
                trackPage(headers, playlistId, offset)
            }
            collected += items
            offset += raw
            onPage(collected.toList())
            if (raw == 0 || offset >= total) break
        }
        collected
    }

    /**
     * Spotify's catalogue searched for [query], songs only: the web player's
     * search page query, signed with the same session as the library.
     */
    suspend fun searchTracks(query: String, limit: Int = 30): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val variables = buildJsonObject {
            put("searchTerm", query)
            put("offset", 0)
            put("limit", limit)
            put("numberOfTopResults", 5)
            put("includeAudiobooks", false)
            put("includeArtistHasConcertsField", false)
            put("includePreReleases", false)
            put("includeLocalConcertsField", false)
            put("includeAuthors", false)
        }
        parseSearchTracks(gql("searchDesktop", SEARCH, variables, authHeaders()))
    }

    /**
     * Spotify's catalogue searched for [query], podcast shows only: the same
     * search page query as [searchTracks], which lists shows beside songs.
     */
    suspend fun searchShows(query: String, limit: Int = 20): List<SpotifyShow> = withContext(Dispatchers.IO) {
        val variables = buildJsonObject {
            put("searchTerm", query)
            put("offset", 0)
            put("limit", limit)
            put("numberOfTopResults", 5)
            put("includeAudiobooks", false)
            put("includeArtistHasConcertsField", false)
            put("includePreReleases", false)
            put("includeLocalConcertsField", false)
            put("includeAuthors", false)
        }
        parseSearchShows(gql("searchDesktop", SEARCH, variables, authHeaders()))
    }

    private fun playlistPage(headers: Map<String, String>, offset: Int): Triple<List<SpotifyPlaylist>, Int, Int> {
        val variables = buildJsonObject {
            putJsonArray("filters") { add("Playlists") }
            put("order", null as String?)
            put("textFilter", "")
            putJsonArray("features") {
                add("LIKED_SONGS")
                add("YOUR_EPISODES_V2")
                add("PRERELEASES")
                add("EVENTS")
            }
            put("limit", 50)
            put("offset", offset)
            put("flatten", true)
            putJsonArray("expandedFolders") {}
            put("folderUri", null as String?)
            put("includeFoldersWhenFlattening", false)
        }
        return parsePlaylistPage(gql("libraryV3", LIBRARY, variables, headers))
    }

    private fun trackPage(
        headers: Map<String, String>,
        playlistId: String,
        offset: Int,
    ): Triple<List<SpotifyTrack>, Int, Int> {
        val variables = buildJsonObject {
            put("uri", "spotify:playlist:$playlistId")
            put("offset", offset)
            put("limit", 100)
            put("enableWatchFeedEntrypoint", false)
        }
        return parseTrackPage(gql("fetchPlaylist", PLAYLIST, variables, headers))
    }

    /**
     * The playlist's cover at its largest. The library list only carries a
     * thumbnail, which looks soft once it fills a playlist page's header, so
     * the page asks for the real image after it opens.
     */
    suspend fun cover(playlistId: String): String? = withContext(Dispatchers.IO) {
        if (playlistId == LIKED_ID) return@withContext LIKED_COVER
        val variables = buildJsonObject {
            put("uri", "spotify:playlist:$playlistId")
            put("offset", 0)
            put("limit", 1)
            put("enableWatchFeedEntrypoint", false)
        }
        val root = gql("fetchPlaylist", PLAYLIST, variables, authHeaders())
        largestSource(root.obj("data")?.obj("playlistV2")?.obj("images"))
    }

    private fun likedPage(headers: Map<String, String>, offset: Int): Triple<List<SpotifyTrack>, Int, Int> {
        val variables = buildJsonObject {
            put("offset", offset)
            put("limit", 100)
        }
        return parseLikedPage(gql("fetchLibraryTracks", LIKED, variables, headers))
    }

    private suspend fun authHeaders(): Map<String, String> {
        val tokens = auth?.tokens()
            ?: throw IllegalStateException("Spotify sign-in expired")
        return buildMap {
            put("Authorization", "Bearer ${tokens.bearer}")
            put("Accept", "application/json")
            put("app-platform", "WebPlayer")
            put("Origin", "https://open.spotify.com")
            put("Referer", "https://open.spotify.com/")
            put("User-Agent", USER_AGENT)
            tokens.clientToken?.let { put("Client-Token", it) }
        }
    }

    private fun gql(operation: String, hash: String, variables: JsonObject, headers: Map<String, String>): JsonObject {
        val body = buildJsonObject {
            put("variables", variables)
            put("operationName", operation)
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("version", 1)
                    put("sha256Hash", hash)
                }
            }
        }
        val request = Request.Builder()
            .url(GQL)
            .post(body.toString().toRequestBody(mediaType))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        val text = Http.client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Spotify $operation failed (${response.code})")
            }
            payload
        }
        val root = json.parseToJsonElement(text).jsonObject
        val error = root.arr("errors")?.firstOrNull()?.jsonObject?.str("message")
        if (!error.isNullOrBlank()) throw IllegalStateException(error)
        return root
    }
}

/** The playlists kept, the library's total, and how many entries the page held before filtering. */
internal fun parsePlaylistPage(root: JsonObject): Triple<List<SpotifyPlaylist>, Int, Int> {
    val library = root.obj("data")?.obj("me")?.obj("libraryV3")
        ?: throw IllegalStateException("Spotify library response was empty")
    val rawItems = library.arr("items").orEmpty()
    val items = rawItems.mapNotNull { element ->
        val wrapper = element.jsonObject.obj("item") ?: return@mapNotNull null
        val typeName = wrapper.str("__typename").orEmpty()
        if (!typeName.contains("Playlist", ignoreCase = true)) return@mapNotNull null
        val data = wrapper.obj("data") ?: return@mapNotNull null
        if (data.str("__typename") != "Playlist") return@mapNotNull null
        val uri = wrapper.str("_uri") ?: return@mapNotNull null
        val name = data.str("name").orEmpty()
        if (name.isBlank()) return@mapNotNull null
        SpotifyPlaylist(
            id = uri.substringAfterLast(":"),
            name = name,
            owner = data.obj("ownerV2")?.obj("data")?.str("name"),
            imageUrl = coverUrl(data.obj("images")),
        )
    }
    return Triple(items, library.int("totalCount") ?: rawItems.size, rawItems.size)
}

/** Spotify's DJ: a card on the mixes shelf that is not a playlist with songs to list. */
private const val SPOTIFY_DJ_URI = "spotify:playlist:37i9dQZF1EYkqdzj48dyYq"

/** The "Your top mixes" shelf of a home page response: found by its uri, or failing that its title. */
internal fun parseHomeTopMixes(root: JsonObject): List<SpotifyPlaylist> {
    val sections = root.obj("data")?.obj("home")?.obj("sectionContainer")?.obj("sections")?.arr("items").orEmpty()
        .map { it.jsonObject }
    val shelf = sections.firstOrNull { it.str("uri") == SpotifyLibrary.TOP_MIXES_SECTION }
        ?: sections.firstOrNull { section ->
            val title = section.obj("data")?.obj("title")
            val text = title?.str("transformedLabel") ?: title?.str("translatedBaseText") ?: title?.str("text")
            text?.trim().equals("Your top mixes", ignoreCase = true)
        }
    return shelf?.let(::sectionPlaylists).orEmpty()
}

/**
 * The podcast shows on a home page response, each once, in the page's order:
 * a show card is one, and an episode card gives the show it is an episode of.
 */
internal fun parseHomeShows(root: JsonObject): List<SpotifyShow> {
    val sections = root.obj("data")?.obj("home")?.obj("sectionContainer")?.obj("sections")?.arr("items").orEmpty()
    val out = LinkedHashMap<String, SpotifyShow>()
    sections.forEach { section ->
        section.jsonObject.obj("sectionItems")?.arr("items").orEmpty().forEach { element ->
            val data = element.jsonObject.obj("content")?.obj("data") ?: return@forEach
            val show = when (data.str("__typename")) {
                "Podcast" -> data
                "Episode" -> data.obj("podcastV2")?.obj("data")
                else -> null
            } ?: return@forEach
            val uri = show.str("uri")?.takeIf { it.startsWith(SPOTIFY_SHOW_PREFIX) } ?: return@forEach
            val name = show.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            out.putIfAbsent(
                uri,
                SpotifyShow(
                    id = uri.removePrefix(SPOTIFY_SHOW_PREFIX),
                    name = name,
                    publisher = show.obj("publisher")?.str("name")?.takeIf { it.isNotBlank() },
                    imageUrl = show.obj("coverArt")?.arr("sources")?.lastUrl(),
                ),
            )
        }
    }
    return out.values.toList()
}

/** What a podcast show's Spotify uri starts with. */
private const val SPOTIFY_SHOW_PREFIX = "spotify:show:"

/** The playlists of a home shelf asked for on its own. */
internal fun parseHomeSectionPlaylists(root: JsonObject): List<SpotifyPlaylist> =
    root.obj("data")?.obj("homeSections")?.arr("sections").orEmpty()
        .flatMap { sectionPlaylists(it.jsonObject) }
        .distinctBy { it.id }

/** A home shelf's playlist cards; albums, artists and the DJ are passed over. */
private fun sectionPlaylists(section: JsonObject): List<SpotifyPlaylist> =
    section.obj("sectionItems")?.arr("items").orEmpty().mapNotNull { element ->
        val data = element.jsonObject.obj("content")?.obj("data") ?: return@mapNotNull null
        if (data.str("__typename") != "Playlist") return@mapNotNull null
        val uri = data.str("uri") ?: return@mapNotNull null
        if (uri == SPOTIFY_DJ_URI || !uri.startsWith(SPOTIFY_PAGE_PREFIX)) return@mapNotNull null
        val name = data.str("name").orEmpty()
        if (name.isBlank()) return@mapNotNull null
        SpotifyPlaylist(
            id = uri.substringAfterLast(":"),
            name = name,
            owner = data.obj("ownerV2")?.obj("data")?.str("name"),
            imageUrl = coverUrl(data.obj("images")),
        )
    }

/** The tracks kept, the playlist's total, and how many entries the page held before filtering. */
internal fun parseTrackPage(root: JsonObject): Triple<List<SpotifyTrack>, Int, Int> {
    val content = root.obj("data")?.obj("playlistV2")?.obj("content")
        ?: throw IllegalStateException("Spotify playlist response was empty")
    val rawItems = content.arr("items").orEmpty()
    val items = rawItems.mapNotNull { element ->
        val data = element.jsonObject.obj("itemV2")?.obj("data") ?: return@mapNotNull null
        val title = data.str("name").orEmpty()
        if (title.isBlank()) return@mapNotNull null
        val uri = data.str("uri") ?: data.str("_uri") ?: return@mapNotNull null
        val artist = data.obj("artists")?.arr("items").orEmpty().mapNotNull { artist ->
            artist.jsonObject.obj("profile")?.str("name")?.takeIf { it.isNotBlank() }
        }.joinToString(", ")
        val album = data.obj("albumOfTrack")
        SpotifyTrack(
            id = uri.substringAfterLast(":"),
            title = title,
            artist = artist,
            album = album?.str("name"),
            durationMs = data.obj("duration")?.int("totalMilliseconds") ?: 0,
            imageUrl = album?.obj("coverArt")?.arr("sources")?.lastUrl(),
        )
    }
    return Triple(items, content.int("totalCount") ?: rawItems.size, rawItems.size)
}

/** The playlist's total, and each entry of the page as its uid (its place in the playlist) to its song's uri. */
internal fun parsePlaylistHead(root: JsonObject): Pair<Int, List<Pair<String, String>>> {
    val content = root.obj("data")?.obj("playlistV2")?.obj("content")
        ?: throw IllegalStateException("Spotify playlist response was empty")
    val rawItems = content.arr("items").orEmpty()
    val entries = rawItems.mapNotNull { element ->
        val entry = element.jsonObject
        val uid = entry.str("uid")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val data = entry.obj("itemV2")?.obj("data")
        uid to (data?.str("uri") ?: data?.str("_uri")).orEmpty()
    }
    return (content.int("totalCount") ?: rawItems.size) to entries
}

/** The song hits of a `searchDesktop` answer, in Spotify's order. */
internal fun parseSearchTracks(root: JsonObject): List<SpotifyTrack> {
    val search = root.obj("data")?.obj("searchV2")
        ?: throw IllegalStateException("Spotify search response was empty")
    return search.obj("tracksV2")?.arr("items").orEmpty().mapNotNull { element ->
        val wrapper = element.jsonObject.obj("item") ?: return@mapNotNull null
        val data = wrapper.obj("data") ?: return@mapNotNull null
        if (data.str("__typename").let { it != null && it != "Track" }) return@mapNotNull null
        val title = data.str("name").orEmpty()
        if (title.isBlank()) return@mapNotNull null
        val uri = wrapper.str("_uri") ?: data.str("uri") ?: return@mapNotNull null
        val album = data.obj("albumOfTrack")
        SpotifyTrack(
            id = uri.substringAfterLast(":"),
            title = title,
            artist = data.obj("artists")?.arr("items").orEmpty().mapNotNull { artist ->
                artist.jsonObject.obj("profile")?.str("name")?.takeIf { it.isNotBlank() }
            }.joinToString(", "),
            album = album?.str("name"),
            durationMs = data.obj("duration")?.int("totalMilliseconds") ?: 0,
            imageUrl = album?.obj("coverArt")?.arr("sources")?.lastUrl(),
        )
    }
}

/**
 * The podcast shows of a `searchDesktop` answer, in Spotify's order:
 * `searchV2.podcasts.items`, each wrapping its show under `data`.
 */
internal fun parseSearchShows(root: JsonObject): List<SpotifyShow> {
    val search = root.obj("data")?.obj("searchV2")
        ?: throw IllegalStateException("Spotify search response was empty")
    return search.obj("podcasts")?.arr("items").orEmpty().mapNotNull { element ->
        val wrapper = element.jsonObject
        val data = wrapper.obj("data") ?: wrapper.obj("item")?.obj("data") ?: return@mapNotNull null
        if (data.str("__typename").let { it != null && it != "Podcast" }) return@mapNotNull null
        val uri = (data.str("uri") ?: wrapper.str("_uri"))?.takeIf { it.startsWith(SPOTIFY_SHOW_PREFIX) }
            ?: return@mapNotNull null
        val name = data.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        SpotifyShow(
            id = uri.removePrefix(SPOTIFY_SHOW_PREFIX),
            name = name,
            publisher = data.obj("publisher")?.str("name")?.takeIf { it.isNotBlank() },
            imageUrl = data.obj("coverArt")?.arr("sources")?.lastUrl(),
        )
    }.distinctBy { it.id }
}

/** The Liked Songs page: `me.library.tracks`, each entry wrapping the track under `track`. */
internal fun parseLikedPage(root: JsonObject): Triple<List<SpotifyTrack>, Int, Int> {
    val tracks = root.obj("data")?.obj("me")?.obj("library")?.obj("tracks")
        ?: throw IllegalStateException("Spotify liked songs response was empty")
    val rawItems = tracks.arr("items").orEmpty()
    val items = rawItems.mapNotNull { element ->
        val wrapper = element.jsonObject.obj("track") ?: return@mapNotNull null
        val data = wrapper.obj("data") ?: return@mapNotNull null
        val title = data.str("name").orEmpty()
        if (title.isBlank()) return@mapNotNull null
        val uri = wrapper.str("_uri") ?: data.str("uri") ?: return@mapNotNull null
        val album = data.obj("albumOfTrack")
        SpotifyTrack(
            id = uri.substringAfterLast(":"),
            title = title,
            artist = data.obj("artists")?.arr("items").orEmpty().mapNotNull { artist ->
                artist.jsonObject.obj("profile")?.str("name")?.takeIf { it.isNotBlank() }
            }.joinToString(", "),
            album = album?.str("name"),
            durationMs = (data.obj("trackDuration") ?: data.obj("duration"))?.int("totalMilliseconds") ?: 0,
            imageUrl = album?.obj("coverArt")?.arr("sources")?.lastUrl(),
        )
    }
    return Triple(items, tracks.int("totalCount") ?: rawItems.size, rawItems.size)
}

/**
 * The widest source of an image's first entry. Spotify lists a playlist's
 * sizes in no fixed order — a 60px thumbnail can come last — so the list is
 * read by width rather than by position.
 */
private fun largestSource(images: JsonObject?): String? =
    images?.arr("items")?.firstOrNull()?.jsonObject?.arr("sources")
        ?.mapNotNull { source ->
            val url = source.jsonObject.str("url") ?: return@mapNotNull null
            url to (source.jsonObject.int("width") ?: 0)
        }
        ?.maxByOrNull { it.second }
        ?.first

private fun coverUrl(images: JsonObject?): String? = largestSource(images)

private fun JsonArray.lastUrl(): String? =
    mapNotNull { it.jsonObject.str("url") }.lastOrNull()

private fun JsonObject.obj(key: String): JsonObject? =
    this[key]?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonObject }.getOrNull() }

private fun JsonObject.arr(key: String): JsonArray? =
    this[key]?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonArray }.getOrNull() }

private fun JsonObject.str(key: String): String? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull

private fun JsonObject.int(key: String): Int? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull
