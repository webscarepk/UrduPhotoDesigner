package com.webscare.urducanvas.common.utils

object Converter {
    fun pxToInches(px: Float): Float = px / 96f
    fun inchesToPx(inches: Float): Int = (inches * 96).toInt()

    fun pxToCm(px: Float): Float = px / 37.8f
    fun cmToPx(cm: Float): Int = (cm * 37.8).toInt()
}
