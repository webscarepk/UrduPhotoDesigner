package com.webscare.urducanvas.domain.usecase

import com.example.urduphotodesigner.data.model.ExportResult
import com.example.urduphotodesigner.domain.repo.ExportResultsRepo
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Singleton
class ExportResultsUseCase @Inject constructor(
    private val repository: com.webscare.urducanvas.domain.repo.ExportResultsRepo
) {

    suspend fun insertExportResult(exportResult: com.webscare.urducanvas.data.model.ExportResult) :Long {
        return repository.insertExportResult(exportResult)
    }

    suspend fun updateExportResult(exportResult: com.webscare.urducanvas.data.model.ExportResult) {
        repository.updateExportResult(exportResult)
    }

    suspend fun deleteExportResult(exportResult: com.webscare.urducanvas.data.model.ExportResult) {
        repository.deleteExportResult(exportResult)
    }

    suspend fun getExportResultById(id: Long): com.webscare.urducanvas.data.model.ExportResult? {
        return repository.getExportResultById(id)
    }

    fun getAllExportResults(): Flow<List<com.webscare.urducanvas.data.model.ExportResult>> {
        return repository.getAllExportResults()
    }
}
