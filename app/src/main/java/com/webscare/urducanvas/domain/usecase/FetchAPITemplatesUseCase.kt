package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.data.model.TemplatesResponse
import com.webscare.urducanvas.domain.repo.FetchTemplatesRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FetchAPITemplatesUseCase @Inject constructor(private val fetchTemplatesRepo: com.webscare.urducanvas.domain.repo.FetchTemplatesRepo) {
    operator fun invoke(): Flow<com.webscare.urducanvas.common.sealed.Response<com.webscare.urducanvas.data.model.TemplatesResponse>> = fetchTemplatesRepo.fetchTemplates()
}
