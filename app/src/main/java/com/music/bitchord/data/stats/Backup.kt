package com.music.bitchord.data.stats

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.SearchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The whole of what this app knows about you, as one JSON file you own.
 *
 * Two errands, and they are the same errand: moving to a new phone, and being
 * able to see what is being kept. Everything Replay counts lives only on this
 * device, which is the point of it — and the flip side of that is that a factory
 * reset takes it with no way back, so there has to be a way out.
 *
 * ## What is in it
 *
 * Every preference except credentials (see [AppSettings.exportPrefs]), which
 * covers the speed dial and artist dial pins; every month of listening; the
 * per-song and playlist stores in [SONG_DATA_FILES]; and the covers picked for
 * playlists, since those are images nothing else can fetch again. Not the audio
 * cache, the downloads or other artwork: those are megabytes that re-fetch
 * themselves, and a backup that is mostly cache is one nobody keeps.
 *
 * ## Typed values rather than a JSON object
 *
 * Preferences come off Android as `Map<String, Any?>` and go back the same way,
 * where the *type* decides which `put` is called. Written as plain JSON, `0.5`
 * comes back a Double and lands in a Float preference as a class-cast crash the
 * first time it is read — weeks later, in a settings screen, with nothing
 * pointing at the import. So each value carries its type and is parsed back into
 * exactly the type it left as, and anything unrecognised is skipped rather than
 * guessed at.
 */
object Backup {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** A suggested filename, dated so successive exports don't collide. */
    fun suggestedName(): String =
        "bitchord-backup-${DateTimeFormatter.ofPattern("yyyy-MM-dd").format(
            Instant.now().atZone(ZoneId.systemDefault()),
        )}.json"

    /**
     * Writes a backup to [target], a document the user picked.
     *
     * Through the content resolver rather than a [java.io.File] because the
     * destination is wherever they chose — Drive, a USB stick, a folder this app
     * has no path to and no permission for. The picker grants access to that one
     * document and nothing else, which is the correct amount.
     */
    suspend fun exportTo(context: Context, target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val (text, months) = encode(context)
            context.contentResolver.openOutputStream(target, "wt")
                ?.use { it.write(text.toByteArray()) }
                ?: error("Couldn't open that file for writing")
            months
        }
    }

    /**
     * Writes a new, dated backup into [folder], a document tree the user picked
     * for automatic backups, and returns the name it was given.
     */
    suspend fun exportToFolder(context: Context, folder: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val (text, _) = encode(context)
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                folder,
                DocumentsContract.getTreeDocumentId(folder),
            )
            val name = autoName()
            val target = DocumentsContract.createDocument(resolver, parent, "application/json", name)
                ?: error("Couldn't create a file in that folder")
            resolver.openOutputStream(target, "wt")
                ?.use { it.write(text.toByteArray()) }
                ?: error("Couldn't open that file for writing")
            name
        }
    }

    /** The JSON of a backup, and how many months of listening it holds. */
    private suspend fun encode(context: Context): Pair<String, Int> {
        val file = buildFile(context)
        return json.encodeToString(BackupFile.serializer(), file) to file.listening.size
    }

    private suspend fun buildFile(context: Context): BackupFile {
        val buckets = ListeningStats.exportAll()
        return BackupFile(
            versionName = BuildConfig.VERSION_NAME,
            exportedAt = Instant.now().toString(),
            settings = AppSettings.exportPrefs().toPrefValues(),
            songData = SONG_DATA_FILES.associateWith { name ->
                context.getSharedPreferences(name, Context.MODE_PRIVATE).all
                    .filterKeys { it !in SKIPPED_KEYS[name].orEmpty() }
                    .toPrefValues()
            }.filterValues { it.isNotEmpty() },
            playlistCovers = com.music.bitchord.data.CustomCovers.exportImages(),
            listening = buckets,
        )
    }

    private fun Map<String, Any?>.toPrefValues(): Map<String, PrefValue> =
        mapNotNull { (key, value) -> PrefValue.of(value)?.let { key to it } }.toMap()

    /** Automatic backups carry the time too, so a manual run the same day gets its own file. */
    private fun autoName(): String =
        "$AUTO_PREFIX${DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").format(
            Instant.now().atZone(ZoneId.systemDefault()),
        )}.json"

    /** Names start with this so the folder's old automatic backups can be told from anything else in it. */
    const val AUTO_PREFIX = "bitchord-auto-backup-"

    /**
     * Choices and records kept outside the main settings file: metadata fixes,
     * canvas and video fixes, source and original-version pins, imported
     * playlists, the Local Music folder's own playlists and likes, and what this
     * device has played (the speed dial's History, which albums and playlists
     * tracks were played from, and the "Last played" sort). They travel with a
     * backup. Album art picks and local file links are left out: they point at
     * images and files on this device, which a backup does not carry.
     */
    private val SONG_DATA_FILES = listOf(
        "song_metadata_fixes",
        "canvas_audio_fixes",
        "bitchord_source_pins",
        "bitchord_original_versions",
        "bitchord_local_playlists",
        "bitchord_offline_playlists",
        "bitchord_device_plays",
        "bitchord_played_collections",
        "bitchord_last_played",
    )

    /**
     * Keys in a [SONG_DATA_FILES] file that belong to something else and stay
     * out of a backup: the play queue saved for after process death shares the
     * "Last played" file, and restoring a queue from another day would be odd.
     * An import leaves them as they are.
     */
    private val SKIPPED_KEYS = mapOf(
        "bitchord_last_played" to setOf("queue", "index", "position"),
    )

    /** Replaces each per-song file the backup has, and has its owner read it again. */
    private fun restoreSongData(context: Context, songData: Map<String, Map<String, PrefValue>>) {
        songData.filterKeys { it in SONG_DATA_FILES }.forEach { (name, values) ->
            val skipped = SKIPPED_KEYS[name].orEmpty()
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            prefs.edit().apply {
                prefs.all.keys.filterNot { it in skipped }.forEach { remove(it) }
                values.filterKeys { it !in skipped }.forEach { (key, pref) ->
                    when (val value = pref.decoded()) {
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                        else -> Unit
                    }
                }
            }.commit()
        }
        if (songData.isEmpty()) return
        com.music.bitchord.data.SongMetadataFixStore.install(context)
        com.music.bitchord.data.CanvasAudioFixStore.install(context)
        com.music.bitchord.playback.SourcePins.init(context)
        com.music.bitchord.playback.OriginalVersion.init(context)
        com.music.bitchord.data.spotify.LocalPlaylistStore.init(context)
        com.music.bitchord.data.OfflinePlaylists.reload()
        com.music.bitchord.data.settings.DevicePlays.init(context)
        com.music.bitchord.data.settings.PlayedCollections.init(context)
        com.music.bitchord.data.settings.LastPlayed.init(context)
    }

    /**
     * Reads [source] and replaces this device's settings and listening with it.
     *
     * Validated before anything is written: a file that isn't one of ours, or is
     * from a schema this build can't read, is refused whole. A half-applied
     * import is worse than a refused one — it leaves settings from two devices
     * mixed together with nothing to say which came from where.
     */
    suspend fun importFrom(context: Context, source: Uri): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(source)
                ?.use { it.readBytes().decodeToString() }
                ?: error("Couldn't open that file")
            val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
                .getOrElse { error("That doesn't look like a Dial backup") }
            require(file.app == APP_TAG) { "That backup is from another app" }
            require(file.version <= SCHEMA_VERSION) {
                "That backup was written by a newer version of Dial"
            }

            ListeningStats.importAll(file.listening)
            AppSettings.importPrefs(file.settings.mapValues { it.value.decoded() })
            restoreSongData(context, file.songData)
            // Absent from backups written before covers were carried, and
            // those must not wipe the covers this device already has.
            file.playlistCovers?.let { com.music.bitchord.data.CustomCovers.restoreImages(context, it) }
            // Shares AppSettings' preference file, so it has already been
            // overwritten by the line above — it just doesn't know yet.
            SearchHistory.reload()
            Summary(
                months = file.listening.size,
                settings = file.settings.size,
                from = file.versionName,
                at = file.exportedAt,
            )
        }
    }

    /**
     * Everything the old app (com.steeben.bitchord) hands Dial when it moves:
     * a whole [BackupFile], plus what a backup deliberately leaves out because
     * a backup file can end up anywhere. This one only ever travels between two
     * apps signed with the same key on the same phone (see
     * [com.music.bitchord.data.migration.HandoverProvider]), so it also carries
     * the sign-ins and the download records.
     */
    @Serializable
    data class Handover(
        val backup: BackupFile,
        /** [AppSettings.exportHandoverExtras]. */
        val settingsExtras: Map<String, PrefValue> = emptyMap(),
        /** The encrypted stores' decrypted contents, by store name; see [HANDOVER_STORES]. */
        val stores: Map<String, Map<String, PrefValue>> = emptyMap(),
    )

    /** Encrypted stores and their plain fallbacks, as [com.music.bitchord.auth.EncryptedPrefs] opens them. */
    private val HANDOVER_STORES = mapOf(
        "bitchord_auth" to "bitchord_auth_plain",
        "bitchord_sources" to "bitchord_sources_plain",
    )

    /** The old app's side of a move: its whole state as JSON. */
    suspend fun encodeHandover(context: Context): String = withContext(Dispatchers.IO) {
        val handover = Handover(
            backup = buildFile(context),
            settingsExtras = AppSettings.exportHandoverExtras().toPrefValues(),
            stores = HANDOVER_STORES.mapValues { (name, plain) ->
                com.music.bitchord.auth.EncryptedPrefs.open(context, name, plain).all.toPrefValues()
            }.filterValues { it.isNotEmpty() },
        )
        json.encodeToString(Handover.serializer(), handover)
    }

    /**
     * Dial's side of a move: replaces this install's state with the old app's.
     * The caller restarts the process afterwards, since sign-ins and downloads
     * are read once at start and nothing re-reads them.
     */
    suspend fun importHandover(context: Context, text: String): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            val handover = json.decodeFromString(Handover.serializer(), text)
            val file = handover.backup
            require(file.app == APP_TAG) { "That isn't data from the old app" }
            require(file.version <= SCHEMA_VERSION) { "The old app's data is from a newer version" }

            ListeningStats.importAll(file.listening)
            AppSettings.importPrefs(file.settings.mapValues { it.value.decoded() })
            AppSettings.importHandoverExtras(handover.settingsExtras.mapValues { it.value.decoded() })
            restoreSongData(context, file.songData)
            file.playlistCovers?.let { com.music.bitchord.data.CustomCovers.restoreImages(context, it) }
            SearchHistory.reload()
            handover.stores.filterKeys { it in HANDOVER_STORES }.forEach { (name, values) ->
                val prefs = com.music.bitchord.auth.EncryptedPrefs.open(context, name, HANDOVER_STORES.getValue(name))
                prefs.edit().apply {
                    values.forEach { (key, pref) ->
                        when (val value = pref.decoded()) {
                            is Boolean -> putBoolean(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is String -> putString(key, value)
                            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                            else -> Unit
                        }
                    }
                }.commit()
            }
            Summary(
                months = file.listening.size,
                settings = file.settings.size,
                from = file.versionName,
                at = file.exportedAt,
            )
        }
    }

    /** What an import turned out to contain, for the line shown afterwards. */
    data class Summary(
        val months: Int,
        val settings: Int,
        val from: String,
        val at: String,
    )

    private const val APP_TAG = "bitchord"

    /**
     * Bump when the shape below stops being readable by an older build. A file
     * from a *newer* schema is refused rather than partially read; one from an
     * older schema is read as-is, since every field has a default.
     */
    private const val SCHEMA_VERSION = 1

    @Serializable
    data class BackupFile(
        val app: String = APP_TAG,
        val version: Int = SCHEMA_VERSION,
        /** Which build wrote it — for the reader, not for any logic here. */
        val versionName: String = "",
        val exportedAt: String = "",
        val settings: Map<String, PrefValue> = emptyMap(),
        /** Per-song preference files by name; see [SONG_DATA_FILES]. */
        val songData: Map<String, Map<String, PrefValue>> = emptyMap(),
        /** Playlist covers' images, base64, by playlist key; null in older backups. */
        val playlistCovers: Map<String, String>? = null,
        val listening: List<StoredBucket> = emptyList(),
    )

    /** One preference, with the type it has to be restored as. */
    @Serializable
    data class PrefValue(
        val type: String,
        val value: String? = null,
        val values: List<String> = emptyList(),
    ) {
        fun decoded(): Any? = when (type) {
            BOOLEAN -> value?.toBooleanStrictOrNull()
            INT -> value?.toIntOrNull()
            LONG -> value?.toLongOrNull()
            FLOAT -> value?.toFloatOrNull()
            STRING -> value
            STRING_SET -> values.toSet()
            else -> null
        }

        companion object {
            /**
             * Numbers go out as strings on purpose. JSON has one number type and
             * every parser picks its own Kotlin type back off it, which is
             * exactly the ambiguity this whole class exists to remove.
             */
            fun of(value: Any?): PrefValue? = when (value) {
                is Boolean -> PrefValue(BOOLEAN, value.toString())
                is Int -> PrefValue(INT, value.toString())
                is Long -> PrefValue(LONG, value.toString())
                is Float -> PrefValue(FLOAT, value.toString())
                is String -> PrefValue(STRING, value)
                is Set<*> -> PrefValue(STRING_SET, values = value.filterIsInstance<String>())
                else -> null
            }

            private const val BOOLEAN = "bool"
            private const val INT = "int"
            private const val LONG = "long"
            private const val FLOAT = "float"
            private const val STRING = "string"
            private const val STRING_SET = "stringSet"
        }
    }
}
