package com.music.bitchord

import com.music.bitchord.ui.components.backdrop.Spares
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class SparesTest {
    private var made = 0
    private val waiting = ArrayList<() -> Unit>()

    /** Spares whose work for later waits until [runWaiting]. */
    private fun spares(ready: Int = 2, most: Int = 3) = Spares<Any>(
        ready = ready,
        most = most,
        later = { waiting.add(it) },
        make = { made++; Any() },
    )

    private fun runWaiting() {
        val work = waiting.toList()
        waiting.clear()
        work.forEach { it() }
    }

    @Test
    fun `with no spare one is made by the taker`() {
        val spares = spares()
        spares.take("a")
        assertEquals(1, made)
    }

    @Test
    fun `one given back is the next one taken`() {
        val spares = spares()
        val first = spares.take("a")
        spares.give("a", first)
        assertSame(first, spares.take("a"))
        assertEquals(1, made)
    }

    @Test
    fun `a kind's spares are its own`() {
        val spares = spares()
        val first = spares.take("a")
        spares.give("a", first)
        assertNotSame(first, spares.take("b"))
    }

    @Test
    fun `a kind that was asked for is made ahead`() {
        val spares = spares(ready = 2)
        spares.take("a")
        assertEquals(0, spares.count("a"))
        runWaiting()
        assertEquals(2, spares.count("a"))
        assertEquals(3, made)
        // Those are taken with nothing made by the taker.
        spares.take("a")
        spares.take("a")
        assertEquals(3, made)
    }

    @Test
    fun `a kind is filled by one piece of work at a time`() {
        val spares = spares(ready = 2)
        spares.take("a")
        spares.take("a")
        spares.take("a")
        assertEquals(1, waiting.size)
        runWaiting()
        assertEquals(2, spares.count("a"))
        // And again once that is done and one is gone.
        spares.take("a")
        assertEquals(1, waiting.size)
        runWaiting()
        assertEquals(2, spares.count("a"))
    }

    @Test
    fun `no more are held than the most`() {
        val spares = spares(ready = 1, most = 3)
        repeat(5) { spares.give("a", Any()) }
        assertEquals(3, spares.count("a"))
    }

    @Test
    fun `one that cannot be made ahead stops the filling and no more`() {
        var fail = true
        val spares = Spares<Any>(
            ready = 2,
            most = 3,
            later = { waiting.add(it) },
            make = { if (fail) error("no") else Any() },
        )
        runCatching { spares.take("a") }
        runWaiting()
        assertEquals(0, spares.count("a"))
        fail = false
        spares.take("a")
        runWaiting()
        assertEquals(2, spares.count("a"))
    }
}
