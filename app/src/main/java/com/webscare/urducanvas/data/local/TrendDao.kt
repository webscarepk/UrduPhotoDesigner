package com.webscare.urducanvas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.urduphotodesigner.data.model.TrendEntity
import com.example.urduphotodesigner.data.model.TrendTemplateCrossRef
import com.example.urduphotodesigner.data.model.TrendWithTemplates
import kotlinx.coroutines.flow.Flow

@Dao
interface TrendDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrends(trends: List<TrendEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<TrendTemplateCrossRef>)

    @Query("DELETE FROM trends WHERE id NOT IN (:ids)")
    suspend fun deleteTrendsNotIn(ids: Set<Int>)

    @Transaction
    @Query("SELECT * FROM trends")
    fun getTrendsWithTemplates(): Flow<List<TrendWithTemplates>>

    @Query("DELETE FROM trends")
    suspend fun clearTrends()

    @Query("DELETE FROM trend_template_cross_ref")
    suspend fun clearCrossRefs()
}
