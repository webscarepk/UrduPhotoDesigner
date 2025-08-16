package com.example.urduphotodesigner.data.model

data class FontLanguages(
    val id: Int,
    val font_category: String,
    var is_selected:Boolean = false,
    val categories: List<FontCategory> = emptyList()
)

data class FontCategory(
    val name: String,
    val isSelected: Boolean = false
)
