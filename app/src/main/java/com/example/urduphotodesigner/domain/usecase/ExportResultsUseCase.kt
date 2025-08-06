package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.common.canvas.model.ExportResult
import com.example.urduphotodesigner.domain.repo.ExportResultsRepository
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Singleton
class ExportResultsUseCase @Inject constructor(
    private val repository: ExportResultsRepository
) {

    suspend fun insertExportResult(exportResult: ExportResult) :Long {
        return repository.insertExportResult(exportResult)
    }

    suspend fun updateExportResult(exportResult: ExportResult) {
        repository.updateExportResult(exportResult)
    }

    suspend fun deleteExportResult(exportResult: ExportResult) {
        repository.deleteExportResult(exportResult)
    }

    suspend fun getExportResultById(id: Long): ExportResult? {
        return repository.getExportResultById(id)
    }

    fun getAllExportResults(): Flow<List<ExportResult>> {
        return repository.getAllExportResults()
    }
}
