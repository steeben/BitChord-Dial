package com.music.bitchord.ui.screens

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.abs

/** A shelf's key in Home's list: by title, so shelves landing above it don't re-key it. */
internal fun homeShelfKey(title: String): String = "shelf:$title"

/** Keys the speed dial's page goes by in Home's list, real or placeholder. */
internal val SPEED_DIAL_PAGE_KEYS: Set<Any> = setOf(homeShelfKey(SPEED_DIAL_SHELF_TITLE), "skeleton:speed-dial")

/** The title the artists' dial goes by as a shelf: never shown, only matched on. */
const val ARTIST_DIAL_SHELF_TITLE = "Artist dial"

/** The artists' dial's key in Home's list. */
val ARTIST_DIAL_PAGE_KEY: String = homeShelfKey(ARTIST_DIAL_SHELF_TITLE)

/**
 * Home's fling over its dial pages — the speed dial, then the artists' dial
 * when there is one: let go anywhere on one of them and the list settles on
 * that page or the next thing below it, like turning a page. Anywhere else it
 * glides on with [decay], the list's ordinary fling, until a dial page comes
 * on screen: then it settles on the dial or on what follows it, whichever the
 * glide would have left more of in view, so it never stops half-way.
 *
 * [restOffsetPx] is where a dial page sits, measured from the content's start
 * (the room the logo leaves above it) — both dials rest there, so the artists'
 * dial sits exactly where the speed dial does. [nextOffsetPx] is where
 * anything after the dials comes to rest, clear of the top bar.
 * [artistPageFollows] says whether the speed dial has the artists' dial after it.
 */
internal class DialPageFling(
    private val listState: LazyListState,
    private val decay: DecayAnimationSpec<Float>,
    private val restOffsetPx: () -> Int,
    private val nextOffsetPx: () -> Int,
    private val artistPageFollows: () -> Boolean = { false },
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val page = straddlingPage()
        if (page == null) return glide(initialVelocity)
        val (toTop, toNext) = page
        val forward = when {
            initialVelocity > PAGE_FLING_VELOCITY -> true
            initialVelocity < -PAGE_FLING_VELOCITY -> false
            // A slow release: whichever it is past a third of the way to.
            else -> -toTop > (toNext - toTop) / 3f
        }
        settle(if (forward) toNext else toTop, initialVelocity)
        return 0f
    }

    private fun isDial(key: Any) = key in SPEED_DIAL_PAGE_KEYS || key == ARTIST_DIAL_PAGE_KEY

    /**
     * The dial page that holds the top of the screen, as the scrolls that bring
     * it back to rest and that bring what follows it up to its own: its
     * resting place is at or above where it is now, and some of it still shows
     * below where the following item would rest. Null when no dial page does.
     */
    private fun straddlingPage(): Pair<Float, Float>? {
        val dialRest = restOffsetPx()
        val next = nextOffsetPx()
        // Where whatever follows a dial page comes to rest.
        fun followingRest(key: Any) =
            if (key in SPEED_DIAL_PAGE_KEYS && artistPageFollows()) dialRest else next
        val page = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            isDial(item.key) && item.offset <= dialRest && item.offset + item.size > followingRest(item.key)
        } ?: return null
        return (page.offset - dialRest).toFloat() to (page.offset + page.size - followingRest(page.key)).toFloat()
    }

    /**
     * The ordinary glide, cut short once a dial page comes on screen: from there
     * it settles on whichever of the dial and what follows it the glide would
     * have left more of in view.
     */
    private suspend fun ScrollScope.glide(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= 1f) return finishOnPage(0f, initialVelocity)
        var remaining = initialVelocity
        var caught = false
        var last = 0f
        AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateDecay(decay) {
            val delta = value - last
            val consumed = scrollBy(delta)
            last = value
            remaining = velocity
            when {
                straddlingPage() != null -> { caught = true; cancelAnimation() }
                abs(delta - consumed) > 0.5f -> { remaining = 0f; cancelAnimation() }
            }
        }
        return if (caught) finishOnPage(remaining, remaining) else remaining
    }

    /** Settles on the dial page's side the glide at [velocity] would have shown more of. */
    private suspend fun ScrollScope.finishOnPage(velocity: Float, handOver: Float): Float {
        val (toTop, toNext) = straddlingPage() ?: return velocity
        val lands = decay.calculateTargetValue(0f, velocity)
        settle(if (lands > (toTop + toNext) / 2f) toNext else toTop, handOver)
        return 0f
    }

    private suspend fun ScrollScope.settle(target: Float, initialVelocity: Float) {
        if (abs(target) < 1f) return
        var scrolled = 0f
        animate(
            initialValue = 0f,
            targetValue = target,
            initialVelocity = initialVelocity,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        ) { value, _ ->
            scrolled += scrollBy(value - scrolled)
        }
    }
}

/** How fast a release has to be, in px/s, to count as a flick one way or the other. */
private const val PAGE_FLING_VELOCITY = 600f
