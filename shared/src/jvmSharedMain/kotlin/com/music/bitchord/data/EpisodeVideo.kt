package com.music.bitchord.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a podcast episode plays with its video or as audio only: the switch
 * in the player's "…" menu for an episode. One choice for all episodes, kept
 * between launches. On by default.
 *
 * Audio only: the player shows the episode's picture, and the video is not
 * decoded or downloaded. The sound is the same stream in both.
 */
object EpisodeVideo {
    private val _on = MutableStateFlow(true)
    val on: StateFlow<Boolean> = _on.asStateFlow()

    /** Where a change is written; set by the app when it reads the saved value. */
    private var save: ((Boolean) -> Unit)? = null

    /** The saved choice, read at launch, and where the next change goes. */
    fun load(saved: Boolean, store: (Boolean) -> Unit) {
        _on.value = saved
        save = store
    }

    fun set(value: Boolean) {
        if (_on.value == value) return
        _on.value = value
        save?.invoke(value)
    }
}
