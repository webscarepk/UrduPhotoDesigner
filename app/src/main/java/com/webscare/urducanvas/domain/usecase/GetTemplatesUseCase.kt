package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.data.model.TemplateEntity
import com.webscare.urducanvas.domain.repo.TemplatesRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTemplatesUseCase @Inject constructor(
    private val templatesRepo: com.webscare.urducanvas.domain.repo.TemplatesRepo
) {
    operator fun invoke(): Flow<List<com.webscare.urducanvas.data.model.TemplateEntity>> {
        return templatesRepo.fetchTemplates()
    }
}
