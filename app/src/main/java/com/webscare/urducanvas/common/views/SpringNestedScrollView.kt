package com.webscare.urducanvas.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.core.widget.NestedScrollView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs

class SpringNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : NestedScrollView(context, attrs, defStyle) {

    // ── Tuning ────────────────────────────────────────────────────────────────
    private val MAX_OVERSCROLL_FRACTION = 0.30f   // 0.40f → 0.30f (edge factory jaisा)
    private val RUBBER_EXPONENT         = 0.50    // 0.30 → 0.50 (zyada resistance, kam stretch)

    private var velocityTracker: VelocityTracker? = null
    private var lastY      = 0f
    private var isBouncing = false

    // Physics-based spring (same as SpringEdgeEffectFactory)
    private var springAnim: SpringAnimation? = null

    private val scrollChild get() = getChildAt(0)
    private val isAtTop get() = scrollY <= 0

    private val isAtBottom get(): Boolean {
        val child = scrollChild ?: return false
        val maxScroll = child.height - (height - paddingTop - paddingBottom)
        return scrollY >= maxScroll.coerceAtLeast(0)
    }

    private val maxTranslation get() = height * MAX_OVERSCROLL_FRACTION

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
                springAnim?.cancel()
                lastFlingVelocity = 0f   // ← add this
                lastY = ev.rawY
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = lastY - ev.rawY
                lastY = ev.rawY

                val pullUp   = dy < 0 && isAtTop && !canScrollVertically(-1)
                val pullDown = dy > 0 && isAtBottom && !canScrollVertically(1)

                if (pullUp || pullDown || isBouncing) {
                    applyRubberBand(-dy)
                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val velocityY = captureVelocityY()
                recycleVelocity()

                if (isBouncing || abs(translationY) > 0f) {
                    springBack(velocityY)
                    return true
                }
            }
        }

        return super.onTouchEvent(ev)
    }

    private var lastFlingVelocity = 0f

    override fun fling(velocityY: Int) {
        super.fling(velocityY)
        lastFlingVelocity = velocityY.toFloat()
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)

        if (clampedY && abs(lastFlingVelocity) > 0f && !isBouncing && translationY == 0f) {
            val velocity = lastFlingVelocity
            lastFlingVelocity = 0f

            val sign = if (scrollY <= 0) 1f else -1f

            springFromFling(abs(velocity) * sign)
        }
    }

    private fun springFromFling(velocity: Float) {
        springAnim?.cancel()
        isBouncing = true

        springAnim = SpringAnimation(this, SpringAnimation.TRANSLATION_Y, 0f).apply {
            spring = SpringForce(0f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
            setStartVelocity(velocity * 0.3f)
            addEndListener { _, _, _, _ ->
                translationY = 0f
                isBouncing = false
            }
            start()
        }
    }

    // ── Rubber-band drag (unchanged — same iOS-style resistance) ──────────────

    private fun applyRubberBand(delta: Float) {
        isBouncing = true
        springAnim?.cancel()
        // Edge-factory jaisा linear stretch: height ka 0.3x, koi rubber resistance nahi
        translationY = (translationY + delta * 0.3f)
            .coerceIn(-maxTranslation, maxTranslation)
    }

    // ── Snap back — physics spring (same as SpringEdgeEffectFactory) ──────────

    private fun springBack(velocityY: Float) {
        if (abs(translationY) < 0.5f) {
            translationY = 0f
            isBouncing = false
            return
        }

        springAnim?.cancel()
        springAnim = SpringAnimation(this, SpringAnimation.TRANSLATION_Y, 0f).apply {
            spring = SpringForce(0f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
            // released finger ki velocity carry karo, taaki natural lage
            setStartVelocity(velocityY * 0.3f)
            addEndListener { _, _, _, _ ->
                translationY = 0f
                isBouncing = false
            }
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
        springAnim?.cancel()
        recycleVelocity()
    }
}