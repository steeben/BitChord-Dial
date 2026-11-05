package com.music.bitchord.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import com.music.bitchord.R
import com.music.bitchord.data.CanvasAudioFixes
import com.music.bitchord.data.VideoCanvases
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.MusicSource
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.playback.OriginalVersion
import com.music.bitchord.playback.SOURCE_YOUTUBE
import com.music.bitchord.playback.SourcePins
import com.music.bitchord.playback.StreamChoice
import com.music.bitchord.playback.swapToVersion
import com.music.bitchord.playback.toSong
import com.music.bitchord.playback.useSource
import com.music.bitchord.ui.theme.LocalNowPlayingTint
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The searches behind the player's "Song fixes" rows: pick the
 * right music video for the Canvas, the right song for the audio, or which
 * addon plays the track. Kept out of MainActivity, which is at the JVM's
 * limits — it only places [SongFixHost] and adds [rememberSongFixChoices].
 */
object SongFixRequests {
    enum class Kind { VIDEO, SONG, SOURCE }

    data class Request(val kind: Kind, val song: Song)

    val request = MutableStateFlow<Request?>(null)

    fun open(kind: Kind, song: Song) {
        request.value = Request(kind, song)
    }
}

/**
 * The Controls brightness rows on the Song fixes page: light or dark lyrics (and the
 * player's text with them) for the playing [song], one pick for while its
 * album art is behind them and one for while a Canvas plays. Each tap steps
 * Auto, Light, Dark and round again; Auto leaves it to the sleeve.
 */
@Composable
fun rememberLyricsTextChoices(song: Song): List<SongMenuChoice> {
    val context = LocalContext.current
    val fixes by CanvasAudioFixes.state.collectAsStateWithLifecycle()
    fun row(overCanvas: Boolean): SongMenuChoice {
        val ink = CanvasAudioFixes.lyricInkOf(fixes, song.videoId, overCanvas)
        return SongMenuChoice(
            icon = if (overCanvas) Icons.Rounded.Movie else Icons.Rounded.Image,
            label = context.getString(if (overCanvas) R.string.lyrics_text_over_canvas else R.string.lyrics_text_over_art),
            value = context.getString(
                when (ink) {
                    null -> R.string.lyrics_text_auto
                    CanvasAudioFixes.LyricInk.LIGHT -> R.string.lyrics_text_light
                    CanvasAudioFixes.LyricInk.DARK -> R.string.lyrics_text_dark
                },
            ),
        ) {
            val next = when (ink) {
                null -> CanvasAudioFixes.LyricInk.LIGHT
                CanvasAudioFixes.LyricInk.LIGHT -> CanvasAudioFixes.LyricInk.DARK
                CanvasAudioFixes.LyricInk.DARK -> null
            }
            CanvasAudioFixes.setLyricInk(song.videoId, overCanvas, next)
        }
    }
    return listOf(row(overCanvas = false), row(overCanvas = true))
}

/**
 * The fix rows that open a search or a choice, for the playing [song]. Added
 * under the Canvas/audio fixes in the player's ⋯ menu.
 */
@Composable
fun rememberSongFixChoices(song: Song, onClose: () -> Unit): List<SongMenuChoice> {
    val context = LocalContext.current
    val fixes by CanvasAudioFixes.state.collectAsStateWithLifecycle()
    // Offered only when the album has an animated cover to use.
    val albumCanvas by produceState(false, song.videoId, song.albumName) {
        value = song.albumName != null && runCatching { CanvasRepository.albumCanvasFor(song) }.getOrNull() != null
    }
    val youTubeTrack = song.localUri == null && SourceRegistry.parseTrackKey(song.videoId) == null
    val otherSources = remember(song.videoId) {
        SourceRegistry.activeForPlayback().any { it.kind != SourceKind.YOUTUBE }
    }
    val close by rememberUpdatedState(onClose)
    return buildList {
        add(
            SongMenuChoice(
                icon = Icons.Rounded.VideoLibrary,
                label = context.getString(R.string.song_fix_find_video),
                description = context.getString(R.string.song_fix_find_video_detail),
            ) {
                close()
                SongFixRequests.open(SongFixRequests.Kind.VIDEO, song)
            },
        )
        if (youTubeTrack) {
            add(
                SongMenuChoice(
                    icon = Icons.Rounded.LibraryMusic,
                    label = context.getString(R.string.song_fix_find_song),
                    description = context.getString(R.string.song_fix_find_song_detail),
                ) {
                    close()
                    SongFixRequests.open(SongFixRequests.Kind.SONG, song)
                },
            )
        }
        if (youTubeTrack && otherSources && !song.isVideo) {
            add(
                SongMenuChoice(
                    icon = Icons.Rounded.SwapHoriz,
                    label = context.getString(R.string.song_fix_pick_source),
                    description = context.getString(R.string.song_fix_pick_source_detail),
                ) {
                    close()
                    SongFixRequests.open(SongFixRequests.Kind.SOURCE, song)
                },
            )
        }
        if (albumCanvas && song.videoId !in fixes.albumArtCanvas) {
            add(
                SongMenuChoice(
                    icon = Icons.Rounded.AutoAwesome,
                    label = context.getString(R.string.song_fix_album_art),
                    description = context.getString(R.string.song_fix_album_art_detail),
                ) {
                    close()
                    CanvasAudioFixes.useAlbumArt(song.videoId)
                },
            )
        }
    }
}

/**
 * Shows whichever fix search was asked for, and plays a song the listener
 * picked earlier wherever its original turns up again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongFixHost(
    controller: MediaController?,
    currentSong: Song?,
    onNotice: (String) -> Unit,
) {
    val context = LocalContext.current
    // A song (not a video: those go through the audio switch, which reads the
    // same picks) the listener replaced by hand plays its pick from now on.
    LaunchedEffect(currentSong?.videoId) {
        val song = currentSong ?: return@LaunchedEffect
        if (song.isVideo) return@LaunchedEffect
        val pick = CanvasAudioFixes.songPick(song.videoId) ?: return@LaunchedEffect
        val c = controller ?: return@LaunchedEffect
        if (c.currentMediaItem?.mediaId != song.videoId) return@LaunchedEffect
        c.swapToVersion(pick.withQueueOf(song), fromStart = c.currentPosition < FRESH_START_MS)
    }

    val request by SongFixRequests.request.collectAsStateWithLifecycle()
    val open = request ?: return
    val dismiss = { SongFixRequests.request.value = null }
    SlideDownBottomSheet(
        onDismissRequest = dismiss,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        when (open.kind) {
            SongFixRequests.Kind.VIDEO -> SearchPicker(
                title = stringResource(R.string.song_fix_find_video),
                about = stringResource(R.string.song_fix_find_video_about),
                song = open.song,
                current = CanvasAudioFixes.pairedVideo(open.song.videoId) ?: VideoCanvases.pictureFor(open.song.videoId),
                search = YtMusicRepository::searchVideos,
            ) { video ->
                CanvasAudioFixes.pairVideo(open.song.videoId, video.videoId)
                dismiss()
                onNotice(context.getString(R.string.song_fix_video_paired))
            }
            SongFixRequests.Kind.SONG -> SearchPicker(
                title = stringResource(R.string.song_fix_find_song),
                about = stringResource(R.string.song_fix_find_song_about),
                song = open.song,
                current = open.song.videoId,
                search = YtMusicRepository::searchSongs,
            ) { picked ->
                dismiss()
                if (picked.videoId == open.song.videoId) return@SearchPicker
                // A song standing in for a music video: the pick answers for
                // the video, and keeps that video's picture as its Canvas.
                val source = VideoCanvases.sourceOf(open.song.videoId)
                    ?: open.song.videoId.takeIf { open.song.isVideo }
                CanvasAudioFixes.pickSong(source ?: open.song.videoId, picked)
                if (source != null) VideoCanvases.link(picked.videoId, source)
                val c = controller
                if (c != null && c.currentMediaItem?.mediaId == open.song.videoId) {
                    val playing = c.currentMediaItem?.toSong() ?: open.song
                    c.swapToVersion(
                        picked.copy(isVideoOrigin = source != null).withQueueOf(playing),
                        fromStart = c.currentPosition < FRESH_START_MS,
                    )
                }
                onNotice(context.getString(R.string.song_fix_song_picked))
            }
            SongFixRequests.Kind.SOURCE -> SourcePicker(song = open.song) { configId ->
                dismiss()
                controller?.takeIf { it.currentMediaItem?.mediaId == open.song.videoId }?.useSource(configId)
                onNotice(context.getString(R.string.song_fix_source_picked))
            }
        }
    }
}

/** The queue bookkeeping of [from], carried onto the song replacing it. */
private fun Song.withQueueOf(from: Song): Song = copy(
    queueTier = from.queueTier,
    queueEntryId = from.queueEntryId,
    radioName = from.radioName,
    playbackSource = from.playbackSource,
    playbackSourceType = from.playbackSourceType,
    playbackSourceId = from.playbackSourceId,
)

/** A swap this close to the start plays the pick from its own beginning. */
private const val FRESH_START_MS = 3_000L

@Composable
private fun SearchPicker(
    title: String,
    about: String,
    song: Song,
    current: String?,
    search: suspend (String) -> Result<List<Song>>,
    onPick: (Song) -> Unit,
) {
    val accent = LocalNowPlayingTint.current ?: MaterialTheme.colorScheme.primary
    var query by remember(song.videoId) { mutableStateOf("${song.title} ${song.artist}".trim()) }
    var asked by remember(song.videoId) { mutableStateOf(query) }
    // Null while the search runs.
    var found by remember(song.videoId) { mutableStateOf<Result<List<Song>>?>(null) }
    LaunchedEffect(asked) {
        found = null
        found = runCatching { search(asked).getOrThrow() }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Header(title, about)
        PillTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.song_fix_search_hint),
            modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER).padding(bottom = 8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) asked = query.trim() }),
        )
        val result = found
        when {
            result == null -> Loading(accent)
            result.isFailure || result.getOrNull().isNullOrEmpty() -> Message(
                stringResource(if (result.isFailure) R.string.song_fix_search_failed else R.string.song_fix_search_none),
            )
            else -> Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                result.getOrThrow().forEach { row ->
                    SongRow(
                        song = row,
                        onClick = { onPick(row) },
                        isCurrent = row.videoId == current,
                        activeTint = accent,
                        showMore = false,
                        rowBackground = Color.Transparent,
                    )
                }
            }
        }
    }
}

/**
 * Every enabled source other than YouTube, each saying whether it has the
 * song, and YouTube's own upload last. A source without a match can't be
 * picked.
 */
@Composable
private fun SourcePicker(song: Song, onPick: (String) -> Unit) {
    val accent = LocalNowPlayingTint.current ?: MaterialTheme.colorScheme.primary
    val pins by SourcePins.pins.collectAsStateWithLifecycle()
    val revertedIds by OriginalVersion.pinned.collectAsStateWithLifecycle()
    val serving = remember(song.videoId) { StreamChoice.of(song.videoId)?.sourceConfigId }
    val matches by produceState<List<Pair<MusicSource, SourceStream?>>?>(null, song.videoId) {
        value = SourceResolver.matchesAcross(
            TrackMatcher.Target(
                title = song.title,
                artist = song.artist,
                durationSec = TrackMatcher.secondsOf(song.durationText),
                album = song.albumName,
                isExplicit = song.isExplicit,
                isVideo = false,
            ),
        ).also { found ->
            // Handed to the service, so a pick plays without searching again.
            found.forEach { (source, stream) -> if (stream != null) SourcePins.offer(song.videoId, source.configId, stream) }
        }
    }
    val chosen = pins[song.videoId] ?: SOURCE_YOUTUBE.takeIf { song.videoId in revertedIds } ?: serving
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Header(stringResource(R.string.song_fix_pick_source), stringResource(R.string.song_fix_pick_source_about))
        val list = matches
        if (list == null) {
            Loading(accent)
            Message(stringResource(R.string.song_fix_source_searching))
        } else {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                list.forEach { (source, stream) ->
                    SourceRow(
                        name = source.displayName,
                        detail = stream?.format?.summary?.takeIf { it.isNotBlank() }
                            ?.let { stringResource(R.string.song_fix_source_match, it) }
                            ?: stringResource(if (stream != null) R.string.song_fix_source_match_plain else R.string.song_fix_source_none),
                        enabled = stream != null,
                        current = chosen == source.configId,
                        accent = accent,
                    ) { onPick(source.configId) }
                }
                SourceRow(
                    name = stringResource(R.string.song_fix_source_youtube),
                    detail = stringResource(R.string.song_fix_source_youtube_detail),
                    enabled = true,
                    current = chosen == SOURCE_YOUTUBE,
                    accent = accent,
                ) { onPick(SOURCE_YOUTUBE) }
            }
        }
    }
}

@Composable
private fun SourceRow(
    name: String,
    detail: String,
    enabled: Boolean,
    current: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = PAGE_GUTTER, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = when {
                    current -> accent
                    enabled -> MaterialTheme.colorScheme.onBackground
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.6f),
            )
        }
        if (current) {
            Box(Modifier.width(8.dp))
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Header(title: String, about: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = PAGE_GUTTER),
    )
    Text(
        text = about,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 4.dp, bottom = 12.dp),
    )
}

@Composable
private fun Loading(accent: Color) {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
    )
}
