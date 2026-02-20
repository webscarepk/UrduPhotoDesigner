package com.webscare.urducanvas.domain.repo

import com.example.urduphotodesigner.data.model.ImageEntity
import kotlinx.coroutines.flow.Flow

interface ImagesRepo {
    fun fetchImages(): Flow<List<ImageEntity>>
    suspend fun insertImages(imageEntity: ImageEntity)
    suspend fun updateImage(imageEntity: ImageEntity)
    suspend fun deleteImages(imageEntity: ImageEntity)
}