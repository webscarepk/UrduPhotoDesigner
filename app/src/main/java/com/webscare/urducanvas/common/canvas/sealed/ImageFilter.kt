package com.webscare.urducanvas.common.canvas.sealed

sealed class ImageFilter(val name: String) {

    object None : ImageFilter("None")
    object Invert : ImageFilter("Invert")
    object Grayscale : ImageFilter("Grayscale")
    object Sepia : ImageFilter("Sepia")
    data object CoolTint : ImageFilter("CoolTint")
    data object WarmTint : ImageFilter("WarmTint")
    data object Vintage : ImageFilter("Vintage")
    data object Film : ImageFilter("Film")
    data object TealOrange : ImageFilter("TealOrange")
    data object HighContrast : ImageFilter("HighContrast")
    data object BlackWhite : ImageFilter("BlackWhite")
    data object BrightnessBoost : ImageFilter("BrightnessBoost")
    data object SoftBlur : ImageFilter("SoftBlur")
    data object Sharpen : ImageFilter("Sharpen")
    data object Glow : ImageFilter("Glow")
    data object Sketch : ImageFilter("Sketch")
    data object Cartoon : ImageFilter("Cartoon")
    data object HDR : ImageFilter("HDR")
    data object Lomo : ImageFilter("Lomo")
    data object Pastel : ImageFilter("Pastel")
    data object Dramatic : ImageFilter("Dramatic")
    data object GoldenHour : ImageFilter("GoldenHour")
    data object Cyberpunk : ImageFilter("Cyberpunk")

    companion object {
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
                else -> None
            }
        }
    }
}