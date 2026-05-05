package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.domain.repo.CanvasSizeRepo
import javax.inject.Inject

class GetCanvasSizesUseCase @Inject constructor(
    private val repo: CanvasSizeRepo
) {
    operator fun invoke() = repo.getLocalSizes()
}