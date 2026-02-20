package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.domain.repo.TemplatesRepo
import javax.inject.Inject

class UpdateTemplateStatusUseCase @Inject constructor(
    private val templatesRepo: com.webscare.urducanvas.domain.repo.TemplatesRepo
) {

    suspend operator fun invoke(id: String, isDownloading: Boolean) {
        templatesRepo.updateStatusTemplates(id, isDownloading)
    }
}