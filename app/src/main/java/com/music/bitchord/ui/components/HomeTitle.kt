package com.music.bitchord.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.music.bitchord.ui.theme.Tiny5Lcd

/** The word set apart in the Home title, wherever the title carries it. */
private val HOME_TITLE_DIAL = Regex("""\bdial\b""", RegexOption.IGNORE_CASE)

/**
 * The Home title with each "Dial" in it set as the mark's own word: Tiny5 LCD,
 * in capitals, in [dialColor]. Sized up so its capitals stand as tall as
 * Metropolis's beside it (cap heights 0.554 and 0.687 em). A title without
 * the word is left as it is. Also the app's name at the foot of Settings.
 */
internal fun homeTitleWithDial(title: String, dialColor: Color): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (match in HOME_TITLE_DIAL.findAll(title)) {
        append(title.substring(at, match.range.first))
        withStyle(
            SpanStyle(
                fontFamily = Tiny5Lcd,
                fontWeight = FontWeight.W400,
                fontSize = 1.24.em,
                color = dialColor,
            ),
        ) {
            append(match.value.uppercase())
        }
        at = match.range.last + 1
    }
    append(title.substring(at))
}
