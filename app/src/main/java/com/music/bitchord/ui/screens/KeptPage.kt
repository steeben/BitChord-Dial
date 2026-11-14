package com.music.bitchord.ui.screens

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.music.bitchord.ui.performance.FrameLog

/**
 * A page that is built once and kept, rather than built again each time it
 * comes back: Home.
 *
 * The pages change places in one transition, which takes a page away once it
 * has left and builds it from nothing when it is wanted again. For Home that
 * is the dials, the rows and every picture on them, on the first frame of the
 * way back. Kept, Home's content is moved instead: it is in its place among
 * the pages while it is there ([Place]), and off stage ([OffStage]), neither
 * drawn nor touched, while another page is up.
 *
 * The content has to be handed from one place to the other within a single
 * composition, or it is lost and built again. So both places go by one rule
 * ([KeptPages.away]), which reads only what the transition itself goes by.
 */
@Stable
class KeptPage(private val key: String) {
    /** The page's own enter and exit among the pages, while it has a place there. */
    private var place: Transition<EnterExitState>? by mutableStateOf(null)

    /** Not before it has been asked for: a launch onto another page does not build this one too. */
    private var entered by mutableStateOf(false)

    /** The setting: off, the page is taken away and built again as it always was. */
    var keep by mutableStateOf(true)

    /**
     * The page's place among the [pages], which has [content] while the page
     * is not off stage. [visibility] is that place's own transition.
     */
    @Composable
    fun Place(visibility: Transition<EnterExitState>, pages: Transition<String>, content: @Composable () -> Unit) {
        DisposableEffect(visibility) {
            place = visibility
            entered = true
            onDispose { if (place === visibility) place = null }
        }
        // As big as the page while it is elsewhere: the pages go by each
        // other's sizes as they change places, and a place of no size would
        // have the one coming in grow from nothing.
        if (!away(pages)) content() else Spacer(Modifier.fillMaxSize())
    }

    /**
     * Where the page waits while another is up. To be composed after the
     * [pages] themselves: what it reads can change while they are composed,
     * and it has to see that in the same pass.
     */
    @Composable
    fun OffStage(pages: Transition<String>, content: @Composable () -> Unit) {
        if (away(pages)) {
            // In the log, so a stop on the way back can be told from one that built the page.
            DisposableEffect(Unit) {
                FrameLog.note("$key kept off stage")
                onDispose { FrameLog.note("$key taken from off stage") }
            }
            KeptOffStage(content)
        }
    }

    /** Whether the page's content is off stage now, rather than in its place among the [pages]. */
    private fun away(pages: Transition<String>): Boolean {
        if (!keep) return false
        val place = place
        return KeptPages.away(
            entered = entered,
            leaving = pages.currentState == key,
            arriving = pages.targetState == key,
            placeDone = place?.let { it.currentState == it.targetState && it.targetState != EnterExitState.Visible },
        )
    }
}

object KeptPages {
    /**
     * Whether a kept page is off stage.
     *
     * [arriving]: it is the page being shown or come back to, so it is in its
     * place. Otherwise, [leaving]: it is the page being left, and stays in its
     * place until its way out is over ([placeDone]; null once the place itself
     * is gone). Neither: another page is up, or two others are changing places.
     */
    fun away(entered: Boolean, leaving: Boolean, arriving: Boolean, placeDone: Boolean?): Boolean = when {
        !entered -> false
        arriving -> false
        !leaving -> true
        else -> placeDone ?: true
    }
}

/**
 * What a page is given by its place among the pages (see [DialLaunchPage]),
 * given here instead so that it is the same wherever the page is composed.
 * Content that finds these changed is composed again from the top, which is
 * the work keeping it is meant to save.
 */
@Composable
fun KeptPageLocals(name: String, cardLaunch: Boolean, content: @Composable () -> Unit) {
    val close = remember {
        // Once for each time the page is built from nothing: kept, once a run.
        FrameLog.note("$name built")
        LaunchClose()
    }
    CompositionLocalProvider(
        LocalCardLaunch provides cardLaunch,
        LocalLaunchClose provides close,
        content = content,
    )
}

/**
 * Where a kept page waits while another is up: measured and placed as it is on
 * screen, so what it knows about where its parts are stays true, but inside a
 * box of no size that cuts off all of it. Nothing of it is drawn, touched or
 * read out.
 *
 * The box itself is put far off the screen, and the page moved back by as
 * much inside it: a box of no size under a finger still counts as touched if
 * the finger is near enough.
 */
@Composable
private fun KeptOffStage(content: @Composable () -> Unit) {
    Box(
        Modifier
            .clearAndSetSemantics { }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(-OFF_STAGE_PX, -OFF_STAGE_PX) }
            }
            .clipToBounds()
            .drawWithContent { }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(0, 0) { placeable.place(OFF_STAGE_PX, OFF_STAGE_PX) }
            },
        propagateMinConstraints = true,
    ) { content() }
}

private const val OFF_STAGE_PX = 100_000
