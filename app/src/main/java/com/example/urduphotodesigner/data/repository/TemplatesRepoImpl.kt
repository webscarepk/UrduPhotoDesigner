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
        appDatabase.allTemplatesDao().insertTemplate(templateEntity)
    }

    override suspend fun updateTemplates(
        id: String,
        isDownloaded: Boolean,
        isDownloading: Boolean,
        filePath: String
    ) {
        appDatabase.allTemplatesDao().updateTemplate(id, isDownloaded, isDownloading, filePath)
    }

    override suspend fun updateStatusTemplates(id: String, isDownloading: Boolean) {
        appDatabase.allTemplatesDao().updateTemplateStatus(id, isDownloading)
    }
}

