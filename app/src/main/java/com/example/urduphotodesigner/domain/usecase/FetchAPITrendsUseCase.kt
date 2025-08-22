package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.data.model.TrendResponse
import com.example.urduphotodesigner.domain.repo.FetchTrendsRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FetchAPITrendsUseCase @Inject constructor(
    private val fetchTrendsRepo: FetchTrendsRepo
) {
    operator fun invoke(): Flow<Response<TrendResponse>> {
        return fetchTrendsRepo.fetchTrends()
    }
}
