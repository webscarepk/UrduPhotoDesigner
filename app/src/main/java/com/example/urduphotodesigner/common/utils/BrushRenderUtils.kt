package com.example.urduphotodesigner.common.utils

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
import com.example.urduphotodesigner.common.canvas.enums.BrushStyle
import com.example.urduphotodesigner.common.canvas.enums.GradientType
import com.example.urduphotodesigner.common.canvas.model.GradientItem
import com.example.urduphotodesigner.common.canvas.model.StrokeData
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
                    alpha = (150 + 90 * hardness).toInt()
                    pathEffect = DashPathEffect(floatArrayOf(4f, 5f, 1f, 3f), 0f)
                    maskFilter = null
                }

                BrushStyle.MARKER -> {
                    strokeCap = Paint.Cap.BUTT
                    alpha = (90 + 160 * hardness).toInt()
                    maskFilter = null
                }

                BrushStyle.HIGHLIGHTER -> {
                    strokeCap = Paint.Cap.BUTT
                    alpha = (60 + 100 * hardness).toInt()
                    maskFilter = null
                }

                BrushStyle.BRUSH -> {
                    maskFilter = if (hardness < 0.9f)
                        BlurMaskFilter((1f - hardness) * 25f, BlurMaskFilter.Blur.NORMAL)
                    else null
                    alpha = (200 + 55 * hardness).toInt()
                }

                BrushStyle.PEN -> {
                    alpha = (120 + 135 * hardness).toInt() // softer = lighter
                    maskFilter = null
                }

                BrushStyle.ERASER -> {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
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

            // 🟢 NEW: Smooth continuous taper (start → end)
            val baseFactor = 1f - 0.45f * t      // starts reducing right from beginning
            val endEase = (1f - t).pow(0.6f)     // softens tail
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
        // 🟢 1. Create isolated drawing layer
        canvas.saveLayer(null, null)

        // 🎨 Base stroke paint
        val paint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
            maskFilter = null
            alpha = paintAlpha
            setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }

        // 🖌️ Draw main tapered stroke on isolated layer
        drawTaperedStroke(canvas, stroke, paint)

        // 🧽 Eraser paint (for bristles / negative space)
        val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = Color.TRANSPARENT
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            maskFilter = null
            setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }

        // 🌾 Bristle generation logic
        val random = java.util.Random()
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

            // Taper and density logic
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

            // Bristle drawing
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

                // 🧽 Now erasing only within this stroke layer
                canvas.drawLine(baseX + jx, baseY + jy, bx + jx, by + jy, erasePaint)
            }

            dist += baseSpacing
        }

        // 🟢 2. Merge this stroke layer back to main canvas
        canvas.restore()
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
            BrushStyle.BRUSH -> drawBrush(canvas, stroke, paintAlpha)
            BrushStyle.PEN -> drawPen(canvas, stroke, paintAlpha)
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
                canvas.drawPath(stroke.path!!, paint)
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

            // 🧮 movement speed for pressure simulation
            val dx = position[0] - prevPos[0]
            val dy = position[1] - prevPos[1]
            val speed = hypot(dx, dy).coerceAtMost(40f)
            val pressureFactor = (1f - (speed / 40f)).coerceIn(0.25f, 1f)

            // 🟢 Linear taper (start→end)
            // 1.0 at start → 0.5 at end = gentle, continuous reduction
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
}