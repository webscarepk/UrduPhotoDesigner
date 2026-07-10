package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.data.model.ImageResponse
import com.webscare.urducanvas.domain.repo.FetchImagesRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FetchAPIImagesUseCase @Inject constructor(private val fetchImagesRepo: com.webscare.urducanvas.domain.repo.FetchImagesRepo) {
    operator fun invoke(): Flow<com.webscare.urducanvas.common.sealed.Response<com.webscare.urducanvas.data.model.ImageResponse>> = fetchImagesRepo.fetchImages()
}
