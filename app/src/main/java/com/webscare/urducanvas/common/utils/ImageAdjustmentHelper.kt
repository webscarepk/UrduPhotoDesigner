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
import com.webscare.urducanvas.common.canvas.model.AdjustmentValues
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import kotlin.math.exp
import kotlin.math.roundToInt

object ImageAdjustmentHelper {

    /**
     * Apply all 12 adjustments to the given bitmap and return a new one.
     * Compatible with Android 8 → 15 (uses RenderScript Toolkit).
     */
    fun applyAllAdjustments(context: Context,
        source: Bitmap, element: CanvasElement
    ): Bitmap {
        if (source.isRecycled) return source

        val values = element.adjustments

        val base = source.copy(Bitmap.Config.ARGB_8888, true)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()

        // -------------------------------------------------
        // 1️⃣ Brightness (-100 → +100)
        // -------------------------------------------------
        val brightnessShift = values.brightness * 2.55f
        if (element.hasLight){
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f,
                        0f,
                        0f,
                        0f,
                        brightnessShift,
                        0f,
                        1f,
                        0f,
                        0f,
                        brightnessShift,
                        0f,
                        0f,
                        1f,
                        0f,
                        brightnessShift,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            )
        }

        // -------------------------------------------------
        // 2️⃣ Contrast (0.5 → 1.5)
        // -------------------------------------------------
        val contrast = values.contrast.coerceIn(0.5f, 1.5f)
        val translate = (1f - contrast) * 128f
        if (element.hasLight){
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast,
                        0f,
                        0f,
                        0f,
                        translate,
                        0f,
                        contrast,
                        0f,
                        0f,
                        translate,
                        0f,
                        0f,
                        contrast,
                        0f,
                        translate,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            )
        }

        // -------------------------------------------------
        // 4️⃣ Saturation (0 → 2)
        // -------------------------------------------------
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(values.saturation.coerceIn(0f, 2f))
        if (element.hasColor){
            cm.postConcat(satMatrix)
        }

        // -------------------------------------------------
        // 5️⃣ Vibrance (0 → 2)
        // -------------------------------------------------
        if (values.vibrance != 1f) {
            val vibrance = values.vibrance.coerceIn(0f, 2f)
            if (element.hasColor){
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            vibrance,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            vibrance,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            vibrance,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            1f,
                            0f
                        )
                    )
                )
            }
        }

        // -------------------------------------------------
        // 6️⃣ Temperature / Tint (-100 → +100)
        // -------------------------------------------------
        val temp = values.temperature / 100f
        val tint = values.tint / 100f
        if (element.hasColor){
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f + temp,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f + tint,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f - temp,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            )
        }

        // -------------------------------------------------
        // 7️⃣ Shadows / Highlights (-100 → +100)
        // -------------------------------------------------
        if (element.hasLight){
            if (values.shadows != 0f || values.highlights != 0f) {
                val shadowScale = 1f + (values.shadows / 200f)
                val highlightScale = 1f - (values.highlights / 200f)
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            shadowScale,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            highlightScale,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            (shadowScale + highlightScale) / 2f,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            1f,
                            0f
                        )
                    )
                )
            }
        }

        // -------------------------------------------------
        // 8️⃣ Clarity (-100 → +100)
        // -------------------------------------------------
        if (element.hasDetail){
            if (values.clarity != 0f) {
                val clarityScale = 1f + (values.clarity / 200f)
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            clarityScale,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            clarityScale,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            clarityScale,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            1f,
                            0f
                        )
                    )
                )
            }
        }

        // -------------------------------------------------
        // 9️⃣ Fade (0 → 100)
        // -------------------------------------------------
        if (element.hasDetail){
            if (values.fade != 0f) {
                val fadeScale = 1f - (values.fade / 100f)
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            fadeScale,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            fadeScale,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            fadeScale,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            1f,
                            0f
                        )
                    )
                )
            }
        }

        // -------------------------------------------------
        // ✅ Apply combined ColorMatrix
        // -------------------------------------------------
        paint.colorFilter = ColorMatrixColorFilter(cm)
        val filtered = createBitmap(base.width, base.height)
        Canvas(filtered).drawBitmap(base, 0f, 0f, paint)

        var result = filtered

        // -------------------------------------------------
        // 9️⃣ Sharpness (0 → 5)
        // -------------------------------------------------
        if (element.hasDetail && values.sharpness > 0f) {
            result = applySharpnessFallback(result, values.sharpness)
        }

        // -------------------------------------------------
        // 🔟 Blur (0 → 25)
        // -------------------------------------------------
        if (element.hasBlur && element.blurValue > 0f) {
            result = applyGaussianBlurWithPadding(context, result, element.blurValue.coerceIn(0f, 25f))
        }
        return result
    }

    // -------------------------------------------------
    // Gaussian blur fallback (fast + RenderScript-free)
    // -------------------------------------------------
    private fun applyGaussianBlurWithPadding(context: Context, src: Bitmap, radius: Float): Bitmap {
        if (radius <= 0f) return src

        val safeRadius = radius.coerceIn(1f, 25f)
        val padding = (safeRadius * 2).roundToInt()
        val width = src.width
        val height = src.height
        val expandedW = width + padding * 2
        val expandedH = height + padding * 2

        val padded = createBitmap(expandedW, expandedH)
        val canvas = Canvas(padded)
        canvas.drawBitmap(src, padding.toFloat(), padding.toFloat(), null)
        val blurred = createBitmap(expandedW, expandedH)
        try {
            val rs = RenderScript.create(context)
            val input = Allocation.createFromBitmap(rs, padded)
            val output = Allocation.createTyped(rs, input.type)
            val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            blur.setRadius(safeRadius)
            blur.setInput(input)
            blur.forEach(output)
            output.copyTo(blurred)
            rs.destroy()
        } catch (e: Exception) {
            return fastBoxBlurWithPadding(padded, safeRadius)
        }
        return blurred
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
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0
                for (dy in -r..r) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    for (dx in -r..r) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val c = pixels[ny * w + nx]
                        rSum += Color.red(c)
                        gSum += Color.green(c)
                        bSum += Color.blue(c)
                        count++
                    }
                }
                out[y * w + x] = Color.rgb(rSum / count, gSum / count, bSum / count)
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Apply sharpening using the new RenderScript Toolkit.
     * Fully supported on Android 14+.
     */
    private fun applySharpnessFallback(src: Bitmap, sharpness: Float): Bitmap {
        if (sharpness <= 0f) return src
        val s = (sharpness / 5f).coerceIn(0f, 1f)

        val kernel = arrayOf(
            floatArrayOf(0f, -s, 0f), floatArrayOf(-s, 1f + (4 * s), -s), floatArrayOf(0f, -s, 0f)
        )

        val w = src.width
        val h = src.height
        val result = src.config?.let { createBitmap(w, h, it) }

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)

        fun clamp(v: Int) = v.coerceIn(0, 255)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var r = 0f
                var g = 0f
                var b = 0f
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
        result?.setPixels(out, 0, w, 0, 0, w, h)
        return result!!
    }
}
