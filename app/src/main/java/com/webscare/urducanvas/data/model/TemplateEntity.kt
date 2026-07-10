package com.webscare.urducanvas.data.model

import androidx.core.net.toUri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import java.io.File
import java.util.Locale

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: Int,
    val template_name: String,
    val thumbnail_url: String,
    val json_url: String,
    val canvas_width: Int,
    val canvas_height: Int,
    val category: String? = null,
    val subcategory: String? = null,
    val tags: List<String>,
    val is_premium: Boolean = false,
    val is_subscribed: Boolean = false,
    val is_popular: Boolean = false,
    val created_at: String? = null,
    val updated_at: String? = null,
    var is_downloaded: Boolean = false,
    var is_downloading: Boolean = false,
    var file_path: String? = null,
    var download_progress: Int = 0,
    var json_size: Long = 0,
)

fun TemplateEntity.toExportResultFinal(): ExportResult {
    val jsonFile = File(file_path!!)
    val fileName = jsonFile.name.ifBlank {
        json_url.toUri().lastPathSegment ?: "${category}_$id.json"
    }
    val sizeMb = if (jsonFile.exists()) {
        (jsonFile.length() / (1024.0 * 1024.0)).formatTwoDecimals()
    } else {
        0.0
    }

    return ExportResult(
        imagePath = thumbnail_url, // prefer local if you saved one
        jsonPath = file_path!!, // local JSON path
        fileName = fileName,
        fileSizeMB = sizeMb,
        resolution = "${canvas_width}x${canvas_height}",
        format = "JSON",
        quality = "-", // JSON has no quality
        canvasSize = CanvasSize(
            id = 0,
            name = "",
            width = canvas_width.toFloat(),
            height = canvas_height.toFloat(),
        ),
        exportDate = created_at ?: nowIso(),
        updatedDate = updated_at ?: nowIso(),
        isExported = true,
        isFromPremiumTemplate = is_premium, // carry it forward
        sourceTemplateId = id,
    )
}

private fun nowIso(): String {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    return fmt.format(java.util.Date())
}

private fun Double.formatTwoDecimals(): Double = kotlin.math.round(this * 100.0) / 100.0
