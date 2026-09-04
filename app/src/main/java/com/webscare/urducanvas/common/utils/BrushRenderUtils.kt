package com.webscare.urducanvas.common.utils

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
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
import com.webscare.urducanvas.common.canvas.enums.BrushEngine
import com.webscare.urducanvas.common.canvas.enums.GradientType
import com.webscare.urducanvas.common.canvas.model.BrushProfile
import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.common.canvas.model.StrokeData
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Turns a [StrokeData] into pixels.
 *
 * Every brush routes through [BrushProfile]: the profile picks the drawing routine and
 * supplies its parameters, so adding a brush is a row in the catalog rather than a new
 * branch here. The three non-stroke engines — fitted shapes, scattered motifs and
 * sprites — are handled by [BrushStampEngine] instead; see [BrushEngine].
 *
 * DETERMINISM: scatter and sprite brushes redraw on every frame, so their randomness is
 * seeded from the stroke's own geometry. The same stroke always produces the same marks.
 */
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

    // ── Paint construction ────────────────────────────────────────────────────

    /** Effective stroke width for a brush — the user's thickness through its profile. */
    fun strokeWidthFor(stroke: StrokeData): Float =
        (stroke.thickness * BrushProfile.of(stroke.style).widthScale).coerceAtLeast(0.5f)

    fun makeStrokePaint(stroke: StrokeData, width: Int, height: Int): Paint {
        val profile = BrushProfile.of(stroke.style)
        val hardness = stroke.hardness.coerceIn(0f, 1f)
        val softness = 1f - hardness
        val strokeWidth = strokeWidthFor(stroke)

        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = profile.cap
            strokeJoin = profile.join
            this.strokeWidth = strokeWidth

            // 🌈 Gradient or solid color
            stroke.gradient?.let {
                shader = createBackgroundGradientShader(it, width.toFloat(), height.toFloat())
            } ?: run { color = stroke.color }

            alpha = profile.alphaFor(hardness)

            profile.dashIntervals(strokeWidth)?.let { pathEffect = DashPathEffect(it, 0f) }

            val blurRadius = when {
                profile.blurFactor <= 0f -> 0f
                profile.blurAlways -> strokeWidth * profile.blurFactor * (0.5f + 0.5f * softness)
                softness > 0.05f -> softness * strokeWidth * profile.blurFactor
                else -> 0f
            }
            maskFilter = if (blurRadius >= 0.5f) {
                BlurMaskFilter(blurRadius, profile.blurStyle)
            } else null

            if (profile.kind == BrushProfile.Kind.ERASE) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                maskFilter = null
            }

            alpha = (alpha * stroke.opacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        }
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    fun drawSingleStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        if (stroke.path == null) return
        val finalAlpha = (paintAlpha * stroke.opacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)

        // Fitted shapes, scattered motifs and sprites are not strokes at all.
        if (stroke.style.engine != BrushEngine.STROKE) {
            BrushStampEngine.draw(canvas, stroke, finalAlpha)
            return
        }

        drawByKind(canvas, stroke, finalAlpha)
    }

    /**
     * Preview rendering for the style swatches. Same routines as the canvas — a swatch
     * that lies about the mark is worse than no swatch.
     *
     * The lambdas are legacy parameters kept so existing callers compile; the dispatch
     * below no longer needs them.
     */
    @Suppress("UNUSED_PARAMETER")
    fun drawStrokePreview(
        canvas: Canvas,
        stroke: StrokeData,
        paintAlpha: Int,
        width: Int,
        height: Int,
        makePaint: (StrokeData, Int, Int) -> Paint = ::makeStrokePaint,
        drawBrush: (Canvas, StrokeData, Int) -> Unit = ::drawBrushStroke,
        drawPen: (Canvas, StrokeData, Int) -> Unit = ::drawTaperedPenStroke
    ) {
        if (stroke.path == null) return
        if (stroke.style.engine != BrushEngine.STROKE) {
            BrushStampEngine.draw(canvas, stroke, paintAlpha)
            return
        }
        drawByKind(canvas, stroke, paintAlpha)
    }

    private fun drawByKind(canvas: Canvas, stroke: StrokeData, alpha: Int) {
        when (BrushProfile.of(stroke.style).kind) {
            BrushProfile.Kind.TAPERED -> drawTaperedPenStroke(canvas, stroke, alpha)
            BrushProfile.Kind.BRISTLE -> drawFlatBrushStroke(canvas, stroke, alpha)
            BrushProfile.Kind.WET -> drawWatercolorStroke(canvas, stroke, alpha)
            BrushProfile.Kind.SCATTER -> drawSplatterStroke(canvas, stroke, alpha)
            BrushProfile.Kind.GLOW -> drawGlowStroke(canvas, stroke, alpha)
            BrushProfile.Kind.NIB -> drawCalligraphyStroke(canvas, stroke, alpha)
            BrushProfile.Kind.TEXTURED -> drawTexturedStroke(canvas, stroke, alpha)
            BrushProfile.Kind.SOLID,
            BrushProfile.Kind.ERASE -> drawBrushStroke(canvas, stroke, alpha)
        }
    }

    /**
     * TEXTURED — grain that runs *with* the drag.
     *
     * The stroke is laid down as a set of thin streaks running the length of the path,
     * each wandering slightly, each broken into segments that are randomly dropped or
     * dimmed. That is what a pencil's tooth, charcoal skipping over paper, and a dry
     * brush's split bristles all look like, and it is why these brushes now read as
     * different marks rather than as one line with a dotted overlay.
     */
    fun drawTexturedStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        val path = stroke.path ?: return
        val measure = PathMeasure(path, false)
        val length = measure.length
        if (length <= 0f) return

        val profile = BrushProfile.of(stroke.style)
        val fullWidth = strokeWidthFor(stroke)
        val streaks = profile.streaks.coerceAtLeast(1)

        val paint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
            pathEffect = null           // grain comes from the streaks, not a dash
            alpha = paintAlpha
        }

        val baseAlpha = paint.alpha
        // Overlapping, not tiled: at 1.6 the streaks sat side by side and a dry brush
        // came out looking like railway track. 2.2 makes them merge into a mass that the
        // dropout then breaks up.
        val streakWidth = (fullWidth / streaks * 2.2f).coerceAtLeast(1f)
        // Grain period is a property of the paper and the brush head, not of how wide the
        // stroke is: a broad marker on rough paper has the same tooth as a narrow one.
        // Scaling the segment straight off thickness gave a 23px step on a 26px-wide
        // swatch — four segments per streak, which read as blocky bars rather than grain.
        val step = (fullWidth * profile.streakStep * 0.28f).coerceIn(2f, 9f)
        val random = seededRandom(stroke)

        val pos = FloatArray(2)
        val tan = FloatArray(2)

        for (s in 0 until streaks) {
            // Spread the streaks across the nib, edges lighter than the middle so the
            // stroke has a soft shoulder instead of a hard band.
            val offsetFactor = (s - (streaks - 1) / 2f) / ((streaks - 1) / 2f).coerceAtLeast(0.5f)
            val baseOffset = offsetFactor * fullWidth * 0.5f
            val edgeFade = 1f - 0.45f * kotlin.math.abs(offsetFactor)

            var dist = 0f
            var penDown = false
            var lastX = 0f
            var lastY = 0f

            while (dist <= length) {
                measure.getPosTan(dist, pos, tan)
                val len = hypot(tan[0], tan[1])
                val perpX = if (len > 0) -tan[1] / len else 0f
                val perpY = if (len > 0) tan[0] / len else 1f

                val wander = (random.nextFloat() - 0.5f) * profile.streakJitter * fullWidth
                val x = pos[0] + perpX * (baseOffset + wander)
                val y = pos[1] + perpY * (baseOffset + wander)

                val skip = random.nextFloat() < profile.streakDropout
                if (!skip) {
                    val variance = 1f - profile.streakAlphaVar * random.nextFloat()
                    paint.alpha = (baseAlpha * variance * edgeFade).toInt().coerceIn(0, 255)
                    paint.strokeWidth = streakWidth * (0.7f + 0.6f * random.nextFloat())
                    if (penDown) canvas.drawLine(lastX, lastY, x, y, paint)
                }
                penDown = !skip
                lastX = x
                lastY = y
                dist += step
            }
        }
        paint.alpha = baseAlpha
    }

    // ── Routines ──────────────────────────────────────────────────────────────

    fun drawTaperedStroke(
        canvas: Canvas,
        stroke: StrokeData,
        paint: Paint
    ) {
        val path = stroke.path ?: return
        val pathMeasure = PathMeasure(path, false)
        val length = pathMeasure.length
        if (length <= 0f) return
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val softness = (1f - stroke.hardness).coerceIn(0f, 1f)
        val smoothness = 30
        val origAlpha = paint.alpha
        val origWidth = strokeWidthFor(stroke)

        val segmentPath = Path()
        pathMeasure.getPosTan(0f, pos, tan)
        segmentPath.moveTo(pos[0], pos[1])

        for (i in 1..smoothness) {
            val t = i / smoothness.toFloat()
            // Soft start and end feathering
            val startTaper = if (t < 0.2f) (t / 0.2f).pow(0.5f) else 1f
            val endTaper = if (t > 0.75f) ((1f - t) / 0.25f).pow(0.5f) else 1f
            val taperFactor = startTaper * endTaper

            val widthFactor = (1f - softness) * 1f + softness * taperFactor
            val width = origWidth * widthFactor.coerceIn(0.2f, 1f)
            val alphaFactor = (1f - softness) * 1f + softness * taperFactor

            pathMeasure.getPosTan(length * t, pos, tan)
            paint.strokeWidth = width
            paint.alpha = (origAlpha * alphaFactor).toInt().coerceIn(0, 255)
            segmentPath.lineTo(pos[0], pos[1])
            canvas.drawPath(segmentPath, paint)
            segmentPath.reset()
            segmentPath.moveTo(pos[0], pos[1])
        }
        paint.alpha = origAlpha
        paint.strokeWidth = origWidth
    }

    /** SOLID and TEXTURED. The dash pattern in the paint is what makes them differ. */
    fun drawBrushStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        val paint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
            alpha = paintAlpha
        }

        val softness = (1f - stroke.hardness).coerceIn(0f, 1f)
        // A dash pattern is the brush's grain; re-drawing it segment by segment for a
        // taper would repeat the pattern from zero on every segment and destroy it.
        if (softness > 0.05f && paint.pathEffect == null) {
            drawTaperedStroke(canvas, stroke, paint)
        } else {
            stroke.path?.let { canvas.drawPath(it, paint) }
        }
    }

    /**
     * NIB — an angled flat pen. Width comes from the angle between the direction of
     * travel and the nib's edge, which is what gives Arabic and Urdu scripts their
     * thick-thin rhythm. [BrushProfile.nibAngleDeg] sets the cut, [BrushProfile.nibMinRatio]
     * how thin the thinnest stroke gets.
     */
    fun drawCalligraphyStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        val path = stroke.path ?: return
        val pathMeasure = PathMeasure(path, false)
        val pathLength = pathMeasure.length
        if (pathLength <= 0f) return

        val profile = BrushProfile.of(stroke.style)
        val fullWidth = strokeWidthFor(stroke)
        val minRatio = profile.nibMinRatio.coerceIn(0.02f, 1f)
        val range = 1f - minRatio

        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val paint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            // The ribbon is built as an outline and filled. Stamping one short line per
            // sample instead left the stroke combed — the quads never overlapped, so a
            // Nastaleeq sweep came out as a row of tick marks rather than a solid mark.
            style = Paint.Style.FILL
            isAntiAlias = true
            alpha = paintAlpha
            pathEffect = null
        }

        val angle = Math.toRadians(profile.nibAngleDeg.toDouble())
        val nibCos = cos(angle).toFloat()
        val nibSin = sin(angle).toFloat()

        val steps = (pathLength / (fullWidth * 0.2f).coerceAtLeast(1f))
            .toInt().coerceIn(32, 1200)

        val leftX = FloatArray(steps + 1)
        val leftY = FloatArray(steps + 1)
        val rightX = FloatArray(steps + 1)
        val rightY = FloatArray(steps + 1)

        for (i in 0..steps) {
            val t = i / steps.toFloat()
            pathMeasure.getPosTan(pathLength * t, pos, tan)
            val tanLen = hypot(tan[0], tan[1])
            val nx = if (tanLen > 0) tan[0] / tanLen else 0f
            val ny = if (tanLen > 0) tan[1] / tanLen else 1f

            // |cos| between travel and nib: 1 when crossing the nib broadside.
            val dot = abs(nx * nibCos + ny * nibSin)
            var nibWidth = fullWidth * (minRatio + range * dot)

            if (profile.nibTapers) {
                val endTaper = if (t > 0.82f) ((1f - t) / 0.18f).pow(0.6f) else 1f
                val startTaper = if (t < 0.08f) (t / 0.08f).pow(0.6f) else 1f
                nibWidth *= (startTaper * endTaper).coerceIn(0.15f, 1f)
            }
            nibWidth = nibWidth.coerceAtLeast(1f)

            val perpX = -nibSin * nibWidth * 0.5f
            val perpY = nibCos * nibWidth * 0.5f
            leftX[i] = pos[0] - perpX
            leftY[i] = pos[1] - perpY
            rightX[i] = pos[0] + perpX
            rightY[i] = pos[1] + perpY
        }

        val ribbon = Path()
        ribbon.moveTo(leftX[0], leftY[0])
        for (i in 1..steps) ribbon.lineTo(leftX[i], leftY[i])
        for (i in steps downTo 0) ribbon.lineTo(rightX[i], rightY[i])
        ribbon.close()
        canvas.drawPath(ribbon, paint)
    }

    /** BRISTLE — several thin parallel lines, optionally wobbling apart. */
    fun drawFlatBrushStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        val path = stroke.path ?: return
        val pathMeasure = PathMeasure(path, false)
        val pathLength = pathMeasure.length
        if (pathLength <= 0f) return

        val profile = BrushProfile.of(stroke.style)
        val fullWidth = strokeWidthFor(stroke)
        val bristles = profile.bristles.coerceAtLeast(1)

        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val paint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
            isAntiAlias = true
            alpha = paintAlpha
        }

        val random = seededRandom(stroke)
        for (b in 0 until bristles) {
            val offsetFactor = (b - bristles / 2f) / (bristles / 2f).coerceAtLeast(0.5f)
            val bristleOffset = offsetFactor * (fullWidth * 0.45f)
            val bristlePaint = Paint(paint).apply {
                strokeWidth = (fullWidth / bristles).coerceAtLeast(1.2f)
            }
            val wobble = profile.bristleJitter * fullWidth
            val bristlePath = Path()
            val steps = 32
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                pathMeasure.getPosTan(pathLength * t, pos, tan)
                val len = hypot(tan[0], tan[1])
                val perpX = if (len > 0) -tan[1] / len else 0f
                val perpY = if (len > 0) tan[0] / len else 1f
                val jitter = if (wobble > 0f) (random.nextFloat() - 0.5f) * wobble else 0f
                val px = pos[0] + perpX * (bristleOffset + jitter)
                val py = pos[1] + perpY * (bristleOffset + jitter)
                if (i == 0) bristlePath.moveTo(px, py) else bristlePath.lineTo(px, py)
            }
            canvas.drawPath(bristlePath, bristlePaint)
        }
    }

    /** SCATTER — dots thrown along the path, over an optional solid core. */
    fun drawSplatterStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        val path = stroke.path ?: return
        val profile = BrushProfile.of(stroke.style)
        val fullWidth = strokeWidthFor(stroke)

        val pathMeasure = PathMeasure(path, false)
        val pathLength = pathMeasure.length
        if (pathLength <= 0f) return

        val pos = FloatArray(2)
        val tan = FloatArray(2)

        val mainPaint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            alpha = paintAlpha
            pathEffect = null
        }

        if (profile.coreWidth > 0f) {
            mainPaint.strokeWidth = fullWidth * profile.coreWidth
            canvas.drawPath(path, mainPaint)
        }

        val dotPaint = Paint(mainPaint).apply { style = Paint.Style.FILL }

        val random = seededRandom(stroke)
        val step = (fullWidth * 0.5f).coerceAtLeast(3f)
        var dist = 0f
        while (dist < pathLength) {
            pathMeasure.getPosTan(dist, pos, tan)
            val len = hypot(tan[0], tan[1])
            val perpX = if (len > 0) -tan[1] / len else 0f
            val perpY = if (len > 0) tan[0] / len else 1f
            repeat(profile.density) {
                val radius = (random.nextFloat() * fullWidth * profile.dotScale).coerceAtLeast(0.8f)
                val spread = (random.nextFloat() - 0.5f) * fullWidth * profile.spread
                dotPaint.alpha = (paintAlpha * (0.45f + 0.55f * random.nextFloat()))
                    .toInt().coerceIn(0, 255)
                canvas.drawCircle(pos[0] + perpX * spread, pos[1] + perpY * spread, radius, dotPaint)
            }
            dist += step
        }
    }

    /** Kept for saved projects that still reference the glitter style. */
    fun drawGlitterStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) =
        drawSplatterStroke(canvas, stroke, paintAlpha)

    /** WET — a wide translucent wash with a denser core over it. */
    fun drawWatercolorStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        val path = stroke.path ?: return
        val profile = BrushProfile.of(stroke.style)
        val fullWidth = strokeWidthFor(stroke)

        val paint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
            pathEffect = null
        }

        paint.strokeWidth = fullWidth * profile.washWidth
        paint.alpha = (paintAlpha * profile.washAlpha).toInt().coerceIn(0, 255)
        canvas.drawPath(path, paint)

        paint.strokeWidth = fullWidth * 0.9f
        paint.alpha = (paintAlpha * profile.coreAlpha).toInt().coerceIn(0, 255)
        canvas.drawPath(path, paint)
    }

    /** GLOW — a blurred halo with a bright core sitting inside it. */
    fun drawGlowStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        val path = stroke.path ?: return
        val profile = BrushProfile.of(stroke.style)
        val fullWidth = strokeWidthFor(stroke)

        val halo = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            pathEffect = null
            strokeWidth = fullWidth
            alpha = (paintAlpha * 0.55f).toInt().coerceIn(0, 255)
            maskFilter = BlurMaskFilter(
                (fullWidth * profile.glowRadius).coerceAtLeast(2f), BlurMaskFilter.Blur.NORMAL
            )
        }
        canvas.drawPath(path, halo)

        // The core is what makes it read as light rather than as a smudge: a near-white
        // centre line inside the halo, drawn at full alpha.
        val core = Paint(halo).apply {
            maskFilter = null
            shader = halo.shader
            strokeWidth = (fullWidth * profile.glowCore).coerceAtLeast(1f)
            alpha = paintAlpha
        }
        canvas.drawPath(path, core)
    }

    fun drawTaperedPenStroke(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        val path = stroke.path ?: return
        val pathMeasure = PathMeasure(path, false)
        val pathLength = pathMeasure.length
        if (pathLength <= 0f) return
        val position = FloatArray(2)
        val tangent = FloatArray(2)
        val prevPos = FloatArray(2)

        val fullWidth = strokeWidthFor(stroke)
        val paint = makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
            alpha = paintAlpha
            pathEffect = null
        }

        val segmentPath = Path()
        val samples = 30
        var prevWidth = fullWidth

        for (i in 0..samples) {
            val t = i / samples.toFloat()
            val dist = pathLength * t
            pathMeasure.getPosTan(dist, position, tangent)

            if (i == 0) {
                segmentPath.moveTo(position[0], position[1])
                prevPos[0] = position[0]
                prevPos[1] = position[1]
                continue
            }

            val dx = position[0] - prevPos[0]
            val dy = position[1] - prevPos[1]
            val speed = hypot(dx, dy).coerceAtMost(40f)
            val pressureFactor = (1f - (speed / 40f)).coerceIn(0.25f, 1f)
            val taperFactor = (1f - 0.5f * t).coerceAtLeast(0.3f)

            val width = fullWidth * taperFactor * pressureFactor
            val smoothWidth = (prevWidth * 0.7f + width * 0.3f)
            paint.strokeWidth = smoothWidth

            segmentPath.lineTo(position[0], position[1])
            canvas.drawPath(segmentPath, paint)

            prevWidth = smoothWidth
            prevPos[0] = position[0]
            prevPos[1] = position[1]
            segmentPath.reset()
            segmentPath.moveTo(position[0], position[1])
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /**
     * A generator seeded from the stroke itself. Scatter and sprite brushes are re-run on
     * every frame, so a fresh [Random] would make the marks crawl; deriving the seed from
     * the geometry keeps each stroke's speckle fixed while still differing between strokes.
     */
    internal fun seededRandom(stroke: StrokeData): Random {
        val path = stroke.path
        val length = if (path != null) PathMeasure(path, false).length else 0f
        val seed = (length * 31f).toLong() xor
                (stroke.thickness * 7f).toLong() xor
                stroke.style.ordinal.toLong() * 2654435761L
        return Random(seed)
    }
}
