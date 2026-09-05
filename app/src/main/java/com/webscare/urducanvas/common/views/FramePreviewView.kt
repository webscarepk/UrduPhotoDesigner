package com.webscare.urducanvas.common.views

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.webscare.urducanvas.R
import kotlin.math.min

class FramePreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : android.view.View(context, attrs, defStyle) {

    private var canvasWidth = 1080f
    private var canvasHeight = 1920f
    private var iconDrawable: Drawable? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.contrast)
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.light_gray)
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(0.8f)
    }

    /** The framed preview fills this fraction of the view's shortest side. */
    private val frameScale = 0.50f
    private val cornerRadiusPx = dpToPx(3f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (canvasWidth <= 0 || canvasHeight <= 0) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val maxSizePx = min(viewW, viewH) * frameScale
        if (maxSizePx <= 0f) return

        // Scale canvas size down proportionally
        val scaleFactor = min(maxSizePx / canvasWidth, maxSizePx / canvasHeight)
        val frameW = canvasWidth * scaleFactor
        val frameH = canvasHeight * scaleFactor

        val left = (viewW - frameW) / 2
        val top = (viewH - frameH) / 2
        val right = left + frameW
        val bottom = top + frameH

        val inset = strokePaint.strokeWidth / 2f
        val rect = RectF(left, top, right, bottom)

        // Draw fill
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, fillPaint)
        // Draw border (inset so the stroke stays inside the frame bounds)
        rect.inset(inset, inset)
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, strokePaint)

        // Draw icon in the center
        iconDrawable?.let { icon ->
            val iconSize = min(frameW, frameH) * 0.58f
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

    /**
     * Recolours the frame and the icon in one call — used to mark the
     * selected size tile. [tintIcon] mutates a copy so shared drawables
     * from the resource cache are never tinted globally.
     */
    fun setAccentColor(color: Int) {
        strokePaint.color = color
        iconDrawable = iconDrawable?.mutate()?.also { DrawableCompat.setTint(it, color) }
        invalidate()
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }
}
