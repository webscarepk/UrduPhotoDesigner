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
    var fileSizeMB: Double,
    var resolution: String,
    var format: String,
    var quality: String,
    var canvasSize: CanvasSize,
    val exportDate: String,
    var updatedDate: String,
    var isExported: Boolean = false
): Serializable