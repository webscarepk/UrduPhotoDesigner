package com.webscare.urducanvas.common.canvas.model

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.core.graphics.createBitmap
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Turns a [Text3DSurface] spec plus the user's chosen colour into the shader and pattern
 * the canvas actually paints. Shared by the material swatches and [com.webscare.urducanvas.common.views.CanvasView]
 * so a swatch always previews what the glyphs will look like.
 */
object Text3DSurfaceShading {

    /**
     * Scales [color] towards white (factor > 1) or black (factor < 1), keeping alpha.
     * Every surface stop is expressed this way so one spec works for the whole palette.
     */
    fun shade(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        fun ch(c: Int): Int =
            if (factor >= 1f) (c + (255 - c) * (factor - 1f).coerceAtMost(1f)).roundToInt().coerceIn(0, 255)
            else (c * factor).roundToInt().coerceIn(0, 255)
        return Color.argb(a, ch(Color.red(color)), ch(Color.green(color)), ch(Color.blue(color)))
    }

    /**
     * Builds the face shader for [surface] over the rect the glyphs occupy.
     * Returns null for a flat surface, which lets the caller keep the plain fill colour.
     *
     * [lightAngleDeg] is the light-source bearing from the lighting pad — 0° is straight
     * up, growing clockwise. The shading axis rotates with it, so dragging the pad moves
     * where the light falls on the face instead of moving the glyphs.
     */
    fun buildShader(
        surface: Text3DSurface,
        baseColor: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        lightAngleDeg: Float = 0f
    ): Shader? {
        if (surface.stops.size < 2) return null
        val colors = surface.stops.map { shade(baseColor, it) }.toIntArray()
        if (colors.all { it == colors[0] }) return null

        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val h = (bottom - top).coerceAtLeast(1f)
        val w = (right - left).coerceAtLeast(1f)

        // Unit vector pointing at the light, in screen space.
        val rad = Math.toRadians((lightAngleDeg - 90f).toDouble())
        val lx = cos(rad).toFloat()
        val ly = sin(rad).toFloat()

        return when (surface.shading) {
            "radial" -> RadialGradient(
                cx + lx * w * 0.3f, cy + ly * h * 0.32f,
                maxOf(w, h) * 0.75f,
                colors, null, Shader.TileMode.CLAMP
            )
            "sweep" -> SweepGradient(cx, cy, closeLoop(colors), null).apply {
                // A sweep starts at 3 o'clock, so spin it round to the light bearing.
                setLocalMatrix(Matrix().apply { setRotate(lightAngleDeg - 90f, cx, cy) })
            }
            // The first stop sits on the lit side and the last on the far side, so the
            // ramp always runs down-light whatever bearing the pad is on.
            else -> {
                val reach = hypot(w, h) / 2f
                LinearGradient(
                    cx + lx * reach, cy + ly * reach,
                    cx - lx * reach, cy - ly * reach,
                    colors, null, Shader.TileMode.CLAMP
                )
            }
        }
    }

    /** A sweep gradient wraps around, so repeat the first colour to avoid a hard seam. */
    private fun closeLoop(colors: IntArray): IntArray =
        if (colors.first() == colors.last()) colors else colors + colors.first()

    // ── Swatch preview ────────────────────────────────────────────────────────

    private val previewCache = mutableMapOf<String, Bitmap>()

    /**
     * A round chip showing exactly what the surface does — its shading ramp, its sheen band
     * and its pattern tile, all at once. The swatch grid uses this instead of a plain
     * gradient ball so a textured finish is recognisable before it is applied.
     *
     * [color] is the surface's own [Text3DSurface.previewColor] when it has one, otherwise
     * the live front colour, so the grid reads as a spread of materials rather than one hue.
     */
    fun previewBitmap(surface: Text3DSurface, color: Int, sizePx: Int): Bitmap {
        val size = sizePx.coerceIn(16, 256)
        val key = "${surface.id}_${color}_$size"
        previewCache[key]?.let { return it }

        val bmp = createBitmap(size, size)
        val canvas = Canvas(bmp)
        val r = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Base ramp, lit from the top-left the way a ball catches a key light.
        paint.shader = buildShader(surface, color, 0f, 0f, size.toFloat(), size.toFloat(), 315f)
        if (paint.shader == null) paint.color = color
        paint.alpha = (surface.alpha * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(r, r, r, paint)

        // Pattern, clipped to the chip.
        surface.pattern?.takeIf { surface.patternAlpha > 0f }?.let { kind ->
            patternShader(kind)?.let { shader ->
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.shader = shader
                    alpha = (surface.patternAlpha * 255f).toInt().coerceIn(0, 255)
                }
                canvas.drawCircle(r, r, r, p)
            }
        }

        // Specular band along the same key light.
        if (surface.sheen > 0f) {
            val a = (surface.sheen * 255f).toInt().coerceIn(0, 255)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    intArrayOf(
                        Color.argb(a, 255, 255, 255),
                        Color.argb((a * 0.15f).toInt(), 255, 255, 255),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.45f, 0.75f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(r, r, r, p)
        }

        // A hairline rim keeps pale finishes from dissolving into the white card.
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.035f
            this.color = Color.argb(38, 0, 0, 0)
        }
        canvas.drawCircle(r, r, r - rim.strokeWidth / 2f, rim)

        previewCache[key] = bmp
        return bmp
    }

    // ── Pattern tiles ─────────────────────────────────────────────────────────

    private val patternCache = mutableMapOf<String, Bitmap>()

    /**
     * A repeating black/white tile for [kind]; the caller tints and fades it with
     * [Text3DSurface.patternAlpha]. Tiles are cached because they are pure functions of [kind].
     */
    fun patternShader(kind: String): Shader? {
        val tile = patternCache.getOrPut(kind) { buildTile(kind) ?: return null }
        return BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private fun buildTile(kind: String): Bitmap? {
        val size = 24
        val bmp = createBitmap(size, size)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (kind) {
            // Fine diagonal twill, reads as denim at glyph scale.
            "hatch" -> {
                paint.strokeWidth = 1.6f
                paint.color = Color.argb(90, 255, 255, 255)
                var x = -size.toFloat()
                while (x < size * 2) {
                    canvas.drawLine(x, 0f, x + size, size.toFloat(), paint)
                    x += 5f
                }
                paint.color = Color.argb(70, 0, 0, 0)
                x = -size.toFloat() + 2.5f
                while (x < size * 2) {
                    canvas.drawLine(x, 0f, x + size, size.toFloat(), paint)
                    x += 5f
                }
            }
            // Over-under basket, the carbon-fibre look.
            "weave" -> {
                paint.style = Paint.Style.FILL
                val half = size / 2
                paint.color = Color.argb(80, 255, 255, 255)
                canvas.drawRect(0f, 0f, half.toFloat(), half.toFloat(), paint)
                canvas.drawRect(half.toFloat(), half.toFloat(), size.toFloat(), size.toFloat(), paint)
                paint.color = Color.argb(110, 0, 0, 0)
                canvas.drawRect(half.toFloat(), 0f, size.toFloat(), half.toFloat(), paint)
                canvas.drawRect(0f, half.toFloat(), half.toFloat(), size.toFloat(), paint)
            }
            // Horizontal bands of varying weight - brushed metal and wood grain.
            "grain" -> {
                paint.strokeWidth = 1f
                val rnd = Random(7)
                for (y in 0 until size) {
                    val a = 30 + rnd.nextInt(70)
                    paint.color = if (y % 3 == 0) Color.argb(a, 0, 0, 0)
                    else Color.argb(a / 2, 255, 255, 255)
                    canvas.drawLine(0f, y.toFloat(), size.toFloat(), y.toFloat(), paint)
                }
            }
            // Scattered specks for a cast-concrete face.
            "speckle" -> {
                val rnd = Random(11)
                repeat(70) {
                    paint.color = if (it % 2 == 0) Color.argb(80, 0, 0, 0)
                    else Color.argb(70, 255, 255, 255)
                    canvas.drawCircle(
                        rnd.nextFloat() * size,
                        rnd.nextFloat() * size,
                        0.6f + rnd.nextFloat() * 1.1f,
                        paint
                    )
                }
            }
            // Sparse soft diagonals reading as marble veining.
            "veins" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f
                paint.color = Color.argb(70, 0, 0, 0)
                canvas.drawLine(-4f, size * 0.8f, size * 0.9f, -4f, paint)
                paint.strokeWidth = 0.8f
                paint.color = Color.argb(50, 0, 0, 0)
                canvas.drawLine(size * 0.2f, size.toFloat(), size.toFloat(), size * 0.25f, paint)
                paint.color = Color.argb(60, 255, 255, 255)
                canvas.drawLine(0f, size * 0.35f, size * 0.6f, size.toFloat(), paint)
            }
            // Even polka dots, offset row to row so the field never lines up in columns.
            "dots" -> {
                paint.style = Paint.Style.FILL
                val r = 2.6f
                paint.color = Color.argb(95, 255, 255, 255)
                canvas.drawCircle(size * 0.25f, size * 0.25f, r, paint)
                canvas.drawCircle(size * 0.75f, size * 0.75f, r, paint)
                paint.color = Color.argb(80, 0, 0, 0)
                canvas.drawCircle(size * 0.75f, size * 0.25f, r, paint)
                canvas.drawCircle(size * 0.25f, size * 0.75f, r, paint)
            }
            // Vertical candy stripes.
            "stripes" -> {
                paint.style = Paint.Style.FILL
                var x = 0f
                while (x < size) {
                    paint.color = Color.argb(85, 255, 255, 255)
                    canvas.drawRect(x, 0f, x + 3f, size.toFloat(), paint)
                    paint.color = Color.argb(70, 0, 0, 0)
                    canvas.drawRect(x + 3f, 0f, x + 6f, size.toFloat(), paint)
                    x += 6f
                }
            }
            // Diagonals both ways — the woollen tweed look.
            "crosshatch" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.3f
                paint.color = Color.argb(75, 255, 255, 255)
                var x = -size.toFloat()
                while (x < size * 2) {
                    canvas.drawLine(x, 0f, x + size, size.toFloat(), paint)
                    x += 6f
                }
                paint.color = Color.argb(70, 0, 0, 0)
                x = -size.toFloat()
                while (x < size * 2) {
                    canvas.drawLine(x + size, 0f, x, size.toFloat(), paint)
                    x += 6f
                }
            }
            // Woven slubs: dense verticals over lighter horizontals.
            "linen" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                val rnd = Random(23)
                for (i in 0 until size step 2) {
                    paint.color = Color.argb(35 + rnd.nextInt(45), 255, 255, 255)
                    canvas.drawLine(i.toFloat(), 0f, i.toFloat(), size.toFloat(), paint)
                    paint.color = Color.argb(30 + rnd.nextInt(40), 0, 0, 0)
                    canvas.drawLine(0f, i + 1f, size.toFloat(), i + 1f, paint)
                }
            }
            // Two-way banding with a thin overcheck — reads as tartan at glyph scale.
            "plaid" -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(70, 0, 0, 0)
                canvas.drawRect(0f, 0f, 9f, size.toFloat(), paint)
                canvas.drawRect(0f, 0f, size.toFloat(), 9f, paint)
                paint.color = Color.argb(85, 255, 255, 255)
                canvas.drawRect(13f, 0f, 17f, size.toFloat(), paint)
                canvas.drawRect(0f, 13f, size.toFloat(), 17f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = Color.argb(60, 255, 255, 255)
                canvas.drawLine(21f, 0f, 21f, size.toFloat(), paint)
                canvas.drawLine(0f, 21f, size.toFloat(), 21f, paint)
            }
            // Interlocking loops for a knitted face.
            "mesh" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.6f
                paint.color = Color.argb(80, 255, 255, 255)
                val half = size / 2f
                canvas.drawArc(RectF(0f, 0f, half, half), 0f, 180f, false, paint)
                canvas.drawArc(RectF(half, half, size.toFloat(), size.toFloat()), 0f, 180f, false, paint)
                paint.color = Color.argb(75, 0, 0, 0)
                canvas.drawArc(RectF(half, 0f, size.toFloat(), half), 180f, 180f, false, paint)
                canvas.drawArc(RectF(0f, half, half, size.toFloat()), 180f, 180f, false, paint)
            }
            // Overlapping half-circles — snake and dragon scales.
            "scales" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                val r = size / 4f
                for (row in 0..2) {
                    val cy = row * r * 2f
                    val shift = if (row % 2 == 0) 0f else r
                    var cxp = -r + shift
                    while (cxp < size + r) {
                        paint.color = Color.argb(80, 0, 0, 0)
                        canvas.drawArc(RectF(cxp - r, cy - r, cxp + r, cy + r), 0f, 180f, false, paint)
                        paint.color = Color.argb(60, 255, 255, 255)
                        canvas.drawArc(RectF(cxp - r, cy - r + 1.5f, cxp + r, cy + r + 1.5f), 20f, 140f, false, paint)
                        cxp += r * 2f
                    }
                }
            }
            // Hex cells.
            "honeycomb" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f
                paint.color = Color.argb(85, 0, 0, 0)
                val hex = Path()
                val r = size / 4f
                for (cell in 0..1) {
                    val ccx = size / 2f
                    val ccy = cell * size / 2f
                    hex.reset()
                    for (i in 0..5) {
                        val a = Math.toRadians((60 * i - 30).toDouble())
                        val px = ccx + (r * kotlin.math.cos(a)).toFloat()
                        val py = ccy + (r * kotlin.math.sin(a)).toFloat()
                        if (i == 0) hex.moveTo(px, py) else hex.lineTo(px, py)
                    }
                    hex.close()
                    canvas.drawPath(hex, paint)
                }
                paint.color = Color.argb(60, 255, 255, 255)
                canvas.drawLine(0f, size / 4f, size.toFloat(), size / 4f, paint)
            }
            // Raised tread plate.
            "diamondplate" -> {
                paint.style = Paint.Style.FILL
                val bar = Path()
                fun tread(x: Float, y: Float, flip: Boolean) {
                    bar.reset()
                    val d = if (flip) -1f else 1f
                    bar.moveTo(x, y)
                    bar.lineTo(x + 9f, y + 5f * d)
                    bar.lineTo(x + 9f, y + 8f * d)
                    bar.lineTo(x, y + 3f * d)
                    bar.close()
                    canvas.drawPath(bar, paint)
                }
                paint.color = Color.argb(95, 255, 255, 255)
                tread(1f, 3f, false)
                tread(13f, 15f, true)
                paint.color = Color.argb(85, 0, 0, 0)
                tread(2f, 6f, false)
                tread(14f, 18f, true)
            }
            // Sparse bright flecks that catch the light.
            "glitter" -> {
                val rnd = Random(31)
                paint.style = Paint.Style.FILL
                repeat(46) {
                    val gx = rnd.nextFloat() * size
                    val gy = rnd.nextFloat() * size
                    paint.color = if (it % 3 == 0) Color.argb(50, 0, 0, 0)
                    else Color.argb(120 + rnd.nextInt(120), 255, 255, 255)
                    val s = 0.5f + rnd.nextFloat() * 1.4f
                    canvas.drawCircle(gx, gy, s, paint)
                }
            }
            // Fine grit, for chalk, sandstone and suede.
            "sand" -> {
                val rnd = Random(41)
                paint.style = Paint.Style.FILL
                repeat(150) {
                    paint.color = if (it % 2 == 0) Color.argb(45, 0, 0, 0)
                    else Color.argb(45, 255, 255, 255)
                    canvas.drawCircle(rnd.nextFloat() * size, rnd.nextFloat() * size, 0.5f, paint)
                }
            }
            // Pebbled hide with soft creases.
            "leather" -> {
                val rnd = Random(53)
                paint.style = Paint.Style.FILL
                repeat(26) {
                    paint.color = Color.argb(40 + rnd.nextInt(40), 0, 0, 0)
                    canvas.drawCircle(
                        rnd.nextFloat() * size, rnd.nextFloat() * size,
                        1.4f + rnd.nextFloat() * 2.2f, paint
                    )
                }
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.9f
                paint.color = Color.argb(55, 255, 255, 255)
                repeat(10) {
                    val gx = rnd.nextFloat() * size
                    val gy = rnd.nextFloat() * size
                    canvas.drawCircle(gx, gy, 1.8f + rnd.nextFloat() * 2f, paint)
                }
            }
            // Rolling bands for liquid and ice.
            "waves" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f
                val wave = Path()
                for (row in 0..3) {
                    val y = row * 6f
                    wave.reset()
                    wave.moveTo(0f, y)
                    wave.rQuadTo(6f, -3f, 12f, 0f)
                    wave.rQuadTo(6f, 3f, 12f, 0f)
                    paint.color = if (row % 2 == 0) Color.argb(75, 255, 255, 255)
                    else Color.argb(65, 0, 0, 0)
                    canvas.drawPath(wave, paint)
                }
            }
            // Soft irregular blobs.
            "camo" -> {
                paint.style = Paint.Style.FILL
                val rnd = Random(67)
                repeat(7) {
                    paint.color = when (it % 3) {
                        0 -> Color.argb(90, 0, 0, 0)
                        1 -> Color.argb(70, 255, 255, 255)
                        else -> Color.argb(60, 0, 0, 0)
                    }
                    val blob = Path()
                    val bx = rnd.nextFloat() * size
                    val by = rnd.nextFloat() * size
                    blob.moveTo(bx, by)
                    blob.rQuadTo(4f, -3f, 7f, 1f)
                    blob.rQuadTo(3f, 4f, -2f, 6f)
                    blob.rQuadTo(-5f, 2f, -7f, -2f)
                    blob.close()
                    canvas.drawPath(blob, paint)
                }
            }
            // Sharp creased facets, for foil and ice.
            "crinkle" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.1f
                val rnd = Random(73)
                repeat(9) {
                    paint.color = if (it % 2 == 0) Color.argb(90, 255, 255, 255)
                    else Color.argb(70, 0, 0, 0)
                    val sx = rnd.nextFloat() * size
                    val sy = rnd.nextFloat() * size
                    val crease = Path()
                    crease.moveTo(sx, sy)
                    crease.lineTo(sx + (rnd.nextFloat() - 0.5f) * 14f, sy + (rnd.nextFloat() - 0.5f) * 14f)
                    crease.rLineTo((rnd.nextFloat() - 0.5f) * 12f, (rnd.nextFloat() - 0.5f) * 12f)
                    canvas.drawPath(crease, paint)
                }
            }
            else -> return null
        }
        return bmp
    }
}
