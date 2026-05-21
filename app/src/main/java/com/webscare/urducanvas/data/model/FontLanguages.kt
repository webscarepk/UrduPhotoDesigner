package com.webscare.urducanvas.data.model

data class FontLanguages(
    val id: Int,
    val name: String,
    var is_selected:Boolean = false,
    val categories: List<FontCategory> = emptyList()
)

data class FontCategory(
    val id: Int,
    val name: String,
    val isSelected: Boolean = false
)
