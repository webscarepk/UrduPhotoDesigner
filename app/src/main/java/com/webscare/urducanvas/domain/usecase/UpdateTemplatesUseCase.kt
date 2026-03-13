package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.domain.repo.TemplatesRepo
import javax.inject.Inject

class UpdateTemplatesUseCase @Inject constructor(
    private val templatesRepo: TemplatesRepo
) {
    suspend operator fun invoke(id: String, isDownloaded: Boolean, isDownloading: Boolean, progress: Int,filePath: String?) {
        templatesRepo.updateTemplates(id, isDownloaded, isDownloading, progress, filePath)
    }

    suspend fun updatePremiumStatus(id: Int, isSubscribed: Boolean) {
        templatesRepo.updateTemplatePremiumStatus(id, isSubscribed)
    }
}