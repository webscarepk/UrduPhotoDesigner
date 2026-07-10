package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.data.model.TrendResponse
import com.webscare.urducanvas.domain.repo.TrendsRepo
import javax.inject.Inject

class InsertTrendsUseCase @Inject constructor(private val trendsRepo: com.webscare.urducanvas.domain.repo.TrendsRepo) {
    suspend operator fun invoke(response: com.webscare.urducanvas.data.model.TrendResponse) {
        trendsRepo.insertTrends(response)
    }
}
