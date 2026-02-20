package com.webscare.urducanvas.domain.usecase

import com.example.urduphotodesigner.common.canvas.model.GradientItem
import com.example.urduphotodesigner.domain.repo.GradientRepo

class UpdateGradientUseCase(private val repo: com.webscare.urducanvas.domain.repo.GradientRepo) {
    suspend operator fun invoke(gradient: com.webscare.urducanvas.common.canvas.model.GradientItem) =
        repo.updateGradient(gradient)
}