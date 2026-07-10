package com.webscare.urducanvas.domain.repo

import com.webscare.urducanvas.data.model.ExportResult
import kotlinx.coroutines.flow.Flow

interface ExportResultsRepo {

    suspend fun insertExportResult(exportResult: ExportResult): Long

    suspend fun updateExportResult(exportResult: ExportResult)

    suspend fun deleteExportResult(exportResult: ExportResult)

    suspend fun getExportResultById(id: Long): ExportResult?

    fun getAllExportResults(): Flow<List<ExportResult>>
}
