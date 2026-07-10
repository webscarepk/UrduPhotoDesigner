package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.domain.repo.FontsRepo
import javax.inject.Inject

class UpdateFontsUseCase @Inject constructor(private val fontsRepo: com.webscare.urducanvas.domain.repo.FontsRepo) {
    suspend operator fun invoke(id: String, isDownloaded: Boolean, isDownloading: Boolean, filePath: String) {
        fontsRepo.updateFont(id, isDownloaded, isDownloading, filePath)
    }

    suspend operator fun invoke(fontEntity: com.webscare.urducanvas.data.model.FontEntity) {
        fontsRepo.updateFont(fontEntity)
    }

    suspend fun updatePremiumEntitlement(subscribed: Boolean) {
        fontsRepo.updatePremiumEntitlement(subscribed)
    }
}
