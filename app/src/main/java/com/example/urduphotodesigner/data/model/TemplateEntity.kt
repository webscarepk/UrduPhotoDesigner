package com.example.urduphotodesigner.data.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.data.model.ExportResult
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
    val subcategory: String,
    val tags: List<String>,
    val is_premium: Boolean,
    val created_at: String? = null,
    val updated_at: String? = null,
    var is_downloaded:Boolean = false,
    var is_downloading:Boolean = false,
    var file_path: String? = null,
    var download_progress: Int = 0
)
    fun TemplateEntity.toExportResultFinal(
    ): ExportResult {
        val jsonFile = File(file_path!!)
        val fileName = jsonFile.name.ifBlank {
            Uri.parse(json_url).lastPathSegment ?: "template_${id}.json"
        }
        val sizeMb = if (jsonFile.exists()) {
            (jsonFile.length() / (1024.0 * 1024.0)).formatTwoDecimals()
        } else 0.0

        return ExportResult(
            imagePath   = thumbnail_url,  // prefer local if you saved one
            jsonPath    = file_path!!,                    // local JSON path
            fileName    = fileName,
            fileSizeMB  = sizeMb,
            resolution  = "${canvas_width}x${canvas_height}",
            format      = "JSON",
            quality     = "-",                              // JSON has no quality
            canvasSize  = CanvasSize(
                name =  "",
                width = canvas_width.toFloat(),
                height = canvas_height.toFloat()
            ),
            exportDate  = created_at?: nowIso(),
            updatedDate = updated_at ?: nowIso(),
            isExported  = true,
        )
    }

    private fun nowIso(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        return fmt.format(java.util.Date())
    }

    private fun Double.formatTwoDecimals(): Double =
        kotlin.math.round(this * 100.0) / 100.0