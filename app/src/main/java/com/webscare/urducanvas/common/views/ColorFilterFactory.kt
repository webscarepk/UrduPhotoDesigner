package com.webscare.urducanvas.common.views

import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter

object ColorFilterFactory {

    fun colorFilterFor(filter: ImageFilter?): ColorFilter? = when (filter) {
        null, ImageFilter.None -> null
        ImageFilter.SoftBlur, ImageFilter.Glow -> null
        ImageFilter.Grayscale -> createGrayscaleFilter()
        ImageFilter.Sepia -> createSepiaFilter()
        ImageFilter.Invert -> createInvertFilter()
        ImageFilter.CoolTint -> createCoolTintFilter()
        ImageFilter.WarmTint -> createWarmTintFilter()
        ImageFilter.Vintage -> createVintageFilter()
        ImageFilter.Film -> createFilmFilter()
        ImageFilter.TealOrange -> createTealOrangeFilter()
        ImageFilter.HighContrast -> createHighContrastFilter()
        ImageFilter.BlackWhite -> createBlackWhiteFilter()
        ImageFilter.BrightnessBoost -> createBrightnessBoostFilter()
        ImageFilter.Sharpen -> createSharpenFilter()
        ImageFilter.Sketch -> createSketchFilter()
        ImageFilter.Cartoon -> createCartoonFilter()
        ImageFilter.HDR -> createHDRFilter()
        ImageFilter.Lomo -> createLomoFilter()
        ImageFilter.Pastel -> createPastelFilter()
        ImageFilter.Dramatic -> createDramaticFilter()
        ImageFilter.GoldenHour -> createGoldenHourFilter()
        ImageFilter.Cyberpunk -> createCyberpunkFilter()
    }

    private fun createGrayscaleFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply { setSaturation(0f) }
    )

    private fun createSepiaFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createInvertFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createCoolTintFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                1.1f, 0f, 0f, 0f, -20f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1.3f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createWarmTintFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                1.3f, 0f, 0f, 0f, 30f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0.8f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createVintageFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                0.9f, 0.3f, 0.1f, 0f, 5f,
                0.2f, 0.8f, 0.2f, 0f, 5f,
                0.1f, 0.2f, 0.7f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createFilmFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                1.2f, 0.1f, 0.1f, 0f, 15f,
                0.1f, 1.2f, 0.1f, 0f, 10f,
                0.1f, 0.1f, 0.9f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createTealOrangeFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                1.2f, 0f, 0f, 0f, 20f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0.8f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createHighContrastFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                1.5f, 0f, 0f, 0f, -50f,
                0f, 1.5f, 0f, 0f, -50f,
                0f, 0f, 1.5f, 0f, -50f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createBlackWhiteFilter(): ColorMatrixColorFilter {
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val contrast = ColorMatrix().apply {
            set(floatArrayOf(
                1.4f, 0f, 0f, 0f, -50f,
                0f, 1.4f, 0f, 0f, -50f,
                0f, 0f, 1.4f, 0f, -50f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        cm.postConcat(contrast)
        return ColorMatrixColorFilter(cm)
    }

    private fun createBrightnessBoostFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                1.2f, 0f, 0f, 0f, 30f,
                0f, 1.2f, 0f, 0f, 30f,
                0f, 0f, 1.2f, 0f, 30f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createSharpenFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                2f, -1f, -1f, 0f, 0f,
                -1f, 2f, -1f, 0f, 0f,
                -1f, -1f, 2f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createSketchFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply { setSaturation(0f) }
    )

    private fun createCartoonFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                1.5f, 0f, 0f, 0f, -30f,
                0f, 1.5f, 0f, 0f, -30f,
                0f, 0f, 1.5f, 0f, -30f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createHDRFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                1.3f, 0f, 0f, 0f, -20f,
                0f, 1.3f, 0f, 0f, -20f,
                0f, 0f, 1.3f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createLomoFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                1.2f, 0.2f, 0.1f, 0f, 10f,
                0.1f, 1.0f, 0.1f, 0f, 5f,
                0.1f, 0.1f, 1.2f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createPastelFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                1.0f, 0f, 0f, 0f, 20f,
                0f, 1.0f, 0f, 0f, 20f,
                0f, 0f, 1.0f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createDramaticFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                1.5f, 0f, 0f, 0f, -40f,
                0f, 1.5f, 0f, 0f, -40f,
                0f, 0f, 1.5f, 0f, -40f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createGoldenHourFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                1.2f, 0.2f, 0f, 0f, 30f,
                0.1f, 1.1f, 0f, 0f, 20f,
                0f, 0f, 0.8f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )

    private fun createCyberpunkFilter() = ColorMatrixColorFilter(
        ColorMatrix().apply {
            set(floatArrayOf(
                0.9f, 0.2f, 0.6f, 0f, 30f,
                0.1f, 0.8f, 0.5f, 0f, 10f,
                0.2f, 0.3f, 1.5f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
    )
}
