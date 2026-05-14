package com.webscare.urducanvas.common.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.core.graphics.createBitmap
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ImageAdjustmentHelper {

    fun applyAllAdjustments(
        context: Context,
        source: Bitmap,
        element: CanvasElement
    ): Bitmap {
        if (source.isRecycled) return source

        val values = element.adjustments
        val cm = ColorMatrix()

        // ─────────────────────────────────────────────
        // 1️⃣  BRIGHTNESS  (-100 → +100)
        // ─────────────────────────────────────────────
        if (element.hasLight && values.brightness != 0f) {
            val shift = values.brightness * 1.0f
            cm.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, shift,
                0f, 1f, 0f, 0f, shift,
                0f, 0f, 1f, 0f, shift,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // ─────────────────────────────────────────────
        // 2️⃣  CONTRAST  (0.5 → 1.5)
        // ─────────────────────────────────────────────
        if (element.hasLight && values.contrast != 1f) {
            val c = values.contrast.coerceIn(0.5f, 1.5f)
            val t = 128f * (1f - c)
            cm.postConcat(ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // ─────────────────────────────────────────────
        // 4️⃣  SATURATION  (0 → 2)
        // ─────────────────────────────────────────────
        if (element.hasColor && values.saturation != 1f) {
            val sat = ColorMatrix()
            sat.setSaturation(values.saturation.coerceIn(0f, 2f))
            cm.postConcat(sat)
        }

        // ─────────────────────────────────────────────
        // 5️⃣  TEMPERATURE  (-100 → +100)
        // ─────────────────────────────────────────────
        if (element.hasColor && values.temperature != 0f) {
            val t = values.temperature / 100f
            cm.postConcat(ColorMatrix(floatArrayOf(
                1f + t * 0.2f, 0f, 0f, 0f, t * 20f,
                0f,            1f, 0f, 0f, 0f,
                0f, 0f, 1f - t * 0.2f, 0f, -t * 20f,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // ─────────────────────────────────────────────
        // 6️⃣  TINT  (-100 → +100)
        // ─────────────────────────────────────────────
        if (element.hasColor && values.tint != 0f) {
            val t = values.tint / 100f
            cm.postConcat(ColorMatrix(floatArrayOf(
                1f + t * 0.1f, 0f,            0f, 0f, t * 10f,
                0f,            1f - t * 0.2f, 0f, 0f, -t * 20f,
                0f,            0f, 1f + t * 0.1f, 0f, t * 10f,
                0f,            0f,            0f, 1f, 0f
            )))
        }

        // ─────────────────────────────────────────────
        // ✅  Apply combined ColorMatrix
        // ─────────────────────────────────────────────
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        val base = source.copy(Bitmap.Config.ARGB_8888, true)
        val filtered = createBitmap(base.width, base.height)
        Canvas(filtered).drawBitmap(base, 0f, 0f, paint)
        base.recycle()

        var result = filtered

        // 5️⃣  VIBRANCE
        if (element.hasColor && values.vibrance != 0f) {
            result = applyVibrance(result, values.vibrance)
        }

        // 7️⃣  SHADOWS
        if (element.hasLight && values.shadows != 0f) {
            result = applyShadows(result, values.shadows)
        }

        // 8️⃣  HIGHLIGHTS
        if (element.hasLight && values.highlights != 0f) {
            result = applyHighlights(result, values.highlights)
        }

        // 9️⃣  CLARITY
        if (element.hasDetail && values.clarity != 0f) {
            result = applyClarity(result, values.clarity)
        }

        // 🔟  FADE
        if (element.hasDetail && values.fade > 0f) {
            result = applyFade(result, values.fade)
        }

        // 1️⃣1️⃣  SHARPNESS
        if (element.hasDetail && values.sharpness > 0f) {
            result = applySharpnessFallback(result, values.sharpness)
        }

        // 1️⃣2️⃣  BLUR
        if (element.hasBlur && element.blurValue > 0f) {
            result = applyGaussianBlurWithPadding(
                context, result, element.blurValue.coerceIn(0f, 25f)
            )
        }

        // 1️⃣3️⃣  FEATHER  (0 → 100)
        //     Fades the edges of the bitmap to transparent using a radial alpha gradient.
        //     0 = no feathering, 100 = very heavy edge fade (nearly invisible at edges).
        if (element.hasFeather && element.featherRadius > 0f) {
            result = applyFeather(result, element.featherRadius)
        }

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FEATHER — radial alpha vignette that fades bitmap edges to transparent
    //   radius 0   = no fade
    //   radius 100 = very wide edge fade (almost the full image is faded)
    //
    //   Strategy:
    //     • We compute an "inner keep radius" as a fraction of the half-diagonal.
    //       At featherRadius=0 the inner radius equals the half-diagonal (no fade).
    //       At featherRadius=100 the inner radius shrinks to 0 (maximum fade).
    //     • A RadialGradient going from opaque (center) to transparent (outer) is
    //       drawn on top of the image using PorterDuff.Mode.DST_IN, which uses the
    //       gradient alpha as a mask — preserving opaque pixels where alpha=255 and
    //       erasing pixels where alpha=0.
    // ─────────────────────────────────────────────────────────────────────────
    private fun applyFeather(src: Bitmap, featherRadius: Float): Bitmap {
        val w = src.width
        val h = src.height

        // Work on a mutable ARGB_8888 copy so we can blend transparency
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)

        val cx = w / 2f
        val cy = h / 2f
        val halfDiag = sqrt((cx * cx + cy * cy).toDouble()).toFloat()

        // strength 0..1 from featherRadius 0..100
        val strength = (featherRadius / 100f).coerceIn(0f, 1f)

        // outer radius of the gradient = half-diagonal (reaches corners)
        val outerRadius = halfDiag

        // inner radius shrinks as strength grows; at strength=1 it's 0
        // clamp to at least 1px to avoid zero-radius gradient crash
        val innerRadius = (halfDiag * (1f - strength)).coerceAtLeast(1f)

        val gradient = RadialGradient(
            cx, cy,
            outerRadius,
            intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
            floatArrayOf(0f, innerRadius / outerRadius, 1f),
            Shader.TileMode.CLAMP
        )

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        }

        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), maskPaint)

        return out
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VIBRANCE — non-linear saturation boost that protects vivid colors
    // ─────────────────────────────────────────────────────────────────────────
    private fun applyVibrance(src: Bitmap, vibrance: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val strength = vibrance / 100f

        for (i in pixels.indices) {
            val px = pixels[i]
            val a = Color.alpha(px)
            val r = Color.red(px) / 255f
            val g = Color.green(px) / 255f
            val b = Color.blue(px) / 255f

            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val sat = if (maxC == 0f) 0f else (maxC - minC) / maxC

            // Boost low-saturation colors more than high-saturation ones
            val boost = strength * (1f - sat)

            val gray = 0.299f * r + 0.587f * g + 0.114f * b
            val nr = (r + (r - gray) * boost).coerceIn(0f, 1f)
            val ng = (g + (g - gray) * boost).coerceIn(0f, 1f)
            val nb = (b + (b - gray) * boost).coerceIn(0f, 1f)

            pixels[i] = Color.argb(a, (nr * 255).roundToInt(), (ng * 255).roundToInt(), (nb * 255).roundToInt())
        }

        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SHADOWS  (-100 → +100)  — lifts or crushes dark pixels
    // ─────────────────────────────────────────────────────────────────────────
    private fun applyShadows(src: Bitmap, shadows: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val shift = shadows / 255f  // -100..+100 → ~-0.39..+0.39

        for (i in pixels.indices) {
            val px = pixels[i]
            val a = Color.alpha(px)
            val r = Color.red(px) / 255f
            val g = Color.green(px) / 255f
            val b = Color.blue(px) / 255f
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            // Weight: full effect at lum=0, zero effect at lum=0.5+
            val weight = (1f - lum * 2f).coerceIn(0f, 1f)

            val adjust = shift * weight
            val nr = (r + adjust).coerceIn(0f, 1f)
            val ng = (g + adjust).coerceIn(0f, 1f)
            val nb = (b + adjust).coerceIn(0f, 1f)

            pixels[i] = Color.argb(a, (nr * 255).roundToInt(), (ng * 255).roundToInt(), (nb * 255).roundToInt())
        }

        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HIGHLIGHTS  (-100 → +100)  — recovers or boosts bright pixels
    // ─────────────────────────────────────────────────────────────────────────
    private fun applyHighlights(src: Bitmap, highlights: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val shift = highlights / 255f

        for (i in pixels.indices) {
            val px = pixels[i]
            val a = Color.alpha(px)
            val r = Color.red(px) / 255f
            val g = Color.green(px) / 255f
            val b = Color.blue(px) / 255f
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            val weight = ((lum * 2f) - 1f).coerceIn(0f, 1f)

            val adjust = (shift * weight) / 255f
            val nr = (r + adjust).coerceIn(0f, 1f)
            val ng = (g + adjust).coerceIn(0f, 1f)
            val nb = (b + adjust).coerceIn(0f, 1f)

            pixels[i] = Color.argb(a, (nr * 255).roundToInt(), (ng * 255).roundToInt(), (nb * 255).roundToInt())
        }

        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CLARITY — mid-tone micro-contrast (local contrast boost)
    // ─────────────────────────────────────────────────────────────────────────
    private fun applyClarity(src: Bitmap, clarity: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)

        val strength = (clarity / 100f).coerceIn(-1f, 1f)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val px = pixels[y * w + x]
                val a = Color.alpha(px)
                val r = Color.red(px) / 255f
                val g = Color.green(px) / 255f
                val b = Color.blue(px) / 255f
                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                val midWeight = 1f - (2f * lum - 1f) * (2f * lum - 1f)

                var rAvg = 0f; var gAvg = 0f; var bAvg = 0f
                for (dy in -1..1) for (dx in -1..1) {
                    val n = pixels[(y + dy) * w + (x + dx)]
                    rAvg += Color.red(n); gAvg += Color.green(n); bAvg += Color.blue(n)
                }
                rAvg /= (9f * 255f); gAvg /= (9f * 255f); bAvg /= (9f * 255f)

                val boost = strength * midWeight
                val nr = (r + (r - rAvg) * boost).coerceIn(0f, 1f)
                val ng = (g + (g - gAvg) * boost).coerceIn(0f, 1f)
                val nb = (b + (b - bAvg) * boost).coerceIn(0f, 1f)

                out[y * w + x] = Color.argb(a, (nr * 255).roundToInt(), (ng * 255).roundToInt(), (nb * 255).roundToInt())
            }
        }
        for (x in 0 until w) { out[x] = pixels[x]; out[(h - 1) * w + x] = pixels[(h - 1) * w + x] }
        for (y in 0 until h) { out[y * w] = pixels[y * w]; out[y * w + w - 1] = pixels[y * w + w - 1] }

        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FADE — white overlay (film-style fade to white)
    // ─────────────────────────────────────────────────────────────────────────
    private fun applyFade(src: Bitmap, fade: Float): Bitmap {
        val alpha = ((fade / 100f) * 255f).roundToInt().coerceIn(0, 255)
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.alpha = alpha
        }
        canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
        return out
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SHARPNESS — unsharp mask (convolution kernel)
    // ─────────────────────────────────────────────────────────────────────────
    private fun applySharpnessFallback(src: Bitmap, sharpness: Float): Bitmap {
        if (sharpness <= 0f) return src
        val s = (sharpness / 5f).coerceIn(0f, 1f)

        val kernel = arrayOf(
            floatArrayOf(0f, -s, 0f),
            floatArrayOf(-s, 1f + (4 * s), -s),
            floatArrayOf(0f, -s, 0f)
        )

        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)

        fun clamp(v: Int) = v.coerceIn(0, 255)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var r = 0f; var g = 0f; var b = 0f
                for (ky in -1..1) for (kx in -1..1) {
                    val px = pixels[(y + ky) * w + (x + kx)]
                    val kr = kernel[ky + 1][kx + 1]
                    r += Color.red(px) * kr
                    g += Color.green(px) * kr
                    b += Color.blue(px) * kr
                }
                out[y * w + x] = Color.argb(
                    Color.alpha(pixels[y * w + x]),
                    clamp(r.roundToInt()),
                    clamp(g.roundToInt()),
                    clamp(b.roundToInt())
                )
            }
        }
        for (x in 0 until w) { out[x] = pixels[x]; out[(h - 1) * w + x] = pixels[(h - 1) * w + x] }
        for (y in 0 until h) { out[y * w] = pixels[y * w]; out[y * w + w - 1] = pixels[y * w + w - 1] }

        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GAUSSIAN BLUR
    // ─────────────────────────────────────────────────────────────────────────
    private fun applyGaussianBlurWithPadding(
        context: Context, src: Bitmap, radius: Float
    ): Bitmap {
        if (radius <= 0f) return src

        val safeRadius = radius.coerceIn(1f, 25f)
        val padding = (safeRadius * 2).roundToInt()
        val expandedW = src.width + padding * 2
        val expandedH = src.height + padding * 2

        val padded = createBitmap(expandedW, expandedH)
        Canvas(padded).drawBitmap(src, padding.toFloat(), padding.toFloat(), null)

        val blurred = createBitmap(expandedW, expandedH)
        return try {
            val rs = RenderScript.create(context)
            val input = Allocation.createFromBitmap(rs, padded)
            val output = Allocation.createTyped(rs, input.type)
            val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            blur.setRadius(safeRadius)
            blur.setInput(input)
            blur.forEach(output)
            output.copyTo(blurred)
            rs.destroy()
            blurred
        } catch (e: Exception) {
            fastBoxBlurWithPadding(padded, safeRadius)
        }
    }

    private fun fastBoxBlurWithPadding(src: Bitmap, radius: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val r = radius.toInt().coerceAtLeast(1)

        for (y in 0 until h) {
            for (x in 0 until w) {
                var rSum = 0; var gSum = 0; var bSum = 0; var count = 0
                for (dy in -r..r) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    for (dx in -r..r) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val c = pixels[ny * w + nx]
                        rSum += Color.red(c); gSum += Color.green(c); bSum += Color.blue(c)
                        count++
                    }
                }
                out[y * w + x] = Color.rgb(rSum / count, gSum / count, bSum / count)
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}