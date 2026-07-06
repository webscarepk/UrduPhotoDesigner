package com.webscare.urducanvas.domain.repo

import com.webscare.urducanvas.data.model.ImageEntity
import kotlinx.coroutines.flow.Flow

interface ImagesRepo {
    fun fetchImages(): Flow<List<ImageEntity>>
    suspend fun insertImages(imageEntity: ImageEntity)
    suspend fun updateImage(imageEntity: ImageEntity)
    suspend fun deleteImages(imageEntity: ImageEntity)
    suspend fun markAsRecent(id: Int)
    suspend fun updatePremiumEntitlement(subscribed: Boolean)
}