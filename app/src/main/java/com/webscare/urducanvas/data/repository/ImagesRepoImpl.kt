package com.webscare.urducanvas.data.repository

import com.webscare.urducanvas.data.local.AppDatabase
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.domain.repo.ImagesRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ImagesRepoImpl @Inject constructor(private val appDatabase: AppDatabase) : ImagesRepo {

    override fun fetchImages(): Flow<List<ImageEntity>> = appDatabase.imagesDao().getAllImages()

    override suspend fun insertImages(imageEntity: ImageEntity) {
        appDatabase.imagesDao().insertImage(imageEntity)
    }

    override suspend fun updateImage(imageEntity: ImageEntity) {
        appDatabase.imagesDao().update(imageEntity)
    }

    override suspend fun deleteImages(imageEntity: ImageEntity) {
        appDatabase.imagesDao().delete(imageEntity)
    }

    override suspend fun markAsRecent(id: Int) {
        appDatabase.imagesDao().markAsRecent(id)
    }

    override suspend fun updatePremiumEntitlement(subscribed: Boolean) {
        appDatabase.imagesDao().updatePremiumEntitlement(subscribed)
    }
}
