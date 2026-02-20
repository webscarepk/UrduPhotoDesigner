package com.webscare.urducanvas.domain.usecase

import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.data.model.ImageResponse
import com.example.urduphotodesigner.domain.repo.FetchImagesRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FetchAPIImagesUseCase @Inject constructor(private val fetchImagesRepo: com.webscare.urducanvas.domain.repo.FetchImagesRepo) {
    operator fun invoke(): Flow<com.webscare.urducanvas.common.sealed.Response<com.webscare.urducanvas.data.model.ImageResponse>> {
        return fetchImagesRepo.fetchImages()
    }
}