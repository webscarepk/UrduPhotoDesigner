package com.webscare.urducanvas.data.model

import androidx.room.Entity

@Entity(
    tableName = "trend_template_cross_ref",
    primaryKeys = ["trendId", "templateId"]
)
data class TrendTemplateCrossRef(
    val trendId: Int,
    val templateId: Int
)