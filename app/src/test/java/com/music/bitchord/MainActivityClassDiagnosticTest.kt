package com.music.bitchord

import java.io.DataInputStream
import org.junit.Test

/** Temporary diagnostic: why the JVM refuses MainActivityKt. */
class MainActivityClassDiagnosticTest {

    @Test
    fun mainActivityKtLoads() {
        val report = StringBuilder()
        listOf("com/music/bitchord/MainActivityKt.class").forEach { path ->
            val bytes = javaClass.classLoader!!.getResourceAsStream(path)!!.readBytes()
            report.append(describe(path, bytes))
        }
        try {
            Class.forName("com.music.bitchord.MainActivityKt")
        } catch (e: Throwable) {
            throw AssertionError("LOAD FAILED: $e\n$report")
        }
        throw AssertionError("LOADED OK\n$report")
    }

    private fun describe(path: String, bytes: ByteArray): String {
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
        return sb.toString()
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
}
