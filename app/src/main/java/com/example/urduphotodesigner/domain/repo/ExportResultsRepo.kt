package com.example.urduphotodesigner.domain.repo

import com.example.urduphotodesigner.data.model.ExportResult
import kotlinx.coroutines.flow.Flow

interface ExportResultsRepo {

    suspend fun insertExportResult(exportResult: ExportResult):Long

    suspend fun updateExportResult(exportResult: ExportResult)

    suspend fun deleteExportResult(exportResult: ExportResult)

    suspend fun getExportResultById(id: Long): ExportResult?

    fun getAllExportResults(): Flow<List<ExportResult>>
}
