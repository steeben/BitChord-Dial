package com.music.bitchord.data.migration

/**
 * Which APK of a release to take.
 *
 * From 1.5.0 a release carries two: `Dial-vX.Y.Z.apk`, the app itself
 * (com.steeben.dial), and `Dial-vX.Y.Z-bridge.apk`, the same app built under
 * the old package (com.steeben.bitchord) so it installs *over* an old install
 * and can hand that install's data to the new one; see [AppMove].
 *
 * Builds up to 1.4.1 take the first `.apk` they find, so the bridge has to
 * come first: it is uploaded first, and its name also sorts first ("-" comes
 * before "."), so it leads whichever order GitHub lists assets in. Everything
 * from 1.5.0 on picks by name here instead.
 */
object ReleaseAssets {

    const val BRIDGE_SUFFIX = "-bridge.apk"

    data class Asset(val name: String, val url: String, val uploaded: Boolean = true)

    /** The app's own APK, or the bridge when [bridge]; null if the release has none. */
    fun pick(assets: List<Asset>, bridge: Boolean): Asset? =
        assets
            .filter { it.uploaded && it.name.endsWith(".apk", ignoreCase = true) }
            .firstOrNull { it.name.endsWith(BRIDGE_SUFFIX, ignoreCase = true) == bridge }
}
