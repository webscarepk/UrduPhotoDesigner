package com.example.urduphotodesigner.common.canvas.model

import android.graphics.Bitmap

data class ExportResult(
    val bitmap: Bitmap,
    val imagePath: String,
    val jsonPath: String,
    val fileName: String,
    val fileSizeMB: Double,
    val resolution: String,
    val format: String,
    val quality: String,
    val exportDate: String
)