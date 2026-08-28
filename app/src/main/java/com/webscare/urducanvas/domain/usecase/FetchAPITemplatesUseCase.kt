package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.data.model.FontsResponse
import com.webscare.urducanvas.data.model.TemplatesResponse
import com.webscare.urducanvas.domain.repo.FetchFontsRepo
import com.webscare.urducanvas.domain.repo.FetchTemplatesRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FetchAPITemplatesUseCase @Inject constructor(private val fetchTemplatesRepo: FetchTemplatesRepo) {
    operator fun invoke(): Flow<Response<TemplatesResponse>> {
        return fetchTemplatesRepo.fetchTemplates()
    }
}