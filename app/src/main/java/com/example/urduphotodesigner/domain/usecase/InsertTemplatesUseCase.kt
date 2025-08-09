package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.data.model.TemplatesResponse
import com.example.urduphotodesigner.domain.repo.TemplatesRepo
import javax.inject.Inject

class InsertTemplatesUseCase @Inject constructor(
    private val templatesRepo: TemplatesRepo
) {
    suspend operator fun invoke(templatesResponse: TemplatesResponse) {
        templatesResponse.templates.forEach { template ->
            templatesRepo.insertTemplates(template)
        }
    }

    suspend fun insertSingleTemplate(templateEntity: TemplateEntity) {
        templatesRepo.insertTemplates(templateEntity)
    }
}