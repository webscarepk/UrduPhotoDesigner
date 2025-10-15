package com.example.urduphotodesigner.common.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.urduphotodesigner.common.canvas.enums.ShapeType
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object ShapeRenderUtils {
    fun drawShape(
        canvas: Canvas,
        paint: Paint,
        shapeType: ShapeType,
        rect: RectF,
        cornerRadius: Float = 0f
    ) {
        val path = Path()

        // Make it proportionally square and centered
        val cx = rect.centerX()
        val cy = rect.centerY()
        val minDimen = min(rect.width(), rect.height())
        val padding = minDimen * 0.1f
        val safeSize = minDimen - padding * 2
        val left = cx - safeSize / 2
        val top = cy - safeSize / 2
        val safeRect = RectF(left, top, left + safeSize, top + safeSize)

        when (shapeType) {
            ShapeType.RECTANGLE -> canvas.drawRect(safeRect, paint)
            ShapeType.ROUNDED_RECTANGLE -> canvas.drawRoundRect(safeRect, cornerRadius, cornerRadius, paint)
            ShapeType.CIRCLE, ShapeType.ELLIPSE -> canvas.drawOval(safeRect, paint)
            ShapeType.LINE -> canvas.drawLine(safeRect.left, cy, safeRect.right, cy, paint)
            ShapeType.TRIANGLE -> drawTriangle(canvas, paint, safeRect, cx)
            ShapeType.RIGHT_TRIANGLE -> drawRightTriangle(canvas, paint, safeRect)
            ShapeType.PARALLELOGRAM -> drawParallelogram(canvas, paint, safeRect)
            ShapeType.TRAPEZOID -> drawTrapezoid(canvas, paint, safeRect)
            ShapeType.PENTAGON -> drawRegularPolygon(canvas, 5, cx, cy, safeSize / 2, paint)
            ShapeType.HEXAGON -> drawRegularPolygon(canvas, 6, cx, cy, safeSize / 2, paint)
            ShapeType.OCTAGON -> drawRegularPolygon(canvas, 8, cx, cy, safeSize / 2, paint)
            ShapeType.STAR_FIVE -> drawStar(canvas, cx, cy, safeSize / 2, 5, paint)
            ShapeType.STAR_SIX -> drawStar(canvas, cx, cy, safeSize / 2, 6, paint)
            ShapeType.STAR_SEVEN -> drawStar(canvas, cx, cy, safeSize / 2, 7, paint)
            ShapeType.STAR_TEN -> drawStar(canvas, cx, cy, safeSize / 2, 10, paint)
            else -> drawSpecialShape(canvas, paint, shapeType, safeRect, cx, cy, safeSize / 2)
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

    private fun drawRegularPolygon(canvas: Canvas, sides: Int, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val path = Path()
        for (i in 0 until sides) {
            val angle = Math.toRadians((360.0 / sides * i - 90))
            val x = (cx + radius * cos(angle)).toFloat()
            val y = (cy + radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, points: Int, paint: Paint) {
        val path = Path()
        val innerR = radius / 2.5f
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

    private fun drawSpecialShape(
        canvas: Canvas,
        paint: Paint,
        shapeType: ShapeType,
        r: RectF,
        cx: Float,
        cy: Float,
        radius: Float
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
                path.cubicTo(r.left, r.bottom - radius, r.left, r.top, cx, r.top + radius / 2)
                path.cubicTo(r.right, r.top, r.right, r.bottom - radius, cx, r.bottom)
            }
            else -> {}
        }
        canvas.drawPath(path, paint)
    }
}