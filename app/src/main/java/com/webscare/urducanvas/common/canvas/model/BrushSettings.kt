package com.webscare.urducanvas.common.canvas.model

import android.graphics.Color
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import java.io.Serializable

data class BrushSettings(
    var defaultColor: Int,
    var defaultThickness: Float,
    var defaultHardness: Float,
    var style: BrushStyle,
    var gradient: GradientItem? = null
) : Serializable{

    companion object {
        fun default(): BrushSettings = BrushSettings(
            defaultColor = Color.BLACK,
            defaultThickness = 20f,
            defaultHardness = 1f,
            style = BrushStyle.PEN,
            gradient = null
        )
    }
}