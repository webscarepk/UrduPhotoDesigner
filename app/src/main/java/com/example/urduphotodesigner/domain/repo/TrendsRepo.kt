package com.example.urduphotodesigner.domain.repo

import com.example.urduphotodesigner.data.model.TrendResponse
import com.example.urduphotodesigner.data.model.TrendWithTemplates
import kotlinx.coroutines.flow.Flow

interface TrendsRepo {
    fun fetchCachedTrends(): Flow<List<TrendWithTemplates>>
    suspend fun insertTrends(response: TrendResponse)
}