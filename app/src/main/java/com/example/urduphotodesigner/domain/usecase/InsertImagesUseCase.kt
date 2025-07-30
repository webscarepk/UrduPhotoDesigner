package com.example.urduphotodesigner.domain.usecase

import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.data.model.ImageResponse
import com.example.urduphotodesigner.domain.repo.ImagesRepo
import javax.inject.Inject

class InsertImagesUseCase @Inject constructor(
    private val imagesRepo: ImagesRepo
) {
    suspend operator fun invoke(imageResponse: ImageResponse) {
        imageResponse.image.forEach { image ->
            imagesRepo.insertImages(image)
        }
    }

    suspend fun insertSingleImage(imageEntity: ImageEntity) {
        imagesRepo.insertImages(imageEntity)
    }
}