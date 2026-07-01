package com.webscare.urducanvas.common.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.EdgeEffect
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.EdgeEffectFactory

/**
 * A horizontally-scrolling RecyclerView (LinearLayoutManager/HORIZONTAL or
 * similar) that swaps the default overscroll edge-glow for a rubber-band
 * spring effect, applied as translationX on the whole RecyclerView.
 *
 * RecyclerView doesn't expose a single onOverScrolled() callback the way
 * NestedScrollView does — it drives overscroll through an EdgeEffect per
 * edge instead. So instead of translating a "content child" on touch/fling
 * like SpringNestedScrollView does, this overrides EdgeEffectFactory for
 * the left/right edges and translates the RecyclerView itself.
 *
 * Damping is looser than SpringNestedScrollView's stock value on purpose —
 * DAMPING_RATIO_HIGH_BOUNCY gives it more oscillation, and the larger pull
 * multiplier/max-overscroll let it travel further per drag before
 * resisting. Stiffness is kept at STIFFNESS_LOW to match
 * SpringNestedScrollView's snap-back speed — damping controls how bouncy/
 * loose it feels, stiffness controls how fast it returns to rest; those are
 * independent, so loosening one didn't need loosening the other.
 *
 * Horizontal only — if you need this for a vertically-scrolling list too,
 * that's a separate class (don't reuse this one for a vertical RV, the top/
 * bottom edges are intentionally left as plain no-op EdgeEffects here).
 *
 * Usage: just use this class in place of a plain RecyclerView in XML, with
 * a horizontal LayoutManager and overScrollMode="always" — no other setup
 * needed.
 */
class SpringRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {

    private var springAnim: SpringAnimation? = null

    init {
        edgeEffectFactory = object : EdgeEffectFactory() {
            override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
                val isHorizontalEdge =
                    direction == EdgeEffectFactory.DIRECTION_LEFT || direction == EdgeEffectFactory.DIRECTION_RIGHT
                return if (isHorizontalEdge) {
                    SpringEdgeEffect(direction)
                } else {
                    // Not expected for a horizontal RV, but fall back to a
                    // plain no-op EdgeEffect instead of the default glow.
                    object : EdgeEffect(context) {
                        override fun onPull(deltaDistance: Float, displacement: Float) {}
                        override fun onRelease() {}
                        override fun onAbsorb(velocity: Int) {}
                        override fun draw(canvas: Canvas) = false
                        override fun isFinished(): Boolean = true
                    }
                }
            }
        }
    }

    private inner class SpringEdgeEffect(direction: Int) : EdgeEffect(context) {

        // Overscrolling at the start (finger dragging further right, past
        // the first item) should translate content right — same direction
        // as the drag, matching SpringNestedScrollView (pulling down at the
        // top moves content down, not up).
        private val sign = if (direction == EdgeEffectFactory.DIRECTION_LEFT) 1f else -1f

        private val maxTranslation: Float
            get() = width * 0.42f

        override fun onPull(deltaDistance: Float, displacement: Float) {
            springAnim?.cancel()
            val delta = deltaDistance * maxTranslation * 0.45f * sign
            translationX = (translationX + delta).coerceIn(-maxTranslation, maxTranslation)
        }

        override fun onRelease() = springBack()

        override fun onAbsorb(velocity: Int) = springBack(velocity * sign * 0.45f)

        private fun springBack(startVelocity: Float = 0f) {
            springAnim?.cancel()
            springAnim = SpringAnimation(this@SpringRecyclerView, SpringAnimation.TRANSLATION_X, 0f).apply {
                spring = SpringForce(0f).apply {
                    dampingRatio = SpringForce.DAMPING_RATIO_HIGH_BOUNCY
                    stiffness = SpringForce.STIFFNESS_LOW
                }
                setStartVelocity(startVelocity)
                start()
            }
        }

        // We render the bounce via translationX on the RecyclerView itself
        // (through the spring above), not the EdgeEffect's own glow
        // drawable — so tell it there's nothing to draw/animate.
        override fun draw(canvas: Canvas) = false
        override fun isFinished(): Boolean = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        springAnim?.cancel()
        translationX = 0f
    }
}