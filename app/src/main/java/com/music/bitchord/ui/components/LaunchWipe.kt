package com.music.bitchord.ui.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator

/**
 * The splash leaving: black swept off the app from the top left corner to the
 * bottom right on a soft diagonal edge, the same sweep a dial tile's new art
 * comes in on.
 *
 * A view of its own laid over the window rather than an animation of the
 * system's splash view, which can be faded and moved but not masked. By the
 * time the splash lets go its logo has faded out (see `drawable/splash_logo`),
 * so it is plain black and this, starting fully black, takes over from it
 * without a seam. Touches pass through: it is never clickable.
 */
class LaunchWipeView(context: Context) : View(context) {
    private val paint = Paint()
    private var progress = 0f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        // Clear up to the edge, black past it. The edge runs down the diagonal
        // from before the top left corner to past the bottom right one, so the
        // sweep starts fully black and ends fully clear.
        val edge = progress * (1f + FEATHER) - FEATHER
        paint.shader = LinearGradient(
            w * edge, h * edge,
            w * (edge + FEATHER), h * (edge + FEATHER),
            Color.TRANSPARENT, Color.BLACK,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    /** Lays the wipe over [root], runs it once, and takes it away again. */
    fun runOver(root: ViewGroup, onCovered: () -> Unit) {
        root.addView(
            this,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        onCovered()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = LaunchGate.EXIT_WIPE_MS
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    root.removeView(this@LaunchWipeView)
                }
            })
            start()
        }
    }

    private companion object {
        /** The soft edge of the sweep, as a share of the window's diagonal. */
        const val FEATHER = 0.35f
    }
}
