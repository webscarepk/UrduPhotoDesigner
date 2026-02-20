package com.webscare.urducanvas.domain.usecase

import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.data.model.FontsResponse
import com.example.urduphotodesigner.data.model.TemplatesResponse
import com.example.urduphotodesigner.domain.repo.FetchFontsRepo
import com.example.urduphotodesigner.domain.repo.FetchTemplatesRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FetchAPITemplatesUseCase @Inject constructor(private val fetchTemplatesRepo: com.webscare.urducanvas.domain.repo.FetchTemplatesRepo) {
    operator fun invoke(): Flow<com.webscare.urducanvas.common.sealed.Response<com.webscare.urducanvas.data.model.TemplatesResponse>> {
        return fetchTemplatesRepo.fetchTemplates()
    }
}