/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package com.music.bitchord.ui.components.backdrop

import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

    // Vendored change: the program of each shader, to give it back by. See
    // [spareRuntimeShaders].
    private val shaderStrings = mutableMapOf<String, String>()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return runtimeShaders.getOrPut(key) {
            shaderStrings[key] = string
            spareRuntimeShaders.take(string)
        }
    }

    fun clear() {
        for ((key, shader) in runtimeShaders) {
            shaderStrings[key]?.let { spareRuntimeShaders.give(it, shader) }
        }
        runtimeShaders.clear()
        shaderStrings.clear()
    }
}

/*
 * Vendored change: a surface's shaders are taken from, and given back to, a
 * store shared by every surface, rather than made for each surface and
 * dropped with it. Making one compiles its program (the stack samples of
 * slow frames in the logs found the app's thread there three times, as a
 * page's glass came on), and a surface that has gone leaves one behind that
 * is as good as new: every user
 * here sets all of a shader's uniforms each time it is used, and an effect or
 * a paint made from a shader keeps the values it was made with.
 *
 * Only reached where runtime shaders are supported (Android 13).
 */
private val spareShaderThread by lazy {
    java.util.concurrent.Executors.newSingleThreadExecutor { work ->
        Thread(work, "glass-shaders").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }
}

private const val SPARE_SHADERS_READY = 4
private const val SPARE_SHADERS_MOST = 12

@android.annotation.SuppressLint("NewApi")
private val spareRuntimeShaders = Spares<RuntimeShader>(
    ready = SPARE_SHADERS_READY,
    most = SPARE_SHADERS_MOST,
    later = { work -> spareShaderThread.execute(work) },
    make = { string -> RuntimeShader(string) },
)
