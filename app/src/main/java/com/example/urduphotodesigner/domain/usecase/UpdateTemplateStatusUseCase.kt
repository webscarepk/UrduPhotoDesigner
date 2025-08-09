package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.domain.repo.TemplatesRepo
import javax.inject.Inject

class UpdateTemplateStatusUseCase @Inject constructor(
    private val templatesRepo: TemplatesRepo
) {

    suspend operator fun invoke(id: String, isDownloading: Boolean) {
        templatesRepo.updateStatusTemplates(id, isDownloading)
    }
}