package com.example.urduphotodesigner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import com.example.urduphotodesigner.data.model.TemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AllTemplatesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: TemplateEntity):Long

    @Query("UPDATE templates SET is_downloaded = :isDownloaded, is_downloading = :isDownloading, file_path = :filePath WHERE id = :id")
    fun updateTemplate(id: String, isDownloaded: Boolean, isDownloading: Boolean, filePath: String)

    @Query("UPDATE templates SET is_downloading = :isDownloading WHERE id = :id")
    fun updateTemplateStatus(id: String, isDownloading: Boolean)

    @Delete
    suspend fun deleteTemplate(template: TemplateEntity)

    @Query("SELECT * FROM templates ORDER BY id DESC")
    fun getAllTemplates(): Flow<List<TemplateEntity>>
}
