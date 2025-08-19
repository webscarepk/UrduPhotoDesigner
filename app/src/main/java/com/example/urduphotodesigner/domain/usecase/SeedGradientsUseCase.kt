package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.common.canvas.model.GradientItem
import com.example.urduphotodesigner.domain.repo.GradientRepo

class SeedGradientsUseCase(private val repo: GradientRepo) {
    suspend operator fun invoke(defaults: List<GradientItem>) =
        repo.seedDefaultGradients(defaults)
}