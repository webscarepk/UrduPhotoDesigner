package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.data.model.FontsResponse
import com.example.urduphotodesigner.domain.repo.FontsRepo
import com.example.urduphotodesigner.domain.repo.TemplatesRepo
import javax.inject.Inject

class UpdateTemplatesUseCase @Inject constructor(
    private val templatesRepo: TemplatesRepo
) {
    suspend operator fun invoke(id: String, isDownloaded: Boolean, isDownloading: Boolean, progress: Int,filePath: String?) {
        templatesRepo.updateTemplates(id, isDownloaded, isDownloading, progress, filePath)
    }
}