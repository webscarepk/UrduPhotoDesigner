package com.webscare.urducanvas.common.utils

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.common.canvas.enums.GradientType
import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.common.canvas.model.StrokeData
import java.util.Random
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

object BrushRenderUtils {
    fun createBackgroundGradientShader(
        gradientItem: GradientItem, width: Float, height: Float
    ): Shader {
        val colors = gradientItem.colors.toIntArray()
        val positions = gradientItem.positions.toFloatArray()

        // compute actual center from relative values
        val cx = width * gradientItem.centerX
        val cy = height * gradientItem.centerY

        val baseShader = when (gradientItem.type) {
            GradientType.LINEAR -> {
                // angle in radians
                val theta = Math.toRadians(gradientItem.angle.toDouble())
                // full hypotenuse scaled, half on each side
                val halfLen = (hypot(width, height) * gradientItem.scale / 2f)
                val dx = (cos(theta) * halfLen).toFloat()
                val dy = (sin(theta) * halfLen).toFloat()

                LinearGradient(
                    cx - dx, cy - dy, cx + dx, cy + dy, colors, positions, Shader.TileMode.CLAMP
                )
            }

            GradientType.RADIAL -> {
                // radius based on the smaller dimension
                val radius =
                    min(width, height) / 2f * gradientItem.radialRadiusFactor * gradientItem.scale
                RadialGradient(
                    cx, cy, radius, colors, positions, Shader.TileMode.CLAMP
                )
            }

            GradientType.SWEEP -> {
                SweepGradient(cx, cy, colors, positions).apply {
                    // rotate start angle around the chosen center
                    val m = Matrix().apply {
                        postRotate(gradientItem.sweepStartAngle, cx, cy)
                    }
                    setLocalMatrix(m)
                }
            }
        }

        return baseShader
    }

    fun makeStrokePaint(stroke: StrokeData, width: Int, height: Int): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = stroke.thickness

            // 🌈 Gradient or solid color
            stroke.gradient?.let {
                shader = createBackgroundGradientShader(it, width.toFloat(), height.toFloat())
            } ?: run { color = stroke.color }

            val hardness = stroke.hardness.coerceIn(0f, 1f)
            // 🖌️ Style-dependent look
            when (stroke.style) {
                BrushStyle.PENCIL -> {
                    alpha = (140 + 100 * hardness).toInt()
                    pathEffect = DashPathEffect(floatArrayOf(3f, 3f, 1f, 2f), 0f)
                    maskFilter = null
                }

                BrushStyle.MARKER -> {
                    strokeCap = Paint.Cap.SQUARE
                    alpha = (100 + 140 * hardness).toInt()
                    maskFilter = null
                }

                BrushStyle.HIGHLIGHTER -> {
                    strokeCap = Paint.Cap.BUTT
                    alpha = (60 + 100 * hardness).toInt()
                    maskFilter = null
                }

                BrushStyle.AIRBRUSH -> {
                    val blurRadius = (stroke.thickness * 0.45f).coerceAtLeast(1f)
                    maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
                    alpha = (100 + 120 * hardness).toInt()
                }

                BrushStyle.CHALK -> {
                    alpha = (160 + 80 * hardness).toInt()
                    pathEffect = DashPathEffect(floatArrayOf(2f, 3f, 4f, 2f, 1f, 3f), 0f)
                    maskFilter = null
                }

                BrushStyle.CHARCOAL -> {
                    alpha = (180 + 75 * hardness).toInt()
                    pathEffect = DashPathEffect(floatArrayOf(4f, 2f, 6f, 3f, 2f, 1f), 0f)
                    maskFilter = null
                }

                BrushStyle.WATERCOLOR -> {
                    alpha = (70 + 90 * hardness).toInt()
                    maskFilter = BlurMaskFilter((stroke.thickness * 0.15f).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
                }

                BrushStyle.FINE_LINER -> {
                    strokeWidth = (stroke.thickness * 0.4f).coerceAtLeast(1.5f)
                    alpha = (200 + 55 * hardness).toInt()
                    maskFilter = null
                }

                BrushStyle.ROUND_BRUSH, BrushStyle.BRUSH -> {
                    maskFilter = if (hardness < 0.9f)
                        BlurMaskFilter((1f - hardness) * 25f, BlurMaskFilter.Blur.NORMAL)
                    else null
                    alpha = (200 + 55 * hardness).toInt()
                }

                BrushStyle.INK_PEN, BrushStyle.PEN, BrushStyle.BRUSH_PEN -> {
                    alpha = (140 + 115 * hardness).toInt()
                    maskFilter = null
                }

                BrushStyle.ERASER -> {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    maskFilter = null
                }

                else -> {
                    alpha = (180 + 75 * hardness).toInt()
                    maskFilter = null
                }
            }
        }
    }

    fun drawTaperedStroke(
        canvas: Canvas,
        stroke: StrokeData,
        paint: Paint
    ) {
        val pathMeasure = PathMeasure(stroke.path, false)
        val length = pathMeasure.length
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val smoothness = 80

        val path = Path()
        pathMeasure.getPosTan(0f, pos, tan)
        path.moveTo(pos[0], pos[1])

        for (i in 1..smoothness) {
            val t = i / smoothness.toFloat()
            val baseFactor = 1f - 0.45f * t
            val endEase = (1f - t).pow(0.6f)
            val factor = (baseFactor * endEase).coerceIn(0.3f, 1f)

            val width = stroke.thickness * factor

            pathMeasure.getPosTan(length * t, pos, tan)
            paint.strokeWidth = width
            path.lineTo(pos[0], pos[1])
            canvas.drawPath(path, paint)
            path.reset()
            path.moveTo(pos[0], pos[1])
        }
    }

    fun drawBrushStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        canvas.saveLayer(null, null)

        val paint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
            maskFilter = null
            alpha = paintAlpha
            setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }

        drawTaperedStroke(canvas, stroke, paint)

        val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = Color.TRANSPARENT
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            maskFilter = null
            setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }

        val random = Random(42)
        val pathMeasure = PathMeasure(stroke.path, false)
        val pathLength = pathMeasure.length
        val pos = FloatArray(2)
        val tan = FloatArray(2)

        val softness = (1f - stroke.hardness).coerceIn(0f, 1f)
        val totalBristles = (10 + softness * 60).toInt()
        val baseSpacing = stroke.thickness * 0.22f

        var dist = 0f
        while (dist < pathLength) {
            val t = dist / pathLength
            val taperFactor = (1f - 0.45f * t).coerceAtLeast(0.3f)
            val localThickness = stroke.thickness * taperFactor
            val densityFactor = t.pow(1.3f)
            val localBristles = (totalBristles * densityFactor).toInt().coerceAtLeast(1)
            val scatter = 0.05f + softness * 0.08f
            erasePaint.strokeWidth = localThickness * (0.012f + 0.025f * softness)

            pathMeasure.getPosTan(dist, pos, tan)
            val len = hypot(tan[0], tan[1])
            val dirX = if (len != 0f) tan[0] / len else 0f
            val dirY = if (len != 0f) tan[1] / len else 0f
            val perpX = -dirY
            val perpY = dirX

            repeat(localBristles) {
                val spreadOffset = (random.nextFloat() - 0.5f) * stroke.thickness
                val baseX = pos[0] + perpX * spreadOffset
                val baseY = pos[1] + perpY * spreadOffset

                val forwardJitter = (random.nextFloat() - 0.3f) * stroke.thickness * 0.15f
                val lenFactor = stroke.thickness * (0.25f + 0.9f * t)
                val bx = baseX + dirX * (lenFactor + forwardJitter)
                val by = baseY + dirY * (lenFactor + forwardJitter)

                val jx = (random.nextFloat() - 0.5f) * stroke.thickness * scatter
                val jy = (random.nextFloat() - 0.5f) * stroke.thickness * scatter

                canvas.drawLine(baseX + jx, baseY + jy, bx + jx, by + jy, erasePaint)
            }

            dist += baseSpacing
        }

        canvas.restore()
    }

    fun drawCalligraphyStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        val pathMeasure = PathMeasure(stroke.path, false)
        val pathLength = pathMeasure.length
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val paint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
            isAntiAlias = true
            alpha = paintAlpha
        }

        val angle = Math.toRadians(45.0)
        val nibCos = cos(angle).toFloat()
        val nibSin = sin(angle).toFloat()

        val steps = 120
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            pathMeasure.getPosTan(pathLength * t, pos, tan)
            val tanLen = hypot(tan[0], tan[1])
            val nx = if (tanLen > 0) tan[0] / tanLen else 0f
            val ny = if (tanLen > 0) tan[1] / tanLen else 1f

            val dot = kotlin.math.abs(nx * nibCos + ny * nibSin)
            val nibWidth = (stroke.thickness * (0.2f + 0.8f * dot)).coerceAtLeast(2f)

            val perpX = -nibSin * nibWidth * 0.5f
            val perpY = nibCos * nibWidth * 0.5f
            canvas.drawLine(pos[0] - perpX, pos[1] - perpY, pos[0] + perpX, pos[1] + perpY, paint)
        }
    }

    fun drawFlatBrushStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        val pathMeasure = PathMeasure(stroke.path, false)
        val pathLength = pathMeasure.length
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val paint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
            isAntiAlias = true
            alpha = paintAlpha
        }

        val bristles = 7
        for (b in 0 until bristles) {
            val offsetFactor = (b - bristles / 2f) / (bristles / 2f)
            val bristleOffset = offsetFactor * (stroke.thickness * 0.45f)
            val bristlePaint = Paint(paint).apply {
                strokeWidth = (stroke.thickness / bristles).coerceAtLeast(1.5f)
            }
            val bristlePath = Path()
            val steps = 80
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                pathMeasure.getPosTan(pathLength * t, pos, tan)
                val len = hypot(tan[0], tan[1])
                val perpX = if (len > 0) -tan[1] / len else 0f
                val perpY = if (len > 0) tan[0] / len else 1f
                val px = pos[0] + perpX * bristleOffset
                val py = pos[1] + perpY * bristleOffset
                if (i == 0) bristlePath.moveTo(px, py) else bristlePath.lineTo(px, py)
            }
            canvas.drawPath(bristlePath, bristlePaint)
        }
    }

    fun drawSplatterStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        val pathMeasure = PathMeasure(stroke.path, false)
        val pathLength = pathMeasure.length
        val pos = FloatArray(2)
        val tan = FloatArray(2)

        val mainPaint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            alpha = paintAlpha
            strokeWidth = stroke.thickness * 0.7f
        }
        stroke.path?.let { canvas.drawPath(it, mainPaint) }

        val dotPaint = Paint(mainPaint).apply {
            style = Paint.Style.FILL
        }

        val random = Random(99)
        var dist = 0f
        while (dist < pathLength) {
            pathMeasure.getPosTan(dist, pos, tan)
            val count = random.nextInt(4) + 1
            repeat(count) {
                val radius = (random.nextFloat() * stroke.thickness * 0.35f).coerceAtLeast(1.5f)
                val spread = (random.nextFloat() - 0.5f) * stroke.thickness * 2.2f
                val len = hypot(tan[0], tan[1])
                val perpX = if (len > 0) -tan[1] / len else 0f
                val perpY = if (len > 0) tan[0] / len else 1f
                canvas.drawCircle(pos[0] + perpX * spread, pos[1] + perpY * spread, radius, dotPaint)
            }
            dist += stroke.thickness * 0.6f + 8f
        }
    }

    fun drawGlitterStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        val pathMeasure = PathMeasure(stroke.path, false)
        val pathLength = pathMeasure.length
        val pos = FloatArray(2)
        val tan = FloatArray(2)

        val basePaint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            alpha = (paintAlpha * 0.6f).toInt()
            strokeWidth = stroke.thickness * 0.5f
        }
        stroke.path?.let { canvas.drawPath(it, basePaint) }

        val glitterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color
            style = Paint.Style.FILL
            alpha = paintAlpha
        }

        val random = Random(123)
        var dist = 0f
        while (dist < pathLength) {
            pathMeasure.getPosTan(dist, pos, tan)
            repeat(6) {
                val r = (random.nextFloat() * stroke.thickness * 0.25f).coerceAtLeast(1.2f)
                val spread = (random.nextFloat() - 0.5f) * stroke.thickness * 1.6f
                val len = hypot(tan[0], tan[1])
                val perpX = if (len > 0) -tan[1] / len else 0f
                val perpY = if (len > 0) tan[0] / len else 1f
                glitterPaint.alpha = (paintAlpha * (0.5f + 0.5f * random.nextFloat())).toInt()
                canvas.drawCircle(pos[0] + perpX * spread, pos[1] + perpY * spread, r, glitterPaint)
            }
            dist += stroke.thickness * 0.4f + 6f
        }
    }

    fun drawWatercolorStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        val paint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        // Layer 1: Wide soft transparent wash
        paint.strokeWidth = stroke.thickness * 1.3f
        paint.alpha = (paintAlpha * 0.35f).toInt()
        stroke.path?.let { canvas.drawPath(it, paint) }

        // Layer 2: Core stroke
        paint.strokeWidth = stroke.thickness * 0.9f
        paint.alpha = (paintAlpha * 0.65f).toInt()
        stroke.path?.let { canvas.drawPath(it, paint) }
    }

    fun drawStrokePreview(
        canvas: Canvas,
        stroke: StrokeData,
        paintAlpha: Int,
        width: Int,
        height: Int,
        makePaint: (StrokeData, Int, Int) -> Paint,
        drawBrush: (Canvas, StrokeData, Int) -> Unit,
        drawPen: (Canvas, StrokeData, Int) -> Unit
    ) {
        when (stroke.style) {
            BrushStyle.ROUND_BRUSH, BrushStyle.BRUSH -> drawBrush(canvas, stroke, paintAlpha)
            BrushStyle.INK_PEN, BrushStyle.PEN, BrushStyle.BRUSH_PEN -> drawPen(canvas, stroke, paintAlpha)
            BrushStyle.CALLIGRAPHY -> drawCalligraphyStroke(canvas, stroke, paintAlpha)
            BrushStyle.FLAT_BRUSH -> drawFlatBrushStroke(canvas, stroke, paintAlpha)
            BrushStyle.SPLATTER -> drawSplatterStroke(canvas, stroke, paintAlpha)
            BrushStyle.GLITTER -> drawGlitterStroke(canvas, stroke, paintAlpha)
            BrushStyle.WATERCOLOR -> drawWatercolorStroke(canvas, stroke, paintAlpha)
            BrushStyle.HIGHLIGHTER -> {
                val paint = makePaint(stroke, width, height)
                paint.alpha = paintAlpha
                val offset = stroke.thickness * 0.3f
                val path = Path(stroke.path)
                val m = Matrix()
                m.postTranslate(0f, offset)
                path.transform(m)
                canvas.drawPath(path, paint)
            }

            else -> {
                val paint = makePaint(stroke, width, height)
                paint.alpha = paintAlpha
                stroke.path?.let { canvas.drawPath(it, paint) }
            }
        }
    }

    fun drawTaperedPenStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        val pathMeasure = PathMeasure(stroke.path, false)
        val pathLength = pathMeasure.length
        val position = FloatArray(2)
        val tangent = FloatArray(2)
        val prevPos = FloatArray(2)

        val paint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
            alpha = paintAlpha
        }

        val path = Path()
        val samples = 100
        var prevWidth = stroke.thickness

        for (i in 0 until samples) {
            val t = i / samples.toFloat()
            val dist = pathLength * t
            pathMeasure.getPosTan(dist, position, tangent)

            if (i == 0) {
                path.moveTo(position[0], position[1])
                prevPos[0] = position[0]
                prevPos[1] = position[1]
                continue
            }

            val dx = position[0] - prevPos[0]
            val dy = position[1] - prevPos[1]
            val speed = hypot(dx, dy).coerceAtMost(40f)
            val pressureFactor = (1f - (speed / 40f)).coerceIn(0.25f, 1f)
            val taperFactor = (1f - 0.5f * t).coerceAtLeast(0.3f)

            val width = stroke.thickness * taperFactor * pressureFactor
            val smoothWidth = (prevWidth * 0.7f + width * 0.3f)
            paint.strokeWidth = smoothWidth

            path.lineTo(position[0], position[1])
            canvas.drawPath(path, paint)

            prevWidth = smoothWidth
            prevPos[0] = position[0]
            prevPos[1] = position[1]
            path.reset()
            path.moveTo(position[0], position[1])
        }
    }

    fun drawSingleStroke(
        canvas: Canvas,
        stroke: StrokeData,
        paintAlpha: Int
    ) {
        val path = stroke.path ?: return
        when (stroke.style) {
            BrushStyle.ROUND_BRUSH, BrushStyle.BRUSH -> drawBrushStroke(canvas, stroke, paintAlpha)
            BrushStyle.INK_PEN, BrushStyle.PEN, BrushStyle.BRUSH_PEN -> drawTaperedPenStroke(canvas, stroke, paintAlpha)
            BrushStyle.CALLIGRAPHY -> drawCalligraphyStroke(canvas, stroke, paintAlpha)
            BrushStyle.FLAT_BRUSH -> drawFlatBrushStroke(canvas, stroke, paintAlpha)
            BrushStyle.SPLATTER -> drawSplatterStroke(canvas, stroke, paintAlpha)
            BrushStyle.GLITTER -> drawGlitterStroke(canvas, stroke, paintAlpha)
            BrushStyle.WATERCOLOR -> drawWatercolorStroke(canvas, stroke, paintAlpha)
            else -> {
                val paint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
                    alpha = paintAlpha
                }
                canvas.drawPath(path, paint)
            }
        }
    }
}