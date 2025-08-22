package com.example.urduphotodesigner.data.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class TrendWithTemplates(
    @Embedded val trend: TrendEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TrendTemplateCrossRef::class,
            parentColumn = "trendId",
            entityColumn = "templateId"
        )
    )
    val templates: List<TemplateEntity>
)
