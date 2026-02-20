package com.webscare.urducanvas.data.repository

import android.content.ContentValues.TAG
import android.util.Log
import com.example.urduphotodesigner.data.local.AppDatabase
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.domain.repo.ImagesRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ImagesRepoImpl @Inject constructor(
    private val appDatabase: com.webscare.urducanvas.data.local.AppDatabase
) : com.webscare.urducanvas.domain.repo.ImagesRepo {

    override fun fetchImages(): Flow<List<com.webscare.urducanvas.data.model.ImageEntity>> {
        return appDatabase.imagesDao().getAllImages()
    }

    override suspend fun insertImages(imageEntity: com.webscare.urducanvas.data.model.ImageEntity) {
        appDatabase.imagesDao().insertImage(imageEntity)
    }

    override suspend fun updateImage(imageEntity: com.webscare.urducanvas.data.model.ImageEntity) {
        appDatabase.imagesDao().update(imageEntity)
    }

    override suspend fun deleteImages(imageEntity: com.webscare.urducanvas.data.model.ImageEntity) {
        appDatabase.imagesDao().delete(imageEntity)
    }
}

