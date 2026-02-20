package com.webscare.urducanvas.domain.repo

import com.webscare.urducanvas.data.model.TrendResponse
import com.webscare.urducanvas.data.model.TrendWithTemplates
import kotlinx.coroutines.flow.Flow

interface TrendsRepo {
    fun fetchCachedTrends(): Flow<List<TrendWithTemplates>>
    suspend fun insertTrends(response: TrendResponse)
}