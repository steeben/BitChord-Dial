package com.music.bitchord.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import dev.chrisbanes.haze.HazeState

/** What a [GlassAlert] somewhere in the app has asked [GlassAlertHost] to show. */
private class GlassAlertEntry {
    var title by mutableStateOf("")
    var onDismiss by mutableStateOf({})
    var content by mutableStateOf<@Composable ColumnScope.() -> Unit>({})
}

private var shownGlassAlert by mutableStateOf<GlassAlertEntry?>(null)

/** Wider than the two-button alerts: these hold lists and controls. */
private val GLASS_ALERT_WIDTH = 300.dp
private val GLASS_ALERT_BODY_MAX_HEIGHT = 460.dp

/**
 * An options window in the app's glass: a title, [content], and a Done row.
 *
 * Call it where a Material AlertDialog would go. The card itself is drawn by
 * [GlassAlertHost] at the top of the activity's window, because the glass and
 * the frosted blur both need the app's backdrop behind them, which a dialog
 * in a window of its own does not have. Text in [content] should take
 * [LocalContentColor], which is set to what reads on the card.
 */
@Composable
fun GlassAlert(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val entry = remember { GlassAlertEntry() }
    SideEffect {
        entry.title = title
        entry.onDismiss = onDismiss
        entry.content = content
    }
    DisposableEffect(entry) {
        shownGlassAlert = entry
        onDispose { if (shownGlassAlert === entry) shownGlassAlert = null }
    }
}

/** Draws the [GlassAlert] that is up, if one is. Placed once, over the whole app. */
@Composable
fun GlassAlertHost(hazeState: HazeState) {
    val entry = shownGlassAlert ?: return
    BackHandler { entry.onDismiss() }
    val shape = RoundedCornerShape(ALERT_CORNER)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SCRIM_COLOR)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { entry.onDismiss() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The scrim is not in the backdrop the glass samples, so it is laid
        // under the glass's own fill; see [LocalGlassUnderlay].
        CompositionLocalProvider(LocalGlassUnderlay provides SCRIM_COLOR) {
            val contentColor = popupContentColor()
            Column(
                modifier = Modifier
                    .width(GLASS_ALERT_WIDTH)
                    .popupSurface(shape, hazeState)
                    // Touching the card itself never dismisses it.
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    ),
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = GLASS_ALERT_BODY_MAX_HEIGHT)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 19.dp),
                    ) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                            color = contentColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        )
                        entry.content(this)
                    }
                }
                AlertRule()
                AlertAction(stringResource(R.string.done), emphasised = true, onClick = { entry.onDismiss() })
            }
        }
    }
}
