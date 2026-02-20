package com.webscare.urducanvas.domain.usecase

import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.data.model.TemplatesResponse
import com.example.urduphotodesigner.domain.repo.TemplatesRepo
import javax.inject.Inject

class InsertTemplatesUseCase @Inject constructor(
    private val templatesRepo: com.webscare.urducanvas.domain.repo.TemplatesRepo
) {
    suspend operator fun invoke(templatesResponse: com.webscare.urducanvas.data.model.TemplatesResponse) {
        templatesResponse.templates.forEach { template ->
            templatesRepo.insertTemplates(template)
        }
    }

    suspend fun insertSingleTemplate(templateEntity: com.webscare.urducanvas.data.model.TemplateEntity) {
        templatesRepo.insertTemplates(templateEntity)
    }
}