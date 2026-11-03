package com.music.bitchord

import com.music.bitchord.data.model.LikeStatus

/**
 * Whether disliking [targetVideoId] should skip past it: only when it is the
 * track playing now, and only when the tap adds a dislike rather than taking
 * one back.
 *
 * Kept in its own file so its test doesn't have to load MainActivity's file
 * facade, which the desktop JVM can refuse to load once BitChordApp is large.
 */
internal fun shouldSkipAfterDislike(
    previousStatus: LikeStatus,
    targetVideoId: String,
    currentVideoId: String?,
): Boolean = previousStatus != LikeStatus.DISLIKE && targetVideoId == currentVideoId
