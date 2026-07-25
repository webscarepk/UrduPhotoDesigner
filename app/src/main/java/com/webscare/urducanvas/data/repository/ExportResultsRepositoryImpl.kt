package com.webscare.urducanvas.data.repository

import com.webscare.urducanvas.data.model.ExportResult
import com.webscare.urducanvas.data.local.ExportResultsDao
import com.webscare.urducanvas.domain.repo.ExportResultsRepo
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportResultsRepositoryImpl @Inject constructor(
    private val exportResultsDao: com.webscare.urducanvas.data.local.ExportResultsDao
) : com.webscare.urducanvas.domain.repo.ExportResultsRepo {

    override suspend fun insertExportResult(exportResult: com.webscare.urducanvas.data.model.ExportResult) :Long  {
        return exportResultsDao.insertCanvasTemplate(exportResult)
    }

    override suspend fun updateExportResult(exportResult: com.webscare.urducanvas.data.model.ExportResult) {
        exportResultsDao.updateCanvasTemplate(exportResult)
    }

    override suspend fun deleteExportResult(exportResult: com.webscare.urducanvas.data.model.ExportResult) {
        try {
            exportResult.imagePath.let { path ->
                if (!path.startsWith("http", ignoreCase = true) && !path.contains("downloaded_templates", ignoreCase = true)) {
                    val imageFile = File(path)
                    if (imageFile.exists()) imageFile.delete()
                }
            }

            exportResult.jsonPath.let { path ->
                if (!path.contains("downloaded_templates", ignoreCase = true) && !path.contains("/template_", ignoreCase = true)) {
                    val jsonFile = File(path)
                    if (jsonFile.exists()) jsonFile.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        exportResultsDao.deleteCanvasTemplate(exportResult)
    }

    override suspend fun getExportResultById(id: Long): com.webscare.urducanvas.data.model.ExportResult? {
        return exportResultsDao.getCanvasTemplateById(id)
    }

    override fun getAllExportResults(): Flow<List<com.webscare.urducanvas.data.model.ExportResult>> {
        return exportResultsDao.getAllCanvasTemplates()
    }
}
