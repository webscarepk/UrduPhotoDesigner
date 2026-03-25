package com.webscare.urducanvas.common.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class RippleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    companion object {
        private const val RIPPLE_COUNT = 4
        private const val DURATION_MS = 3200L
        private const val STAGGER_MS = DURATION_MS / RIPPLE_COUNT
    }

    // Pure fill only — no stroke at all
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x1AFFFFFF.toInt()  // exactly 0x1A alpha (~10%) white
    }

    private val startTimes = LongArray(RIPPLE_COUNT) { index ->
        System.currentTimeMillis() - index * STAGGER_MS
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        // Subtract 1px so the outermost ring never touches the view boundary
        val maxRadius = (width.coerceAtLeast(height)) / 2f - 1f

        // Clip strictly to the view bounds so nothing bleeds outside
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())

        val now = System.currentTimeMillis()

        for (i in 0 until RIPPLE_COUNT) {
            val elapsed = (now - startTimes[i]) % DURATION_MS
            val progress = elapsed / DURATION_MS.toFloat()

            val eased = easeInOutCubic(progress)
            val radius = eased * maxRadius

            // Fade envelope: ramp in over 60%, ramp out over 40%
            val fade = when {
                progress < 0.6f -> progress / 0.6f
                else -> 1f - (progress - 0.6f) / 0.4f
            }

            // Scale the color's own 0x1A alpha by the fade envelope
            fillPaint.alpha = (0x1A * fade).toInt().coerceIn(0, 0x1A)
            canvas.drawCircle(cx, cy, radius, fillPaint)
        }

        postInvalidateOnAnimation()
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) 4f * t * t * t
        else 1f - (-2f * t + 2f).let { it * it * it } / 2f
    }
}