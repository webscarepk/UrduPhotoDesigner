package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.domain.repo.TemplatesRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTemplatesUseCase @Inject constructor(
    private val templatesRepo: TemplatesRepo
) {
    operator fun invoke(): Flow<List<TemplateEntity>> {
        return templatesRepo.fetchTemplates()
    }
}
