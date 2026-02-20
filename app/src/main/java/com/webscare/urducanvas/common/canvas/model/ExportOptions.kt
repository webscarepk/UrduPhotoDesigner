package com.webscare.urducanvas.common.canvas.model

data class ExportOptions(
    val resolution: ExportResolution,
    val quality: ExportQuality,
    val format: ExportFormat
)