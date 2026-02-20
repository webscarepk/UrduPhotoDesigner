package com.webscare.urducanvas.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fonts")
data class FontEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val file_name: String,
    val font_name: String,
    val font_category: String,
    val font_language: String,
    val file_url: String,
    val file_size: String,
    val font_image: String? = null,
    val image_url: String,
    val alt_text: String? = null,
    val user_id: Int,
    val is_premium: Boolean = false,
    val created_at: String? = null,
    val updated_at: String? = null,
    var is_selected:Boolean = false,
    var is_downloaded:Boolean = false,
    var is_downloading:Boolean = false,
    var file_path: String? = null,
    var download_progress: Int = 0
)
