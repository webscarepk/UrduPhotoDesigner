package com.webscare.urducanvas.common.utils

import android.widget.EdgeEffect
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView

/**
 * Adds an iOS-style spring/stretch overscroll to any RecyclerView
 * WITHOUT breaking view recycling. Just set:
 *   recyclerView.edgeEffectFactory = SpringEdgeEffectFactory()
 */
class SpringEdgeEffectFactory : RecyclerView.EdgeEffectFactory() {

    override fun createEdgeEffect(rv: RecyclerView, direction: Int): EdgeEffect = object : EdgeEffect(rv.context) {

        private var translationAnim: SpringAnimation? = null

        // pull = drag overscroll; deltaDistance is fraction of size pulled
        override fun onPull(deltaDistance: Float) {
            super.onPull(deltaDistance)
            handlePull(deltaDistance)
        }

        override fun onPull(deltaDistance: Float, displacement: Float) {
            super.onPull(deltaDistance, displacement)
            handlePull(deltaDistance)
        }

        private fun handlePull(deltaDistance: Float) {
            // top edge pulls content down (+), bottom edge pulls up (−)
            val sign = if (direction == DIRECTION_BOTTOM) -1 else 1
            val push = deltaDistance * rv.height * 0.3f * sign
            rv.translationY += push
            translationAnim?.cancel()
        }

        override fun onRelease() {
            super.onRelease()
            if (rv.translationY != 0f) {
                translationAnim = SpringAnimation(rv, SpringAnimation.TRANSLATION_Y, 0f).apply {
                    spring = SpringForce(0f).apply {
                        dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                        stiffness = SpringForce.STIFFNESS_LOW
                    }
                    start()
                }
            }
        }

        override fun onAbsorb(velocity: Int) {
            super.onAbsorb(velocity)
            val sign = if (direction == DIRECTION_BOTTOM) -1 else 1
            translationAnim = SpringAnimation(rv, SpringAnimation.TRANSLATION_Y, 0f).apply {
                spring = SpringForce(0f).apply {
                    dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                    stiffness = SpringForce.STIFFNESS_LOW
                }
                setStartVelocity(velocity * 0.3f * sign)
                start()
            }
        }

        override fun draw(canvas: android.graphics.Canvas?): Boolean = false // koi glow nahi
        override fun isFinished(): Boolean = translationAnim?.isRunning != true
    }
}

class HorizontalSpringEdgeEffectFactory : RecyclerView.EdgeEffectFactory() {

    override fun createEdgeEffect(rv: RecyclerView, direction: Int): EdgeEffect {
        val isHorizontal = direction == DIRECTION_LEFT || direction == DIRECTION_RIGHT
        if (!isHorizontal) {
            return object : EdgeEffect(rv.context) {
                override fun onPull(deltaDistance: Float, displacement: Float) {}
                override fun onRelease() {}
                override fun onAbsorb(velocity: Int) {}
                override fun draw(canvas: android.graphics.Canvas) = false
                override fun isFinished(): Boolean = true
            }
        }
        return object : EdgeEffect(rv.context) {
            private var translationAnim: SpringAnimation? = null
            private val sign = if (direction == DIRECTION_LEFT) 1f else -1f
            private val maxTranslation: Float get() = rv.width * 0.42f

            override fun onPull(deltaDistance: Float) {
                super.onPull(deltaDistance)
                handlePull(deltaDistance)
            }

            override fun onPull(deltaDistance: Float, displacement: Float) {
                super.onPull(deltaDistance, displacement)
                handlePull(deltaDistance)
            }

            private fun handlePull(deltaDistance: Float) {
                val delta = deltaDistance * maxTranslation * 0.45f * sign
                rv.translationX = (rv.translationX + delta).coerceIn(-maxTranslation, maxTranslation)
                translationAnim?.cancel()
            }

            override fun onRelease() {
                super.onRelease()
                springBack()
            }

            override fun onAbsorb(velocity: Int) {
                super.onAbsorb(velocity)
                springBack(velocity * sign * 0.45f)
            }

            private fun springBack(startVelocity: Float = 0f) {
                translationAnim?.cancel()
                translationAnim = SpringAnimation(rv, SpringAnimation.TRANSLATION_X, 0f).apply {
                    spring = SpringForce(0f).apply {
                        dampingRatio = SpringForce.DAMPING_RATIO_HIGH_BOUNCY
                        stiffness = SpringForce.STIFFNESS_LOW
                    }
                    setStartVelocity(startVelocity)
                    start()
                }
            }

            override fun draw(canvas: android.graphics.Canvas?): Boolean = false
            override fun isFinished(): Boolean = translationAnim?.isRunning != true
        }
    }
}
