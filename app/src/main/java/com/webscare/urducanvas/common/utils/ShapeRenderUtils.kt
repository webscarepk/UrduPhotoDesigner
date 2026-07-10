package com.webscare.urducanvas.common.utils

import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.webscare.urducanvas.common.canvas.enums.ShapeType
import kotlin.math.cos
import kotlin.math.sin

object ShapeRenderUtils {

    /**
     * Shapes where CornerPathEffect has no meaningful effect:
     * already smooth curves, or open/degenerate paths.
     */
    val NON_ROUNDABLE = setOf(
        ShapeType.ELLIPSE,
        ShapeType.LINE,
        ShapeType.HEART,
        ShapeType.RECTANGLE,
        ShapeType.ROUNDED_RECTANGLE,
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Applies [CornerPathEffect] with [radius] to [paint] for the duration of [block],
     * then restores the original PathEffect.
     *
     * Call this in **CanvasView.drawShapeElement** on every paint (shadow/fill/stroke)
     * before drawing the path, so that corner rounding works for ALL shape types.
     *
     * Skipped automatically when:
     *  - [radius] is 0 or negative
     *  - [shapeType] is in [NON_ROUNDABLE] (already round or irrelevant)
     */
    inline fun withCornerEffect(paint: Paint, radius: Float, shapeType: ShapeType, block: () -> Unit) {
        if (radius <= 0f || shapeType in NON_ROUNDABLE) {
            block()
            return
        }
        val original = paint.pathEffect
        paint.pathEffect = CornerPathEffect(radius)
        block()
        paint.pathEffect = original
    }

    fun drawShape(canvas: Canvas, paint: Paint, shapeType: ShapeType, rect: RectF, cornerRadius: Float = 0f) {
        val cy = rect.centerY()

        when (shapeType) {
            ShapeType.RECTANGLE,
            ShapeType.ROUNDED_RECTANGLE,
            ->
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

            ShapeType.ELLIPSE -> canvas.drawOval(rect, paint)

            ShapeType.LINE ->
                canvas.drawLine(rect.left, cy, rect.right, cy, paint)

            else -> {
                val path = buildShapePath(shapeType, rect)
                withCornerEffect(paint, cornerRadius, shapeType) {
                    canvas.drawPath(path, paint)
                }
            }
        }
    }

    /**
     * Builds the raw [Path] for a shape. Corner rounding is NOT baked in here
     * (except for RECTANGLE / ROUNDED_RECTANGLE which use [Path.addRoundRect]).
     * For all other shapes, apply rounding at draw-time via [withCornerEffect].
     */
    fun buildShapePath(shapeType: ShapeType, rect: RectF, cornerRadius: Float = 0f): Path {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val path = Path()

        when (shapeType) {
            ShapeType.RECTANGLE ->
                path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)

            ShapeType.ROUNDED_RECTANGLE ->
                path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)

            ShapeType.ELLIPSE ->
                path.addOval(rect, Path.Direction.CW)

            ShapeType.LINE -> {
                path.moveTo(rect.left, cy)
                path.lineTo(rect.right, cy)
            }

            ShapeType.TRIANGLE -> {
                path.moveTo(cx, rect.top)
                path.lineTo(rect.right, rect.bottom)
                path.lineTo(rect.left, rect.bottom)
                path.close()
            }

            ShapeType.RIGHT_TRIANGLE -> {
                path.moveTo(rect.left, rect.top)
                path.lineTo(rect.right, rect.bottom)
                path.lineTo(rect.left, rect.bottom)
                path.close()
            }

            ShapeType.PARALLELOGRAM -> {
                val offset = rect.width() * 0.25f
                path.moveTo(rect.left + offset, rect.top)
                path.lineTo(rect.right, rect.top)
                path.lineTo(rect.right - offset, rect.bottom)
                path.lineTo(rect.left, rect.bottom)
                path.close()
            }

            ShapeType.TRAPEZOID -> {
                val offset = rect.width() * 0.2f
                path.moveTo(rect.left + offset, rect.top)
                path.lineTo(rect.right - offset, rect.top)
                path.lineTo(rect.right, rect.bottom)
                path.lineTo(rect.left, rect.bottom)
                path.close()
            }

            ShapeType.PENTAGON -> buildPolygonPath(path, 5, cx, cy, rect.width() / 2)
            ShapeType.HEXAGON -> buildPolygonPath(path, 6, cx, cy, rect.width() / 2)
            ShapeType.OCTAGON -> buildPolygonPath(path, 8, cx, cy, rect.width() / 2)

            ShapeType.STAR_FIVE -> buildStarPath(path, cx, cy, rect.width() / 2, 5)
            ShapeType.STAR_SIX -> buildStarPath(path, cx, cy, rect.width() / 2, 6)
            ShapeType.STAR_SEVEN -> buildStarPath(path, cx, cy, rect.width() / 2, 7)
            ShapeType.STAR_TEN -> buildStarPath(path, cx, cy, rect.width() / 2, 10)

            ShapeType.DIAMOND -> {
                path.moveTo(cx, rect.top)
                path.lineTo(rect.right, cy)
                path.lineTo(cx, rect.bottom)
                path.lineTo(rect.left, cy)
                path.close()
            }

            ShapeType.ARROW_RIGHT -> {
                val h = rect.width() * 0.3f
                val b = rect.height() * 0.25f
                path.moveTo(rect.left, cy - b)
                path.lineTo(rect.right - h, cy - b)
                path.lineTo(rect.right - h, rect.top)
                path.lineTo(rect.right, cy)
                path.lineTo(rect.right - h, rect.bottom)
                path.lineTo(rect.right - h, cy + b)
                path.lineTo(rect.left, cy + b)
                path.close()
            }

            ShapeType.ARROW_LEFT -> {
                val h = rect.width() * 0.3f
                val b = rect.height() * 0.25f
                path.moveTo(rect.right, cy - b)
                path.lineTo(rect.left + h, cy - b)
                path.lineTo(rect.left + h, rect.top)
                path.lineTo(rect.left, cy)
                path.lineTo(rect.left + h, rect.bottom)
                path.lineTo(rect.left + h, cy + b)
                path.lineTo(rect.right, cy + b)
                path.close()
            }

            ShapeType.DOUBLE_ARROW -> {
                val h = rect.width() * 0.25f
                val b = rect.height() * 0.25f
                path.moveTo(rect.right, cy)
                path.lineTo(rect.right - h, rect.top)
                path.lineTo(rect.right - h, cy - b)
                path.lineTo(rect.left + h, cy - b)
                path.lineTo(rect.left + h, rect.top)
                path.lineTo(rect.left, cy)
                path.lineTo(rect.left + h, rect.bottom)
                path.lineTo(rect.left + h, cy + b)
                path.lineTo(rect.right - h, cy + b)
                path.lineTo(rect.right - h, rect.bottom)
                path.close()
            }

            ShapeType.HEART -> {
                val curveRadius = rect.width() / 2f
                path.moveTo(cx, rect.bottom)
                path.cubicTo(
                    rect.left,
                    rect.bottom - curveRadius,
                    rect.left,
                    rect.top,
                    cx,
                    rect.top + curveRadius / 2f,
                )
                path.cubicTo(
                    rect.right,
                    rect.top,
                    rect.right,
                    rect.bottom - curveRadius,
                    cx,
                    rect.bottom,
                )
            }
        }

        return path
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildPolygonPath(path: Path, sides: Int, cx: Float, cy: Float, radius: Float) {
        for (i in 0 until sides) {
            val angle = Math.toRadians(360.0 / sides * i - 90)
            val x = (cx + radius * cos(angle)).toFloat()
            val y = (cy + radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private fun buildStarPath(path: Path, cx: Float, cy: Float, radius: Float, points: Int) {
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
    }

    /**
     * Computes the actual visual bounds of a shape based on its type and logical dimensions.
     *
     * For shapes like LINE, the visual bounds are NOT the same as the logical rect —
     * a line is just a horizontal stroke, so its tight visual height equals the stroke thickness,
     * not the full logical rect height.
     *
     * @param shapeType the shape type
     * @param logicalW  the logical content width (full rect width)
     * @param logicalH  the logical content height (full rect height)
     * @param strokeWidth the shape's stroke thickness (for line-like shapes)
     * @return tight visual bounds centered at (0,0)
     */
    fun computeVisualBounds(shapeType: ShapeType, logicalW: Float, logicalH: Float, strokeWidth: Float = 0f): RectF = when (shapeType) {
        // LINE: only the stroke thickness has visual presence vertically.
        ShapeType.LINE -> {
            val halfH = (strokeWidth.coerceAtLeast(1f)) / 2f
            RectF(-logicalW / 2f, -halfH, logicalW / 2f, halfH)
        }

        // Polygons & stars: drawn as inscribed in width — height equals width visually.
        ShapeType.PENTAGON,
        ShapeType.HEXAGON,
        ShapeType.OCTAGON,
        ShapeType.STAR_FIVE,
        ShapeType.STAR_SIX,
        ShapeType.STAR_SEVEN,
        ShapeType.STAR_TEN,
        -> {
            // These use rect.width()/2 as radius — bounds = square of side = width.
            val side = logicalW
            RectF(-side / 2f, -side / 2f, side / 2f, side / 2f)
        }

        // Everything else uses full logical rect.
        else -> RectF(-logicalW / 2f, -logicalH / 2f, logicalW / 2f, logicalH / 2f)
    }
}
