package com.webscare.urducanvas.domain.usecase

import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.data.model.TrendResponse
import com.example.urduphotodesigner.domain.repo.FetchTrendsRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FetchAPITrendsUseCase @Inject constructor(
    private val fetchTrendsRepo: com.webscare.urducanvas.domain.repo.FetchTrendsRepo
) {
    operator fun invoke(): Flow<com.webscare.urducanvas.common.sealed.Response<com.webscare.urducanvas.data.model.TrendResponse>> {
        return fetchTrendsRepo.fetchTrends()
    }
}
