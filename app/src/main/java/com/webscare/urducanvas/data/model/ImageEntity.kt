package com.webscare.urducanvas.data.model

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
    val parent_category: String? = null,
    val user_id: Int,
    val is_premium: Boolean = false,
    val is_subscribed: Boolean = false,
    var is_selected: Boolean = false,
    var bitmapData: String? = null,
    var created_at: String? = null,
    val is_recent: Boolean = false,
) {
    fun matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        return alt_text?.contains(query, ignoreCase = true) == true ||
            file_name.contains(query, ignoreCase = true) ||
            category.contains(query, ignoreCase = true)
    }
}
