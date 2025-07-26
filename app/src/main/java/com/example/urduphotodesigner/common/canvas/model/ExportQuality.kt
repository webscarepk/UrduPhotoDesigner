package com.example.urduphotodesigner.common.canvas.model

data class ExportQuality(
    val label: String,           // "High Quality"
    val quality: Int,            // 100
    val description: String,     // "Maximum quality, larger file size"
    val extraSizePercent: Int    // 40 for "+40%"
)
