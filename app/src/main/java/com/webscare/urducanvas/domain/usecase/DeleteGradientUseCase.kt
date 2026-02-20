package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.domain.repo.GradientRepo

class DeleteGradientUseCase(private val repo: com.webscare.urducanvas.domain.repo.GradientRepo) {
  suspend operator fun invoke(id: Long) =
    repo.deleteGradientById(id)
}