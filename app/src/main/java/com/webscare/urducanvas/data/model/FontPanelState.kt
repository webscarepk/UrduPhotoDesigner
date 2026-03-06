package com.webscare.urducanvas.data.model

data class FontPanelState(
    val selectedLanguage: String = "All",
    val selectedCategory: String? = null,
    val scrollPositionIndex: Int = 0,
    val scrollPositionOffset: Int = 0
)