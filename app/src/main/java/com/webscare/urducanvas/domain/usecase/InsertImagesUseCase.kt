package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.data.model.ImageResponse
import com.webscare.urducanvas.domain.repo.ImagesRepo
import javax.inject.Inject

class InsertImagesUseCase @Inject constructor(private val imagesRepo: com.webscare.urducanvas.domain.repo.ImagesRepo) {
    suspend operator fun invoke(imageResponse: com.webscare.urducanvas.data.model.ImageResponse) {
        imageResponse.image.forEach { image ->
            imagesRepo.insertImages(image)
        }
    }

    suspend fun insertSingleImage(imageEntity: com.webscare.urducanvas.data.model.ImageEntity) {
        imagesRepo.insertImages(imageEntity)
    }
}
