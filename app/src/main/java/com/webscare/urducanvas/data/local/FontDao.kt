package com.webscare.urducanvas.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.webscare.urducanvas.data.model.FontEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FontDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFonts(fonts: FontEntity)

    @Query("SELECT * FROM fonts ORDER BY id DESC")
    fun getAllFonts(): Flow<List<FontEntity>>

    @Query(
        "UPDATE fonts SET is_downloaded = :isDownloaded, is_downloading = :isDownloading, file_path = :filePath WHERE id = :id",
    )
    suspend fun updateFont(id: String, isDownloaded: Boolean, isDownloading: Boolean, filePath: String)

    @Query("UPDATE fonts SET is_downloading = :isDownloading WHERE id = :id")
    suspend fun updateFontStatus(id: String, isDownloading: Boolean)

    @Delete
    suspend fun delete(font: FontEntity)

    @Update
    suspend fun update(font: FontEntity)

    @Query("UPDATE fonts SET is_subscribed = :isSubscribed WHERE is_premium = 1")
    suspend fun updatePremiumEntitlement(isSubscribed: Boolean)
}
