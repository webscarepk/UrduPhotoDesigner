package com.example.urduphotodesigner.common.canvas.model

import android.graphics.Bitmap

data class ExportFormat(
    val name: String,                     // "JPEG"
    val format: Bitmap.CompressFormat? = null,   // Bitmap.CompressFormat.JPEG
    val description: String,             // "Compressed, smaller size"
    val tags: List<String>,               // ["Small size", "Good for photos", "No transparency"]
    var isSelected: Boolean = false
)
