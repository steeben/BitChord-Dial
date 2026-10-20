package com.music.bitchord.ui.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.icons.BitChordIcons
import org.jetbrains.compose.resources.stringResource

/**
 * What to call an output on screen.
 *
 * Bluetooth and USB devices carry their own name and keep it. The phone's own
 * speaker and a pair of wired headphones do not — `productName` gives the
 * *phone's* model for both, which is why the caption used to read "SM-S911B"
 * — so those get a label from here instead.
 */
@Composable
internal fun outputLabel(device: AudioOutputDevice, accountName: String?): String {
    val firstName = accountName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotBlank() }
    return when {
        device.name.isNotBlank() -> device.name
        device.kind == AudioOutputKind.WIRED -> stringResource(Res.string.wired_headphones)
        device.kind == AudioOutputKind.USB -> stringResource(Res.string.usb_audio)
        device.kind == AudioOutputKind.HDMI -> stringResource(Res.string.hdmi_output)
        firstName != null -> stringResource(Res.string.personal_phone, firstName)
        else -> stringResource(Res.string.this_phone)
    }
}

/**
 * The name of whatever is playing the music, for the line under the transport.
 *
 * Derived from the same list the picker shows, so the two can never disagree —
 * which they did constantly when this read `android.media.MediaRouter`'s
 * selected route and the picker read the audio devices.
 */
@Composable
internal fun rememberAudioOutputName(accountName: String?): String {
    val outputs = rememberAudioOutputs()
    val active = outputs.firstOrNull { it.isActive }
        ?: return if (accountName.isNullOrBlank()) {
            stringResource(Res.string.this_phone)
        } else {
            stringResource(
                Res.string.personal_phone,
                accountName.trim().split(Regex("\\s+")).first(),
            )
        }
    return outputLabel(active, accountName)
}

/**
 * What to draw for an output: the thing itself where the platform could tell
 * what it is, and how it is connected where it could not.
 */
internal fun outputIcon(device: AudioOutputDevice): ImageVector = when (device.form) {
    AudioOutputForm.EARBUDS -> BitChordIcons.Earbuds
    AudioOutputForm.HEADPHONES -> Icons.Rounded.Headphones
    AudioOutputForm.SPEAKER -> Icons.Rounded.Speaker
    AudioOutputForm.CAR -> Icons.Rounded.DirectionsCar
    null -> when (device.kind) {
        AudioOutputKind.PHONE -> Icons.Rounded.PhoneAndroid
        AudioOutputKind.WIRED -> Icons.Rounded.Headphones
        AudioOutputKind.USB -> Icons.Rounded.Usb
        AudioOutputKind.BLUETOOTH -> Icons.Rounded.Bluetooth
        AudioOutputKind.HDMI -> Icons.Rounded.Tv
        AudioOutputKind.OTHER -> Icons.Rounded.Speaker
    }
}

/**
 * The icon for whatever is playing the music, for the output button on the
 * player. The phone when nothing else is, the same fallback as
 * [rememberAudioOutputName].
 */
@Composable
internal fun rememberAudioOutputIcon(): ImageVector =
    rememberAudioOutputs().firstOrNull { it.isActive }?.let(::outputIcon) ?: Icons.Rounded.PhoneAndroid
