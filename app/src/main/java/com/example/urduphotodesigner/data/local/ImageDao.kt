package com.example.urduphotodesigner.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.urduphotodesigner.data.model.ImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(imageEntity: ImageEntity)

    @Query("SELECT * FROM images ORDER BY id DESC")
    fun getAllImages(): Flow<List<ImageEntity>>

    @Delete
    suspend fun delete(imageEntity: ImageEntity)

    @Update
    suspend fun update(imageEntity: ImageEntity)
}
