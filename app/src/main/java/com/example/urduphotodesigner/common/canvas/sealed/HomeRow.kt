package com.example.urduphotodesigner.common.canvas.sealed

import com.example.urduphotodesigner.data.model.TemplateEntity

sealed class HomeRow {
    data class CategoryRow(
        val title: String,
        val templates: List<TemplateEntity>
    ) : HomeRow()
}
