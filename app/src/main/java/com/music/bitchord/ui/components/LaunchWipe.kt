package com.music.bitchord.ui.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import com.music.bitchord.R

/**
 * The splash leaving: the black screen and its lit logo swept off the app from
 * the top left corner to the bottom right on a soft diagonal edge, the same
 * sweep a dial tile's new art comes in on.
 *
 * A view of its own laid over the window rather than an animation of the
 * system's splash view, which can be faded and moved but not masked. The
 * splash lets go with its logo fully lit (see `drawable/splash_logo`), so this
 * draws the same thing, black with the lit logo where the splash had it, and
 * takes over from it without a seam. Touches pass through: it is never
 * clickable.
 */
class LaunchWipeView(context: Context) : View(context) {
    private val paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val logo: Drawable? = context.getDrawable(R.drawable.splash_logo_lit)
    private val logoBox = Rect()
    private var progress = 0f
    private var onFirstDraw: (() -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawColor(Color.BLACK)
        logo?.let {
            if (logoBox.isEmpty) centreLogoOn(width / 2, height / 2)
            it.bounds = logoBox
            it.draw(canvas)
        }
        // Keep what is past the edge and clear what is before it. The edge
        // runs down the diagonal from before the top left corner to past the
        // bottom right one, so the sweep starts whole and ends fully clear.
        val edge = progress * (1f + FEATHER) - FEATHER
        paint.shader = LinearGradient(
            w * edge, h * edge,
            w * (edge + FEATHER), h * (edge + FEATHER),
            Color.TRANSPARENT, Color.BLACK,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        canvas.restoreToCount(layer)
        firstDrawn()
    }

    /**
     * The splash draws its icon in a 288dp box whatever size its icon view
     * says it is (that reports the 192dp the system keeps clear, and a logo
     * drawn to it comes out a third too small), so only the centre is taken
     * from the view.
     */
    private fun centreLogoOn(cx: Int, cy: Int) {
        val half = (LOGO_DP * resources.displayMetrics.density / 2f).toInt()
        logoBox.set(cx - half, cy - half, cx + half, cy + half)
    }

    /**
     * Lays the wipe over [root], runs it once, and takes it away again.
     * [icon] is the splash's logo view, when the system hands one over: the
     * logo is drawn centred where that was.
     */
    fun runOver(root: ViewGroup, icon: View?, onCovered: () -> Unit) {
        if (icon != null && icon.width > 0 && icon.height > 0) {
            val at = IntArray(2)
            val origin = IntArray(2)
            icon.getLocationInWindow(at)
            root.getLocationInWindow(origin)
            centreLogoOn(at[0] - origin[0] + icon.width / 2, at[1] - origin[1] + icon.height / 2)
        }
        root.addView(
            this,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        // The splash goes only once this has been drawn over it and that frame
        // has had time to reach the screen. The splash's logo is on a surface
        // of its own that goes the moment it is removed, a frame before a view
        // added in the same breath is first drawn: taken away at once, the
        // logo blinked off for that frame.
        onFirstDraw = {
            postOnAnimation {
                postOnAnimation {
                    onCovered()
                    sweep(root)
                }
            }
        }
        invalidate()
        // Whatever happens to the first draw, the splash is not left up.
        postDelayed({ firstDrawn() }, FIRST_DRAW_WAIT_MS)
    }

    private fun firstDrawn() {
        val then = onFirstDraw ?: return
        onFirstDraw = null
        then()
    }

    private fun sweep(root: ViewGroup) {
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

        /** The splash icon's box when it has no icon background. */
        const val LOGO_DP = 288f

        /** The longest the splash waits for the wipe's first draw before it goes anyway. */
        const val FIRST_DRAW_WAIT_MS = 400L
    }
}
