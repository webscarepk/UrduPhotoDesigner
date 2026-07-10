package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.domain.repo.FontsRepo
import javax.inject.Inject

class DeleteFontsUseCase @Inject constructor(private val fontsRepo: com.webscare.urducanvas.domain.repo.FontsRepo) {
    suspend operator fun invoke(fontEntity: com.webscare.urducanvas.data.model.FontEntity) {
        fontsRepo.deleteFont(fontEntity)
    }
}
