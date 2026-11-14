package com.music.bitchord.data

private val KNOWN_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

private fun String.isFor(abi: String): Boolean =
    contains(abi, ignoreCase = true) &&
        // "x86" is also the start of "x86_64".
        (abi != "x86" || !contains("x86_64", ignoreCase = true))

/**
 * Which of a release's files this phone can install. A release carries one
 * APK for each ABI, named after it (`Dial-v1.8.0-arm64-v8a.apk`); older ones
 * carry a single `Dial-v1.7.1.apk`.
 *
 * [supportedAbis] is the phone's own list, best first (`Build.SUPPORTED_ABIS`):
 * a 64-bit phone gets the arm64 APK, a phone with a 32-bit system the
 * armeabi-v7a one. With no APK of its own ABIs, a universal APK or one with no
 * ABI in its name will do. An APK for another ABI never does, as it would
 * download and then fail to install: the answer is null.
 */
fun pickUpdateApk(assetNames: List<String>, supportedAbis: List<String>): String? {
    val apks = assetNames.filter { it.endsWith(".apk", ignoreCase = true) }
    for (abi in supportedAbis) {
        apks.firstOrNull { it.isFor(abi) }?.let { return it }
    }
    return apks.firstOrNull { it.contains("universal", ignoreCase = true) }
        ?: apks.firstOrNull { name -> KNOWN_ABIS.none { name.isFor(it) } }
}
