package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.domain.repo.GradientRepo

class DeleteGradientUseCase(private val repo: GradientRepo) {
  suspend operator fun invoke(id: Long) =
    repo.deleteGradientById(id)
}