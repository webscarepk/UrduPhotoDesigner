package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.common.canvas.model.GradientItem
import com.example.urduphotodesigner.domain.repo.GradientRepo

class UpdateGradientUseCase(private val repo: GradientRepo) {
    suspend operator fun invoke(gradient: GradientItem) =
        repo.updateGradient(gradient)
}