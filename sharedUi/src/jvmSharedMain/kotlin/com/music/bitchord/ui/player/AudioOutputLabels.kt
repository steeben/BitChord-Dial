package com.music.bitchord.ui.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.icons.BitChordIcons
import org.jetbrains.compose.resources.StringResource
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

/** The icons a listener can give an output by long-pressing the output button. */
enum class AudioOutputIcon {
    PHONE, EARBUDS, HEADPHONES, HEADSET, HEARING_AID, SPEAKER, SPEAKER_GROUP,
    TV, CAR, WATCH, COMPUTER, USB, BLUETOOTH,
}

internal val AudioOutputIcon.vector: ImageVector
    get() = when (this) {
        AudioOutputIcon.PHONE -> Icons.Rounded.PhoneAndroid
        AudioOutputIcon.EARBUDS -> BitChordIcons.Earbuds
        AudioOutputIcon.HEADPHONES -> Icons.Rounded.Headphones
        AudioOutputIcon.HEADSET -> Icons.Rounded.HeadsetMic
        AudioOutputIcon.HEARING_AID -> Icons.Rounded.Hearing
        AudioOutputIcon.SPEAKER -> Icons.Rounded.Speaker
        AudioOutputIcon.SPEAKER_GROUP -> Icons.Rounded.SpeakerGroup
        AudioOutputIcon.TV -> Icons.Rounded.Tv
        AudioOutputIcon.CAR -> Icons.Rounded.DirectionsCar
        AudioOutputIcon.WATCH -> Icons.Rounded.Watch
        AudioOutputIcon.COMPUTER -> Icons.Rounded.Computer
        AudioOutputIcon.USB -> Icons.Rounded.Usb
        AudioOutputIcon.BLUETOOTH -> Icons.Rounded.Bluetooth
    }

internal val AudioOutputIcon.label: StringResource
    get() = when (this) {
        AudioOutputIcon.PHONE -> Res.string.output_icon_phone
        AudioOutputIcon.EARBUDS -> Res.string.output_icon_earbuds
        AudioOutputIcon.HEADPHONES -> Res.string.output_icon_headphones
        AudioOutputIcon.HEADSET -> Res.string.output_icon_headset
        AudioOutputIcon.HEARING_AID -> Res.string.output_icon_hearing_aid
        AudioOutputIcon.SPEAKER -> Res.string.output_icon_speaker
        AudioOutputIcon.SPEAKER_GROUP -> Res.string.output_icon_speaker_group
        AudioOutputIcon.TV -> Res.string.output_icon_tv
        AudioOutputIcon.CAR -> Res.string.output_icon_car
        AudioOutputIcon.WATCH -> Res.string.output_icon_watch
        AudioOutputIcon.COMPUTER -> Res.string.output_icon_computer
        AudioOutputIcon.USB -> Res.string.output_icon_usb
        AudioOutputIcon.BLUETOOTH -> Res.string.output_icon_bluetooth
    }

/**
 * What a chosen icon is remembered under: the kind of connection and the device's own
 * name, which outlive the id the platform hands out afresh on every reconnect. A device
 * with no name of its own (the phone, a wired pair) goes by its kind alone.
 */
internal fun outputIconKey(device: AudioOutputDevice?): String = when {
    device == null -> AudioOutputKind.PHONE.name
    device.name.isNotBlank() -> "${device.kind.name}:${device.name}"
    else -> device.kind.name
}

/**
 * What to draw for an output: the icon the listener picked for it, if any; otherwise the
 * thing itself where the platform could tell what it is, and how it is connected where it
 * could not.
 */
internal fun outputIcon(device: AudioOutputDevice, chosen: Map<String, String> = emptyMap()): ImageVector =
    chosen[outputIconKey(device)]
        ?.let { name -> AudioOutputIcon.entries.firstOrNull { it.name == name } }
        ?.vector
        ?: defaultOutputIcon(device)

private fun defaultOutputIcon(device: AudioOutputDevice): ImageVector = when (device.form) {
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

/** The output that is playing, or null for the phone's own speaker. */
@Composable
internal fun rememberActiveAudioOutput(): AudioOutputDevice? =
    rememberAudioOutputs().firstOrNull { it.isActive }

/**
 * The icon for whatever is playing the music, for the output button on the
 * player. The phone when nothing else is, the same fallback as
 * [rememberAudioOutputName]. A listener's own pick wins either way.
 */
@Composable
internal fun rememberAudioOutputIcon(): ImageVector {
    val chosen by PlayerSettings.outputIcons.collectAsStateWithLifecycle()
    val active = rememberActiveAudioOutput()
    return if (active != null) {
        outputIcon(active, chosen)
    } else {
        chosen[outputIconKey(null)]
            ?.let { name -> AudioOutputIcon.entries.firstOrNull { it.name == name } }
            ?.vector
            ?: Icons.Rounded.PhoneAndroid
    }
}
