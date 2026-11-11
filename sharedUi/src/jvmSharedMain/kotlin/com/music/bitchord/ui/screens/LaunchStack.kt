package com.music.bitchord.ui.screens

/** A page opened from a tile: the page's key, and the key of the page the tile is on. */
interface Launched {
    val key: String
    val parent: String
}

/**
 * The launches with [next] added: on top of the one whose page it was opened
 * from, or alone when it was opened from anywhere else. Null lets go of all.
 */
fun <T : Launched> stackWith(stack: List<T>, next: T?): List<T> {
    if (next == null) return emptyList()
    val under = stack.indexOfLast { it.key == next.parent }
    return if (under >= 0) stack.take(under + 1) + next else listOf(next)
}

/** The launches without [gone], nor whatever was launched from its page. */
fun <T : Launched> stackWithout(stack: List<T>, gone: T): List<T> {
    val at = stack.indexOf(gone)
    return if (at >= 0) stack.take(at) else stack
}

/** The launches left once [page] is back on screen: those that close onto it are done with. */
fun <T : Launched> stackSettledOn(stack: List<T>, page: String): List<T> {
    val at = stack.indexOfFirst { it.parent == page }
    return if (at >= 0) stack.take(at) else stack
}

/** The launch in [stack] that going from page [from] to page [to] opens or closes. */
fun <T : Launched> launchBetween(stack: List<T>, from: String, to: String): T? =
    stack.lastOrNull { (from == it.parent && to == it.key) || (from == it.key && to == it.parent) }
