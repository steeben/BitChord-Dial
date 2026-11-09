package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.OldAppNotice
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tells someone coming from the old app that their data doesn't come along on
 * its own, and takes them to Your data to import it; see [OldAppNotice].
 * Shaped like [UpdateAvailableDialog].
 */
@Composable
fun OldAppNoticeHost(hazeState: HazeState, onTakeMeThere: () -> Unit) {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        show = withContext(Dispatchers.IO) { OldAppNotice.shouldShow(context) }
    }
    if (!show) return

    val close = {
        OldAppNotice.markSeen(context)
        show = false
    }
    val shape = RoundedCornerShape(ALERT_CORNER)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SCRIM_COLOR)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalGlassUnderlay provides SCRIM_COLOR) {
            val contentColor = popupContentColor()
            Column(
                modifier = Modifier
                    .width(ALERT_WIDTH)
                    .popupSurface(shape, hazeState),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 19.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.old_app_notice_title),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                        color = contentColor,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.old_app_notice_body),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                        color = contentColor,
                        textAlign = TextAlign.Center,
                    )
                }
                AlertRule()
                AlertAction(stringResource(R.string.old_app_notice_take_me_there), emphasised = true, onClick = {
                    close()
                    onTakeMeThere()
                })
                AlertRule()
                AlertAction(stringResource(R.string.old_app_notice_got_it), emphasised = false, onClick = close)
            }
        }
    }
}
