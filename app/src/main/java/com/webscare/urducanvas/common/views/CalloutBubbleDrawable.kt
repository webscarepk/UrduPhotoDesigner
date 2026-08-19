package com.webscare.urducanvas.common.views

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable

enum class CalloutArrowDirection {
    NONE,
    TOP,
    BOTTOM
}

class CalloutBubbleDrawable(
    private var fillColor: Int,
    private var strokeColor: Int,
    private var strokeWidth: Float = 2f,
    private var cornerRadius: Float = 24f,
    private var arrowWidth: Float = 28f,
    private var arrowHeight: Float = 14f,
    var arrowDirection: CalloutArrowDirection = CalloutArrowDirection.NONE,
    var arrowX: Float = 0f
) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = strokeColor
        strokeWidth = this@CalloutBubbleDrawable.strokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val path = Path()

    fun update(
        fillColor: Int = this.fillColor,
        strokeColor: Int = this.strokeColor,
        arrowDirection: CalloutArrowDirection = this.arrowDirection,
        arrowX: Float = this.arrowX
    ) {
        this.fillColor = fillColor
        this.strokeColor = strokeColor
        this.arrowDirection = arrowDirection
        this.arrowX = arrowX
        fillPaint.color = fillColor
        strokePaint.color = strokeColor
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        buildPath(bounds)
    }

    private fun buildPath(bounds: Rect) {
        path.reset()
        val halfStroke = strokeWidth / 2f
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0 || h <= 0) return

        val r = cornerRadius

        when (arrowDirection) {
            CalloutArrowDirection.TOP -> {
                val cardLeft = halfStroke
                val cardTop = arrowHeight + halfStroke
                val cardRight = w - halfStroke
                val cardBottom = h - halfStroke

                val halfArrow = arrowWidth / 2f
                val clampedArrowX = arrowX.coerceIn(cardLeft + r + halfArrow, cardRight - r - halfArrow)

                path.moveTo(cardLeft + r, cardTop)
                path.lineTo(clampedArrowX - halfArrow, cardTop)
                path.lineTo(clampedArrowX, halfStroke)
                path.lineTo(clampedArrowX + halfArrow, cardTop)
                path.lineTo(cardRight - r, cardTop)
                path.arcTo(RectF(cardRight - 2 * r, cardTop, cardRight, cardTop + 2 * r), 270f, 90f, false)
                path.lineTo(cardRight, cardBottom - r)
                path.arcTo(RectF(cardRight - 2 * r, cardBottom - 2 * r, cardRight, cardBottom), 0f, 90f, false)
                path.lineTo(cardLeft + r, cardBottom)
                path.arcTo(RectF(cardLeft, cardBottom - 2 * r, cardLeft + 2 * r, cardBottom), 90f, 90f, false)
                path.lineTo(cardLeft, cardTop + r)
                path.arcTo(RectF(cardLeft, cardTop, cardLeft + 2 * r, cardTop + 2 * r), 180f, 90f, false)
                path.close()
            }
            CalloutArrowDirection.BOTTOM -> {
                val cardLeft = halfStroke
                val cardTop = halfStroke
                val cardRight = w - halfStroke
                val cardBottom = h - arrowHeight - halfStroke

                val halfArrow = arrowWidth / 2f
                val clampedArrowX = arrowX.coerceIn(cardLeft + r + halfArrow, cardRight - r - halfArrow)

                path.moveTo(cardLeft + r, cardTop)
                path.lineTo(cardRight - r, cardTop)
                path.arcTo(RectF(cardRight - 2 * r, cardTop, cardRight, cardTop + 2 * r), 270f, 90f, false)
                path.lineTo(cardRight, cardBottom - r)
                path.arcTo(RectF(cardRight - 2 * r, cardBottom - 2 * r, cardRight, cardBottom), 0f, 90f, false)
                path.lineTo(clampedArrowX + halfArrow, cardBottom)
                path.lineTo(clampedArrowX, h - halfStroke)
                path.lineTo(clampedArrowX - halfArrow, cardBottom)
                path.lineTo(cardLeft + r, cardBottom)
                path.arcTo(RectF(cardLeft, cardBottom - 2 * r, cardLeft + 2 * r, cardBottom), 90f, 90f, false)
                path.lineTo(cardLeft, cardTop + r)
                path.arcTo(RectF(cardLeft, cardTop, cardLeft + 2 * r, cardTop + 2 * r), 180f, 90f, false)
                path.close()
            }
            CalloutArrowDirection.NONE -> {
                val cardLeft = halfStroke
                val cardTop = halfStroke
                val cardRight = w - halfStroke
                val cardBottom = h - halfStroke

                path.addRoundRect(
                    RectF(cardLeft, cardTop, cardRight, cardBottom),
                    r, r,
                    Path.Direction.CW
                )
            }
        }
    }

    override fun draw(canvas: Canvas) {
        buildPath(bounds)
        canvas.drawPath(path, fillPaint)
        if (strokeWidth > 0) {
            canvas.drawPath(path, strokePaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
