package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.common.canvas.model.GradientItem
import com.example.urduphotodesigner.domain.repo.GradientRepo
import kotlinx.coroutines.flow.Flow

class GetAllGradientsUseCase(private val repo: GradientRepo) {
    operator fun invoke(): Flow<List<GradientItem>> =
        repo.getAllGradients()
}