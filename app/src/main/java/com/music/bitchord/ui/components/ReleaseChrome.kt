package com.music.bitchord.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics

/**
 * The album or playlist page's own controls that live in the top bar: the
 * save "+" beside the sort button, and the ⋯ that stands in for the account
 * photo and opens everything else the page can do.
 *
 * A single slot, like [ListSelection]: the page that is up publishes through
 * [PublishReleaseChrome], and the bar shows the controls only while the page
 * it is titling is the one that published them.
 */
object ReleaseChrome {
    private var owner: Any? = null

    /** The page the controls belong to. */
    var browseId by mutableStateOf<String?>(null)
        private set

    /** Whether the release is in the library; null where saving isn't offered. */
    var saved by mutableStateOf<Boolean?>(null)
        private set

    private var toggle: (() -> Unit)? = null
    private var more: (() -> Unit)? = null

    var hasMore by mutableStateOf(false)
        private set

    /** Whether the bar over [pageId] should show the page's controls. */
    fun owns(pageId: String?): Boolean = pageId != null && browseId == pageId

    fun toggleSave() {
        toggle?.invoke()
    }

    fun openMore() {
        more?.invoke()
    }

    internal fun publish(
        key: Any,
        pageId: String,
        saved: Boolean?,
        onToggleSave: (() -> Unit)?,
        onMore: (() -> Unit)?,
        claim: Boolean,
    ) {
        if (!claim && owner !== key) return
        owner = key
        browseId = pageId
        this.saved = saved
        toggle = onToggleSave
        more = onMore
        hasMore = onMore != null
    }

    internal fun withdraw(key: Any) {
        if (owner !== key) return
        owner = null
        browseId = null
        saved = null
        toggle = null
        more = null
        hasMore = false
    }
}

/**
 * Hands an album or playlist page's bar controls to [ReleaseChrome]. Claims
 * the slot once, when the page arrives; after that only keeps its own entry
 * current, so a page on its way out can't take the bar back from the one
 * that replaced it.
 */
@Composable
fun PublishReleaseChrome(
    browseId: String,
    saved: Boolean?,
    onToggleSave: (() -> Unit)?,
    onMore: (() -> Unit)?,
) {
    val key = remember { Any() }
    val toggle by rememberUpdatedState(onToggleSave)
    val more by rememberUpdatedState(onMore)
    val hasToggle = onToggleSave != null
    val hasMore = onMore != null
    DisposableEffect(browseId) {
        ReleaseChrome.publish(
            key, browseId, saved,
            if (hasToggle) ({ toggle?.invoke(); Unit }) else null,
            if (hasMore) ({ more?.invoke(); Unit }) else null,
            claim = true,
        )
        onDispose { ReleaseChrome.withdraw(key) }
    }
    SideEffect {
        ReleaseChrome.publish(
            key, browseId, saved,
            if (hasToggle) ({ toggle?.invoke(); Unit }) else null,
            if (hasMore) ({ more?.invoke(); Unit }) else null,
            claim = false,
        )
    }
}

/**
 * The save control in the bar's pill: a plus that folds into a tick once the
 * release is in the library, with the like heart's burst. Nothing at all
 * where the page offers no saving (a guest, a device playlist).
 */
@Composable
fun ReleaseSaveButton(pageId: String?) {
    if (!ReleaseChrome.owns(pageId)) return
    val saved = ReleaseChrome.saved ?: return
    val burst = rememberLikeAnimation()
    val description = stringResource(if (saved) R.string.remove_from_library else R.string.add_to_library)
    val haptics = rememberHaptics()
    IconButton(
        onClick = {
            haptics.play(if (saved) Haptic.ToggleOff else Haptic.ToggleOn)
            burst.onToggle(wasLiked = saved)
            ReleaseChrome.toggleSave()
        },
    ) {
        LibraryCheck(
            saved = saved,
            animation = burst,
            tint = MaterialTheme.colorScheme.onSurface,
            size = 22.dp,
            contentDescription = description,
        )
    }
}

/**
 * The ⋯ in place of the account photo on an album or playlist page: the
 * page's menu, or with rows ticked, what can be done to them.
 */
@Composable
fun ReleaseMoreButton(modifier: Modifier = Modifier) {
    IconButton(
        onClick = {
            if (ListSelection.active) ListSelection.openMenu() else ReleaseChrome.openMore()
        },
        modifier = modifier.menuAnchor(),
    ) {
        Icon(
            Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(R.string.more),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
