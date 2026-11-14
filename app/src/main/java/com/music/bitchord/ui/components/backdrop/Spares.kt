package com.music.bitchord.ui.components.backdrop

/**
 * Things that are slow to make and can be used again, kept ready by kind.
 *
 * Made for the glass's shaders. Android compiles a shader's program each time
 * one is made, on the thread that asks, and every glass surface asks for its
 * own as it comes onto the screen: the bottom bar changing its form brings
 * three surfaces on in one frame, in the middle of a swipe. A surface that
 * goes away gives its shaders back here, and the next one takes them. Past
 * that, a few of each kind in use are made ahead on another thread
 * ([later]), so that the first surfaces to come on find some too.
 *
 * A thing taken belongs to the taker alone until it is given back.
 */
internal class Spares<T : Any>(
    /** How many of a kind are made ahead once one has been asked for. */
    private val ready: Int,
    /** How many of a kind are held at most. */
    private val most: Int,
    /** Runs work off the caller's thread. */
    private val later: (() -> Unit) -> Unit,
    private val make: (kind: String) -> T,
) {
    private val held = HashMap<String, ArrayDeque<T>>()
    private val filling = HashSet<String>()

    /** One of [kind]: a spare if there is one, or else a new one, made here. */
    fun take(kind: String): T {
        val spare = synchronized(this) { held[kind]?.removeLastOrNull() }
        fill(kind)
        return spare ?: make(kind)
    }

    /** [item], of [kind], is in use no longer. */
    fun give(kind: String, item: T) {
        synchronized(this) {
            val spares = held.getOrPut(kind) { ArrayDeque() }
            if (spares.size < most) spares.addLast(item)
        }
    }

    /** How many of [kind] are held. */
    fun count(kind: String): Int = synchronized(this) { held[kind]?.size ?: 0 }

    private fun fill(kind: String) {
        synchronized(this) {
            if ((held[kind]?.size ?: 0) >= ready || !filling.add(kind)) return
        }
        later {
            try {
                while (count(kind) < ready) give(kind, make(kind))
            } catch (_: Throwable) {
                // The taker's own will fail the same way, where it can be seen.
            } finally {
                synchronized(this) { filling.remove(kind) }
            }
        }
    }
}
