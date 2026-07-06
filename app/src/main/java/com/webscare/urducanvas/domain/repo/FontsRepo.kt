package com.webscare.urducanvas.domain.repo

import com.webscare.urducanvas.data.model.FontEntity
import kotlinx.coroutines.flow.Flow

interface FontsRepo {
    fun fetchFonts(): Flow<List<FontEntity>>
    suspend fun insertFonts(fontEntity: FontEntity)
    suspend fun updateFont(id: String, isDownloaded: Boolean, isDownloading: Boolean, filePath: String)
    suspend fun updateStatusFont(id: String, isDownloading: Boolean)
    suspend fun deleteFont(fontEntity: FontEntity)
    suspend fun updateFont(fontEntity: FontEntity)
    suspend fun updatePremiumEntitlement(subscribed: Boolean)
}