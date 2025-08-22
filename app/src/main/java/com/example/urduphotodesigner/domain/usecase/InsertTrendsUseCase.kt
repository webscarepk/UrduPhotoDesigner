package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.data.model.TrendResponse
import com.example.urduphotodesigner.domain.repo.TrendsRepo
import javax.inject.Inject

class InsertTrendsUseCase @Inject constructor(
    private val trendsRepo: TrendsRepo
) {
    suspend operator fun invoke(response: TrendResponse) {
        trendsRepo.insertTrends(response)
    }
}
