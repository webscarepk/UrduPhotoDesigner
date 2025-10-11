package com.example.urduphotodesigner.common.canvas.model

import android.graphics.Path
import com.example.urduphotodesigner.common.canvas.enums.BrushStyle
import java.io.Serializable

data class StrokeData(
    val path: Path,
    var color: Int,
    var thickness: Float,
    var hardness: Float,
    var style: BrushStyle,
    var gradient: GradientItem? = null
) : Serializable