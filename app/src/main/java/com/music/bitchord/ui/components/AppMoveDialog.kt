package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.migration.AppMove
import com.music.bitchord.data.migration.AppMove.Stage
import com.music.bitchord.data.migration.AppMove.Work
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The prompts that move someone from the old app to Dial; see [AppMove].
 * Re-checked on every resume, since each step ends in a system screen
 * (installer, uninstaller) and the next step depends on what happened there.
 * Draws nothing outside the old and new packages.
 */
@Composable
fun AppMoveHost(hazeState: HazeState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf<Stage>(Stage.None) }
    val work by AppMove.work.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            stage = withContext(Dispatchers.IO) { AppMove.stage(context) }
        }
    }

    val dismiss = {
        AppMove.dismissedThisLaunch = true
        AppMove.resetWork()
        stage = Stage.None
    }

    val (title, body) = when (stage) {
        Stage.None -> return
        Stage.Import -> R.string.app_move_import_title to R.string.app_move_import_body
        Stage.UpdateOld -> R.string.app_move_update_old_title to R.string.app_move_update_old_body
        Stage.UninstallOld -> R.string.app_move_uninstall_title to R.string.app_move_uninstall_body
        Stage.GetDial -> R.string.app_move_get_dial_title to R.string.app_move_get_dial_body
        Stage.OpenDial -> R.string.app_move_open_dial_title to R.string.app_move_open_dial_body
    }

    AppMoveAlert(
        title = stringResource(title),
        body = stringResource(body),
        work = work,
        hazeState = hazeState,
    ) {
        when (stage) {
            Stage.Import -> {
                AlertAction(stringResource(R.string.app_move_import_action), emphasised = true, onClick = {
                    scope.launch { AppMove.importFromOldApp(context) }
                })
                AlertRule()
                AlertAction(stringResource(R.string.app_move_not_now), emphasised = false, onClick = dismiss)
            }
            Stage.UpdateOld -> {
                AlertAction(stringResource(R.string.app_move_update_old_action), emphasised = true, onClick = {
                    scope.launch { AppMove.downloadAndInstall(context, bridge = true) }
                })
                AlertRule()
                AlertAction(stringResource(R.string.app_move_not_now), emphasised = false, onClick = dismiss)
            }
            Stage.UninstallOld -> {
                AlertAction(stringResource(R.string.app_move_uninstall_action), emphasised = true, onClick = {
                    AppMove.uninstallOldApp(context)
                })
                AlertRule()
                AlertAction(stringResource(R.string.app_move_keep_old), emphasised = false, onClick = {
                    AppMove.keepOldApp(context)
                    dismiss()
                })
            }
            Stage.GetDial -> {
                AlertAction(stringResource(R.string.app_move_get_dial_action), emphasised = true, onClick = {
                    scope.launch { AppMove.downloadAndInstall(context, bridge = false) }
                })
                AlertRule()
                AlertAction(stringResource(R.string.app_move_not_now), emphasised = false, onClick = dismiss)
            }
            Stage.OpenDial -> {
                AlertAction(stringResource(R.string.app_move_open_dial_action), emphasised = true, onClick = {
                    AppMove.openDial(context)
                })
                AlertRule()
                AlertAction(stringResource(R.string.app_move_not_now), emphasised = false, onClick = dismiss)
            }
            Stage.None -> Unit
        }
    }
}

@Composable
private fun AppMoveAlert(
    title: String,
    body: String,
    work: Work,
    hazeState: HazeState,
    actions: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(ALERT_CORNER)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SCRIM_COLOR)
            // Swallows taps: the scrim doesn't dismiss, the actions do, so a
            // stray tap mid-move can't hide what is happening.
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
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                        color = contentColor,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = when (work) {
                            Work.Moving -> stringResource(R.string.app_move_moving)
                            is Work.Downloading -> stringResource(R.string.app_move_downloading)
                            else -> body
                        },
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                        color = contentColor,
                        textAlign = TextAlign.Center,
                    )
                    if (work is Work.Downloading) {
                        Box(
                            Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(contentColor.copy(alpha = 0.14f)),
                        ) {
                            if (work.fraction > 0f) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(work.fraction)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    }
                    if (work is Work.Failed) {
                        Text(
                            text = work.message,
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                // Mid-download or mid-move there is nothing to choose.
                if (work !is Work.Downloading && work != Work.Moving) {
                    AlertRule()
                    actions()
                }
            }
        }
    }
}
