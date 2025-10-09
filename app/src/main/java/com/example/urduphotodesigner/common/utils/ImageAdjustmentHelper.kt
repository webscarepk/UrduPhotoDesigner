package com.example.urduphotodesigner.common.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.example.urduphotodesigner.common.canvas.model.AdjustmentValues
import kotlin.math.exp
import kotlin.math.roundToInt

object ImageAdjustmentHelper {

    /**
     * Apply all 12 adjustments to the given bitmap and return a new one.
     * Compatible with Android 8 → 15 (uses RenderScript Toolkit).
     */
    fun applyAllAdjustments(
        source: Bitmap, values: AdjustmentValues
    ): Bitmap {
        if (source.isRecycled) return source

        val base = source.copy(Bitmap.Config.ARGB_8888, true)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()

        // -------------------------------------------------
        // 1️⃣ Brightness (-100 → +100)
        // -------------------------------------------------
        val brightnessShift = values.brightness * 2.55f
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

        // -------------------------------------------------
        // 2️⃣ Contrast (0.5 → 1.5)
        // -------------------------------------------------
        val contrast = values.contrast.coerceIn(0.5f, 1.5f)
        val translate = (1f - contrast) * 128f
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

        // -------------------------------------------------
        // 4️⃣ Saturation (0 → 2)
        // -------------------------------------------------
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(values.saturation.coerceIn(0f, 2f))
        cm.postConcat(satMatrix)

        // -------------------------------------------------
        // 5️⃣ Vibrance (0 → 2)
        // -------------------------------------------------
        if (values.vibrance != 1f) {
            val vibrance = values.vibrance.coerceIn(0f, 2f)
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

        // -------------------------------------------------
        // 6️⃣ Temperature / Tint (-100 → +100)
        // -------------------------------------------------
        val temp = values.temperature / 100f
        val tint = values.tint / 100f
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

        // -------------------------------------------------
        // 7️⃣ Shadows / Highlights (-100 → +100)
        // -------------------------------------------------
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

        // -------------------------------------------------
        // 8️⃣ Clarity (-100 → +100)
        // -------------------------------------------------
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

        // -------------------------------------------------
        // 9️⃣ Fade (0 → 100)
        // -------------------------------------------------
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
        if (values.sharpness > 0f) {
            result = applySharpnessFallback(result, values.sharpness)
        }

        // -------------------------------------------------
        // 🔟 Blur (0 → 25)
        // -------------------------------------------------
        if (values.blur > 0f) {
            result = applyGaussianBlur(result, values.blur.coerceIn(0f, 25f))
        }
        return result
    }

    // -------------------------------------------------
    // Gaussian blur fallback (fast + RenderScript-free)
    // -------------------------------------------------
    private fun applyGaussianBlur(src: Bitmap, radius: Float): Bitmap {
        if (radius <= 0f) return src

        // 🔹 Create blurred bitmap with expanded bounds (padding around)
        val blurred = applyGaussianConvolutionBlurWithPadding(src, radius)

        val result = createBitmap(blurred.width, blurred.height)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw blurred bitmap fully (no crop or masking to src size)
        canvas.drawBitmap(blurred, 0f, 0f, paint)

        return result
    }

    // -------------------------------------------------
    // Expanded Gaussian convolution blur
    // -------------------------------------------------
    private fun applyGaussianConvolutionBlurWithPadding(src: Bitmap, radius: Float): Bitmap {
        if (radius <= 0f) return src

        val sigma = radius / 2f
        val kernelSize = ((radius * 4).roundToInt() or 1).coerceIn(3, 49)
        val half = kernelSize / 2
        val kernel = FloatArray(kernelSize)
        var sum = 0f

        // --- Build Gaussian kernel ---
        for (i in 0 until kernelSize) {
            val x = i - half
            val g = exp(-(x * x) / (2 * sigma * sigma))
            kernel[i] = g
            sum += g
        }

        // Normalize
        for (i in kernel.indices) kernel[i] /= sum

        val padding = half
        val width = src.width
        val height = src.height
        val expandedW = width + padding * 2
        val expandedH = height + padding * 2

        // --- Create padded bitmap ---
        val padded = createBitmap(expandedW, expandedH)
        val canvas = Canvas(padded)
        canvas.drawBitmap(src, padding.toFloat(), padding.toFloat(), null)

        val pixels = IntArray(expandedW * expandedH)
        val temp = IntArray(expandedW * expandedH)
        padded.getPixels(pixels, 0, expandedW, 0, 0, expandedW, expandedH)

        // --- Horizontal pass ---
        for (y in 0 until expandedH) {
            for (x in 0 until expandedW) {
                var r = 0f
                var g = 0f
                var b = 0f
                var a = 0f
                for (k in -half..half) {
                    val px = (x + k).coerceIn(0, expandedW - 1)
                    val color = pixels[y * expandedW + px]
                    val w = kernel[k + half]
                    a += Color.alpha(color) * w
                    r += Color.red(color) * w
                    g += Color.green(color) * w
                    b += Color.blue(color) * w
                }
                temp[y * expandedW + x] = Color.argb(a.toInt(), r.toInt(), g.toInt(), b.toInt())
            }
        }

        // --- Vertical pass ---
        val out = IntArray(expandedW * expandedH)
        for (x in 0 until expandedW) {
            for (y in 0 until expandedH) {
                var r = 0f
                var g = 0f
                var b = 0f
                var a = 0f
                for (k in -half..half) {
                    val py = (y + k).coerceIn(0, expandedH - 1)
                    val color = temp[py * expandedW + x]
                    val w = kernel[k + half]
                    a += Color.alpha(color) * w
                    r += Color.red(color) * w
                    g += Color.green(color) * w
                    b += Color.blue(color) * w
                }
                out[y * expandedW + x] = Color.argb(a.toInt(), r.toInt(), g.toInt(), b.toInt())
            }
        }

        val result = createBitmap(expandedW, expandedH)
        result.setPixels(out, 0, expandedW, 0, 0, expandedW, expandedH)
        return result
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
