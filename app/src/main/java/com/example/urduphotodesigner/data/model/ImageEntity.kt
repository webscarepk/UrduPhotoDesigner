package com.example.urduphotodesigner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val file_name: String,
    val file_url: String,
    val file_size: String,
    val alt_text: String? = null,
    val category: String,
    val user_id: Int,
    var is_selected: Boolean = false,
    var bitmapData: String? = null,
    var created_at: String? = null,
    val is_recent: Boolean = false
)
