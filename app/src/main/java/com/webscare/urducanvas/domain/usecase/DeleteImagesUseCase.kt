package com.webscare.urducanvas.domain.usecase

import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.domain.repo.ImagesRepo
import javax.inject.Inject

class DeleteImagesUseCase @Inject constructor(
    private val imagesRepo: com.webscare.urducanvas.domain.repo.ImagesRepo
) {
    suspend operator fun invoke(imageEntity: com.webscare.urducanvas.data.model.ImageEntity) {
        imagesRepo.deleteImages(imageEntity)
    }
}