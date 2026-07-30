package com.webscare.urducanvas.common.canvas.sealed

sealed class ImageFilter(val name: String, val category: String = "Basic") {

    object None : ImageFilter("None", "Basic")
    object Invert : ImageFilter("Invert", "Basic")
    object Grayscale : ImageFilter("Grayscale", "B&W")
    object Sepia : ImageFilter("Sepia", "Vintage")
    data object CoolTint : ImageFilter("CoolTint", "Portrait")
    data object WarmTint : ImageFilter("WarmTint", "Portrait")
    data object Vintage : ImageFilter("Vintage", "Vintage")
    data object Film : ImageFilter("Film", "Cinematic")
    data object TealOrange : ImageFilter("TealOrange", "Cinematic")
    data object HighContrast : ImageFilter("HighContrast", "B&W")
    data object BlackWhite : ImageFilter("BlackWhite", "B&W")
    data object BrightnessBoost : ImageFilter("BrightnessBoost", "Artistic")
    data object SoftBlur : ImageFilter("SoftBlur", "Artistic")
    data object Sharpen : ImageFilter("Sharpen", "Artistic")
    data object Glow : ImageFilter("Glow", "Artistic")
    data object Sketch : ImageFilter("Sketch", "Artistic")
    data object Cartoon : ImageFilter("Cartoon", "Artistic")
    data object HDR : ImageFilter("HDR", "Artistic")
    data object Lomo : ImageFilter("Lomo", "Vintage")
    data object Pastel : ImageFilter("Pastel", "Vintage")
    data object Dramatic : ImageFilter("Dramatic", "Cinematic")
    data object GoldenHour : ImageFilter("GoldenHour", "Cinematic")
    data object Cyberpunk : ImageFilter("Cyberpunk", "Cinematic")
    
    // Instagram Filter Presets
    data object Clarendon : ImageFilter("Clarendon", "Portrait")
    data object Lark : ImageFilter("Lark", "Portrait")
    data object Valencia : ImageFilter("Valencia", "Portrait")
    data object Gingham : ImageFilter("Gingham", "Vintage")
    data object Moon : ImageFilter("Moon", "B&W")
    data object Juno : ImageFilter("Juno", "Portrait")
    data object Reyes : ImageFilter("Reyes", "Vintage")
    data object Slumber : ImageFilter("Slumber", "Vintage")

    fun createColorFilter(intensity: Float = 1.0f): android.graphics.ColorFilter? {
        return Companion.getColorFilter(this, intensity)
    }

    companion object {
        fun getColorFilter(filter: ImageFilter?, intensity: Float = 1.0f): android.graphics.ColorFilter? {
            if (filter == null || filter is None) return null
            val s = intensity.coerceIn(0f, 1f)
            if (s <= 0f) return null

            val rawMatrixArray: FloatArray = when (filter) {
                None -> null
                Grayscale -> floatArrayOf(
                    0.213f, 0.715f, 0.072f, 0f, 0f,
                    0.213f, 0.715f, 0.072f, 0f, 0f,
                    0.213f, 0.715f, 0.072f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                Sepia -> floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                Invert -> floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
                CoolTint -> floatArrayOf(
                    1.1f, 0f, 0f, 0f, -20f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1.3f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                )
                WarmTint -> floatArrayOf(
                    1.3f, 0f, 0f, 0f, 30f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 0.8f, 0f, -20f,
                    0f, 0f, 0f, 1f, 0f
                )
                Vintage -> floatArrayOf(
                    0.9f, 0.3f, 0.1f, 0f, 5f,
                    0.2f, 0.8f, 0.2f, 0f, 5f,
                    0.1f, 0.2f, 0.7f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )
                Film -> floatArrayOf(
                    1.2f, 0.1f, 0.1f, 0f, 15f,
                    0.1f, 1.2f, 0.1f, 0f, 10f,
                    0.1f, 0.1f, 0.9f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )
                TealOrange -> floatArrayOf(
                    1.2f, 0f, 0f, 0f, 20f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 0.8f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )
                HighContrast -> floatArrayOf(
                    1.5f, 0f, 0f, 0f, -50f,
                    0f, 1.5f, 0f, 0f, -50f,
                    0f, 0f, 1.5f, 0f, -50f,
                    0f, 0f, 0f, 1f, 0f
                )
                BlackWhite -> floatArrayOf(
                    0.298f, 0.587f, 0.114f, 0f, -20f,
                    0.298f, 0.587f, 0.114f, 0f, -20f,
                    0.298f, 0.587f, 0.114f, 0f, -20f,
                    0f, 0f, 0f, 1f, 0f
                )
                BrightnessBoost -> floatArrayOf(
                    1.2f, 0f, 0f, 0f, 30f,
                    0f, 1.2f, 0f, 0f, 30f,
                    0f, 0f, 1.2f, 0f, 30f,
                    0f, 0f, 0f, 1f, 0f
                )
                Sharpen -> floatArrayOf(
                    1.4f, -0.2f, -0.2f, 0f, 0f,
                    -0.2f, 1.4f, -0.2f, 0f, 0f,
                    -0.2f, -0.2f, 1.4f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                Cartoon -> floatArrayOf(
                    1.5f, 0f, 0f, 0f, -30f,
                    0f, 1.5f, 0f, 0f, -30f,
                    0f, 0f, 1.5f, 0f, -30f,
                    0f, 0f, 0f, 1f, 0f
                )
                HDR -> floatArrayOf(
                    1.3f, 0f, 0f, 0f, -20f,
                    0f, 1.3f, 0f, 0f, -20f,
                    0f, 0f, 1.3f, 0f, -20f,
                    0f, 0f, 0f, 1f, 0f
                )
                Lomo -> floatArrayOf(
                    1.2f, 0.2f, 0.1f, 0f, -20f,
                    0.1f, 1.2f, 0.1f, 0f, -20f,
                    0.1f, 0.1f, 1.2f, 0f, -20f,
                    0f, 0f, 0f, 1f, 0f
                )
                Pastel -> floatArrayOf(
                    0.75f, 0.25f, 0.25f, 0f, 40f,
                    0.25f, 0.75f, 0.25f, 0f, 40f,
                    0.25f, 0.25f, 0.75f, 0f, 40f,
                    0f, 0f, 0f, 1f, 0f
                )
                Dramatic -> floatArrayOf(
                    1.4f, 0f, 0f, 0f, -40f,
                    0f, 1.1f, 0f, 0f, -20f,
                    0f, 0f, 1.0f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                GoldenHour -> floatArrayOf(
                    1.25f, 0.1f, 0f, 0f, 25f,
                    0.05f, 1.1f, 0f, 0f, 15f,
                    0f, 0f, 0.75f, 0f, -15f,
                    0f, 0f, 0f, 1f, 0f
                )
                Cyberpunk -> floatArrayOf(
                    1.3f, 0f, 0.2f, 0f, 20f,
                    0f, 0.8f, 0.1f, 0f, -10f,
                    0.2f, 0.1f, 1.4f, 0f, 30f,
                    0f, 0f, 0f, 1f, 0f
                )
                Clarendon -> floatArrayOf(
                    1.15f, 0.05f, 0f, 0f, 10f,
                    0f, 1.10f, 0.05f, 0f, 10f,
                    0f, 0.05f, 1.20f, 0f, 15f,
                    0f, 0f, 0f, 1f, 0f
                )
                Lark -> floatArrayOf(
                    1.08f, 0.08f, 0f, 0f, 5f,
                    0f, 1.15f, 0.05f, 0f, 8f,
                    0f, 0.05f, 1.20f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )
                Valencia -> floatArrayOf(
                    1.14f, 0.08f, 0f, 0f, 12f,
                    0.08f, 1.08f, 0.04f, 0f, 8f,
                    0.04f, 0.08f, 0.90f, 0f, -5f,
                    0f, 0f, 0f, 1f, 0f
                )
                Gingham -> floatArrayOf(
                    0.95f, 0.05f, 0.05f, 0f, 15f,
                    0.05f, 0.95f, 0.05f, 0f, 15f,
                    0.05f, 0.05f, 0.90f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                )
                Moon -> floatArrayOf(
                    0.25f, 0.65f, 0.1f, 0f, -10f,
                    0.25f, 0.65f, 0.1f, 0f, -10f,
                    0.25f, 0.65f, 0.1f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )
                Juno -> floatArrayOf(
                    1.20f, 0.05f, 0f, 0f, 10f,
                    0f, 1.10f, 0.05f, 0f, 5f,
                    0.05f, 0f, 1.15f, 0f, -5f,
                    0f, 0f, 0f, 1f, 0f
                )
                Reyes -> floatArrayOf(
                    0.90f, 0.10f, 0.05f, 0f, 22f,
                    0.05f, 0.95f, 0.05f, 0f, 18f,
                    0.05f, 0.10f, 0.85f, 0f, 15f,
                    0f, 0f, 0f, 1f, 0f
                )
                Slumber -> floatArrayOf(
                    0.90f, 0.05f, 0.05f, 0f, 8f,
                    0.05f, 0.85f, 0.05f, 0f, 5f,
                    0.05f, 0.05f, 0.80f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                )
                else -> null
            } ?: return null

            if (s >= 1.0f) {
                return android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(rawMatrixArray))
            }

            val identity = floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            val interpolated = FloatArray(20) { i ->
                identity[i] + (rawMatrixArray[i] - identity[i]) * s
            }
            return android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(interpolated))
        }

        fun fromName(name: String): ImageFilter {
            return when (name) {
                "None" -> None
                "Grayscale" -> Grayscale
                "Sepia" -> Sepia
                "Invert" -> Invert
                "CoolTint" -> CoolTint
                "WarmTint" -> WarmTint
                "Film" -> Film
                "TealOrange" -> TealOrange
                "BlackWhite" -> BlackWhite
                "HighContrast" -> HighContrast
                "Vintage" -> Vintage
                "BrightnessBoost" -> BrightnessBoost
                "SoftBlur" -> SoftBlur
                "Sharpen" -> Sharpen
                "Glow" -> Glow
                "Sketch" -> Sketch
                "Cartoon" -> Cartoon
                "HDR" -> HDR
                "Lomo" -> Lomo
                "Pastel" -> Pastel
                "Dramatic" -> Dramatic
                "GoldenHour" -> GoldenHour
                "Cyberpunk" -> Cyberpunk
                "Clarendon" -> Clarendon
                "Lark" -> Lark
                "Valencia" -> Valencia
                "Gingham" -> Gingham
                "Moon" -> Moon
                "Juno" -> Juno
                "Reyes" -> Reyes
                "Slumber" -> Slumber
                else -> None
            }
        }
    }
}