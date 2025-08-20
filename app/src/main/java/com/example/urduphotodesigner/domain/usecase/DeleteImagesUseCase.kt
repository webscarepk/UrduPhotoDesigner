package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.domain.repo.ImagesRepo
import javax.inject.Inject

class DeleteImagesUseCase @Inject constructor(
    private val imagesRepo: ImagesRepo
) {
    suspend operator fun invoke(imageEntity: ImageEntity) {
        imagesRepo.deleteImages(imageEntity)
    }
}