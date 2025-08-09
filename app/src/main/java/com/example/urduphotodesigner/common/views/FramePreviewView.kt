package com.example.urduphotodesigner.common.views

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.example.urduphotodesigner.R
import kotlin.math.min

class FramePreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var canvasWidth = 1080f
    private var canvasHeight = 1920f
    private var iconDrawable: Drawable? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.contrast)
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gray)
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(0.5f)
    }

    private val maxSizePx = dpToPx(32f)  // max width or height
    private val cornerRadiusPx = dpToPx(3f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (canvasWidth <= 0 || canvasHeight <= 0) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        // Scale canvas size down proportionally
        val scaleFactor = min(maxSizePx / canvasWidth, maxSizePx / canvasHeight)
        val frameW = canvasWidth * scaleFactor
        val frameH = canvasHeight * scaleFactor

        val left = (viewW - frameW) / 2
        val top = (viewH - frameH) / 2
        val right = left + frameW
        val bottom = top + frameH

        val rect = RectF(left, top, right, bottom)

        // Draw fill
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, fillPaint)
        // Draw border
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, strokePaint)

        // Draw icon in the center
        iconDrawable?.let { icon ->
            val iconSize = min(frameW, frameH) * 0.7f  // increased to 60%
            val iconLeft = (viewW - iconSize) / 2
            val iconTop = (viewH - iconSize) / 2
            icon.setBounds(
                iconLeft.toInt(),
                iconTop.toInt(),
                (iconLeft + iconSize).toInt(),
                (iconTop + iconSize).toInt()
            )
            icon.draw(canvas)
        }
    }

    fun setCanvasSize(width: Float, height: Float) {
        canvasWidth = width
        canvasHeight = height
        invalidate()
    }

    fun setIcon(drawable: Drawable) {
        iconDrawable = drawable
        invalidate()
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }
}