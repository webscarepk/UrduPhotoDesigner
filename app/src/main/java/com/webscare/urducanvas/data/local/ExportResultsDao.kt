package com.webscare.urducanvas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import com.example.urduphotodesigner.data.model.ExportResult
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportResultsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanvasTemplate(canvasTemplate: ExportResult):Long

    @Update
    suspend fun updateCanvasTemplate(canvasTemplate: ExportResult)

    @Delete
    suspend fun deleteCanvasTemplate(canvasTemplate: ExportResult)

    @Query("SELECT * FROM recent_exports WHERE id = :id LIMIT 1")
    suspend fun getCanvasTemplateById(id: Long): ExportResult?

    @Query("SELECT * FROM recent_exports ORDER BY updatedDate DESC")
    fun getAllCanvasTemplates(): Flow<List<ExportResult>>
}
