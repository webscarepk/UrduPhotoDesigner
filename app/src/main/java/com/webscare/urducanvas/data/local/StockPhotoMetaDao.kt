package com.webscare.urducanvas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.webscare.urducanvas.data.model.StockPhotoMeta
import kotlinx.coroutines.flow.Flow

@Dao
interface StockPhotoMetaDao {
 
    @Query("SELECT * FROM stock_photo_meta WHERE superQuery = :query")
    suspend fun getMeta(query: String): StockPhotoMeta?
 
    @Query("SELECT * FROM stock_photo_meta")
    fun observeAll(): Flow<List<StockPhotoMeta>>
 
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: StockPhotoMeta)
 
    @Query("DELETE FROM stock_photo_meta WHERE superQuery = :query")
    suspend fun delete(query: String)
 
    @Query("DELETE FROM stock_photo_meta")
    suspend fun deleteAll()
}