package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.DetailChoice
import com.music.bitchord.data.SongDetailChoices
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.theme.AccentRed
import com.music.bitchord.ui.theme.LocalNowPlayingTint

private enum class DetailField { TITLE, ARTIST, ALBUM }

/** The cell being typed in: a detail, and the found side of it or the file's. */
private data class EditingCell(val field: DetailField, val foundSide: Boolean)

private val CellShape = RoundedCornerShape(16.dp)
private val CELL_GAP = 10.dp

/**
 * What "Find this song's details" shows once a song is picked: the file's
 * details on the left, the found ones on the right, and for each the one to
 * keep. A second tap on the kept one turns it into a text box, for a fix
 * neither side has right. Opens with every found detail kept.
 *
 * [fromFile] is false for an upload, which names its column differently, and
 * [canSaveToFile] puts the Settings switch for writing into the file here too.
 */
@Composable
internal fun SongDetailCompare(
    initial: SongDetailChoices,
    fromFile: Boolean,
    canSaveToFile: Boolean,
    onBack: () -> Unit,
    onUse: (SongDetailChoices) -> Unit,
) {
    val accent = LocalNowPlayingTint.current ?: MaterialTheme.colorScheme.primary
    val focus = LocalFocusManager.current
    var choices by remember(initial) { mutableStateOf(initial) }
    var editing by remember(initial) { mutableStateOf<EditingCell?>(null) }
    var draft by remember(initial) { mutableStateOf(TextFieldValue("")) }

    fun choiceOf(field: DetailField) = when (field) {
        DetailField.TITLE -> choices.title
        DetailField.ARTIST -> choices.artist
        DetailField.ALBUM -> choices.album
    }

    fun set(field: DetailField, choice: DetailChoice) {
        choices = when (field) {
            DetailField.TITLE -> choices.copy(title = choice)
            DetailField.ARTIST -> choices.copy(artist = choice)
            DetailField.ALBUM -> choices.copy(album = choice)
        }
    }

    // Whatever is being typed is kept before anything else happens.
    fun commit() {
        val cell = editing ?: return
        set(cell.field, choiceOf(cell.field).typed(cell.foundSide, draft.text))
        editing = null
        focus.clearFocus()
    }

    fun edit(field: DetailField, foundSide: Boolean) {
        val text = choiceOf(field).text(foundSide)
        draft = TextFieldValue(text, TextRange(text.length))
        editing = EditingCell(field, foundSide)
    }

    fun tap(field: DetailField, foundSide: Boolean) {
        if (editing == EditingCell(field, foundSide)) return
        commit()
        val choice = choiceOf(field)
        if (choice.useFound == foundSide) edit(field, foundSide) else set(field, choice.pick(foundSide))
    }

    Column(Modifier.fillMaxSize().imePadding().padding(bottom = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = PAGE_GUTTER),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable {
                        commit()
                        onBack()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.song_fix_compare_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = stringResource(R.string.song_fix_compare_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 2.dp, bottom = 12.dp),
        )
        Column(
            Modifier
                // The rest of the screen, so the switch and the button stay at the foot.
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PAGE_GUTTER),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                ColumnHead(
                    name = stringResource(if (fromFile) R.string.song_fix_compare_file else R.string.song_fix_compare_upload),
                    accent = accent,
                    modifier = Modifier.weight(1f),
                ) {
                    commit()
                    choices = choices.useAll(foundSide = false)
                }
                ColumnHead(
                    name = stringResource(R.string.song_fix_compare_found),
                    accent = accent,
                    modifier = Modifier.weight(1f),
                ) {
                    commit()
                    choices = choices.useAll(foundSide = true)
                }
            }
            if (choices.coverDiffers) {
                FieldLabel(stringResource(R.string.song_fix_compare_cover))
                Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                    CoverCell(choices.fileCover, !choices.useFoundCover, accent, Modifier.weight(1f)) {
                        commit()
                        choices = choices.copy(useFoundCover = false)
                    }
                    CoverCell(choices.foundCover, choices.useFoundCover, accent, Modifier.weight(1f)) {
                        commit()
                        choices = choices.copy(useFoundCover = true)
                    }
                }
            }
            DetailField.entries.forEach { field ->
                val choice = choiceOf(field)
                FieldLabel(
                    stringResource(
                        when (field) {
                            DetailField.TITLE -> R.string.song_fix_compare_field_title
                            DetailField.ARTIST -> R.string.song_fix_compare_field_artist
                            DetailField.ALBUM -> R.string.song_fix_compare_field_album
                        },
                    ),
                )
                if (choice.same) {
                    // Nothing to choose between, so the one cell is only for typing.
                    val cell = EditingCell(field, foundSide = true)
                    TextCell(
                        text = choice.text(foundSide = true),
                        tag = stringResource(
                            if (choice.edited(foundSide = true)) R.string.song_fix_compare_edited else R.string.song_fix_compare_same,
                        ),
                        selected = choice.edited(foundSide = true),
                        draft = draft.takeIf { editing == cell },
                        onDraft = { draft = it },
                        onDone = { commit() },
                        accent = accent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (editing != cell) {
                            commit()
                            set(field, choiceOf(field).pick(foundSide = true))
                            edit(field, foundSide = true)
                        }
                    }
                } else {
                    Row(
                        Modifier.height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
                    ) {
                        listOf(false, true).forEach { foundSide ->
                            TextCell(
                                text = choice.text(foundSide),
                                tag = stringResource(R.string.song_fix_compare_edited).takeIf { choice.edited(foundSide) },
                                selected = choice.useFound == foundSide,
                                draft = draft.takeIf { editing == EditingCell(field, foundSide) },
                                onDraft = { draft = it },
                                onDone = { commit() },
                                accent = accent,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) { tap(field, foundSide) }
                        }
                    }
                }
            }
        }
        if (canSaveToFile) {
            val saveToFile by AppSettings.writeSongFixToFile.collectAsStateWithLifecycle()
            Row(
                Modifier
                    .padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 14.dp)
                    .fillMaxWidth()
                    .clip(CellShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                    .clickable { AppSettings.setWriteSongFixToFile(!saveToFile) }
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.song_fix_compare_save_to_file),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.song_fix_compare_save_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentRed,
                    )
                }
                Spacer(Modifier.width(8.dp))
                IosSwitch(checked = saveToFile, onCheckedChange = AppSettings::setWriteSongFixToFile)
            }
        }
        Box(
            Modifier
                .padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 12.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(CircleShape)
                .background(accent)
                .clickable {
                    // Read after the commit: the typing in progress is part of the answer.
                    commit()
                    onUse(choices)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.song_fix_compare_apply),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = accent.ink(),
            )
        }
    }
}

/** Black or white, whichever reads on this colour. */
private fun Color.ink(): Color = if (luminance() > 0.5f) Color.Black else Color.White

@Composable
private fun ColumnHead(name: String, accent: Color, modifier: Modifier, onUseAll: () -> Unit) {
    Row(modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.song_fix_compare_use_all),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            maxLines = 1,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onUseAll)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 5.dp),
    )
}

/** The fill and outline of a cell: the kept one is outlined in [accent]. */
@Composable
private fun Modifier.cell(selected: Boolean, accent: Color): Modifier =
    clip(CellShape)
        .background(if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        .border(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
            shape = CellShape,
        )

@Composable
private fun CheckBadge(accent: Color, modifier: Modifier = Modifier) {
    Icon(
        Icons.Rounded.Check,
        contentDescription = null,
        tint = accent.ink(),
        modifier = modifier.size(18.dp).clip(CircleShape).background(accent).padding(3.dp),
    )
}

@Composable
private fun CoverCell(url: String?, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.cell(selected, accent).clickable(onClick = onClick).padding(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (url == null) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                AsyncImage(
                    model = url.artworkAt(CARD_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        if (selected) CheckBadge(accent, Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}

/**
 * One side of a text detail. While [draft] is set it is a text box holding
 * it; otherwise it reads [text], with [tag] in small print underneath.
 */
@Composable
private fun TextCell(
    text: String,
    tag: String?,
    selected: Boolean,
    draft: TextFieldValue?,
    onDraft: (TextFieldValue) -> Unit,
    onDone: () -> Unit,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val ink = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = ink,
    )
    Row(
        modifier
            .heightIn(min = 56.dp)
            .cell(selected || draft != null, accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (draft != null) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                textStyle = style.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
        } else {
            Column(Modifier.weight(1f)) {
                Text(
                    text = text.ifBlank { stringResource(R.string.song_fix_compare_none) },
                    style = style,
                    color = if (text.isBlank()) ink.copy(alpha = 0.6f) else ink,
                )
                if (tag != null) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Spacer(Modifier.width(6.dp))
                CheckBadge(accent)
            }
        }
    }
}
