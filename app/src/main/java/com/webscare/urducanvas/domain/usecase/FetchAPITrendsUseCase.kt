package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.data.model.TrendResponse
import com.webscare.urducanvas.domain.repo.FetchTrendsRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FetchAPITrendsUseCase @Inject constructor(
    private val fetchTrendsRepo: com.webscare.urducanvas.domain.repo.FetchTrendsRepo,
) {
    operator fun invoke(): Flow<com.webscare.urducanvas.common.sealed.Response<com.webscare.urducanvas.data.model.TrendResponse>> = fetchTrendsRepo.fetchTrends()
}
