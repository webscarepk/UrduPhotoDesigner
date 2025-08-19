package com.example.urduphotodesigner.data.repository

import com.example.urduphotodesigner.data.model.ExportResult
import com.example.urduphotodesigner.data.local.ExportResultsDao
import com.example.urduphotodesigner.domain.repo.ExportResultsRepo
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportResultsRepositoryImpl @Inject constructor(
    private val exportResultsDao: ExportResultsDao
) : ExportResultsRepo {

    override suspend fun insertExportResult(exportResult: ExportResult) :Long  {
        return exportResultsDao.insertCanvasTemplate(exportResult)
    }

    override suspend fun updateExportResult(exportResult: ExportResult) {
        exportResultsDao.updateCanvasTemplate(exportResult)
    }

    override suspend fun deleteExportResult(exportResult: ExportResult) {
        try {
            exportResult.imagePath.let {
                val imageFile = File(it)
                if (imageFile.exists()) imageFile.delete()
            }

            exportResult.jsonPath.let {
                val jsonFile = File(it)
                if (jsonFile.exists()) jsonFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        exportResultsDao.deleteCanvasTemplate(exportResult)
    }

    override suspend fun getExportResultById(id: Long): ExportResult? {
        return exportResultsDao.getCanvasTemplateById(id)
    }

    override fun getAllExportResults(): Flow<List<ExportResult>> {
        return exportResultsDao.getAllCanvasTemplates()
    }
}
