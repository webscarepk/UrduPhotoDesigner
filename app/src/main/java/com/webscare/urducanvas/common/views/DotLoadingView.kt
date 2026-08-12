package com.webscare.urducanvas.common.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.webscare.urducanvas.R
import kotlin.math.sin
import androidx.core.graphics.toColorInt

class DotLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dotCount = 3
    private val appColor: Int by lazy {
        try {
            ContextCompat.getColor(context, R.color.appColor)
        } catch (e: Exception) {
            "#005D28".toColorInt()
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var animValue = 0f
    private var animator: ValueAnimator? = null

    init {
        setupAnimator()
    }

    private fun setupAnimator() {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                animValue = anim.animatedValue as Float
                invalidate()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) {
            animator?.start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            if (animator?.isStarted != true) {
                animator?.start()
            }
        } else {
            animator?.cancel()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val dotRadius = (h / 4f).coerceAtMost(w / (dotCount * 3f))
        val totalDotWidth = dotCount * (dotRadius * 2)
        val spacing = (w - totalDotWidth) / (dotCount + 1)
        val centerY = h / 2f
        val maxBounce = h / 4f

        for (i in 0 until dotCount) {
            val cx = spacing * (i + 1) + dotRadius * (i * 2 + 1)
            val phase = (animValue + i * 0.25f) % 1.0f
            val bounceOffset = (sin(phase * 2 * Math.PI) * maxBounce).toFloat()
            val cy = centerY - bounceOffset

            // Smooth alpha pulsing along with vertical bounce
            val alphaFraction = 0.4f + 0.6f * ((sin(phase * 2 * Math.PI) + 1f) / 2f)
            paint.color = appColor
            paint.alpha = (alphaFraction * 255).toInt().coerceIn(0, 255)

            canvas.drawCircle(cx, cy, dotRadius, paint)
        }
    }
}
