package com.webscare.urducanvas.data.repository

import android.content.ContentValues.TAG
import android.util.Log
import com.webscare.urducanvas.data.local.AppDatabase
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.domain.repo.FontsRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FontsRepoImpl @Inject constructor(
    private val appDatabase: com.webscare.urducanvas.data.local.AppDatabase
) : com.webscare.urducanvas.domain.repo.FontsRepo {

    override fun fetchFonts(): Flow<List<com.webscare.urducanvas.data.model.FontEntity>> {
        return appDatabase.fontsDao().getAllFonts()
    }

    override suspend fun insertFonts(fontEntity: com.webscare.urducanvas.data.model.FontEntity) {
        appDatabase.fontsDao().insertFonts(fontEntity)
    }

    override suspend fun updateFont(
        id: String,
        isDownloaded: Boolean,
        isDownloading: Boolean,
        filePath: String
    ) {
        appDatabase.fontsDao().updateFont(id, isDownloaded, isDownloading, filePath)
    }

    override suspend fun updateStatusFont(id: String, isDownloading: Boolean) {
        appDatabase.fontsDao().updateFontStatus(id, isDownloading)
    }

    override suspend fun deleteFont(fontEntity: com.webscare.urducanvas.data.model.FontEntity) {
        appDatabase.fontsDao().delete(fontEntity)
    }

    override suspend fun updateFont(fontEntity: com.webscare.urducanvas.data.model.FontEntity) {
        appDatabase.fontsDao().update(fontEntity)
    }
}

