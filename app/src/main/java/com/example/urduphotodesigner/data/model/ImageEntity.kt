package com.example.urduphotodesigner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey val id: Int,
    val file_name: String,
    val file_url: String,
    val file_size: String,
    val alt_text: String,
    val category: String,
    val user_id: Int,
    var is_selected: Boolean = false,
    var bitmapData: String? = null
)
