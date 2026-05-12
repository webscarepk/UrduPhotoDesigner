package com.webscare.urducanvas.data.model

data class ImagesData(
    val tabs: List<String>,
    val imagesByCategory: Map<String, List<ImageEntity>>,
    val recents: List<ImageEntity>
) {
    companion object {
        // Empty initial value — tabs list is empty so UI shows nothing until DB loads
        val Initial = ImagesData(
            tabs = emptyList(),
            imagesByCategory = emptyMap(),
            recents = emptyList()
        )
    }
}