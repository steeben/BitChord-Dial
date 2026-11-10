package com.music.bitchord.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.Onboarding
import com.music.bitchord.data.settings.DeveloperOptions
import com.music.bitchord.ui.components.IosSwitch

/*
 * The rows developer options add to Advanced & experimental options, once
 * the Dial logo at the foot of Settings has been tapped ten times; see
 * [DeveloperOptions].
 */

/** The way back out: off hides these rows and undoes what they changed. */
@Composable
internal fun DeveloperOptionsRow(title: String) {
    val context = LocalContext.current
    val on by DeveloperOptions.enabled.collectAsStateWithLifecycle()
    SettingsRow(
        icon = Icons.Rounded.DeveloperMode,
        title = title,
        subtitle = stringResource(R.string.developer_options_subtitle),
        trailing = { IosSwitch(checked = on, onCheckedChange = { DeveloperOptions.setEnabled(context, it) }) },
        onClick = { DeveloperOptions.setEnabled(context, !on) },
    )
}

/** Offers a signed-out account in the account switcher. */
@Composable
internal fun DeveloperPlaceholderAccountRow(title: String) {
    val on by DeveloperOptions.placeholderAccount.collectAsStateWithLifecycle()
    SettingsRow(
        icon = Icons.Rounded.PersonOff,
        title = title,
        subtitle = stringResource(R.string.developer_placeholder_account_subtitle),
        trailing = { IosSwitch(checked = on, onCheckedChange = { DeveloperOptions.setPlaceholderAccount(it) }) },
        onClick = { DeveloperOptions.setPlaceholderAccount(!on) },
    )
}

/** Restarts the app into the first-launch tour. */
@Composable
internal fun DeveloperOnboardingRow(title: String) {
    val context = LocalContext.current
    SettingsRow(
        icon = Icons.Rounded.Explore,
        title = title,
        subtitle = stringResource(R.string.developer_onboarding_subtitle),
        onClick = { Onboarding.replay(context) },
    )
}

/** Every setting at its default while on; the listener's own back when off. */
@Composable
internal fun DeveloperStockSettingsRow(title: String) {
    val context = LocalContext.current
    val on by DeveloperOptions.stockSettingsTest.collectAsStateWithLifecycle()
    SettingsRow(
        icon = Icons.Rounded.SettingsBackupRestore,
        title = title,
        subtitle = stringResource(
            if (on) R.string.developer_stock_settings_on else R.string.developer_stock_settings_subtitle,
        ),
        trailing = {
            IosSwitch(checked = on, onCheckedChange = { DeveloperOptions.setStockSettingsTest(context, it) })
        },
        onClick = { DeveloperOptions.setStockSettingsTest(context, !on) },
    )
}
