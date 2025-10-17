package com.example.urduphotodesigner.common.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import com.example.urduphotodesigner.common.canvas.enums.ShapeType
import kotlin.math.cos
import kotlin.math.sin

object ShapeRenderUtils {
    fun drawShape(
        canvas: Canvas, paint: Paint, shapeType: ShapeType, rect: RectF, cornerRadius: Float = 5f
    ) {
        val cx = rect.centerX()
        val cy = rect.centerY()

        val safeRect = rect

        when (shapeType) {
            // RECTANGLE is always sharp corners
            ShapeType.RECTANGLE ->
                canvas.drawRect(safeRect, paint)
            // ROUNDED_RECTANGLE uses the provided radius
            ShapeType.ROUNDED_RECTANGLE ->
                canvas.drawRoundRect(safeRect, cornerRadius, cornerRadius, paint)

            // ELLIPSE (Will look like a CIRCLE if rect is square)
            ShapeType.ELLIPSE -> canvas.drawOval(safeRect, paint)

            ShapeType.LINE -> canvas.drawLine(safeRect.left, cy, safeRect.right, cy, paint)

            ShapeType.ARROW_RIGHT -> drawArrow(canvas, paint, safeRect, isRight = true)
            ShapeType.ARROW_LEFT -> drawArrow(canvas, paint, safeRect, isRight = false)
            ShapeType.DOUBLE_ARROW -> drawDoubleArrow(canvas, paint, safeRect)

            ShapeType.TRIANGLE -> drawTriangle(canvas, paint, safeRect, cx)
            ShapeType.RIGHT_TRIANGLE -> drawRightTriangle(canvas, paint, safeRect)
            ShapeType.PARALLELOGRAM -> drawParallelogram(canvas, paint, safeRect)
            ShapeType.TRAPEZOID -> drawTrapezoid(canvas, paint, safeRect)

            ShapeType.PENTAGON -> drawRegularPolygon(canvas, 5, cx, cy, safeRect.width() / 2, paint)
            ShapeType.HEXAGON -> drawRegularPolygon(canvas, 6, cx, cy, safeRect.width() / 2, paint)
            ShapeType.OCTAGON -> drawRegularPolygon(canvas, 8, cx, cy, safeRect.width() / 2, paint)

            ShapeType.STAR_FIVE -> drawStar(canvas, cx, cy, safeRect.width() / 2, 5, paint)
            ShapeType.STAR_SIX -> drawStar(canvas, cx, cy, safeRect.width() / 2, 6, paint)
            ShapeType.STAR_SEVEN -> drawStar(canvas, cx, cy, safeRect.width() / 2, 7, paint)
            ShapeType.STAR_TEN -> drawStar(canvas, cx, cy, safeRect.width() / 2, 10, paint)

            else -> drawSpecialShape(
                canvas,
                paint,
                shapeType,
                safeRect,
                cx,
                cy,
            )
        }
    }

    private fun drawTriangle(canvas: Canvas, paint: Paint, r: RectF, cx: Float) {
        val path = Path().apply {
            moveTo(cx, r.top)
            lineTo(r.right, r.bottom)
            lineTo(r.left, r.bottom)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawRightTriangle(canvas: Canvas, paint: Paint, r: RectF) {
        val path = Path().apply {
            moveTo(r.left, r.top)
            lineTo(r.right, r.bottom)
            lineTo(r.left, r.bottom)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawParallelogram(canvas: Canvas, paint: Paint, r: RectF) {
        val offset = r.width() * 0.25f
        val path = Path().apply {
            moveTo(r.left + offset, r.top)
            lineTo(r.right, r.top)
            lineTo(r.right - offset, r.bottom)
            lineTo(r.left, r.bottom)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawTrapezoid(canvas: Canvas, paint: Paint, r: RectF) {
        val offset = r.width() * 0.2f
        val path = Path().apply {
            moveTo(r.left + offset, r.top)
            lineTo(r.right - offset, r.top)
            lineTo(r.right, r.bottom)
            lineTo(r.left, r.bottom)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawRegularPolygon(
        canvas: Canvas, sides: Int, cx: Float, cy: Float, radius: Float, paint: Paint
    ) {
        val path = Path()
        for (i in 0 until sides) {
            // Start at the top point (-90 degrees)
            val angle = Math.toRadians((360.0 / sides * i - 90))
            val x = (cx + radius * cos(angle)).toFloat()
            val y = (cy + radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawStar(
        canvas: Canvas, cx: Float, cy: Float, radius: Float, points: Int, paint: Paint
    ) {
        val path = Path()
        val innerR = radius / 2.5f
        // Ensure star starts pointing up (-90 degrees)
        val step = Math.PI / points
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) radius else innerR
            val angle = i * step - Math.PI / 2
            val x = (cx + r * cos(angle)).toFloat()
            val y = (cy + r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    // ===================================
    // FIX 2: Corrected Arrow Proportions to avoid stretching/compression
    // ===================================

    /**
     * Draws a block arrow (chevron style) within the bounding box.
     */
    private fun drawArrow(canvas: Canvas, paint: Paint, r: RectF, isRight: Boolean) {
        val path = Path()
        val cy = r.centerY()

        // Use a proportion relative to the dimensions to maintain aspect
        // Head length: 30% of total width
        val headLength = r.width() * 0.3f
        // Body thickness: 50% of total height
        val bodyInset = r.height() * 0.25f

        if (isRight) {
            // Right Arrow
            path.moveTo(r.left, cy - bodyInset)
            path.lineTo(r.right - headLength, cy - bodyInset)
            path.lineTo(r.right - headLength, r.top)
            path.lineTo(r.right, cy)
            path.lineTo(r.right - headLength, r.bottom)
            path.lineTo(r.right - headLength, cy + bodyInset)
            path.lineTo(r.left, cy + bodyInset)
        } else {
            // Left Arrow
            path.moveTo(r.right, cy - bodyInset)
            path.lineTo(r.left + headLength, cy - bodyInset)
            path.lineTo(r.left + headLength, r.top)
            path.lineTo(r.left, cy)
            path.lineTo(r.left + headLength, r.bottom)
            path.lineTo(r.left + headLength, cy + bodyInset)
            path.lineTo(r.right, cy + bodyInset)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    /**
     * Draws a double-headed block arrow (chevron style).
     */
    private fun drawDoubleArrow(canvas: Canvas, paint: Paint, r: RectF) {
        val path = Path()
        val cy = r.centerY()

        // Head Length is 25% of total width for each head (50% of total width is used for heads)
        val headLength = r.width() * 0.25f
        // Body Height is 50% of total height
        val bodyInset = r.height() * 0.25f

        path.moveTo(r.right, cy) // Start at Right Tip

        // Top side
        path.lineTo(r.right - headLength, r.top)
        path.lineTo(r.right - headLength, cy - bodyInset)
        path.lineTo(r.left + headLength, cy - bodyInset)
        path.lineTo(r.left + headLength, r.top)
        path.lineTo(r.left, cy) // Left Tip

        // Bottom side
        path.lineTo(r.left + headLength, r.bottom)
        path.lineTo(r.left + headLength, cy + bodyInset)
        path.lineTo(r.right - headLength, cy + bodyInset)
        path.lineTo(r.right - headLength, r.bottom)

        path.close()
        canvas.drawPath(path, paint)
    }
    // ===================================

    private fun drawSpecialShape(
        canvas: Canvas,
        paint: Paint,
        shapeType: ShapeType,
        r: RectF,
        cx: Float,
        cy: Float,
    ) {
        val path = Path()
        when (shapeType) {
            ShapeType.DIAMOND -> {
                path.moveTo(cx, r.top)
                path.lineTo(r.right, cy)
                path.lineTo(cx, r.bottom)
                path.lineTo(r.left, cy)
                path.close()
            }

            ShapeType.HEART -> {
                path.moveTo(cx, r.bottom)
                // Use the rect dimensions to define the heart shape
                val curveRadius = r.width() / 2f
                path.cubicTo(
                    r.left, r.bottom - curveRadius, r.left, r.top, cx, r.top + curveRadius / 2f
                )
                path.cubicTo(r.right, r.top, r.right, r.bottom - curveRadius, cx, r.bottom)
            }

            else -> {}
        }
        if (!path.isEmpty) {
            canvas.drawPath(path, paint)
        }
    }
}