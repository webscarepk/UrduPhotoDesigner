package com.webscare.urducanvas.common.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.animation.Interpolator
import androidx.core.widget.NestedScrollView
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

class SpringNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : NestedScrollView(context, attrs, defStyle) {

    // ── Tuning ────────────────────────────────────────────────────────────────
    //  MAX_OVERSCROLL_FRACTION  — how far the list can stretch (fraction of height)
    //  RETURN_DURATION          — how long the snap-back takes in ms
    //  RUBBER_EXPONENT          — resistance curve (higher = shorter pull, more rigid)
    //  FLING_SCALE              — how much fling velocity translates to stretch distance
    private val MAX_OVERSCROLL_FRACTION = 0.40f
    private val RETURN_DURATION         = 500L
    private val RUBBER_EXPONENT         = 0.30    // 0.5 = very stretchy, 1.0 = barely moves

    private var velocityTracker: VelocityTracker? = null
    private var lastY          = 0f
    private var isBouncing     = false
    private var springAnimator: ValueAnimator? = null

    private val scrollChild    get() = getChildAt(0)
    private val isAtTop        get() = scrollY <= 0
    private val isAtBottom     get() = scrollChild != null &&
            scrollY >= (scrollChild.height - height).coerceAtLeast(0)
    private val maxTranslation get() = height * MAX_OVERSCROLL_FRACTION

    // ── Critically-damped return interpolator ─────────────────────────────────
    //
    //  zeta >= 1 → overdamped / critically damped — zero oscillation, zero overshoot.
    //  This is exactly what iOS uses: the list returns smoothly and stops dead.
    //
    //  Formula: f(t) = 1 - e^(-ω·t) · (1 + ω·t)   where ω = sqrt(stiffness)
    //
    //  stiffness = 200 gives a natural-feeling snap — increase for snappier,
    //  decrease for slower. damping is set to exactly critical (zeta = 1).
    //
    private inner class CriticalSpringInterpolator(
        private val stiffness: Float = 200f
    ) : Interpolator {
        private val omega = sqrt(stiffness)
        override fun getInterpolation(t: Float): Float {
            // Critically damped: zeta = 1, no oscillation
            return 1f - exp(-omega * t) * (1f + omega * t)
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)
        if (ev.action == MotionEvent.ACTION_DOWN) lastY = ev.rawY
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                // Cancel any in-progress return animation so the list "catches"
                springAnimator?.cancel()
                lastY = ev.rawY
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = lastY - ev.rawY
                lastY  = ev.rawY

                val pullUp   = dy < 0 && isAtTop
                val pullDown = dy > 0 && isAtBottom

                if (pullUp || pullDown || isBouncing) {
                    applyRubberBand(-dy)
                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                recycleVelocity()

                // If the list is stretched at all, snap straight back — no extra push
                if (isBouncing || abs(translationY) > 0f) {
                    springBack()
                    return true
                }
            }
        }

        return super.onTouchEvent(ev)
    }

    // Fling is left as default NestedScrollView behaviour — spring only triggers on drag.

    // ── Rubber-band drag ──────────────────────────────────────────────────────
    //  Resistance increases as the list is pulled further — exactly like iOS.
    //  ratio^RUBBER_EXPONENT maps [0,1] pull distance to [0,1] resistance factor.

    private fun applyRubberBand(delta: Float) {
        isBouncing = true
        val ratio      = (abs(translationY) / maxTranslation).coerceIn(0f, 1f)
        val resistance = 1f - Math.pow(ratio.toDouble(), RUBBER_EXPONENT).toFloat()
        translationY   = (translationY + delta * resistance)
            .coerceIn(-maxTranslation, maxTranslation)
    }

    // ── Snap back — no bounce, no overshoot ───────────────────────────────────
    //  Uses CriticalSpringInterpolator (zeta = 1) so the return curve is a
    //  smooth deceleration that lands exactly on 0 with no oscillation.

    private fun springBack() {
        springAnimator?.cancel()
        val start = translationY
        if (abs(start) < 0.5f) { translationY = 0f; isBouncing = false; return }

        // Scale duration by how far we are — short pull = quick return
        val distFraction = (abs(start) / maxTranslation).coerceIn(0.3f, 1f)
        val duration     = (RETURN_DURATION * distFraction).toLong()

        springAnimator = ValueAnimator.ofFloat(start, 0f).apply {
            this.duration = duration
            interpolator  = CriticalSpringInterpolator(stiffness = 80f)
            addUpdateListener { translationY = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    translationY = 0f
                    isBouncing   = false
                }
            })
            start()
        }
    }

    // ── Velocity tracker ──────────────────────────────────────────────────────

    private fun trackVelocity(ev: MotionEvent) {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            velocityTracker?.recycle()
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(ev)
    }

    private fun captureVelocityY(): Float {
        velocityTracker?.computeCurrentVelocity(1000)
        return velocityTracker?.yVelocity ?: 0f
    }

    private fun recycleVelocity() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        springAnimator?.cancel()
        recycleVelocity()
    }
}