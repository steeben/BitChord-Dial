package com.music.bitchord

import java.io.DataInputStream
import org.junit.Test

/**
 * Guards MainActivityKt against the JVM's per-method limit of 255 argument
 * slots. BitChordApp's lambdas are compiled to methods of that class taking
 * every local they read as an argument; once one passed 255, the class no
 * longer loaded and the app crashed at launch. Fails with the biggest methods
 * listed, well before the limit, so the next one is split in time.
 */
class MainActivityClassSizeTest {

    @Test
    fun mainActivityKtStaysUnderTheArgumentLimit() {
        val path = "com/music/bitchord/MainActivityKt.class"
        val bytes = javaClass.classLoader!!.getResourceAsStream(path)!!.readBytes()
        val (report, maxSlots) = describe(path, bytes)
        try {
            Class.forName("com.music.bitchord.MainActivityKt")
        } catch (e: Throwable) {
            throw AssertionError("MainActivityKt does not load: $e\n$report")
        }
        if (maxSlots > SLOT_BUDGET) {
            throw AssertionError("A MainActivityKt method takes $maxSlots argument slots (budget $SLOT_BUDGET, JVM limit 255)\n$report")
        }
    }

    private fun describe(path: String, bytes: ByteArray): Pair<String, Int> {
        val input = DataInputStream(bytes.inputStream())
        input.readInt(); input.readUnsignedShort(); input.readUnsignedShort()
        val count = input.readUnsignedShort()
        val utf = arrayOfNulls<String>(count)
        var i = 1
        while (i < count) {
            when (val tag = input.readUnsignedByte()) {
                1 -> utf[i] = input.readUTF()
                3, 4 -> input.readInt()
                5, 6 -> { input.readLong(); i++ }
                7, 8, 16, 19, 20 -> input.readUnsignedShort()
                9, 10, 11, 12, 17, 18 -> input.readInt()
                15 -> { input.readUnsignedByte(); input.readUnsignedShort() }
                else -> error("tag $tag")
            }
            i++
        }
        input.readUnsignedShort(); input.readUnsignedShort(); input.readUnsignedShort()
        repeat(input.readUnsignedShort()) { input.readUnsignedShort() }
        val codeSize = HashMap<String, Int>()
        fun skipMembers(): List<Pair<String, String>> {
            val out = mutableListOf<Pair<String, String>>()
            repeat(input.readUnsignedShort()) {
                input.readUnsignedShort()
                val name = utf[input.readUnsignedShort()]!!
                val desc = utf[input.readUnsignedShort()]!!
                repeat(input.readUnsignedShort()) {
                    val attr = utf[input.readUnsignedShort()]
                    val len = input.readInt()
                    if (attr == "Code") {
                        input.readUnsignedShort(); input.readUnsignedShort()
                        val code = input.readInt()
                        codeSize[name] = code
                        input.skipBytes(len - 8)
                    } else {
                        input.skipBytes(len)
                    }
                }
                out += name to desc
            }
            return out
        }
        skipMembers()
        val methods = skipMembers()
        val sb = StringBuilder("$path: constant pool $count, methods ${methods.size}\n")
        methods.map { (n, d) -> Triple(n, d, slots(d)) }
            .sortedByDescending { it.third }
            .take(8)
            .forEach { (n, d, s) -> sb.append("  slots=$s $n ${d.length} chars code=${codeSize[n]}\n") }
        codeSize.entries.sortedByDescending { it.value }.take(5)
            .forEach { sb.append("  code=${it.value} ${it.key}\n") }
        val names = methods.groupBy { it }.filter { it.value.size > 1 }.keys
        if (names.isNotEmpty()) sb.append("  DUPLICATES: $names\n")
        return sb.toString() to (methods.maxOfOrNull { slots(it.second) } ?: 0)
    }

    private fun slots(desc: String): Int {
        var n = 0
        var j = 1
        while (desc[j] != ')') {
            when (desc[j]) {
                'J', 'D' -> { n += 2; j++ }
                'L' -> { n++; j = desc.indexOf(';', j) + 1 }
                '[' -> {
                    while (desc[j] == '[') j++
                    j = if (desc[j] == 'L') desc.indexOf(';', j) + 1 else j + 1
                    n++
                }
                else -> { n++; j++ }
            }
        }
        return n
    }

    private companion object {
        /** Headroom under the JVM's 255, so growth shows up here first. */
        const val SLOT_BUDGET = 230
    }
}
