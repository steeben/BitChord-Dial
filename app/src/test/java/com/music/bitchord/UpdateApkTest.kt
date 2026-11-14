package com.music.bitchord

import com.music.bitchord.data.pickUpdateApk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateApkTest {
    private val phone64 = listOf("arm64-v8a", "armeabi-v7a", "armeabi")
    private val phone32 = listOf("armeabi-v7a", "armeabi")
    private val release = listOf(
        "Dial-v1.8.0-arm64-v8a.apk",
        "Dial-v1.8.0-armeabi-v7a.apk",
    )

    @Test
    fun `a 64-bit phone takes the arm64 APK`() {
        assertEquals("Dial-v1.8.0-arm64-v8a.apk", pickUpdateApk(release, phone64))
        assertEquals("Dial-v1.8.0-arm64-v8a.apk", pickUpdateApk(release.reversed(), phone64))
    }

    @Test
    fun `a phone with a 32-bit system takes the armeabi-v7a APK`() {
        assertEquals("Dial-v1.8.0-armeabi-v7a.apk", pickUpdateApk(release, phone32))
    }

    @Test
    fun `a release with one unnamed APK still updates`() {
        assertEquals("Dial-v1.7.1.apk", pickUpdateApk(listOf("notes.txt", "Dial-v1.7.1.apk"), phone64))
    }

    @Test
    fun `a universal APK stands in for a missing ABI`() {
        val names = listOf("Dial-v1.8.0-arm64-v8a.apk", "Dial-v1.8.0-universal.apk")
        assertEquals("Dial-v1.8.0-universal.apk", pickUpdateApk(names, phone32))
    }

    @Test
    fun `an APK for another ABI is never taken`() {
        assertNull(pickUpdateApk(listOf("Dial-v1.8.0-arm64-v8a.apk"), phone32))
        assertNull(pickUpdateApk(listOf("Dial-v1.8.0-x86_64.apk"), listOf("x86")))
        assertNull(pickUpdateApk(emptyList(), phone64))
    }

    @Test
    fun `x86 is not taken for x86_64`() {
        val names = listOf("Dial-v1.8.0-x86_64.apk", "Dial-v1.8.0-x86.apk")
        assertEquals("Dial-v1.8.0-x86.apk", pickUpdateApk(names, listOf("x86")))
        assertEquals("Dial-v1.8.0-x86_64.apk", pickUpdateApk(names, listOf("x86_64", "x86")))
    }
}
