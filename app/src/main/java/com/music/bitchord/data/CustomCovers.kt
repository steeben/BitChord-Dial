package com.music.bitchord.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Covers the user picked for playlists, kept on this device only.
 *
 * YouTube Music's own custom-thumbnail upload isn't part of the API the app
 * talks to, so a picked image never reaches the account: it is cropped square,
 * saved under the app's files and drawn in place of the playlist's artwork
 * wherever the app shows it. The playlist's real thumbnail is left alone in
 * every record (speed dial pins, downloads), so removing the cover — or
 * clearing app data — is the whole undo.
 */
object CustomCovers {
    private const val PREF_NAME = "bitchord_custom_covers"
    private const val KEY_COVERS = "covers_json"
    private const val DIR = "playlist_covers"
    private const val MAX_PX = 1200

    /**
     * What a generated cover's file name starts with, so the playlist's page
     * can tell it from a picture the owner chose: only the latter goes in
     * front of the page's generated image.
     */
    const val GENERATED_MARK = "cover_gen_"

    private var prefs: SharedPreferences? = null
    private var dir: File? = null

    /** Playlist key (see [keyOf]) to a `file://` url of its cover. */
    private val _covers = MutableStateFlow<Map<String, String>>(emptyMap())
    val covers = _covers.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs = p
        dir = File(context.filesDir, DIR)
        val raw = p.getString(KEY_COVERS, null) ?: return
        runCatching {
            val obj = JSONObject(raw)
            _covers.value = obj.keys().asSequence()
                .associateWith { obj.getString(it) }
                .filterValues { File(Uri.parse(it).path.orEmpty()).exists() }
        }
    }

    /**
     * The same playlist reaches the app as `VLPL…` from a page or a library
     * card and as `PL…` from some feed cards, so both spell one key.
     */
    fun keyOf(browseId: String): String = browseId.removePrefix("VL")

    fun coverFor(browseId: String?, covers: Map<String, String> = _covers.value): String? =
        browseId?.let { covers[keyOf(it)] }

    /**
     * Crops [source] square, scales it down to [MAX_PX] and saves it as
     * [browseId]'s cover. Each save is a new file name, so the image loader's
     * cache never hands back the cover it replaced.
     */
    suspend fun set(context: Context, browseId: String, source: Uri): Boolean = withContext(Dispatchers.IO) {
        val folder = dir ?: File(context.filesDir, DIR).also { dir = it }
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val shortSide = minOf(bounds.outWidth, bounds.outHeight)
            if (shortSide <= 0) return@runCatching false
            var sample = 1
            while (shortSide / (sample * 2) >= MAX_PX) sample *= 2
            val decoded = context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching false
            // A camera photo is stored sideways with a note saying so, which
            // the decoder ignores; turn it upright before cropping.
            val degrees = runCatching {
                context.contentResolver.openInputStream(source)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                }
            }.getOrNull() ?: 0f
            val side = minOf(decoded.width, decoded.height)
            val cropped = Bitmap.createBitmap(
                decoded,
                (decoded.width - side) / 2,
                (decoded.height - side) / 2,
                side,
                side,
                Matrix().apply { postRotate(degrees) },
                true,
            )
            val scaled = if (side > MAX_PX) Bitmap.createScaledBitmap(cropped, MAX_PX, MAX_PX, true) else cropped
            folder.mkdirs()
            val key = keyOf(browseId)
            val file = File(folder, "cover_${key.hashCode().toUInt()}_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (scaled !== decoded) decoded.recycle()
            if (cropped !== scaled && cropped !== decoded) cropped.recycle()
            scaled.recycle()
            val previous = _covers.value[key]
            update(_covers.value + (key to Uri.fromFile(file).toString()))
            previous?.let { deleteFile(it) }
            true
        }.getOrDefault(false)
    }

    /** Saves [bitmap] (already square) as [browseId]'s cover: a generated one. */
    suspend fun setBitmap(context: Context, browseId: String, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val folder = dir ?: File(context.filesDir, DIR).also { dir = it }
        runCatching {
            folder.mkdirs()
            val key = keyOf(browseId)
            val file = File(folder, "$GENERATED_MARK${key.hashCode().toUInt()}_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            val previous = _covers.value[key]
            update(_covers.value + (key to Uri.fromFile(file).toString()))
            previous?.let { deleteFile(it) }
            true
        }.getOrDefault(false)
    }

    fun remove(browseId: String) {
        val key = keyOf(browseId)
        val previous = _covers.value[key] ?: return
        update(_covers.value - key)
        deleteFile(previous)
    }

    /** Each cover's image, base64, by playlist key: what a backup carries of them. */
    fun exportImages(): Map<String, String> = _covers.value.mapNotNull { (key, url) ->
        runCatching { File(Uri.parse(url).path.orEmpty()).readBytes() }.getOrNull()
            ?.let { key to Base64.encodeToString(it, Base64.NO_WRAP) }
    }.toMap()

    /**
     * Replaces every cover with [images] from a backup, written out as new
     * files so the image loader can't hand back one it cached from before.
     */
    fun restoreImages(context: Context, images: Map<String, String>) {
        val folder = dir ?: File(context.filesDir, DIR).also { dir = it }
        folder.mkdirs()
        val now = System.currentTimeMillis()
        val restored = images.mapNotNull { (key, data) ->
            runCatching {
                val file = File(folder, "cover_${key.hashCode().toUInt()}_$now.jpg")
                file.writeBytes(Base64.decode(data, Base64.NO_WRAP))
                key to Uri.fromFile(file).toString()
            }.getOrNull()
        }.toMap()
        val previous = _covers.value.values.toSet() - restored.values.toSet()
        if (prefs == null) prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        update(restored)
        previous.forEach { deleteFile(it) }
    }

    private fun deleteFile(url: String) {
        runCatching { Uri.parse(url).path?.let { File(it).delete() } }
    }

    private fun update(map: Map<String, String>) {
        _covers.value = map
        prefs?.edit()?.putString(KEY_COVERS, JSONObject(map).toString())?.apply()
    }
}

fun ShelfItem.withCustomCover(covers: Map<String, String>): ShelfItem =
    CustomCovers.coverFor(browseId, covers)?.let { copy(thumbnailUrl = it) } ?: this

fun List<ShelfItem>.withCustomCovers(covers: Map<String, String>): List<ShelfItem> =
    if (covers.isEmpty()) this else map { it.withCustomCover(covers) }

fun HomeShelf.withCustomCovers(covers: Map<String, String>): HomeShelf =
    if (covers.isEmpty()) this else copy(items = items.withCustomCovers(covers))

fun DetailPage.withCustomCover(covers: Map<String, String>): DetailPage =
    CustomCovers.coverFor(browseId, covers)?.let { copy(thumbnailUrl = it) } ?: this

@JvmName("homeWithCustomCovers")
fun UiState<List<HomeShelf>>.withCustomCovers(covers: Map<String, String>): UiState<List<HomeShelf>> =
    if (covers.isEmpty() || this !is UiState.Success) this else UiState.Success(data.map { it.withCustomCovers(covers) })

@JvmName("libraryWithCustomCovers")
fun UiState<LibraryPage>.withCustomCovers(covers: Map<String, String>): UiState<LibraryPage> =
    if (covers.isEmpty() || this !is UiState.Success) {
        this
    } else {
        UiState.Success(data.copy(shelves = data.shelves.map { it.withCustomCovers(covers) }))
    }
