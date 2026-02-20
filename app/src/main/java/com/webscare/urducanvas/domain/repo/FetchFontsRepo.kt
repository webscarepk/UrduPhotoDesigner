package com.webscare.urducanvas.domain.repo

import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.data.model.FontsResponse
import kotlinx.coroutines.flow.Flow

interface FetchFontsRepo {
    fun fetchFonts(): Flow<Response<FontsResponse>>
}