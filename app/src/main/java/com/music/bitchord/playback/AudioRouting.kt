package com.music.bitchord.playback

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which output the music is coming out of, and which one the listener asked for.
 *
 * Two halves that have to be kept apart, because the obvious API for each is
 * the wrong one.
 *
 * ## Showing where the audio is: not MediaRouter
 *
 * Reading [android.media.MediaRouter]'s selected route back gives an answer that
 * changes while a Bluetooth profile is connecting, so the name under the
 * transport flickered between the speaker and the headset that was plainly
 * playing. [outputs] works it out from the connected-device list instead, which
 * is a pure function and therefore steady.
 *
 * ## Changing where the audio goes: only the player can
 *
 * An unprivileged app cannot move the system's media routing. `MediaRouter`'s
 * `selectRoute` is ignored, and `MediaRouter2.transferTo` was measured doing
 * nothing at all here — STREAM_MUSIC stayed on `bt_a2dp` across the call.
 * Moving the system route needs MEDIA_ROUTING_CONTROL, which is privileged.
 *
 * What is left, and what works, is
 * [ExoPlayer.setPreferredAudioDevice][androidx.media3.exoplayer.ExoPlayer.setPreferredAudioDevice]:
 * it re-points our own AudioTrack. See `PlaybackService.applyOutputRoute`.
 *
 * **The sink handed to it has to be one that can play music**, and that is the
 * whole of the difficulty. A pair of Bluetooth earbuds publishes two devices
 * under one name — `TYPE_BLUETOOTH_A2DP` for media and `TYPE_BLUETOOTH_SCO` for
 * telephony — and pointing the track at the SCO one does not fail loudly. The
 * audio comes out of the phone speaker instead, at the *Bluetooth* volume index
 * (7/15 against the speaker's own 15/15, measured) because the system's own
 * route never moved, and the volume slider writes that same unreachable index.
 * It reads as "switching did nothing except break the volume". So SCO is not
 * offered at all, and A2DP wins any tie — see [kindOf] and [preferenceWithin].
 *
 * ## Why the selection is not persisted
 *
 * An [AudioDeviceInfo.getId] is valid for as long as that device stays connected
 * and no longer — the same headphones come back with a different id after a
 * reconnect, and a stored id would either match nothing or, eventually, match
 * something else entirely. So a choice lasts as long as the device does, and
 * unplugging falls back to [systemDefault] on its own rather than stranding
 * playback on a sink that is gone.
 */
object AudioRouting {

    /** Output kinds worth telling apart on screen, in the order they are listed. */
    enum class Kind { PHONE, WIRED, USB, BLUETOOTH, HDMI, OTHER }

    /** What a Bluetooth output physically is, for its icon — see [formOf]. */
    enum class Form { EARBUDS, HEADPHONES, SPEAKER, CAR }

    data class Device(
        val id: Int,
        /**
         * The framework's own [AudioDeviceInfo.getType]. Carried rather than
         * collapsed into [kind] because several entries can share a [kind] and
         * a name while only one of them can play music — see [preferenceWithin].
         */
        val type: Int,
        /**
         * What the device calls itself, and blank for the ones that have no
         * name of their own — see [nameOf]. The UI supplies the label for
         * those, because it is the half of the app that has a Context and a
         * translation of "This phone".
         */
        val name: String,
        val kind: Kind,
        /** Where the audio is going right now — see [activeOf]. */
        val isActive: Boolean = false,
        /** Null for everything that is not Bluetooth. */
        val form: Form? = null,
    )

    private val _selectedId = MutableStateFlow<Int?>(null)

    /** The device the listener chose, or null for "wherever Android would send it". */
    val selectedId: StateFlow<Int?> = _selectedId.asStateFlow()

    /** Send the music to this device. [PlaybackService] does the rest. */
    fun select(id: Int?) {
        _selectedId.value = id
    }

    /** Drops the remembered choice: the device it names has gone. */
    fun forget() {
        _selectedId.value = null
    }

    /** The device matching a chosen id, for handing to the player. */
    fun infoFor(manager: AudioManager, id: Int?): AudioDeviceInfo? {
        if (id == null) return null
        return runCatching { manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
            .getOrNull()
            ?.firstOrNull { it.id == id }
    }

    /** Every sink this device can play music through, in a stable order. */
    fun outputs(manager: AudioManager): List<Device> {
        val infos = runCatching { manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
            .getOrNull()
            ?: return emptyList()
        val active = activeOf(infos, _selectedId.value)
        return infos
            .mapNotNull { info -> kindOf(info.type)?.let { info to it } }
            .map { (info, kind) ->
                val name = nameOf(info, kind)
                Device(
                    id = info.id,
                    type = info.type,
                    name = name,
                    kind = kind,
                    isActive = info.id == active?.id,
                    form = if (kind == Kind.BLUETOOTH) formOf(info, name) else null,
                )
            }
            // One row per physical device. Several entries under one name is
            // normal, and which one survives matters: see [preferenceWithin],
            // and the class comment for what happens when it is the wrong one.
            .groupBy { it.name to it.kind }
            .map { (_, sameDevice) -> sameDevice.minByOrNull { preferenceWithin(it.type) }!! }
            .sortedBy { it.kind.ordinal }
    }

    /**
     * Which device the audio is actually reaching.
     *
     * The chosen one when it is still connected. Otherwise Android's own order
     * of preference, reproduced here rather than asked for, because there is no
     * API that answers it for a sink this app has not opened yet — and because a
     * pure function of the connected-device list is steady, where reading the
     * system's current route is a value that flickers while a Bluetooth profile
     * is negotiating.
     */
    fun activeOf(infos: Array<AudioDeviceInfo>, chosenId: Int?): AudioDeviceInfo? {
        chosenId?.let { id -> infos.firstOrNull { it.id == id } }?.let { return it }
        return systemDefault(infos)
    }

    /**
     * Which of several entries for one device to offer, lowest first.
     *
     * A2DP is the Bluetooth media profile and the only one of a headset's
     * entries that can render music, so it wins outright wherever it appears.
     */
    private fun preferenceWithin(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 0
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> 1
        else -> 2
    }

    private fun systemDefault(infos: Array<AudioDeviceInfo>): AudioDeviceInfo? =
        infos.firstOrNull { kindOf(it.type) == Kind.BLUETOOTH }
            ?: infos.firstOrNull { kindOf(it.type) == Kind.WIRED }
            ?: infos.firstOrNull { kindOf(it.type) == Kind.USB }
            ?: infos.firstOrNull { kindOf(it.type) == Kind.HDMI }
            ?: infos.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

    /**
     * Null for the sinks that are not somewhere music is listened to — the
     * telephony earpiece, a remote submix, the virtual sink a screen recorder
     * opens. Offering those as a choice is offering silence.
     */
    internal fun kindOf(type: Int): Kind? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Kind.PHONE
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_AUX_LINE,
        -> Kind.WIRED
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        -> Kind.USB
        // Compile-time constants, so naming the newer ones costs nothing on an
        // older device — see [bitchord-minsdk-api-landmines]; the sinks they
        // name simply never appear there.
        // TYPE_BLUETOOTH_SCO is deliberately absent: it is the telephony
        // profile, it cannot play music, and offering it is what made
        // switching to headphones come out of the phone speaker instead.
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_HEARING_AID,
        -> Kind.BLUETOOTH
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        -> Kind.HDMI
        else -> null
    }

    /**
     * `productName` is the phone's model for the built-in speaker and for wired
     * headphones — "SM-S911B" is not what anybody calls the thing in their ears
     * — so only the outputs that carry their own name keep it.
     */
    private fun nameOf(info: AudioDeviceInfo, kind: Kind): String = when (kind) {
        Kind.PHONE, Kind.WIRED -> ""
        else -> info.productName?.toString()?.trim().orEmpty()
    }

    /**
     * Earbuds, headphones, a speaker or a car, as near as an ordinary app can tell.
     *
     * The phone's own Bluetooth settings know for certain because the headset
     * announces it in its metadata, but reading that needs a privileged
     * permission. What is left is the device's Bluetooth class, which reliably
     * separates speakers and cars from things worn on the head but files earbuds
     * and headphones under the same few classes. So the class decides speaker
     * and car, and the name decides earbuds; anything worn that the name does
     * not give away is drawn as headphones.
     */
    private fun formOf(info: AudioDeviceInfo, name: String): Form {
        when (info.type) {
            AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST -> return Form.SPEAKER
            AudioDeviceInfo.TYPE_HEARING_AID -> return Form.EARBUDS
        }
        when (bluetoothClassOf(info)) {
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> return Form.CAR
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX,
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR,
            -> return Form.SPEAKER
        }
        val lower = name.lowercase()
        return when {
            HEADPHONE_NAMES.any { it in lower } -> Form.HEADPHONES
            EARBUD_NAMES.any { it in lower } -> Form.EARBUDS
            SPEAKER_NAMES.any { it in lower } -> Form.SPEAKER
            else -> Form.HEADPHONES
        }
    }

    /**
     * The device's Bluetooth class, or null without the permission to read it —
     * in which case the name is all [formOf] has to go on.
     */
    @SuppressLint("MissingPermission")
    private fun bluetoothClassOf(info: AudioDeviceInfo): Int? {
        if (android.os.Build.VERSION.SDK_INT < 28) return null
        val address = info.address?.takeIf { BluetoothAdapter.checkBluetoothAddress(it) } ?: return null
        return runCatching {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)?.bluetoothClass?.deviceClass
        }.getOrNull()
    }

    /** Names that are earbuds; checked after [HEADPHONE_NAMES] so "AirPods Max" stays headphones. */
    private val EARBUD_NAMES = listOf(
        "buds", "pods", "earbud", "airdots", "tws", "in-ear", "wf-", "liberty", "powerbeats",
        "fit pro", "nothing ear", "jabra elite", "earfun free", "soundpeats",
    )

    /** Headphones whose names would otherwise read as earbuds. */
    private val HEADPHONE_NAMES = listOf("airpods max", "headphone", "wh-", "elite 45h", "elite 85h")

    /** Speakers named as such, for when the class could not be read. */
    private val SPEAKER_NAMES = listOf(
        "speaker", "soundbar", "soundlink", "boom", "sonos", "jbl flip", "jbl charge", "jbl go",
        "jbl clip", "jbl xtreme", "jbl pulse", "partybox", "soundcore motion", "echo",
    )
}
