package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.data.model.TrendWithTemplates
import com.example.urduphotodesigner.domain.repo.TrendsRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTrendsUseCase @Inject constructor(
    private val trendsRepo: TrendsRepo
) {
    operator fun invoke(): Flow<List<TrendWithTemplates>> {
        return trendsRepo.fetchCachedTrends()
    }
}
