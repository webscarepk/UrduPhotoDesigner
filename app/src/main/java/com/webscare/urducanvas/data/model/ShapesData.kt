package com.webscare.urducanvas.data.model

data class ShapesData(
    val tabs: List<String>,
    val imagesByCategory: Map<String, List<ImageEntity>>,
    val recents: List<ImageEntity>,
) {
    companion object {
        fun initial() = ShapesData(
            tabs = emptyList(),
            imagesByCategory = emptyMap(),
            recents = emptyList(),
        )
    }
}
