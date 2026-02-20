package com.webscare.urducanvas.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import java.io.Serializable

@Entity(tableName = "recent_exports")
data class ExportResult(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var imagePath: String,
    var jsonPath: String,
    var pdfPath: String? = null,
    var fileName: String,
    var fileSizeMB: Double,
    var resolution: String,
    var format: String,
    var quality: String,
    var canvasSize: com.webscare.urducanvas.common.canvas.model.CanvasSize,
    val exportDate: String,
    var updatedDate: String,
    var isExported: Boolean = false,
    var thumbnailPath: String? = null
) : Serializable