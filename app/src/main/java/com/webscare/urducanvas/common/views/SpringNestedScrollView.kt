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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

class SpringNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : NestedScrollView(context, attrs, defStyle) {

    private val MAX_OVERSCROLL_FRACTION        = 0.22f
    private val BASE_SPRING_DURATION           = 550L
    private val FLING_SCALE                    = 0.08f
    private val MAX_FLING_TRANSLATION_FRACTION = 0.22f

    private var velocityTracker: VelocityTracker? = null
    private var lastY              = 0f
    private var isBouncing         = false
    private var springAnimator: ValueAnimator? = null
    private var savedFlingVelocity = 0f
    private var lastScrollY        = 0
    private var flingSettleChecker: Runnable? = null

    private val scrollChild    get() = getChildAt(0)
    private val isAtTop        get() = scrollY <= 0
    private val isAtBottom     get() = scrollChild != null &&
            scrollY >= (scrollChild.height - height).coerceAtLeast(0)
    private val maxTranslation get() = height * MAX_OVERSCROLL_FRACTION

    // ── spring interpolator (damped harmonic oscillator) ──────────────────────
    //
    //  Solves:  x(t) = 1 - e^(-ζωt) * [cos(ωdt) + (ζ/√(1-ζ²))·sin(ωdt)]
    //
    //  stiffness  → how fast it oscillates (higher = snappier)
    //  damping    → how quickly oscillations die out
    //              < 1.0 = underdamped (bounces)   ← we want this
    //             == 1.0 = critically damped (no bounce)
    //              > 1.0 = overdamped (sluggish)
    //
    private inner class SpringInterpolator(
        private val stiffness: Float = 260f,   // tune: higher = faster oscillation
        private val damping:   Float = 18f     // tune: lower  = more bounces
    ) : Interpolator {
        override fun getInterpolation(t: Float): Float {
            val omega0 = sqrt(stiffness)           // natural frequency
            val zeta   = damping / (2f * omega0)   // damping ratio

            return if (zeta < 1f) {
                // underdamped — produces the bounce overshoot
                val omegaD = omega0 * sqrt(1f - zeta * zeta)
                val scale  = zeta / sqrt(1f - zeta * zeta)
                1f - exp(-zeta * omega0 * t) *
                        (cos(omegaD * t) + scale * sin(omegaD * t))
            } else {
                // fallback: critically damped (no bounce) — shouldn't hit this
                // with the default values but keeps it safe
                1f - exp(-omega0 * t) * (1f + omega0 * t)
            }
        }
    }

    // ── touch ─────────────────────────────────────────────────────────────────

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)
        if (ev.action == MotionEvent.ACTION_DOWN) lastY = ev.rawY
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                springAnimator?.cancel()
                flingSettleChecker?.let { removeCallbacks(it) }
                flingSettleChecker = null
                savedFlingVelocity = 0f
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
                val vy = captureVelocityY()
                recycleVelocity()

                if (isBouncing || abs(translationY) > 0f) {
                    if (abs(vy) > 100f && sign(vy) == sign(translationY)) {
                        val extra  = (vy * FLING_SCALE).coerceIn(-maxTranslation, maxTranslation)
                        val target = (translationY + extra).coerceIn(-maxTranslation, maxTranslation)
                        animateTo(target) { springBack() }
                    } else {
                        springBack()
                    }
                    return true
                }

                if (isAtTop && vy > 300f) {
                    val target = (vy * FLING_SCALE).coerceAtMost(height * MAX_FLING_TRANSLATION_FRACTION)
                    isBouncing = true
                    animateTo(target) { springBack() }
                    return true
                }
                if (isAtBottom && vy < -300f) {
                    val target = (vy * FLING_SCALE).coerceAtLeast(-height * MAX_FLING_TRANSLATION_FRACTION)
                    isBouncing = true
                    animateTo(target) { springBack() }
                    return true
                }
            }
        }

        return super.onTouchEvent(ev)
    }

    // ── fling settle detection ────────────────────────────────────────────────

    override fun fling(velocityY: Int) {
        savedFlingVelocity = velocityY.toFloat()
        lastScrollY = scrollY
        super.fling(velocityY)
        scheduleFlingSettleCheck()
    }

    private fun scheduleFlingSettleCheck() {
        flingSettleChecker?.let { removeCallbacks(it) }

        val checker = object : Runnable {
            override fun run() {
                val currentScrollY = scrollY
                val stopped = currentScrollY == lastScrollY

                if (stopped) {
                    val vy = savedFlingVelocity
                    savedFlingVelocity = 0f
                    flingSettleChecker = null

                    if (!isBouncing && springAnimator?.isRunning != true) {
                        when {
                            isAtTop && vy < -300f -> {
                                val target = (abs(vy) * FLING_SCALE)
                                    .coerceAtMost(height * MAX_FLING_TRANSLATION_FRACTION)
                                isBouncing = true
                                animateTo(target) { springBack() }
                            }
                            isAtBottom && vy > 300f -> {
                                val target = -(abs(vy) * FLING_SCALE)
                                    .coerceAtMost(height * MAX_FLING_TRANSLATION_FRACTION)
                                isBouncing = true
                                animateTo(target) { springBack() }
                            }
                        }
                    }
                } else {
                    lastScrollY = currentScrollY
                    postDelayed(this, 16)
                }
            }
        }

        flingSettleChecker = checker
        postDelayed(checker, 16)
    }

    // ── rubber-band drag ──────────────────────────────────────────────────────

    private fun applyRubberBand(delta: Float) {
        isBouncing = true
        val ratio      = (abs(translationY) / maxTranslation).coerceIn(0f, 1f)
        val resistance = 1f - Math.pow(ratio.toDouble(), 0.7).toFloat()
        translationY   = (translationY + delta * resistance)
            .coerceIn(-maxTranslation, maxTranslation)
    }

    // ── animation ─────────────────────────────────────────────────────────────

    // Push content to [target] with a fast decelerate, then spring back.
    // We use a short DecelerateInterpolator here — the spring feel comes
    // entirely from springBack() which uses SpringInterpolator.
    private fun animateTo(target: Float, onEnd: (() -> Unit)? = null) {
        springAnimator?.cancel()
        val start    = translationY
        val dist     = abs(target - start)
        val duration = (200L * (dist / maxTranslation).coerceIn(0.3f, 1f)).toLong()

        springAnimator = ValueAnimator.ofFloat(start, target).apply {
            this.duration = duration
            interpolator  = android.view.animation.DecelerateInterpolator(2f)
            addUpdateListener { translationY = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    translationY = target
                    onEnd?.invoke()
                }
            })
            start()
        }
    }

    // Spring back to 0 using the damped harmonic oscillator interpolator.
    // This is where the actual bouncy feel comes from.
    private fun springBack() {
        springAnimator?.cancel()
        val start = translationY
        if (abs(start) < 0.5f) { translationY = 0f; isBouncing = false; return }

        // Duration scales with displacement so bigger stretches feel heavier
        val distFraction = (abs(start) / maxTranslation).coerceIn(0.4f, 1f)
        val duration     = (BASE_SPRING_DURATION * distFraction).toLong()

        springAnimator = ValueAnimator.ofFloat(start, 0f).apply {
            this.duration = duration
            interpolator  = SpringInterpolator(stiffness = 260f, damping = 18f)
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

    // ── velocity tracker ──────────────────────────────────────────────────────

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

    // ── cleanup ───────────────────────────────────────────────────────────────

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        springAnimator?.cancel()
        flingSettleChecker?.let { removeCallbacks(it) }
        recycleVelocity()
    }
}