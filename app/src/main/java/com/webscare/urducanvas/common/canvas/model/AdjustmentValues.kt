package com.webscare.urducanvas.common.canvas.model

import java.io.Serializable

data class AdjustmentValues(
    var brightness: Float = 0f,       // -100 to +100
    var contrast: Float = 1f,         // 0.5 to 1.5
    var shadows: Float = 0f,          // -100 to +100
    var saturation: Float = 1f,       // 0 to 2
    var vibrance: Float = 1f,         // 0 to 2
    var temperature: Float = 0f,      // -100 to +100
    var tint: Float = 0f,             // -100 to +100
    var highlights: Float = 0f,       // -100 to +100
    var sharpness: Float = 0f,        // 0 to 5
    var clarity: Float = 0f,          // -100 to +100
    var fade: Float = 0f,              // 0 to 100
    var blur: Float = 0f                // 0 → 25
) : Serializable