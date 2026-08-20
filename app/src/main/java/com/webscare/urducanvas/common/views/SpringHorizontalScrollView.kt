package com.webscare.urducanvas.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.widget.HorizontalScrollView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs

class SpringHorizontalScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : HorizontalScrollView(context, attrs, defStyle) {

    private val MAX_OVERSCROLL_FRACTION = 0.30f

    private var velocityTracker: VelocityTracker? = null
    private var lastX = 0f
    private var isBouncing = false
    private var lastFlingVelocity = 0f

    private var springAnim: SpringAnimation? = null

    // We animate the CONTENT child, not the scroll view itself.
    private val scrollChild get() = getChildAt(0)

    private val isAtStart get() = scrollX <= 0
    private val isAtEnd get(): Boolean {
        val child = scrollChild ?: return false
        val maxScroll = child.width - (width - paddingLeft - paddingRight)
        return scrollX >= maxScroll.coerceAtLeast(0)
    }

    private val maxTranslation get() = width * MAX_OVERSCROLL_FRACTION

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) lastX = ev.rawX
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                springAnim?.cancel()
                lastFlingVelocity = 0f
                lastX = ev.rawX
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = lastX - ev.rawX
                lastX = ev.rawX

                val pullLeft = dx < 0 && isAtStart && !canScrollHorizontally(-1)
                val pullRight = dx > 0 && isAtEnd && !canScrollHorizontally(1)

                if (pullLeft || pullRight || isBouncing) {
                    applyRubberBand(-dx)
                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val velocityX = captureVelocityX()
                recycleVelocity()

                val child = scrollChild
                if (child != null && (isBouncing || abs(child.translationX) > 0f)) {
                    springBack(velocityX)
                    return true
                }
            }
        }

        return super.onTouchEvent(ev)
    }

    override fun fling(velocityX: Int) {
        super.fling(velocityX)
        lastFlingVelocity = velocityX.toFloat()
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)

        if (clampedX && abs(lastFlingVelocity) > 0f && !isBouncing) {
            val child = scrollChild ?: return
            if (child.translationX == 0f) {
                val velocity = lastFlingVelocity
                lastFlingVelocity = 0f
                val sign = if (scrollX <= 0) 1f else -1f
                springFromFling(abs(velocity) * sign)
            }
        }
    }

    private fun applyRubberBand(delta: Float) {
        isBouncing = true
        springAnim?.cancel()
        val child = scrollChild ?: return
        child.translationX = (child.translationX + delta * 0.35f)
            .coerceIn(-maxTranslation, maxTranslation)
    }

    private fun springFromFling(velocity: Float) {
        val child = scrollChild ?: return
        springAnim?.cancel()
        isBouncing = true

        springAnim = SpringAnimation(child, SpringAnimation.TRANSLATION_X, 0f).apply {
            spring = SpringForce(0f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
            setStartVelocity(velocity * 0.35f)
            addEndListener { _, _, _, _ ->
                child.translationX = 0f
                isBouncing = false
            }
            start()
        }
    }

    private fun springBack(velocityX: Float) {
        val child = scrollChild ?: return
        if (abs(child.translationX) < 0.5f) {
            child.translationX = 0f
            isBouncing = false
            return
        }

        springAnim?.cancel()
        springAnim = SpringAnimation(child, SpringAnimation.TRANSLATION_X, 0f).apply {
            spring = SpringForce(0f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
            setStartVelocity(velocityX * 0.35f)
            addEndListener { _, _, _, _ ->
                child.translationX = 0f
                isBouncing = false
            }
            start()
        }
    }

    private fun trackVelocity(ev: MotionEvent) {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            velocityTracker?.recycle()
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(ev)
    }

    private fun captureVelocityX(): Float {
        velocityTracker?.computeCurrentVelocity(1000)
        return velocityTracker?.xVelocity ?: 0f
    }

    private fun recycleVelocity() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        springAnim?.cancel()
        scrollChild?.translationX = 0f
        recycleVelocity()
    }
}
