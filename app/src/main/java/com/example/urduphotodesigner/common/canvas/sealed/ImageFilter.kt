package com.example.urduphotodesigner.common.canvas.sealed

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
                else -> None
            }
        }
    }
}