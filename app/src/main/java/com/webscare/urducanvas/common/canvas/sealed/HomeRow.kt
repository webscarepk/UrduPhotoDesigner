package com.webscare.urducanvas.common.canvas.sealed

import com.example.urduphotodesigner.data.model.TemplateEntity
import com.webscare.urducanvas.data.model.TemplateEntity

sealed class HomeRow {
    data class CategoryRow(
        val title: String,
        val templates: List<TemplateEntity>
    ) : HomeRow()

    data class TrendRow(val title: String, val templates: List<TemplateEntity>) : HomeRow()

}
