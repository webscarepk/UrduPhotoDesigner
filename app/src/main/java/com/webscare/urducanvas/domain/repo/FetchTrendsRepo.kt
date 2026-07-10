package com.webscare.urducanvas.domain.repo

import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.data.model.TrendResponse
import kotlinx.coroutines.flow.Flow

interface FetchTrendsRepo {
    fun fetchTrends(): Flow<Response<TrendResponse>>
}
