package com.webscare.urducanvas.domain.usecase

import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.data.model.FontsResponse
import com.example.urduphotodesigner.domain.repo.FetchFontsRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FetchAPIFontsUseCase @Inject constructor(private val fetchFontsRepo: com.webscare.urducanvas.domain.repo.FetchFontsRepo) {
    operator fun invoke(): Flow<com.webscare.urducanvas.common.sealed.Response<com.webscare.urducanvas.data.model.FontsResponse>> {
        return fetchFontsRepo.fetchFonts()
    }
}