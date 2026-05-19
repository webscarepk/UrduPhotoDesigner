package com.webscare.urducanvas.common.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.core.graphics.createBitmap
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

        // NOTE: Feather edge-fading is NOT applied here during normal rendering.
        // It is applied in real-time on the canvas in CanvasView via drawFeatherMask(),
        // which uses four LinearGradient strips with DST_IN compositing — pure GPU,
        // zero pixel loops, instant live preview on every seekbar frame.
        // For export, call applyFeatherForExport() separately on the final bitmap.

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FEATHER FOR EXPORT — called on the final composed bitmap during export only.
    // Live preview uses drawFeatherMask() in CanvasView (GPU, zero CPU cost).
    // ─────────────────────────────────────────────────────────────────────────
    fun applyFeatherForExport(src: Bitmap, featherRadius: Float, featherWidth: Float = 50f): Bitmap {
        return applyFeather(src, featherRadius, featherWidth)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FEATHER — fades each edge of the bitmap to fully transparent.
    //
    //   featherRadius (0–100): how far INWARD from the edge the fade zone extends.
    //     0  = feather band starts right at the very edge (ultra-thin, barely visible)
    //     50 = feather band extends 50% of the image half-dimension inward
    //     100 = feather band reaches the center (entire image fades)
    //
    //   featherWidth (0–100): how gradual the transition is within the fade zone.
    //     0  = sharp linear ramp — you see the gradient band clearly
    //     100 = smooth cubic ease-in — very soft, photographic look
    //
    //   Algorithm: per-pixel alpha multiplication using four independent edge ramps.
    //   Each pixel's alpha = originalAlpha * leftRamp * rightRamp * topRamp * bottomRamp
    //   where each ramp goes from 0 (at the very edge) to 1 (at the inner boundary).
    //   This handles rectangular images correctly — the fade is uniform along each edge,
    //   unlike a radial gradient which produces oval feathering on non-square images.
    // ─────────────────────────────────────────────────────────────────────────
    private fun applyFeather(src: Bitmap, featherRadius: Float, featherWidth: Float = 50f): Bitmap {
        val w = src.width
        val h = src.height
        if (w == 0 || h == 0) return src

        // How far inward the fade zone extends (in pixels) for each axis
        // featherRadius=100 → bandW = w/2 (reaches center), bandH = h/2
        val bandW = (w / 2f) * (featherRadius / 100f)
        val bandH = (h / 2f) * (featherRadius / 100f)

        // Smoothing exponent: featherWidth=0 → exponent=1 (linear), =100 → exponent=4 (very soft)
        val exponent = 1f + (featherWidth / 100f) * 3f

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            // Top and bottom ramps: 0 at edge, 1 at bandH depth, stay 1 beyond
            val topRamp: Float = if (bandH <= 0f) 1f else {
                val t = (y / bandH).coerceIn(0f, 1f)
                smoothStep(t, exponent)
            }
            val bottomRamp: Float = if (bandH <= 0f) 1f else {
                val t = ((h - 1 - y) / bandH).coerceIn(0f, 1f)
                smoothStep(t, exponent)
            }
            val vertRamp = topRamp * bottomRamp

            for (x in 0 until w) {
                val leftRamp: Float = if (bandW <= 0f) 1f else {
                    val t = (x / bandW).coerceIn(0f, 1f)
                    smoothStep(t, exponent)
                }
                val rightRamp: Float = if (bandW <= 0f) 1f else {
                    val t = ((w - 1 - x) / bandW).coerceIn(0f, 1f)
                    smoothStep(t, exponent)
                }

                val alphaMult = vertRamp * leftRamp * rightRamp
                val px = pixels[y * w + x]
                val origAlpha = Color.alpha(px)
                val newAlpha = (origAlpha * alphaMult).roundToInt().coerceIn(0, 255)
                pixels[y * w + x] = Color.argb(newAlpha, Color.red(px), Color.green(px), Color.blue(px))
            }
        }

        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    // Smooth ramp: t=0 → 0.0 (transparent), t=1 → 1.0 (opaque)
    // exponent > 1 adds ease-in curve for softer appearance
    private fun smoothStep(t: Float, exponent: Float): Float {
        // Cubic smooth: 3t²-2t³ gives a nicer S-curve than linear
        val smooth = t * t * (3f - 2f * t)
        // Apply exponent for extra softness (featherWidth control)
        return Math.pow(smooth.toDouble(), exponent.toDouble()).toFloat().coerceIn(0f, 1f)
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
    // SHADOWS  (-100 → +100)
    // ─────────────────────────────────────────────────────────────────────────
    private fun applyShadows(src: Bitmap, shadows: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val shift = shadows / 255f

        for (i in pixels.indices) {
            val px = pixels[i]
            val a = Color.alpha(px)
            val r = Color.red(px) / 255f
            val g = Color.green(px) / 255f
            val b = Color.blue(px) / 255f
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

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
    // HIGHLIGHTS  (-100 → +100)
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
    // CLARITY — mid-tone micro-contrast
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
    // FADE — white overlay
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
    // SHARPNESS — unsharp mask
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