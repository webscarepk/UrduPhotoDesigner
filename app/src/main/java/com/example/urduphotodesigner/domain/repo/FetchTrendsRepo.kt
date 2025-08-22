package com.example.urduphotodesigner.domain.repo

import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.data.model.TrendResponse
import kotlinx.coroutines.flow.Flow

interface FetchTrendsRepo {
    fun fetchTrends(): Flow<Response<TrendResponse>>
}