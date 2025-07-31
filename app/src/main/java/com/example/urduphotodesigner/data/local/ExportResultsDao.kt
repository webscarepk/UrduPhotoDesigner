package com.example.urduphotodesigner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.example.urduphotodesigner.common.canvas.model.ExportResult
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportResultsDao {

    @Insert
    suspend fun insertCanvasTemplate(canvasTemplate: ExportResult)

    @Update
    suspend fun updateCanvasTemplate(canvasTemplate: ExportResult)

    @Delete
    suspend fun deleteCanvasTemplate(canvasTemplate: ExportResult)

    @Query("SELECT * FROM recent_exports WHERE id = :id LIMIT 1")
    suspend fun getCanvasTemplateById(id: Long): ExportResult?

    @Query("SELECT * FROM recent_exports")
    fun getAllCanvasTemplates(): Flow<List<ExportResult>>
}
