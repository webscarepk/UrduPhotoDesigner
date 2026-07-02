package com.webscare.urducanvas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import com.webscare.urducanvas.data.model.TemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AllTemplatesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: TemplateEntity):Long

    @Query("SELECT * FROM templates WHERE id = :id LIMIT 1")
    suspend fun getTemplateById(id: Int): TemplateEntity?

    @Query("UPDATE templates SET is_downloaded = :isDownloaded, is_downloading = :isDownloading, download_progress = :progress, file_path = :filePath WHERE id = :id")
    suspend fun updateTemplate(id: String, isDownloaded: Boolean, isDownloading: Boolean, progress: Int, filePath: String?)

    @Query("UPDATE templates SET is_downloading = :isDownloading WHERE id = :id")
    suspend fun updateTemplateStatus(id: String, isDownloading: Boolean)

    @Delete
    suspend fun deleteTemplate(template: TemplateEntity)

    @Query("SELECT * FROM templates ORDER BY id DESC")
    fun getAllTemplates(): Flow<List<TemplateEntity>>

    @Query("UPDATE templates SET is_subscribed = :isSubscribed WHERE id = :id")
    suspend fun updateTemplatePremiumStatus(id: Int, isSubscribed: Boolean)
}
