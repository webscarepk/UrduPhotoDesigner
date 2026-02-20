package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.data.model.FontsResponse
import com.webscare.urducanvas.domain.repo.FontsRepo
import com.webscare.urducanvas.domain.repo.TemplatesRepo
import javax.inject.Inject

class UpdateTemplatesUseCase @Inject constructor(
    private val templatesRepo: com.webscare.urducanvas.domain.repo.TemplatesRepo
) {
    suspend operator fun invoke(id: String, isDownloaded: Boolean, isDownloading: Boolean, progress: Int,filePath: String?) {
        templatesRepo.updateTemplates(id, isDownloaded, isDownloading, progress, filePath)
    }
}