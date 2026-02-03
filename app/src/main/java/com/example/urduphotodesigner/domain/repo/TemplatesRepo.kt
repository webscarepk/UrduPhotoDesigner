package com.example.urduphotodesigner.domain.repo

import com.example.urduphotodesigner.data.model.TemplateEntity
import kotlinx.coroutines.flow.Flow

interface TemplatesRepo {
    fun fetchTemplates(): Flow<List<TemplateEntity>>
    suspend fun insertTemplates(templateEntity: TemplateEntity)
    suspend fun updateTemplates(id: String, isDownloaded: Boolean, isDownloading: Boolean, progress: Int,filePath: String?)
    suspend fun updateStatusTemplates(id: String, isDownloading: Boolean)
}