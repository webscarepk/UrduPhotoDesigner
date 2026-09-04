package com.webscare.urducanvas.common.utils

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import com.webscare.urducanvas.common.canvas.enums.BrushEngine
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.common.canvas.model.StrokeData
import java.util.Random
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The brushes that are not strokes.
 *
 * Decorative, Shapes & Strokes and most of Effects cannot be expressed as paint laid
 * continuously along a path. An arrow is one shape spanning the drag; leaves are a motif
 * repeated along it; smoke is soft sprites layered over it. Those are the three engines
 * here — [BrushEngine.FITTED], [BrushEngine.SCATTERED] and [BrushEngine.SPRITE].
 *
 * WHAT A DRAG GIVES US: the stroke arrives as a sampled polyline, which is all these
 * engines need. Fitted shapes read the first point, the last point and the bounding box;
 * scattered and sprite brushes walk the path. Nothing here needs a new field on
 * [StrokeData], so fitted shapes serialise and reload through the existing path data.
 *
 * These marks bake into the drawing layer like any other brush — they are paint, not
 * editable objects.
 */
object BrushStampEngine {

    fun draw(canvas: Canvas, stroke: StrokeData, paintAlpha: Int) {
        val path = stroke.path ?: return
        val measure = PathMeasure(path, false)
        val length = measure.length
        if (length <= 0f) return

        when (stroke.style.engine) {
            BrushEngine.FITTED -> drawFitted(canvas, stroke, paintAlpha, measure, length)
            BrushEngine.SCATTERED -> drawScattered(canvas, stroke, paintAlpha, measure, length)
            BrushEngine.SPRITE -> drawSprite(canvas, stroke, paintAlpha, measure, length)
            BrushEngine.STROKE -> Unit
        }
    }

    // ── Fitted: one shape spanning the drag ───────────────────────────────────

    private fun drawFitted(
        canvas: Canvas, stroke: StrokeData, alpha: Int, measure: PathMeasure, length: Float
    ) {
        val start = FloatArray(2)
        val end = FloatArray(2)
        measure.getPosTan(0f, start, null)
        measure.getPosTan(length, end, null)

        val bounds = RectF()
        stroke.path?.computeBounds(bounds, true)

        val paint = basePaint(stroke, canvas, alpha)
        val w = strokeWidth(stroke)
        paint.strokeWidth = w

        when (stroke.style) {
            BrushStyle.ARROWS -> drawArrow(canvas, paint, start, end, w)
            BrushStyle.CIRCLES -> drawEllipse(canvas, paint, bounds)
            BrushStyle.FRAMES -> drawFrame(canvas, paint, bounds, w)
            BrushStyle.DIVIDERS -> drawDivider(canvas, paint, start, end, w)
            BrushStyle.CURVES -> drawCurve(canvas, paint, start, end)
            BrushStyle.WAVES -> drawWave(canvas, paint, start, end, w)
            BrushStyle.UNDERLINES -> drawUnderline(canvas, paint, start, end, w)
            BrushStyle.SWIRLS -> drawSpiral(canvas, paint, start, end)
            BrushStyle.FLOURISHES -> drawFlourish(canvas, paint, start, end, w)
            BrushStyle.LIGHTNING -> drawLightning(canvas, paint, stroke, start, end, w)
            else -> canvas.drawLine(start[0], start[1], end[0], end[1], paint)
        }
    }

    private fun drawArrow(canvas: Canvas, paint: Paint, s: FloatArray, e: FloatArray, w: Float) {
        canvas.drawLine(s[0], s[1], e[0], e[1], paint)
        val angle = atan2(e[1] - s[1], e[0] - s[0])
        val head = (w * 4f).coerceAtLeast(12f)
        val spread = Math.toRadians(26.0).toFloat()
        for (side in intArrayOf(-1, 1)) {
            val a = angle + Math.PI.toFloat() + side * spread
            canvas.drawLine(e[0], e[1], e[0] + cos(a) * head, e[1] + sin(a) * head, paint)
        }
    }

    private fun drawEllipse(canvas: Canvas, paint: Paint, bounds: RectF) {
        // A near-zero drag in one axis would collapse the ellipse to a line, so give it
        // a floor derived from the other axis.
        val minSide = (maxOf(bounds.width(), bounds.height()) * 0.12f).coerceAtLeast(4f)
        val rect = RectF(bounds)
        if (rect.width() < minSide) rect.inset(-(minSide - rect.width()) / 2f, 0f)
        if (rect.height() < minSide) rect.inset(0f, -(minSide - rect.height()) / 2f)
        canvas.drawOval(rect, paint)
    }

    private fun drawFrame(canvas: Canvas, paint: Paint, bounds: RectF, w: Float) {
        val radius = (minOf(bounds.width(), bounds.height()) * 0.08f).coerceAtMost(w * 2f)
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }

    private fun drawDivider(canvas: Canvas, paint: Paint, s: FloatArray, e: FloatArray, w: Float) {
        canvas.drawLine(s[0], s[1], e[0], e[1], paint)
        // Diamond finials at both ends — the ornament that separates a divider from a rule.
        val angle = atan2(e[1] - s[1], e[0] - s[0])
        val r = (w * 1.6f).coerceAtLeast(5f)
        val fill = Paint(paint).also { it.style = Paint.Style.FILL }
        for (p in arrayOf(s, e)) {
            val d = Path()
            d.moveTo(p[0] + cos(angle) * r, p[1] + sin(angle) * r)
            d.lineTo(p[0] + cos(angle + 1.5708f) * r * 0.6f, p[1] + sin(angle + 1.5708f) * r * 0.6f)
            d.lineTo(p[0] - cos(angle) * r, p[1] - sin(angle) * r)
            d.lineTo(p[0] - cos(angle + 1.5708f) * r * 0.6f, p[1] - sin(angle + 1.5708f) * r * 0.6f)
            d.close()
            canvas.drawPath(d, fill)
        }
    }

    private fun drawCurve(canvas: Canvas, paint: Paint, s: FloatArray, e: FloatArray) {
        val dx = e[0] - s[0]
        val dy = e[1] - s[1]
        val len = hypot(dx, dy)
        if (len <= 0f) return
        // Bow the line out perpendicular to the drag by a quarter of its length.
        val px = -dy / len
        val py = dx / len
        val bow = len * 0.25f
        val path = Path().apply {
            moveTo(s[0], s[1])
            quadTo((s[0] + e[0]) / 2f + px * bow, (s[1] + e[1]) / 2f + py * bow, e[0], e[1])
        }
        canvas.drawPath(path, paint)
    }

    private fun drawWave(canvas: Canvas, paint: Paint, s: FloatArray, e: FloatArray, w: Float) {
        val dx = e[0] - s[0]
        val dy = e[1] - s[1]
        val len = hypot(dx, dy)
        if (len <= 0f) return
        val ux = dx / len
        val uy = dy / len
        val px = -uy
        val py = ux
        val amp = (w * 2.2f).coerceAtLeast(6f)
        val wavelength = (amp * 3.4f).coerceAtLeast(14f)
        val path = Path().apply { moveTo(s[0], s[1]) }
        var d = 0f
        var up = true
        while (d < len) {
            val next = (d + wavelength / 2f).coerceAtMost(len)
            val mid = (d + next) / 2f
            val sign = if (up) 1f else -1f
            path.quadTo(
                s[0] + ux * mid + px * amp * sign,
                s[1] + uy * mid + py * amp * sign,
                s[0] + ux * next,
                s[1] + uy * next
            )
            up = !up
            d = next
        }
        canvas.drawPath(path, paint)
    }

    private fun drawUnderline(canvas: Canvas, paint: Paint, s: FloatArray, e: FloatArray, w: Float) {
        // Two strokes: a full-weight rule with a lighter one tucked beneath it.
        canvas.drawLine(s[0], s[1], e[0], e[1], paint)
        val offset = w * 1.8f
        val light = Paint(paint).apply {
            strokeWidth = w * 0.45f
            alpha = (paint.alpha * 0.6f).toInt().coerceIn(0, 255)
        }
        val inset = hypot(e[0] - s[0], e[1] - s[1]) * 0.12f
        val ux = (e[0] - s[0]) / hypot(e[0] - s[0], e[1] - s[1]).coerceAtLeast(1f)
        val uy = (e[1] - s[1]) / hypot(e[0] - s[0], e[1] - s[1]).coerceAtLeast(1f)
        canvas.drawLine(
            s[0] + ux * inset, s[1] + uy * inset + offset,
            e[0] - ux * inset, e[1] - uy * inset + offset, light
        )
    }

    private fun drawSpiral(canvas: Canvas, paint: Paint, s: FloatArray, e: FloatArray) {
        val radius = hypot(e[0] - s[0], e[1] - s[1])
        if (radius <= 0f) return
        val turns = 2.5f
        val steps = 160
        val path = Path()
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val a = t * turns * 2f * Math.PI.toFloat()
            val r = radius * t
            val x = s[0] + cos(a) * r
            val y = s[1] + sin(a) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawFlourish(canvas: Canvas, paint: Paint, s: FloatArray, e: FloatArray, w: Float) {
        val dx = e[0] - s[0]
        val dy = e[1] - s[1]
        val len = hypot(dx, dy)
        if (len <= 0f) return
        val ux = dx / len
        val uy = dy / len
        val px = -uy
        val py = ux
        val amp = len * 0.28f
        // A long S-curve with a small hook returning at the tail.
        val path = Path().apply {
            moveTo(s[0], s[1])
            cubicTo(
                s[0] + ux * len * 0.3f + px * amp, s[1] + uy * len * 0.3f + py * amp,
                s[0] + ux * len * 0.7f - px * amp, s[1] + uy * len * 0.7f - py * amp,
                e[0], e[1]
            )
            quadTo(
                e[0] + ux * w * 3f + px * w * 3f, e[1] + uy * w * 3f + py * w * 3f,
                e[0] + px * w * 4f, e[1] + py * w * 4f
            )
        }
        canvas.drawPath(path, paint)
    }

    private fun drawLightning(
        canvas: Canvas, paint: Paint, stroke: StrokeData, s: FloatArray, e: FloatArray, w: Float
    ) {
        val random = BrushRenderUtils.seededRandom(stroke)
        val dx = e[0] - s[0]
        val dy = e[1] - s[1]
        val len = hypot(dx, dy)
        if (len <= 0f) return
        val px = -dy / len
        val py = dx / len

        val segments = 7
        val path = Path().apply { moveTo(s[0], s[1]) }
        for (i in 1..segments) {
            val t = i / segments.toFloat()
            val jag = if (i == segments) 0f else (random.nextFloat() - 0.5f) * len * 0.18f
            path.lineTo(s[0] + dx * t + px * jag, s[1] + dy * t + py * jag)
        }

        val halo = Paint(paint).apply {
            strokeWidth = w * 2.6f
            alpha = (paint.alpha * 0.4f).toInt().coerceIn(0, 255)
            maskFilter = BlurMaskFilter((w * 2f).coerceAtLeast(3f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(path, halo)
        canvas.drawPath(path, paint)
    }

    // ── Scattered: a motif repeated along the path ────────────────────────────

    private fun drawScattered(
        canvas: Canvas, stroke: StrokeData, alpha: Int, measure: PathMeasure, length: Float
    ) {
        val paint = basePaint(stroke, canvas, alpha)
        val w = strokeWidth(stroke)
        val random = BrushRenderUtils.seededRandom(stroke)
        val pos = FloatArray(2)
        val tan = FloatArray(2)

        val spacing = (w * motifSpacing(stroke.style)).coerceAtLeast(6f)
        var dist = 0f
        while (dist <= length) {
            measure.getPosTan(dist, pos, tan)
            val heading = atan2(tan[1], tan[0])
            val jitterX = (random.nextFloat() - 0.5f) * w * 1.6f
            val jitterY = (random.nextFloat() - 0.5f) * w * 1.6f
            val scale = 0.6f + random.nextFloat() * 0.7f
            val spin = (random.nextFloat() - 0.5f) * 2.4f

            paint.alpha = (alpha * (0.55f + 0.45f * random.nextFloat())).toInt().coerceIn(0, 255)

            canvas.save()
            canvas.translate(pos[0] + jitterX, pos[1] + jitterY)
            canvas.rotate(Math.toDegrees((heading + spin).toDouble()).toFloat())
            drawMotif(canvas, paint, stroke.style, w * scale)
            canvas.restore()

            dist += spacing
        }
    }

    private fun motifSpacing(style: BrushStyle): Float = when (style) {
        BrushStyle.DUST -> 1.1f
        BrushStyle.STARS -> 2.4f
        BrushStyle.SPARK -> 1.6f
        BrushStyle.LEAVES -> 2.2f
        BrushStyle.DOODLES -> 3.0f
        else -> 2f
    }

    private fun drawMotif(canvas: Canvas, paint: Paint, style: BrushStyle, size: Float) {
        when (style) {
            BrushStyle.LEAVES -> {
                val leaf = Path().apply {
                    moveTo(0f, 0f)
                    quadTo(size * 0.5f, -size * 0.45f, size * 1.3f, 0f)
                    quadTo(size * 0.5f, size * 0.45f, 0f, 0f)
                    close()
                }
                val fill = Paint(paint).also { it.style = Paint.Style.FILL }
                canvas.drawPath(leaf, fill)
                canvas.drawLine(0f, 0f, size * 1.3f, 0f, paint)
            }

            BrushStyle.STARS -> drawStar(canvas, paint, size, points = 5)
            BrushStyle.SPARK -> {
                // A four-armed sparkle: two crossed tapers rather than a filled star.
                val arm = size * 1.1f
                canvas.drawLine(-arm, 0f, arm, 0f, paint)
                canvas.drawLine(0f, -arm * 0.6f, 0f, arm * 0.6f, paint)
            }

            BrushStyle.DUST -> {
                val fill = Paint(paint).also { it.style = Paint.Style.FILL }
                canvas.drawCircle(0f, 0f, (size * 0.16f).coerceAtLeast(0.8f), fill)
            }

            BrushStyle.DOODLES -> {
                val loop = Path().apply {
                    moveTo(-size * 0.7f, 0f)
                    cubicTo(-size * 0.3f, -size * 0.9f, size * 0.3f, size * 0.9f, size * 0.7f, 0f)
                }
                canvas.drawPath(loop, paint)
            }

            else -> canvas.drawCircle(0f, 0f, size * 0.3f, paint)
        }
    }

    private fun drawStar(canvas: Canvas, paint: Paint, size: Float, points: Int) {
        val outer = size * 0.8f
        val inner = outer * 0.42f
        val path = Path()
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outer else inner
            val a = (i * Math.PI / points).toFloat() - Math.PI.toFloat() / 2f
            val x = cos(a) * r
            val y = sin(a) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, Paint(paint).also { it.style = Paint.Style.FILL })
    }

    // ── Sprite: soft layered puffs along the path ─────────────────────────────

    private fun drawSprite(
        canvas: Canvas, stroke: StrokeData, alpha: Int, measure: PathMeasure, length: Float
    ) {
        val w = strokeWidth(stroke)
        val random = BrushRenderUtils.seededRandom(stroke)
        val pos = FloatArray(2)
        val tan = FloatArray(2)

        val layers = spriteLayers(stroke.style)
        val spacing = (w * 0.5f).coerceAtLeast(4f)

        for ((layerIndex, layer) in layers.withIndex()) {
            val paint = basePaint(stroke, canvas, alpha).apply {
                style = Paint.Style.FILL
                strokeWidth = 0f
                maskFilter = BlurMaskFilter(
                    (w * layer.blur).coerceAtLeast(2f), BlurMaskFilter.Blur.NORMAL
                )
                if (layer.tint != null) {
                    // Fire and water read as themselves only if the hotter or brighter
                    // core shifts hue, so the inner layers tint toward it.
                    shader = null
                    color = blend(stroke.color, layer.tint, layer.tintAmount)
                }
            }

            var dist = 0f
            while (dist <= length) {
                measure.getPosTan(dist, pos, tan)
                val drift = (random.nextFloat() - 0.5f) * w * layer.drift
                val lift = -(dist / length.coerceAtLeast(1f)) * w * layer.lift
                val radius = w * layer.radius * (0.7f + random.nextFloat() * 0.6f)
                paint.alpha = (alpha * layer.alpha * (0.6f + 0.4f * random.nextFloat()))
                    .toInt().coerceIn(0, 255)
                canvas.drawCircle(
                    pos[0] + drift,
                    pos[1] + lift + (layerIndex - layers.size / 2f) * w * 0.15f,
                    radius,
                    paint
                )
                dist += spacing
            }
        }
    }

    private class SpriteLayer(
        val radius: Float,
        val blur: Float,
        val alpha: Float,
        val drift: Float,
        val lift: Float,
        val tint: Int? = null,
        val tintAmount: Float = 0f
    )

    private fun spriteLayers(style: BrushStyle): List<SpriteLayer> = when (style) {
        BrushStyle.SMOKE -> listOf(
            SpriteLayer(radius = 0.95f, blur = 0.9f, alpha = 0.22f, drift = 2.2f, lift = 1.4f),
            SpriteLayer(radius = 0.55f, blur = 0.6f, alpha = 0.28f, drift = 1.4f, lift = 0.9f)
        )

        BrushStyle.FIRE -> listOf(
            SpriteLayer(radius = 0.9f, blur = 0.8f, alpha = 0.25f, drift = 1.6f, lift = 1.8f),
            SpriteLayer(
                radius = 0.5f, blur = 0.45f, alpha = 0.45f, drift = 1f, lift = 1.2f,
                tint = Color.parseColor("#FFC24A"), tintAmount = 0.55f
            ),
            SpriteLayer(
                radius = 0.24f, blur = 0.25f, alpha = 0.6f, drift = 0.6f, lift = 0.8f,
                tint = Color.WHITE, tintAmount = 0.5f
            )
        )

        BrushStyle.WATER -> listOf(
            SpriteLayer(radius = 0.85f, blur = 0.7f, alpha = 0.2f, drift = 1.8f, lift = 0f),
            SpriteLayer(
                radius = 0.4f, blur = 0.35f, alpha = 0.35f, drift = 1.1f, lift = 0f,
                tint = Color.WHITE, tintAmount = 0.35f
            )
        )

        else -> listOf(SpriteLayer(radius = 0.7f, blur = 0.6f, alpha = 0.3f, drift = 1.5f, lift = 0f))
    }

    private fun blend(base: Int, toward: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        fun mix(c1: Int, c2: Int) = (c1 + (c2 - c1) * a).toInt().coerceIn(0, 255)
        return Color.argb(
            Color.alpha(base),
            mix(Color.red(base), Color.red(toward)),
            mix(Color.green(base), Color.green(toward)),
            mix(Color.blue(base), Color.blue(toward))
        )
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    private fun strokeWidth(stroke: StrokeData): Float =
        BrushRenderUtils.strokeWidthFor(stroke)

    private fun basePaint(stroke: StrokeData, canvas: Canvas, alpha: Int): Paint =
        BrushRenderUtils.makeStrokePaint(stroke, canvas.width, canvas.height).apply {
            this.alpha = alpha
            pathEffect = null
            maskFilter = null
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
}
