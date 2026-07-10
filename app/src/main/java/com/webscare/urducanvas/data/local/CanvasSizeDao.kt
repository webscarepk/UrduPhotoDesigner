package com.webscare.urducanvas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.webscare.urducanvas.data.model.CanvasSizeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CanvasSizeDao {
    @Query("SELECT * FROM canvas_sizes ORDER BY id ASC")
    fun getAllSizes(): Flow<List<CanvasSizeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sizes: List<CanvasSizeEntity>)

    @Query("SELECT COUNT(*) FROM canvas_sizes")
    suspend fun count(): Int
}
