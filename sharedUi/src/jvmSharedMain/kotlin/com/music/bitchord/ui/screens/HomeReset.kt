package com.music.bitchord.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Tapping Home while already on Home: the list goes back to the top, and each
 * dial turns back to its first page. The list is the app's to scroll; the
 * dials' pagers live inside their shelves, so they hear it from here.
 */
object HomeReset {
    /** How many times Home has been asked back to its start. Only changes matter. */
    var requests by mutableIntStateOf(0)
        private set

    fun request() {
        requests++
    }
}
