package com.example.urduphotodesigner.data.repository

import com.example.urduphotodesigner.common.canvas.model.ExportResult
import com.example.urduphotodesigner.data.local.ExportResultsDao
import com.example.urduphotodesigner.domain.repo.ExportResultsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportResultsRepositoryImpl @Inject constructor(
    private val exportResultsDao: ExportResultsDao
) : ExportResultsRepository {

    override suspend fun insertExportResult(exportResult: ExportResult) {
        exportResultsDao.insertCanvasTemplate(exportResult)
    }

    override suspend fun updateExportResult(exportResult: ExportResult) {
        exportResultsDao.updateCanvasTemplate(exportResult)
    }

    override suspend fun deleteExportResult(exportResult: ExportResult) {
        exportResultsDao.deleteCanvasTemplate(exportResult)
    }

    override suspend fun getExportResultById(id: Long): ExportResult? {
        return exportResultsDao.getCanvasTemplateById(id)
    }

    override fun getAllExportResults(): Flow<List<ExportResult>> {
        return exportResultsDao.getAllCanvasTemplates()
    }
}
