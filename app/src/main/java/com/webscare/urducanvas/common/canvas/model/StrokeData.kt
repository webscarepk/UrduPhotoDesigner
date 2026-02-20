package com.webscare.urducanvas.common.canvas.model

import android.graphics.Path
import android.graphics.PathMeasure
import com.example.urduphotodesigner.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import java.io.Serializable

data class StrokeData(
    @Transient var path: Path? = Path(),
    var color: Int = 0,
    var thickness: Float = 0f,
    var hardness: Float = 1f,
    var style: BrushStyle,
    var gradient: GradientItem? = null,
    var pathData: List<Float>? = null // Serialized path points
) : Serializable {

    /** Convert Path to list of float coordinates for saving */
    fun serializePath(step: Float = 2f) {
        val currentPath = path ?: run {
            pathData = emptyList()
            return
        }

        if (currentPath.isEmpty) {
            pathData = emptyList()
            return
        }
        val measure = PathMeasure(path, false)
        val points = mutableListOf<Float>()
        val pos = FloatArray(2)
        var distance = 0f

        while (distance < measure.length) {
            measure.getPosTan(distance, pos, null)
            points.add(pos[0])
            points.add(pos[1])
            distance += step
        }

        pathData = points
    }

    /** Restore Path from saved coordinates when loading JSON */
    fun restorePath() {
        val pts = pathData ?: return
        if (pts.size < 4) return
        path = Path()
        path?.moveTo(pts[0], pts[1])
        for (i in 2 until pts.size step 2) {
            path?.lineTo(pts[i], pts[i + 1])
        }
    }
}