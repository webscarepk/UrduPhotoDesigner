package com.webscare.urducanvas.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.webscare.urducanvas.common.canvas.enums.GradientType

@Entity(tableName = "gradient_presets")
data class GradientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val colors: List<Int>,
    val positions: List<Float>,
    val angle: Float,
    val scale: Float,
    val type: GradientType,
    val radialRadiusFactor: Float,
    val sweepStartAngle: Float,
    val centerX: Float,
    val centerY: Float,
)
