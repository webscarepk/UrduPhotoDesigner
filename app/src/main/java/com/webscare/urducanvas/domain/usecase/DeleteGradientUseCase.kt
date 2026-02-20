package com.webscare.urducanvas.domain.usecase

import com.example.urduphotodesigner.domain.repo.GradientRepo

class DeleteGradientUseCase(private val repo: com.webscare.urducanvas.domain.repo.GradientRepo) {
  suspend operator fun invoke(id: Long) =
    repo.deleteGradientById(id)
}