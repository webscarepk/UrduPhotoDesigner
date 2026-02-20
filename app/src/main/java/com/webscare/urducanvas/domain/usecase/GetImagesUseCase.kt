package com.webscare.urducanvas.domain.usecase

import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.domain.repo.ImagesRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetImagesUseCase @Inject constructor(
    private val imagesRepo: com.webscare.urducanvas.domain.repo.ImagesRepo
) {
    operator fun invoke(): Flow<List<com.webscare.urducanvas.data.model.ImageEntity>> {
        return imagesRepo.fetchImages()
    }
}
