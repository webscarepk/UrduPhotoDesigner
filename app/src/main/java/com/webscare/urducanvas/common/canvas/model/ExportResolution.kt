package com.webscare.urducanvas.common.canvas.model

data class ExportResolution(
    val name: String,
    val width: Int,
    val height: Int,
    val scaleFactor: Float = 1f,
    val label: String = "", // e.g. "HD"
    val description: String = "", // e.g. "Standard quality"
    val estimatedSizeKb: Int = 0, // e.g. 800 for ~800 KB
    var isSelected: Boolean = false,
    var isPremium: Boolean = false,
)
