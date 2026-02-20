package com.webscare.urducanvas.data.mapper

import com.example.urduphotodesigner.common.canvas.model.GradientItem
import com.example.urduphotodesigner.data.model.GradientEntity

fun com.webscare.urducanvas.data.model.GradientEntity.toDomain() =
    _root_ide_package_.com.webscare.urducanvas.common.canvas.model.GradientItem(
        id = id,
        colors = colors,
        positions = positions,
        angle = angle,
        scale = scale,
        type = type,
        radialRadiusFactor = radialRadiusFactor,
        sweepStartAngle = sweepStartAngle,
        centerX = centerX,
        centerY = centerY
    )

fun com.webscare.urducanvas.common.canvas.model.GradientItem.toEntity() =
    _root_ide_package_.com.webscare.urducanvas.data.model.GradientEntity(
        id = id,
        colors = colors,
        positions = positions,
        angle = angle,
        scale = scale,
        type = type,
        radialRadiusFactor = radialRadiusFactor,
        sweepStartAngle = sweepStartAngle,
        centerX = centerX,
        centerY = centerY
    )