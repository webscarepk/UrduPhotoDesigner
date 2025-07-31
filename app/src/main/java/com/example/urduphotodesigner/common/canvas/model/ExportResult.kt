package com.example.urduphotodesigner.common.canvas.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "recent_exports")
data class ExportResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imagePath: String,
    val jsonPath: String,
    val fileName: String,
    val fileSizeMB: Double,
    val resolution: String,
    val format: String,
    val quality: String,
    val canvasSize: CanvasSize,
    val exportDate: String
): Serializable