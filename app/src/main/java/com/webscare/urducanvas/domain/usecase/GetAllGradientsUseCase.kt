package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.domain.repo.GradientRepo
import kotlinx.coroutines.flow.Flow

class GetAllGradientsUseCase(private val repo: com.webscare.urducanvas.domain.repo.GradientRepo) {
    operator fun invoke(): Flow<List<com.webscare.urducanvas.common.canvas.model.GradientItem>> = repo.getAllGradients()
}
