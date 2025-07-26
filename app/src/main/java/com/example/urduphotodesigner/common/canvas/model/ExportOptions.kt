package com.example.urduphotodesigner.common.canvas.model

import android.graphics.Bitmap

data class ExportOptions(
    val resolution: ExportResolution,
    val quality: ExportQuality,
    val format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG // New: PNG or JPEG
)