package com.webscare.urducanvas.domain.usecase

import com.example.urduphotodesigner.data.model.TrendWithTemplates
import com.example.urduphotodesigner.domain.repo.TrendsRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTrendsUseCase @Inject constructor(
    private val trendsRepo: com.webscare.urducanvas.domain.repo.TrendsRepo
) {
    operator fun invoke(): Flow<List<com.webscare.urducanvas.data.model.TrendWithTemplates>> {
        return trendsRepo.fetchCachedTrends()
    }
}
