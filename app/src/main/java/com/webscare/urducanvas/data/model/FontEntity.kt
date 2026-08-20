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
    val is_subscribed: Boolean = false,
    val created_at: String? = null,
    val updated_at: String? = null,
    var is_selected:Boolean = false,
    var is_downloaded:Boolean = false,
    var is_downloading:Boolean = false,
    var file_path: String? = null,
    var download_progress: Int = 0
)

fun List<FontEntity>.orderWithUrduFirst(sortAlphabetically: Boolean = true): List<FontEntity> {
    val urdu = this.filter { it.font_language.trim().equals("Urdu", ignoreCase = true) }
    val english = this.filter { it.font_language.trim().equals("English", ignoreCase = true) }
    val others = this.filter {
        !it.font_language.trim().equals("Urdu", ignoreCase = true) &&
        !it.font_language.trim().equals("English", ignoreCase = true)
    }

    val sortedUrdu = if (sortAlphabetically) urdu.sortedBy { it.font_name.lowercase() } else urdu
    val sortedEnglish = if (sortAlphabetically) english.sortedBy { it.font_name.lowercase() } else english
    val sortedOthers = if (sortAlphabetically) others.sortedBy { it.font_name.lowercase() } else others

    return sortedUrdu + sortedEnglish + sortedOthers
}

fun List<FontEntity>.shuffleWithUrduFirst(): List<FontEntity> {
    val urdu = this.filter { it.font_language.trim().equals("Urdu", ignoreCase = true) }.shuffled()
    val english = this.filter { it.font_language.trim().equals("English", ignoreCase = true) }.shuffled()
    val others = this.filter {
        !it.font_language.trim().equals("Urdu", ignoreCase = true) &&
        !it.font_language.trim().equals("English", ignoreCase = true)
    }.shuffled()

    return urdu + english + others
}
