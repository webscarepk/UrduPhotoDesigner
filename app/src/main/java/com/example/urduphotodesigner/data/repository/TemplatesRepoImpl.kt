package com.example.urduphotodesigner.data.repository

import com.example.urduphotodesigner.data.local.AppDatabase
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.domain.repo.TemplatesRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TemplatesRepoImpl @Inject constructor(
    private val appDatabase: AppDatabase
) : TemplatesRepo {

    override fun fetchTemplates(): Flow<List<TemplateEntity>> {
        return appDatabase.allTemplatesDao().getAllTemplates()
    }

    override suspend fun insertTemplates(templateEntity: TemplateEntity) {
        val dao = appDatabase.allTemplatesDao()
        val existing = dao.getTemplateById(templateEntity.id)

        val merged = if (existing != null) {
            templateEntity.copy(
                is_downloaded = existing.is_downloaded,
                is_downloading = existing.is_downloading,
                file_path = existing.file_path,
                download_progress = existing.download_progress
            )
        } else templateEntity

        dao.insertTemplate(merged)
    }

    override suspend fun updateTemplates(
        id: String,
        isDownloaded: Boolean,
        isDownloading: Boolean,
        progress: Int,
        filePath: String?
    ) {
        appDatabase.allTemplatesDao().updateTemplate(id, isDownloaded, isDownloading,progress, filePath)
    }

    override suspend fun updateStatusTemplates(id: String, isDownloading: Boolean) {
        appDatabase.allTemplatesDao().updateTemplateStatus(id, isDownloading)
    }
}

