package com.webscare.urducanvas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.webscare.urducanvas.data.model.GradientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GradientDao {
    @Query("SELECT * FROM gradient_presets ORDER BY id DESC")
    fun getAll(): Flow<List<GradientEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(presets: List<GradientEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: GradientEntity)

    @Query("DELETE FROM gradient_presets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Update
    suspend fun updateGradient(entity: GradientEntity)

    @Query("SELECT COUNT(*) FROM gradient_presets")
    suspend fun count(): Int
}
