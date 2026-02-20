package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.domain.repo.GradientRepo

class UpdateGradientUseCase(private val repo: com.webscare.urducanvas.domain.repo.GradientRepo) {
    suspend operator fun invoke(gradient: com.webscare.urducanvas.common.canvas.model.GradientItem) =
        repo.updateGradient(gradient)
}