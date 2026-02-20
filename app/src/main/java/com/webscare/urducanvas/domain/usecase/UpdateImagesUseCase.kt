package com.webscare.urducanvas.domain.usecase

import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.domain.repo.ImagesRepo
import javax.inject.Inject

class UpdateImagesUseCase @Inject constructor(
    private val imagesRepo: com.webscare.urducanvas.domain.repo.ImagesRepo
) {
    suspend operator fun invoke(imageEntity: com.webscare.urducanvas.data.model.ImageEntity) {
        imagesRepo.updateImage(imageEntity)
    }
}